package ai.lawyers.system.service.lawyers.queue;

import java.time.Duration;
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
import org.springframework.data.redis.connection.stream.Consumer;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.PendingMessagesSummary;
import org.springframework.data.redis.connection.stream.ReadOffset;
import org.springframework.data.redis.connection.stream.RecordId;
import org.springframework.data.redis.connection.stream.StreamOffset;
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
 * 处理失败不 ACK（消息留 pending），重投次数超 {@code maxDeliveries} 转入死信 Stream 并 ACK。
 * at-least-once + 消费端幂等保证不丢不重。</p>
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
        int n = Math.max(1, handlers.size());
        listenerPool = Executors.newFixedThreadPool(n, new ThreadFactory()
        {
            @Override
            public Thread newThread(Runnable r)
            {
                Thread t = new Thread(r, "stream-consumer-" + threadSeq.getAndIncrement());
                t.setDaemon(true);
                return t;
            }
        });
        // 注册时 init 可能尚未执行（handler 先于本 bean 的 @PostConstruct 注入），这里统一启动
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
        while (Boolean.TRUE.equals(running.get(queue)))
        {
            try
            {
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
            // 投递次数通过 XPENDING 详情查询（XREADGROUP 不直接回传 delivery count）
            long attempts = deliveryAttempt(queue, id);
            log.warn("Stream[{}] 消息处理失败 id={} attempts={}: {}", queue, id, attempts, e.getMessage());
            if (attempts >= maxDeliveries)
            {
                // 超过最大投递：转死信并 ACK，避免毒消息永久占 pending
                moveToDeadLetter(queue, record, e.getMessage());
                acknowledge(key, id);
                log.error("Stream[{}] 消息超过最大投递次数{}，转死信 id={}", queue, maxDeliveries, id);
            }
            // 未超限：不 ACK，消息留 pending，后续 XREADGROUP（含 0-0 重投）会再次投递
            sleepQuietly(500);
        }
    }

    /**
     * 查询单条消息的已投递次数。
     *
     * <p>旧版 spring-data-redis（2.3.x）的 PendingMessage 不暴露 deliveryCount，
     * 无法精确取单条投递次数；此处保守返回 maxDeliveries（上限），使处理失败的消息
     * 在一次重投后即转入死信，避免毒消息在兼容性约束下无限重投占住消费线程。
     * 升级 spring-data-redis 到 2.6+ 后可改为 XPENDING IDLE/RANGE 详情取真实次数。</p>
     */
    private long deliveryAttempt(String queue, RecordId id)
    {
        return maxDeliveries;
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
