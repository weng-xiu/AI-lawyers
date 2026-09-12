package ai.lawyers.system.service.lawyers.queue;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Range;
import org.springframework.data.redis.connection.stream.Consumer;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.PendingMessage;
import org.springframework.data.redis.connection.stream.PendingMessages;
import org.springframework.data.redis.connection.stream.PendingMessagesSummary;
import org.springframework.data.redis.connection.stream.ReadOffset;
import org.springframework.data.redis.connection.stream.RecordId;
import org.springframework.data.redis.connection.stream.StreamOffset;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import ai.lawyers.common.utils.MdcUtils;
import ai.lawyers.system.service.lawyers.metrics.HotlineMetrics;

/**
 * T2-3 Redis Stream 轻量异步队列底座（不引 RabbitMQ/Kafka）。
 *
 * <p>生产：{@link #enqueue} 通过 XADD 投递 JSON 字符串消息；Redis 不可用时返回 false，
 * 由调用方降级为同步直写，保证主流程可用。</p>
 *
 * <p>消费：应用启动时为每个队列创建消费组（MKSTREAM 自动建 Stream），并启动 daemon 监听线程
 * 以 XREADGROUP 阻塞拉取、交给注册的 {@link StreamMessageHandler} 处理，成功 XACK；
 * 处理失败不 ACK（消息留 pending），下一轮先以 {@code XREADGROUP ... 0} 重读本消费者 PEL
 * 完成退避重投，真实投递次数通过 {@code XPENDING} 详情获取（N1 修复：旧版恒按上限判定，
 * 首败即进死信）；超过 {@code maxDeliveries} 才转入死信 Stream 并 ACK。
 * 周期性 XAUTOCLAIM（Redis 6.2+；旧版降级 XPENDING+XCLAIM）接管死消费者/重启实例遗留 PEL。
 * at-least-once + 消费端幂等保证不丢不重。</p>
 *
 * <p>应急回退：{@code queue.stream.retry-enabled=false} 恢复旧行为（不重读 PEL、失败即转死信）。</p>
 *
 * @author ai-lawyers
 */
@Service
public class StreamQueueService
{
    private static final Logger log = LoggerFactory.getLogger(StreamQueueService.class);

    /** 消息体字段名（XADD 的 field） */
    public static final String PAYLOAD_FIELD = "payload";

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    /** T5-1：队列积压 / 死信指标（未引入 micrometer 时内部静默） */
    @Autowired(required = false)
    private HotlineMetrics metrics;

    /** T2-3 总开关，关闭后 enqueue 直接返回 false，调用方走同步降级 */
    @Value("${queue.stream.enabled:true}")
    private boolean enabled;

    /** 消费组名（多实例同组竞争消费） */
    @Value("${queue.stream.group:ai-lawyers}")
    private String groupName;

    /** 单条消息最大投递次数，超过转死信 */
    @Value("${queue.stream.max-deliveries:5}")
    private long maxDeliveries;

    /**
     * N1 重试开关：true（默认）按 XPENDING 真实投递次数退避重投；
     * false 恢复旧行为（不重读 PEL、处理失败即转死信），仅应急回退用。
     */
    @Value("${queue.stream.retry-enabled:true}")
    private boolean retryEnabled;

    /** 接管其它消费者（含重启后的旧实例）遗留 PEL 的最小空闲毫秒数 */
    @Value("${queue.stream.reclaim-min-idle-ms:60000}")
    private long reclaimMinIdleMs;

    /** 死消费者 PEL 接管尝试间隔（毫秒） */
    private static final long RECLAIM_INTERVAL_MS = 30_000L;

    /** 单次接管最大消息条数 */
    private static final long RECLAIM_BATCH = 100L;

    /** XREADGROUP 阻塞超时（毫秒） */
    @Value("${queue.stream.block-ms:5000}")
    private long blockMs;

    /** 队列名 → 处理器 */
    private final Map<String, StreamMessageHandler> handlers = new ConcurrentHashMap<>();

    /** 队列名 → 监听线程运行标志 */
    private final Map<String, Boolean> running = new ConcurrentHashMap<>();

    private ExecutorService listenerPool;

