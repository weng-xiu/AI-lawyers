package ai.lawyers.system.task;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Duration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import ai.lawyers.system.service.lawyers.IAiUnifiedSessionService;
import ai.lawyers.system.service.lawyers.cluster.RedisLeaderLock;

/**
 * 会话占用残留看门狗测试：总开关、集群锁单主执行、回收过期活跃会话。
 *
 * @author ai-lawyers
 */
class SessionOccupyWatchdogTaskTest
{
    private IAiUnifiedSessionService unifiedSessionService;

    private RedisLeaderLock leaderLock;

    private SessionOccupyWatchdogTask task;

    @BeforeEach
    void setUp()
    {
        unifiedSessionService = mock(IAiUnifiedSessionService.class);
        leaderLock = mock(RedisLeaderLock.class);
        task = new SessionOccupyWatchdogTask();
        ReflectionTestUtils.setField(task, "unifiedSessionService", unifiedSessionService);
        ReflectionTestUtils.setField(task, "leaderLock", leaderLock);
        ReflectionTestUtils.setField(task, "occupyEnabled", true);
    }

    @Test
    void 开关关闭时不参与竞选也不回收()
    {
        ReflectionTestUtils.setField(task, "occupyEnabled", false);

        task.scanExpired();

        verifyNoInteractions(leaderLock);
        verifyNoInteractions(unifiedSessionService);
    }

    @Test
    void 取得集群锁后回收过期会话()
    {
        when(leaderLock.tryRun(anyString(), any(Duration.class), any(Runnable.class))).thenAnswer(invocation ->
        {
            invocation.getArgument(2, Runnable.class).run();
            return true;
        });
        when(unifiedSessionService.reclaimExpired()).thenReturn(3);

        task.scanExpired();

        verify(unifiedSessionService).reclaimExpired();
    }

    @Test
    void 未取得集群锁时不回收()
    {
        when(leaderLock.tryRun(anyString(), any(Duration.class), any(Runnable.class))).thenReturn(false);

        task.scanExpired();

        verify(unifiedSessionService, never()).reclaimExpired();
    }
}
