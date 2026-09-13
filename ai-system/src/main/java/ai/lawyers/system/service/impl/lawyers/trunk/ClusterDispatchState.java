package ai.lawyers.system.service.impl.lawyers.trunk;

import java.util.Collections;
import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;

import ai.lawyers.system.domain.lawyers.trunk.DialRequest;

/**
 * N2：呼叫调度集群状态底座（Redis 版）。
 *
 * <p>把 {@code CallDispatchServiceImpl} 中四个 JVM 内存态结构外置到 Redis，使多实例部署时
 * 全局并发上限、线路 CPS 限速、呼叫占用线路映射、排队队列在全集群范围内一致：</p>
 * <ul>
 *   <li><b>全局并发</b>：Lua 原子 "get &lt; max 则 incr"，杜绝本地 check-then-act 竞态；
 *       Redis 故障时 <b>fail-closed</b>（返回满），避免突破运营商并发限额；</li>
 *   <li><b>线路 CPS</b>：每秒级 INCR 计数（首次 INCR 设 1s TTL），Redis 故障时
 *       <b>fail-open</b>（放行，CPS 为软限速，不阻断呼叫）；</li>
 *   <li><b>呼叫-线路占用</b>：callUuid→trunkId 单 key（1h TTL 自动清理），挂断时优先查 Redis，
 *       未命中再回退 DB（{@code ai_call_dial_log.trunk_id}）；</li>
 *   <li><b>排队队列</b>：Redis Sorted Set，score = priority * 1e13 + 入队毫秒，
 *       ZPOPMIN 原子出队，天然实现多实例负载分发，无需额外选主锁。</li>
 * </ul>
 *
 * <p><b>回退开关</b>：{@code call.dispatch.cluster.enabled=false}（默认）时本组件不被调用，
 * 调度服务走原 JVM 内存态实现，零行为变化。</p>
 *
 * @author ai-lawyers
 */
@Component
public class ClusterDispatchState
{
    private static final Logger log = LoggerFactory.getLogger(ClusterDispatchState.class);

    private static final String KEY_PREFIX = "call:dispatch:";
    private static final String KEY_GLOBAL = KEY_PREFIX + "global-concurrent";
    private static final String KEY_QUEUE = KEY_PREFIX + "queue";
    private static final String KEY_CPS_PREFIX = KEY_PREFIX + "cps:";
    private static final String KEY_TRUNK_PREFIX = KEY_PREFIX + "trunk:";

    /** 呼叫-线路映射的自动清理 TTL（秒），防止异常挂断遗漏导致 key 永久残留 */
    private static final long TRUNK_HOLDER_TTL_SECONDS = 3600L;

    /** 全局并发原子占位：get < max 才 incr，返回 -1 表示已满 */
    private static final DefaultRedisScript<Long> ACQUIRE_GLOBAL_SCRIPT = new DefaultRedisScript<>(
            "local v = tonumber(redis.call('get', KEYS[1]) or '0') "
          + "if v < tonumber(ARGV[1]) then return redis.call('incr', KEYS[1]) else return -1 end",
            Long.class);

    /** 排队入队原子校验容量：zcard < capacity 才 zadd，返回 0 表示队列已满 */
    private static final DefaultRedisScript<Long> ENQUEUE_SCRIPT = new DefaultRedisScript<>(
            "if redis.call('zcard', KEYS[1]) >= tonumber(ARGV[1]) then return 0 end "
          + "redis.call('zadd', KEYS[1], ARGV[2], ARGV[3]) return 1",
            Long.class);

    /**
     * 原子弹出最小 score 元素：ZRANGE 0 0 WITHSCORES 取首元素后立即 ZREM。
     * 返回数组 [member, score]；空队列返回 nil。Lua 保证取与删原子，避免多实例重复消费。
     */
    private static final DefaultRedisScript<List> POLL_QUEUE_SCRIPT = new DefaultRedisScript<>(
            "local r = redis.call('zrange', KEYS[1], 0, 0, 'WITHSCORES') "
          + "if #r == 0 then return nil end "
          + "redis.call('zrem', KEYS[1], r[1]) "
          + "return r",
            List.class);

