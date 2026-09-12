package ai.lawyers.system.service.lawyers.cluster;

import java.net.InetAddress;
import java.time.Duration;
import java.util.Collections;
import java.util.UUID;
import javax.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

/**
 * N3/N7：基于 Redis SET NX PX + Lua CAS 的轻量分布式协调底座（不引入 Redisson）。
 *
 * <p>提供两种语义：</p>
 * <ul>
 *   <li><b>单次单主</b>（{@link #tryRun}）：多实例的定时任务同一时刻仅一个实例执行，
 *       执行结束立即释放，异常崩溃靠 TTL 自动过期；释放用 Lua 比对令牌，杜绝误删他人锁；</li>
 *   <li><b>领导者租约</b>（{@link #acquireLeader}/{@link #renewLeader}/{@link #resign}）：
 *       供 {@link LeaderElector} 做 ESL 等长连接的单主竞争，后台心跳续期。</li>
 * </ul>
 *
 * <p>总开关 {@code cluster.lock.enabled=false} 时全部退化为单机旧行为
 * （tryRun 直接执行、竞选恒为领导者），作为应急回退路径。</p>
 *
 * @author ai-lawyers
 */
@Component
public class RedisLeaderLock
{
    private static final Logger log = LoggerFactory.getLogger(RedisLeaderLock.class);

    /** 释放：仅当持有者仍是自己才 DEL，防 TTL 过期后误删他人锁 */
    private static final DefaultRedisScript<Long> RELEASE_SCRIPT = new DefaultRedisScript<>(
            "if redis.call('get', KEYS[1]) == ARGV[1] then "
          + "  return redis.call('del', KEYS[1]) "
          + "else return 0 end", Long.class);

    /** 续期：仅当持有者仍是自己才 PEXPIRE */
    private static final DefaultRedisScript<Long> RENEW_SCRIPT = new DefaultRedisScript<>(
            "if redis.call('get', KEYS[1]) == ARGV[1] then "
          + "  return redis.call('pexpire', KEYS[1], ARGV[2]) "
          + "else return 0 end", Long.class);

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    /** 总开关：false 时退化为单机行为（应急回退，生产多实例必须 true） */
    @Value("${cluster.lock.enabled:true}")
    private boolean enabled;

    @Value("${cluster.lock.key-prefix:ai-law:lock:}")
    private String keyPrefix;

    /** 本实例标识：主机名 + 随机短码（领导者租约的稳定持有者令牌） */
    private String instanceId;

    @PostConstruct
    public void init()
    {
        String host;
        try
        {
            host = InetAddress.getLocalHost().getHostName();
        }
        catch (Exception e)
        {
            host = "unknown-host";
        }
        this.instanceId = host + "-" + UUID.randomUUID().toString().substring(0, 8);
        if (enabled)
        {
            log.info("分布式协调锁已启用（N3/N7），instanceId={}", instanceId);
        }
        else
        {
            log.warn("========== 高风险配置告警 ========== 分布式协调锁已关闭"
                    + "（cluster.lock.enabled=false），多实例将重复执行定时任务/重复建立 ESL 连接");
        }
    }

    public boolean isEnabled()
    {
        return enabled;
    }

    /** 本实例标识（日志/竞选观察用） */
    public String getInstanceId()
    {
        return instanceId;
    }

    // ------------------------------------------------------------ 单次单主

    /**
     * 获取一次性执行锁；失败（他人持有）返回 null。令牌为实例+UUID，释放时 CAS 比对。
     */
    public String acquireOnce(String lockName, Duration ttl)
    {
        String token = instanceId + ":" + UUID.randomUUID();
        Boolean ok = stringRedisTemplate.opsForValue()
                .setIfAbsent(key(lockName), token, ttl);
        return Boolean.TRUE.equals(ok) ? token : null;
    }

    /**
     * 释放一次性锁（CAS，token 不匹配说明 TTL 已过期且被他人取得，不删除）。
     */
    public void releaseOnce(String lockName, String token)
    {
        if (token == null)
        {
            return;
        }
        stringRedisTemplate.execute(RELEASE_SCRIPT,
                Collections.singletonList(key(lockName)), token);
    }

    /**
     * 抢到锁才执行任务；未抢到（其它实例正在执行）直接跳过。
     *
     * <p>故障策略：Redis 异常时<b>跳过本轮</b>（fail-closed）——外呼扫描等任务若在
     * Redis 故障时各实例放开执行，会造成重复外呼，宁可漏一轮等下个周期；
     * 总开关关闭时退化为直接执行（单机旧行为）。</p>
     *
     * @return true 表示本实例执行了任务
     */
    public boolean tryRun(String lockName, Duration ttl, Runnable task)
    {
        if (!enabled)
        {
            task.run();
            return true;
        }
        String token;
        try
        {
            token = acquireOnce(lockName, ttl);
        }
        catch (Exception e)
        {
            log.warn("分布式锁获取异常，跳过本轮执行 lock={} err={}", lockName, e.getMessage());
            return false;
        }
        if (token == null)
        {
            log.debug("分布式锁被其它实例持有，跳过 lock={}", lockName);
            return false;
        }
        try
        {
            task.run();
            return true;
        }
        finally
        {
            try
            {
                releaseOnce(lockName, token);
            }
            catch (Exception e)
            {
                log.debug("分布式锁释放异常（等待 TTL 过期）lock={} err={}", lockName, e.getMessage());
            }
        }
    }

    // ------------------------------------------------------------ 领导者租约

    /**
     * 竞选领导者：SET NX PX，持有者令牌为本实例稳定 ID。
     */
    public boolean acquireLeader(String lockName, Duration ttl)
    {
        Boolean ok = stringRedisTemplate.opsForValue()
                .setIfAbsent(key(lockName), instanceId, ttl);
        return Boolean.TRUE.equals(ok);
    }

    /**
     * 续期领导者租约；返回 false 表示租约已丢失（过期被他人取得），调用方必须降级。
     */
    public boolean renewLeader(String lockName, Duration ttl)
    {
        Long r = stringRedisTemplate.execute(RENEW_SCRIPT,
                Collections.singletonList(key(lockName)), instanceId, String.valueOf(ttl.toMillis()));
        return r != null && r > 0;
    }

    /**
     * 主动放弃领导者（优雅停机时调用，加速故障切换）。
     */
    public void resign(String lockName)
    {
        stringRedisTemplate.execute(RELEASE_SCRIPT,
                Collections.singletonList(key(lockName)), instanceId);
    }

    private String key(String lockName)
    {
        return keyPrefix + lockName;
    }
}
