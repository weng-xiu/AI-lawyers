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
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.ScanOptions;
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

    @Test
    void scanJobLocks_returnsSnapshotWithHolderAndTtl()
    {
        String fullKey = "ai-law:lock:job:data-archive";
        String token = "gov-host-01-ab12cd34:550e8400-e29b";
        byte[] keyBytes = fullKey.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        byte[] tokenBytes = token.getBytes(java.nio.charset.StandardCharsets.UTF_8);

        RedisConnection connection = Mockito.mock(RedisConnection.class);
        @SuppressWarnings("resource")
        Cursor<byte[]> cursor = Mockito.mock(Cursor.class);
        when(cursor.hasNext()).thenReturn(true, false);
        when(cursor.next()).thenReturn(keyBytes);
        when(connection.scan(any(ScanOptions.class))).thenReturn(cursor);
        when(connection.get(keyBytes)).thenReturn(tokenBytes);
        when(connection.ttl(keyBytes)).thenReturn(1750L);
        // execute 桩真实执行回调，覆盖锁名剥离/实例解析等内部逻辑
        when(redis.execute(any(RedisCallback.class))).thenAnswer(inv ->
                ((RedisCallback<?>) inv.getArgument(0)).doInRedis(connection));

        List<Map<String, Object>> locks = lock.scanJobLocks();

        assertThat(locks).hasSize(1);
        Map<String, Object> item = locks.get(0);
        // 锁名去掉 keyPrefix；实例标识取令牌最后一个冒号前
        assertThat(item.get("lock")).isEqualTo("job:data-archive");
        assertThat(item.get("holder")).isEqualTo(token);
        assertThat(item.get("instance")).isEqualTo("gov-host-01-ab12cd34");
        assertThat(item.get("ttlSeconds")).isEqualTo(1750L);
    }

    @Test
    void scanJobLocks_redisDown_returnsEmptyList()
    {
        when(redis.execute(any(RedisCallback.class)))
                .thenThrow(new RuntimeException("connection refused"));

        List<Map<String, Object>> locks = lock.scanJobLocks();

        assertThat(locks).isEmpty();
    }
}