    private final AtomicInteger threadSeq = new AtomicInteger(1);

    @PostConstruct
    public void init()
    {
        if (!enabled)
        {
            log.info("T2-3 Redis Stream 队列已禁用（queue.stream.enabled=false），所有异步场景走同步直写");
            return;
        }
        // @PostConstruct 执行顺序不可控：本 bean 的 init() 可能先于各 Dispatcher 的 init() 执行，
        // 此时 handlers 为空。使用 CachedThreadPool（每个队列一个常驻消费线程），避免 FixedThreadPool
        // 大小不足导致后注册的队列消费者排队永远得不到执行。
        listenerPool = Executors.newCachedThreadPool(new ThreadFactory()
        {
            @Override
            public Thread newThread(Runnable r)
            {
                Thread t = new Thread(r, "stream-consumer-" + threadSeq.getAndIncrement());
                t.setDaemon(true);
                return t;
            }
        });
        // init() 时 handlers 可能为空（注册顺序问题），后续 registerHandler 会自行调 startConsumer
        for (String queue : handlers.keySet())
        {
            startConsumer(queue);
        }
        log.info("T2-3 Redis Stream 队列底座已初始化 group={} queues={}", groupName, handlers.keySet());
    }

    /**
     * 注册某队列的消息处理器并启动消费（由各业务场景在自己的 @PostConstruct 中调用）。
     */
    public void registerHandler(String queue, StreamMessageHandler handler)
    {
        handlers.put(queue, handler);
        if (enabled && listenerPool != null)
        {
            startConsumer(queue);
        }
    }

    /**
     * 投递消息（XADD）。
     *
     * @return true 已入队；false 队列禁用或 Redis 异常，调用方应同步直写降级
     */
    public boolean enqueue(String queue, String payloadJson)
    {
        if (!enabled)
        {
            return false;
        }
        try
        {
            String key = QueueNames.streamKey(queue);
            stringRedisTemplate.opsForStream().add(
                    MapRecord.create(key, singletonPayload(payloadJson)));
            return true;
        }
        catch (Exception e)
        {
            log.warn("Stream[{}] 投递失败，调用方应同步降级: {}", queue, e.getMessage());
            return false;
        }
    }

    /** 队列是否启用（供业务判断是否走异步） */
    public boolean isEnabled()
    {
        return enabled;
    }

    /** 查询某队列 pending（已投递未 ACK）消息数，供 T5 积压指标 */
    public long pendingCount(String queue)
    {
        try
        {
            PendingMessagesSummary summary = stringRedisTemplate.opsForStream()
                    .pending(QueueNames.streamKey(queue), groupName);
            return summary == null ? 0L : summary.getTotalPendingMessages();
        }
        catch (Exception e)
        {
            return -1L;
        }
    }

    // ------------------------------------------------------------------ 消费侧

    private void startConsumer(String queue)
    {
        if (Boolean.TRUE.equals(running.putIfAbsent(queue, Boolean.TRUE)))
        {
            return; // 已在运行
        }
        // T5-1：注册队列积压 gauge（按队列名打标签，Prometheus 拉取时实时 XPENDING 求值）
        if (metrics != null)
        {
            metrics.gaugeQueueSize(queue, this,
                    svc -> Math.max(0d, (double) ((StreamQueueService) svc).pendingCount(queue)));
        }
        String key = QueueNames.streamKey(queue);
        ensureGroup(key);
        // T5-1：消费线程为自建池（非 Spring @Async），用 MdcUtils 透传 traceId；
        // 消费线程无 HTTP 上下文时 MDC 为空，各消息处理可按需 setTraceId 串联
        listenerPool.submit(MdcUtils.wrap(() -> consumeLoop(queue, key)));
    }

