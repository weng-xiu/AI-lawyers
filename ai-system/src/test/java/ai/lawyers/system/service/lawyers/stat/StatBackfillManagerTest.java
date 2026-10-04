package ai.lawyers.system.service.lawyers.stat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.test.util.ReflectionTestUtils;
import ai.lawyers.common.core.redis.RedisCache;
import ai.lawyers.common.exception.ServiceException;
import ai.lawyers.system.service.lawyers.cluster.RedisLeaderLock;
import ai.lawyers.system.task.StatMinuteScheduleTask;

/**
 * {@link StatBackfillManager} 单元测试：用同步执行器替换后台线程池，
 * 用 Map 模拟 Redis 进度存取，验证提交/执行/进度/互斥/校验。
 */
class StatBackfillManagerTest
{
    @Mock
    private RedisCache redisCache;

    @Mock
    private RedisLeaderLock leaderLock;

    @Mock
    private StatMinuteScheduleTask statMinuteScheduleTask;

    @Mock
    private ExecutorService executor;

    private StatBackfillManager manager;

    /** 模拟 Redis 任务进度存储 */
    private final Map<String, StatBackfillManager.Progress> store = new HashMap<>();

    @BeforeEach
    void setUp()
    {
        MockitoAnnotations.openMocks(this);
        manager = new StatBackfillManager();
        ReflectionTestUtils.setField(manager, "redisCache", redisCache);
        ReflectionTestUtils.setField(manager, "leaderLock", leaderLock);
        ReflectionTestUtils.setField(manager, "statMinuteScheduleTask", statMinuteScheduleTask);
        ReflectionTestUtils.setField(manager, "executor", executor);

        // 后台线程池：同步直接执行
        when(executor.submit(any(Runnable.class))).thenAnswer(inv ->
        {
            ((Runnable) inv.getArgument(0)).run();
            return null;
        });
        // Redis 进度读写
        org.mockito.Mockito.doAnswer(inv ->
        {
            store.put(inv.getArgument(0), inv.getArgument(1));
            return null;
        }).when(redisCache).setCacheObject(any(String.class), any(), any(Integer.class), any(TimeUnit.class));
        when(redisCache.getCacheObject(any(String.class)))
                .thenAnswer(inv -> store.get(inv.getArgument(0)));
    }

    @Test
    void submit_runsAndUpdatesProgress()
    {
        when(leaderLock.acquireOnce(any(String.class), any())).thenReturn("token-1");
        when(statMinuteScheduleTask.aggregateMinute(any(LocalDateTime.class))).thenReturn(40);

        LocalDateTime begin = LocalDateTime.of(2026, 10, 3, 10, 0);
        LocalDateTime end = begin.plusMinutes(3);
        String taskId = manager.submit(begin, end);

        StatBackfillManager.Progress p = manager.getProgress(taskId);
        assertEquals(StatBackfillManager.Progress.SUCCESS, p.status);
        assertEquals(3, p.totalMinutes);
        assertEquals(3, p.processedMinutes);
        assertEquals(120, p.rowsWritten);
        assertEquals(100, p.getPercent());
        verify(leaderLock).releaseOnce(contains("stat-backfill"), org.mockito.ArgumentMatchers.eq("token-1"));
    }

    @Test
    void submit_rejectsWhenLockHeld()
    {
        when(leaderLock.acquireOnce(any(String.class), any())).thenReturn(null);
        ServiceException ex = assertThrows(ServiceException.class,
                () -> manager.submit(LocalDateTime.of(2026, 10, 3, 10, 0),
                        LocalDateTime.of(2026, 10, 3, 10, 1)));
        assertEquals("已有回填任务正在执行，请等待其完成后再提交", ex.getMessage());
    }

    @Test
    void submit_rejectsInvalidRange()
    {
        LocalDateTime t = LocalDateTime.of(2026, 10, 3, 10, 0);
        assertThrows(ServiceException.class, () -> manager.submit(t, t));
        assertThrows(ServiceException.class, () -> manager.submit(null, t));
        assertThrows(ServiceException.class, () -> manager.submit(t.minusDays(8), t));
    }

    @Test
    void submit_marksFailedAndReleasesLockOnError()
    {
        when(leaderLock.acquireOnce(any(String.class), any())).thenReturn("token-2");
        when(statMinuteScheduleTask.aggregateMinute(any(LocalDateTime.class)))
                .thenThrow(new RuntimeException("db down"));

        LocalDateTime begin = LocalDateTime.of(2026, 10, 3, 10, 0);
        String taskId = manager.submit(begin, begin.plusMinutes(1));

        StatBackfillManager.Progress p = manager.getProgress(taskId);
        assertEquals(StatBackfillManager.Progress.FAILED, p.status);
        assertEquals("db down", p.errorMsg);
        verify(leaderLock).releaseOnce(any(String.class), org.mockito.ArgumentMatchers.eq("token-2"));
    }

    @Test
    void getProgress_missingThrows()
    {
        assertThrows(ServiceException.class, () -> manager.getProgress("nope"));
        assertThrows(ServiceException.class, () -> manager.getProgress(""));
    }
}