    /** score 放大因子：priority * SCORE_SCALE + enqueueTimeMs，保证优先级主导、同优先级按入队时间 FIFO */
    private static final double SCORE_SCALE = 1e13;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${call.dispatch.cluster.enabled:false}")
    private boolean clusterEnabled;

    /** @return 集群调度开关是否开启（供调用方选择本地/Redis 实现） */
    public boolean isClusterEnabled()
    {
        return clusterEnabled;
    }

    // ------------------------------------------------------------------ 全局并发

    /**
     * 原子获取一个全局并发槽位。
     *
     * @return 新的全局并发数（≥1 表示获取成功）；-1 表示已达上限或 Redis 故障（fail-closed）
     */
    public long acquireGlobalConcurrent(int max)
    {
        try
        {
            Long r = stringRedisTemplate.execute(ACQUIRE_GLOBAL_SCRIPT,
                    Collections.singletonList(KEY_GLOBAL), String.valueOf(max));
            return r == null ? -1L : r;
        }
        catch (Exception e)
        {
            log.warn("全局并发占位 Redis 异常，按已满处理（fail-closed）: {}", e.getMessage());
            return -1L;
        }
    }

    /** 释放一个全局并发槽位（Redis 异常仅记录，靠线路级 DB 并发兜底）。 */
    public void releaseGlobalConcurrent()
    {
        try
        {
            stringRedisTemplate.opsForValue().decrement(KEY_GLOBAL);
        }
        catch (Exception e)
        {
            log.warn("全局并发释放 Redis 异常: {}", e.getMessage());
        }
    }

    /**
     * 重置全局并发计数为 0。
     * 仅在服务启动 init 阶段调用，与 {@code trunkMapper.resetAllConcurrent()} 配套，
     * 清理进程异常退出后残留的计数（多实例下若有其它实例仍有在途呼叫会被一并清零，
     * 属与线路级并发重置一致的既有约束，重启应在全集群低峰/停服时进行）。
     */
    public void resetGlobalConcurrent()
    {
        try
        {
            stringRedisTemplate.delete(KEY_GLOBAL);
        }
        catch (Exception e)
        {
            log.warn("全局并发重置 Redis 异常: {}", e.getMessage());
        }
    }

    /** @return 当前全局并发数（Redis 故障返回 0，由调用方决定是否保守处理）。 */
    public int getGlobalConcurrent()
    {
        try
        {
            String v = stringRedisTemplate.opsForValue().get(KEY_GLOBAL);
            return v == null ? 0 : Integer.parseInt(v);
        }
        catch (Exception e)
        {
            return 0;
        }
    }

    // ------------------------------------------------------------------ CPS 限速

    /**
     * 尝试获取一个 CPS 令牌。
     *
     * @return true 放行；false 达到该线路 CPS 上限；Redis 故障返回 true（fail-open）
     */
    public boolean acquireCps(long trunkId, int limit)
    {
        if (limit <= 0)
        {
            return true;
        }
        try
        {
            String key = KEY_CPS_PREFIX + trunkId;
            Long v = stringRedisTemplate.opsForValue().increment(key);
            if (v != null && v == 1L)
            {
                // 首次计数设置 1 秒过期，形成滑动秒窗口
                stringRedisTemplate.expire(key, 1, java.util.concurrent.TimeUnit.SECONDS);
            }
            return v == null || v <= limit;
        }
        catch (Exception e)
        {
            log.debug("CPS 限速 Redis 异常，放行（fail-open）trunkId={}: {}", trunkId, e.getMessage());
            return true;
        }
    }

    // ------------------------------------------------------------------ 呼叫-线路占用

    /** 记录某呼叫占用的线路 ID（1h 后自动清理）。 */
    public void holdTrunk(String callUuid, Long trunkId)
    {
        if (callUuid == null || trunkId == null)
        {
            return;
        }
        try
        {
            stringRedisTemplate.opsForValue().set(KEY_TRUNK_PREFIX + callUuid,
                    String.valueOf(trunkId), TRUNK_HOLDER_TTL_SECONDS, java.util.concurrent.TimeUnit.SECONDS);
        }
        catch (Exception e)
        {
            log.warn("呼叫线路占用登记失败 uuid={}: {}", callUuid, e.getMessage());
        }
    }