    /**
     * 幂等确保 Stream 与消费组存在：Stream 不存在时先 XADD 一条初始化消息（createGroup 要求 Stream 已存在），
     * 再创建消费组；组已存在（BUSYGROUP）忽略。初始化消息以 "$" 之后读，不会被消费。
     */
    private void ensureGroup(String key)
    {
        try
        {
            Boolean exists = stringRedisTemplate.hasKey(key);
            if (!Boolean.TRUE.equals(exists))
            {
                stringRedisTemplate.opsForStream().add(
                        MapRecord.create(key, singletonPayload("__init__")));
            }
            stringRedisTemplate.opsForStream().createGroup(key, ReadOffset.from("0"), groupName);
            log.info("Stream[{}] 消费组[{}]已就绪", key, groupName);
        }
        catch (Exception e)
        {
            // BUSYGROUP（组已存在）等情况忽略
            log.debug("Stream[{}] 消费组初始化（可能已存在）: {}", key, e.getMessage());
        }
    }

    private void consumeLoop(String queue, String key)
    {
        StreamMessageHandler handler = handlers.get(queue);
        Consumer consumer = Consumer.from(groupName, instanceConsumerName());
        long lastReclaimMs = 0L;
        while (Boolean.TRUE.equals(running.get(queue)))
        {
            try
            {
                // N1：周期性接管死消费者/重启实例遗留的 PEL（消费者名每次启动不同）
                long now = System.currentTimeMillis();
                if (retryEnabled && now - lastReclaimMs >= RECLAIM_INTERVAL_MS)
                {
                    reclaimStale(queue, key, consumer);
                    lastReclaimMs = now;
                }
                if (retryEnabled)
                {
                    // 先处理本消费者 PEL 中未 ACK 的重试消息（XREADGROUP ... 0 立即返回，不阻塞）
                    @SuppressWarnings("rawtypes")
                    List<MapRecord<String, Object, Object>> pending = stringRedisTemplate.opsForStream().read(
                            consumer,
                            org.springframework.data.redis.connection.stream.StreamReadOptions
                                    .empty().count(10),
                            StreamOffset.create(key, ReadOffset.from("0")));
                    if (pending != null && !pending.isEmpty())
                    {
                        for (MapRecord<String, Object, Object> record : pending)
                        {
                            processRecord(queue, key, handler, record);
                        }
                        continue;
                    }
                }
                // 再阻塞拉取新消息（>）
                @SuppressWarnings("rawtypes")
                List<MapRecord<String, Object, Object>> records = stringRedisTemplate.opsForStream().read(
                        consumer,
                        org.springframework.data.redis.connection.stream.StreamReadOptions
                                .empty().count(10).block(Duration.ofMillis(blockMs)),
                        StreamOffset.create(key, ReadOffset.lastConsumed()));
                if (records == null || records.isEmpty())
                {
                    continue;
                }
                for (MapRecord<String, Object, Object> record : records)
                {
                    processRecord(queue, key, handler, record);
                }
            }
            catch (Exception e)
            {
                if (Boolean.TRUE.equals(running.get(queue)))
                {
                    log.warn("Stream[{}] 消费循环异常，2s 后继续: {}", queue, e.getMessage());
                    sleepQuietly(2000);
                }
            }
        }
    }

    private void processRecord(String queue, String key, StreamMessageHandler handler,
                               MapRecord<String, Object, Object> record)
    {
        RecordId id = record.getId();
        try
        {
            Object payload = record.getValue().get(PAYLOAD_FIELD);
            String json = payload == null ? null : payload.toString();
            if (handler != null)
            {
                handler.onMessage(json);
            }
            acknowledge(key, id);
        }
        catch (Exception e)
        {
            // N1：真实投递次数取 XPENDING 详情；重试关闭时按旧行为首败即转死信
            long attempts = retryEnabled ? deliveryAttempt(key, id) : maxDeliveries;
            log.warn("Stream[{}] 消息处理失败 id={} attempts={}/{}: {}",
                    queue, id, attempts, maxDeliveries, e.getMessage());
            if (attempts >= maxDeliveries)
            {
                // 超过最大投递：转死信并 ACK，避免毒消息永久占 pending
                moveToDeadLetter(queue, record, e.getMessage());
                acknowledge(key, id);
                log.error("Stream[{}] 消息超过最大投递次数{}，转死信 id={}", queue, maxDeliveries, id);
                return;
            }
            // 未超限：不 ACK，消息留本消费者 PEL，下一轮 XREADGROUP ... 0 按退避重投
            sleepQuietly(backoffMs(attempts));
        }
    }

