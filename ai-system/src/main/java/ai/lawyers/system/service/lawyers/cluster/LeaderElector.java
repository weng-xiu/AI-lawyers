package ai.lawyers.system.service.lawyers.cluster;

import java.time.Duration;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * N3：基于 {@link RedisLeaderLock} 租约的单主竞选器（ESL 长连接等场景）。
 *
 * <p>心跳线程周期性：非领导者尝试 SET NX 竞选；领导者 Lua CAS 续期。
 * 竞选成功回调 {@code onGranted}（建立连接）；续期失败（租约丢失/被接管）回调
 * {@code onRevoked}（必须主动断开，防止双主）。实例崩溃不续期 → TTL 到期后
 * 其它实例自动接管，实现故障切换。</p>
 *
 * <p>不依赖 Spring 注解，由使用方（如 EslEventBridgeService）显式构造；
 * {@link #tick()} 包级可见以便单测驱动。</p>
 *
 * @author ai-lawyers
 */
public class LeaderElector
{
    private static final Logger log = LoggerFactory.getLogger(LeaderElector.class);

    private final RedisLeaderLock lock;
    private final String lockName;
    private final Duration leaseTtl;
    private final Duration heartbeatInterval;
    private final Runnable onGranted;
    private final Runnable onRevoked;

    private volatile boolean leader = false;
    private ScheduledExecutorService executor;

    /**
     * @param lock 分布式锁底座
     * @param lockName 竞选锁名（如 leader:esl-bridge）
     * @param leaseTtl 租约 TTL（须 ≥ 2 倍心跳间隔，建议 30s）
     * @param heartbeatInterval 心跳间隔（建议 TTL 的 1/3）
     * @param onGranted 成为领导者时执行（建连），异常仅记录不改变领导状态
     * @param onRevoked 丢失领导权时执行（断连），异常仅记录
     */
    public LeaderElector(RedisLeaderLock lock, String lockName, Duration leaseTtl,
                         Duration heartbeatInterval, Runnable onGranted, Runnable onRevoked)
    {
        this.lock = lock;
        this.lockName = lockName;
        this.leaseTtl = leaseTtl;
        this.heartbeatInterval = heartbeatInterval;
        this.onGranted = onGranted;
        this.onRevoked = onRevoked;
    }

    /** 启动心跳调度（daemon 单线程）。 */
    public synchronized void start()
    {
        if (executor != null)
        {
            return;
        }
        executor = Executors.newSingleThreadScheduledExecutor(r ->
        {
            Thread t = new Thread(r, "leader-elector-" + lockName);
            t.setDaemon(true);
            return t;
        });
        long intervalMs = heartbeatInterval.toMillis();
        // 首次立即执行，便于启动后快速建连
        executor.scheduleAtFixedRate(this::safeTick, 0L, intervalMs, TimeUnit.MILLISECONDS);
        log.info("LeaderElector 已启动 name={} ttl={}ms interval={}ms instance={}",
                lockName, leaseTtl.toMillis(), intervalMs, safeInstanceId());
    }

    /** 停止心跳并主动放弃领导权（优雅停机）。 */
    public synchronized void stop()
    {
        if (executor != null)
        {
            executor.shutdownNow();
            executor = null;
        }
        if (leader)
        {
            revoke("elector stopped");
        }
        try
        {
            lock.resign(lockName);
        }
        catch (Exception ignored)
        {
            // 等待 TTL 自然过期
        }
    }

    public boolean isLeader()
    {
        return leader;
    }

    private void safeTick()
    {
        try
        {
            tick();
        }
        catch (Throwable t)
        {
            // 心跳线程绝不能因异常死亡
            log.warn("LeaderElector 心跳异常 name={} err={}", lockName, t.getMessage());
        }
    }

    /**
     * 单次心跳：竞选或续期，按结果触发回调。包级可见供单测。
     */
    void tick()
    {
        if (!lock.isEnabled())
        {
            // 单机回退模式：恒为领导者且不触碰 Redis
            if (!leader)
            {
                grant("lock disabled (single-instance fallback)");
            }
            return;
        }
        try
        {
            if (!leader)
            {
                if (lock.acquireLeader(lockName, leaseTtl))
                {
                    grant("lock acquired");
                }
            }
            else if (!lock.renewLeader(lockName, leaseTtl))
            {
                revoke("lease lost");
            }
        }
        catch (Exception e)
        {
            // Redis 抖动：领导者保持不变，下个心跳重试续期；
            // 若持续故障超过 TTL，renew 返回 false（key 已被接管）时会正确降级
            log.warn("LeaderElector Redis 操作异常 name={} leader={} err={}",
                    lockName, leader, e.getMessage());
        }
    }

    private void grant(String reason)
    {
        leader = true;
        log.info("★ 本实例成为领导者 name={} reason={} instance={}",
                lockName, reason, safeInstanceId());
        try
        {
            onGranted.run();
        }
        catch (Exception e)
        {
            log.error("LeaderElector onGranted 回调异常 name={}", lockName, e);
        }
    }

    private void revoke(String reason)
    {
        leader = false;
        log.warn("☆ 本实例丢失领导权 name={} reason={} instance={}",
                lockName, reason, safeInstanceId());
        try
        {
            onRevoked.run();
        }
        catch (Exception e)
        {
            log.error("LeaderElector onRevoked 回调异常 name={}", lockName, e);
        }
    }

    private String safeInstanceId()
    {
        try
        {
            return lock.getInstanceId();
        }
        catch (Exception e)
        {
            return "unknown";
        }
    }
}
