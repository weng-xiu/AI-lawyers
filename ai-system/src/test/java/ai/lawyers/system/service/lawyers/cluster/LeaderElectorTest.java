package ai.lawyers.system.service.lawyers.cluster;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

/**
 * N3：{@link LeaderElector} 心跳状态机测试（竞选/续期/丢失/单机回退/异常保持）。
 *
 * @author ai-lawyers
 */
class LeaderElectorTest
{
    private RedisLeaderLock lock;
    private AtomicInteger grants;
    private AtomicInteger revokes;
    private LeaderElector elector;

    @BeforeEach
    void setUp()
    {
        lock = Mockito.mock(RedisLeaderLock.class);
        when(lock.isEnabled()).thenReturn(true);
        grants = new AtomicInteger();
        revokes = new AtomicInteger();
        elector = new LeaderElector(lock, "leader:esl-bridge",
                Duration.ofSeconds(30), Duration.ofSeconds(10),
                grants::incrementAndGet, revokes::incrementAndGet);
    }

    @Test
    void tick_followerAcquires_becomesLeaderOnce()
    {
        when(lock.acquireLeader(eq("leader:esl-bridge"), any(Duration.class))).thenReturn(true);
        when(lock.renewLeader(eq("leader:esl-bridge"), any(Duration.class))).thenReturn(true);

        elector.tick();
        elector.tick();
        elector.tick();

        assertThat(elector.isLeader()).isTrue();
        assertThat(grants).hasValue(1);
        assertThat(revokes).hasValue(0);
        // 第二拍起只续期，不再竞选
        verify(lock, Mockito.times(1)).acquireLeader(eq("leader:esl-bridge"), any(Duration.class));
        verify(lock, Mockito.times(2)).renewLeader(eq("leader:esl-bridge"), any(Duration.class));
    }

    @Test
    void tick_followerLosesElection_staysFollower()
    {
        when(lock.acquireLeader(eq("leader:esl-bridge"), any(Duration.class))).thenReturn(false);

        elector.tick();

        assertThat(elector.isLeader()).isFalse();
        assertThat(grants).hasValue(0);
        verify(lock, never()).renewLeader(eq("leader:esl-bridge"), any(Duration.class));
    }

    @Test
    void tick_renewFails_revokesLeadership()
    {
        when(lock.acquireLeader(eq("leader:esl-bridge"), any(Duration.class))).thenReturn(true);
        when(lock.renewLeader(eq("leader:esl-bridge"), any(Duration.class)))
                .thenReturn(true)
                .thenReturn(false);

        elector.tick(); // 竞选成功
        elector.tick(); // 续期成功
        elector.tick(); // 续期失败 → 丢权

        assertThat(elector.isLeader()).isFalse();
        assertThat(grants).hasValue(1);
        assertThat(revokes).hasValue(1);
    }

    @Test
    void tick_lockDisabled_alwaysLeaderWithoutRedis()
    {
        when(lock.isEnabled()).thenReturn(false);

        elector.tick();
        elector.tick();

        assertThat(elector.isLeader()).isTrue();
        assertThat(grants).hasValue(1);
        verify(lock, never()).acquireLeader(any(), any(Duration.class));
        verify(lock, never()).renewLeader(any(), any(Duration.class));
    }

    @Test
    void tick_redisJitter_keepsCurrentState()
    {
        // 跟随者状态下 Redis 异常：保持跟随，不触发回调，下拍可正常竞选
        when(lock.acquireLeader(eq("leader:esl-bridge"), any(Duration.class)))
                .thenThrow(new RuntimeException("redis timeout"))
                .thenReturn(true);

        elector.tick();
        assertThat(elector.isLeader()).isFalse();
        assertThat(grants).hasValue(0);

        elector.tick();
        assertThat(elector.isLeader()).isTrue();
        assertThat(grants).hasValue(1);
    }

    @Test
    void tick_grantCallbackThrows_leadershipStillHeld()
    {
        when(lock.acquireLeader(eq("leader:esl-bridge"), any(Duration.class))).thenReturn(true);
        LeaderElector badCallback = new LeaderElector(lock, "leader:esl-bridge",
                Duration.ofSeconds(30), Duration.ofSeconds(10),
                () -> { throw new IllegalStateException("connect fail"); },
                revokes::incrementAndGet);

        badCallback.tick();

        // 回调异常被吞掉，但领导状态已置位（后续心跳负责续期/检测）
        assertThat(badCallback.isLeader()).isTrue();
    }

    @Test
    void stop_asLeader_revokesAndResigns()
    {
        when(lock.acquireLeader(eq("leader:esl-bridge"), any(Duration.class))).thenReturn(true);
        elector.tick();

        elector.stop();

        assertThat(elector.isLeader()).isFalse();
        assertThat(revokes).hasValue(1);
        verify(lock).resign("leader:esl-bridge");
    }
}