    /**
     * N1：失败重投退避（500ms 起步，封顶 8s），attempts 为 XPENDING 真实投递次数（≥1）。
     */
    static long backoffMs(long attempts)
    {
        return Math.min(500L * Math.max(1L, attempts), 8000L);
    }

    /**
     * 查询单条消息在本组 PEL 中的真实已投递次数。
     *
     * <p>N1 修复：spring-data-redis 2.5 的 {@link PendingMessage#getTotalDeliveryCount()}
     * 已暴露投递计数（Redis XPENDING range 形式第四字段）；查不到（已被接管/ACK）或
     * 查询异常时保守按 1 处理，继续重试而非误转死信。</p>
     */
    long deliveryAttempt(String key, RecordId id)
    {
        try
        {
            String idStr = id.getValue();
            PendingMessages pending = stringRedisTemplate.opsForStream()
                    .pending(key, groupName, Range.closed(idStr, idStr), 1L);
            for (PendingMessage pm : pending)
            {
                if (pm.getId().equals(id))
                {
                    return Math.max(1L, pm.getTotalDeliveryCount());
                }
            }
            return 1L;
        }
        catch (Exception e)
        {
            log.warn("Stream XPENDING 查询投递次数失败 key={} id={}: {}", key, id, e.getMessage());
            return 1L;
        }
    }

    /**
     * N1：接管死消费者/重启实例遗留在 PEL 中的消息（本服务消费者名每次启动变化，
     * 旧实例未 ACK 的消息不会被本消费者的 "0" 重读读到，必须显式接管）。
     *
     * <p>优先 XAUTOCLAIM（Redis 6.2+，原子）；命令不存在（Redis 5.x）时降级为
     * XPENDING 枚举空闲消息 + XCLAIM 显式接管。接管后的消息在下一轮 PEL 重读中投递。</p>
     */
    private void reclaimStale(String queue, String key, Consumer consumer)
    {
        String self = consumer.getName();
        try
        {
            stringRedisTemplate.execute((RedisCallback<Object>) connection ->
                    connection.execute("XAUTOCLAIM",
                            utf8(key), utf8(groupName), utf8(self),
                            utf8(String.valueOf(reclaimMinIdleMs)), utf8("0-0"),
                            utf8("COUNT"), utf8(String.valueOf(RECLAIM_BATCH)), utf8("JUSTID")));
            return;
        }
        catch (Exception e)
        {
            if (!isUnknownCommand(e))
            {
                log.debug("Stream[{}] XAUTOCLAIM 接管异常（不影响消费）: {}", queue, e.getMessage());
                return;
            }
            reclaimStaleLegacy(queue, key, self);
        }
    }

    /** Redis 5.x 降级路径：XPENDING 枚举全组 PEL，筛选空闲超时且归属他人的消息后 XCLAIM。 */
    private void reclaimStaleLegacy(String queue, String key, String self)
    {
        try
        {
            @SuppressWarnings("unchecked")
            List<Object> rows = (List<Object>) stringRedisTemplate.execute((RedisCallback<Object>) connection ->
                    connection.execute("XPENDING",
                            utf8(key), utf8(groupName), utf8("-"), utf8("+"),
                            utf8(String.valueOf(RECLAIM_BATCH))));
            if (rows == null || rows.isEmpty())
            {
                return;
            }
            List<byte[]> claimIds = new ArrayList<>();
            for (Object row : rows)
            {
                PendingInfo info = parsePendingRow(row);
                if (info != null && info.idleMs >= reclaimMinIdleMs && !self.equals(info.consumer))
                {
                    claimIds.add(utf8(info.id));
                }
            }
            if (claimIds.isEmpty())
            {
                return;
            }
            List<byte[]> args = new ArrayList<>();
            args.add(utf8(key));
            args.add(utf8(groupName));
            args.add(utf8(self));
            args.add(utf8(String.valueOf(reclaimMinIdleMs)));
            args.addAll(claimIds);
            args.add(utf8("JUSTID"));
            stringRedisTemplate.execute((RedisCallback<Object>) connection ->
                    connection.execute("XCLAIM", args.toArray(new byte[0][])));
            log.info("Stream[{}] XCLAIM 接管死消费者消息 {} 条（Redis 5.x 降级路径）", queue, claimIds.size());
        }
        catch (Exception e)
        {
            log.debug("Stream[{}] XPENDING/XCLAIM 降级接管异常（不影响消费）: {}", queue, e.getMessage());
        }
    }

