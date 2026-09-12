package ai.lawyers.system.service.lawyers.cluster;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * N7/N3 底座：{@link RedisLeaderLock} 抢锁、CAS 释放、fail-closed 与单机回退测试。
 *
 * @author ai-lawyers
 */
@SuppressWarnings({"unchecked", "rawtypes"})
class RedisLeaderLockTest
{
    private StringRedisTemplate redis;
    private ValueOperations<String, String> valueOps;
    private RedisLeaderLock lock;

    @BeforeEach
    void setUp()
    {
        redis = Mockito.mock(StringRedisTemplate.class);
        valueOps = Mockito.mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(valueOps);
        lock = new RedisLeaderLock();
        ReflectionTestUtils.setField(lock, "stringRedisTemplate", redis);
        ReflectionTestUtils.setField(lock, "enabled", true);
        ReflectionTestUtils.setField(lock, "keyPrefix", "ai-law:lock:");
        lock.init();
    }

    @Test
    void tryRun_acquired_executesAndReleases()
    {
        when(valueOps.setIfAbsent(anyString(), anyString(), any(Duration.class))).thenReturn(true);
        AtomicInteger runs = new AtomicInteger();

        boolean executed = lock.tryRun("job:demo", Duration.ofSeconds(10), runs::incrementAndGet);

        assertThat(executed).isTrue();
        assertThat(runs).hasValue(1);
        // 执行结束必须经 Lua CAS 释放（不能裸 delete）
        verify(redis).execute(any(DefaultRedisScript.class), anyList(), any(String.class));
    }

    @Test
    void tryRun_heldByOther_skipsWithoutRelease()
    {
        when(valueOps.setIfAbsent(anyString(), anyString(), any(Duration.class))).thenReturn(false);
        AtomicInteger runs = new AtomicInteger();

        boolean executed = lock.tryRun("job:demo", Duration.ofSeconds(10), runs::incrementAndGet);

        assertThat(executed).isFalse();
        assertThat(runs).hasValue(0);
        verify(redis, never()).execute(any(DefaultRedisScript.class), anyList(), any(String.class));
    }

    @Test
    void tryRun_redisDown_failsClosedAndSkips()
    {
        // 外呼类任务重复执行风险高：Redis 异常必须跳过本轮（fail-closed），而不是降级各实例都跑
        when(valueOps.setIfAbsent(anyString(), anyString(), any(Duration.class)))
                .thenThrow(new RuntimeException("connection refused"));
        AtomicInteger runs = new AtomicInteger();

        boolean executed = lock.tryRun("job:demo", Duration.ofSeconds(10), runs::incrementAndGet);

        assertThat(executed).isFalse();
        assertThat(runs).hasValue(0);
    }

    @Test
    void tryRun_disabled_runsDirectlyWithoutRedis()
    {
        ReflectionTestUtils.setField(lock, "enabled", false);
        AtomicInteger runs = new AtomicInteger();

        boolean executed = lock.tryRun("job:demo", Duration.ofSeconds(10), runs::incrementAndGet);

        assertThat(executed).isTrue();
        assertThat(runs).hasValue(1);
        // 单机回退路径不触碰 Redis
        verify(valueOps, never()).setIfAbsent(anyString(), anyString(), any(Duration.class));
    }

    @Test
    void tryRun_taskThrows_lockStillReleased()
    {
        when(valueOps.setIfAbsent(anyString(), anyString(), any(Duration.class))).thenReturn(true);

        assertThatThrownBy(() -> lock.tryRun("job:demo", Duration.ofSeconds(10), () ->
        {
            throw new IllegalStateException("boom");
        })).isInstanceOf(IllegalStateException.class);

        // finally 必须释放锁，避免等 TTL
        verify(redis).execute(any(DefaultRedisScript.class), anyList(), any(String.class));
    }

    @Test
    void leaderApis_acquireAndRenew()
    {
        when(valueOps.setIfAbsent(anyString(), anyString(), any(Duration.class))).thenReturn(true);
        when(redis.execute(any(DefaultRedisScript.class), anyList(), anyString(), anyString()))
                .thenReturn(1L);

        assertThat(lock.acquireLeader("leader:esl", Duration.ofSeconds(30))).isTrue();
        assertThat(lock.renewLeader("leader:esl", Duration.ofSeconds(30))).isTrue();

        // 续期返回 0 = 租约已被接管，调用方必须降级
        when(redis.execute(any(DefaultRedisScript.class), anyList(), anyString(), anyString()))
                .thenReturn(0L);
        assertThat(lock.renewLeader("leader:esl", Duration.ofSeconds(30))).isFalse();
    }
}