    /** 查询某呼叫占用的线路 ID；未命中返回 null（调用方应回退查 DB）。 */
    public Long getTrunk(String callUuid)
    {
        if (callUuid == null)
        {
            return null;
        }
        try
        {
            String v = stringRedisTemplate.opsForValue().get(KEY_TRUNK_PREFIX + callUuid);
            return v == null ? null : Long.valueOf(v);
        }
        catch (Exception e)
        {
            return null;
        }
    }

    /** 释放某呼叫的线路占用映射。 */
    public void releaseTrunk(String callUuid)
    {
        if (callUuid == null)
        {
            return;
        }
        try
        {
            stringRedisTemplate.delete(KEY_TRUNK_PREFIX + callUuid);
        }
        catch (Exception e)
        {
            log.debug("呼叫线路占用释放失败 uuid={}: {}", callUuid, e.getMessage());
        }
    }

    // ------------------------------------------------------------------ 排队队列

    /**
     * 入队。
     *
     * @return true 入队成功；false 队列已满或 Redis 故障
     */
    public boolean enqueue(DialRequest request, int capacity)
    {
        return enqueue(request, capacity, System.currentTimeMillis());
    }

    /**
     * 入队（指定入队时间，用于重入队时保留原始等待时长，保证 FIFO 公平性）。
     */
    public boolean enqueue(DialRequest request, int capacity, long enqueueTime)
    {
        if (request == null)
        {
            return false;
        }
        int priority = request.getPriority() == null ? 100 : request.getPriority();
        double score = priority * SCORE_SCALE + enqueueTime;
        String member;
        try
        {
            QueueItem item = new QueueItem();
            item.qid = UUID.randomUUID().toString();
            item.enqueueTime = enqueueTime;
            item.request = request;
            member = objectMapper.writeValueAsString(item);
        }
        catch (Exception e)
        {
            log.error("排队请求序列化失败 callee={}", request.getCalleeNumber(), e);
            return false;
        }
        try
        {
            Long r = stringRedisTemplate.execute(ENQUEUE_SCRIPT,
                    Collections.singletonList(KEY_QUEUE),
                    String.valueOf(capacity), String.valueOf(score), member);
            return r != null && r == 1L;
        }
        catch (Exception e)
        {
            log.warn("排队入队 Redis 异常 callee={}: {}", request.getCalleeNumber(), e.getMessage());
            return false;
        }
    }

    /**
     * 出队：取优先级最高（score 最小）的一个请求。
     *
     * @return 排队项；队列为空或 Redis 故障返回 null
     */
    public QueueItem pollQueue()
    {
        try
        {
            @SuppressWarnings("unchecked")
            List<Object> r = stringRedisTemplate.execute(POLL_QUEUE_SCRIPT,
                    Collections.singletonList(KEY_QUEUE));
            if (r == null || r.isEmpty())
            {
                return null;
            }
            String member = String.valueOf(r.get(0));
            return objectMapper.readValue(member, QueueItem.class);
        }
        catch (Exception e)
        {
            log.warn("排队出队 Redis 异常: {}", e.getMessage());
            return null;
        }
    }

    /** @return 当前队列长度；Redis 故障返回 0。 */
    public long queueSize()
    {
        try
        {
            Long size = stringRedisTemplate.opsForZSet().zCard(KEY_QUEUE);
            return size == null ? 0 : size;
        }
        catch (Exception e)
        {
            return 0;
        }
    }

    /** 队列项 JSON 信封（含唯一 qid 避免 ZADD 同 score 同请求覆盖）。 */
    public static class QueueItem
    {
        /** 唯一排队 ID */
        public String qid;
        /** 入队毫秒时间戳 */
        public long enqueueTime;
        /** 外呼请求 */
        public DialRequest request;
    }
}