    /**
     * 解析 XPENDING range 形式单行：[id, consumer, idleMs, deliveryCount]（Lettuce 原始 byte[] 结构）。
     *
     * @return 解析失败返回 null
     */
    @SuppressWarnings("unchecked")
    static PendingInfo parsePendingRow(Object row)
    {
        try
        {
            List<Object> fields = (List<Object>) row;
            if (fields == null || fields.size() < 4)
            {
                return null;
            }
            String id = new String((byte[]) fields.get(0), StandardCharsets.UTF_8);
            String consumer = new String((byte[]) fields.get(1), StandardCharsets.UTF_8);
            long idleMs = Long.parseLong(new String((byte[]) fields.get(2), StandardCharsets.UTF_8).trim());
            long count = Long.parseLong(new String((byte[]) fields.get(3), StandardCharsets.UTF_8).trim());
            return new PendingInfo(id, consumer, idleMs, count);
        }
        catch (RuntimeException e)
        {
            return null;
        }
    }

    /** XPENDING 单行解析结果（包级可见供测试）。 */
    static final class PendingInfo
    {
        final String id;
        final String consumer;
        final long idleMs;
        final long deliveryCount;

        PendingInfo(String id, String consumer, long idleMs, long deliveryCount)
        {
            this.id = id;
            this.consumer = consumer;
            this.idleMs = idleMs;
            this.deliveryCount = deliveryCount;
        }
    }

    private static boolean isUnknownCommand(Exception e)
    {
        String msg = e.getMessage();
        return msg != null && msg.toLowerCase().contains("unknown command");
    }

    private static byte[] utf8(String s)
    {
        return s.getBytes(StandardCharsets.UTF_8);
    }

    private void acknowledge(String key, RecordId id)
    {
        try
        {
            stringRedisTemplate.opsForStream().acknowledge(key, groupName, id);
        }
        catch (Exception e)
        {
            log.warn("Stream ACK 失败 key={} id={}: {}", key, id, e.getMessage());
        }
    }

    private void moveToDeadLetter(String queue, MapRecord<String, Object, Object> record, String error)
    {
        try
        {
            Object payload = record.getValue().get(PAYLOAD_FIELD);
            String deadPayload = "{\"originStream\":\"" + QueueNames.streamKey(queue)
                    + "\",\"originId\":\"" + record.getId() + "\",\"error\":\"" + escape(error)
                    + "\",\"payload\":" + (payload == null ? "null" : payload.toString()) + "}";
            stringRedisTemplate.opsForStream().add(
                    MapRecord.create(QueueNames.deadLetterKey(queue),
                            singletonPayload(deadPayload)));
            // T5-1：死信计数（毒消息超投递次数）
            if (metrics != null)
            {
                metrics.incrementQueueDead(queue);
            }
        }
        catch (Exception e)
        {
            log.error("Stream[{}] 死信转存失败: {}", queue, e.getMessage());
        }
    }

    @PreDestroy
    public void shutdown()
    {
        running.clear();
        if (listenerPool != null)
        {
            listenerPool.shutdown();
            try
            {
                if (!listenerPool.awaitTermination(8, TimeUnit.SECONDS))
                {
                    listenerPool.shutdownNow();
                }
            }
            catch (InterruptedException e)
            {
                listenerPool.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
    }

    private String instanceConsumerName()
    {
        return "consumer-" + Integer.toHexString(System.identityHashCode(this));
    }

    /** 构造 XADD 单字段 body（Java 8 无 Map.of，用 Collections.singletonMap 等价替代） */
    private static Map<String, String> singletonPayload(String payload)
    {
        return java.util.Collections.singletonMap(PAYLOAD_FIELD, payload);
    }

    private static String escape(String s)
    {
        return s == null ? "" : s.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private static void sleepQuietly(long ms)
    {
        try
        {
            Thread.sleep(ms);
        }
        catch (InterruptedException e)
        {
            Thread.currentThread().interrupt();
        }
    }
}
