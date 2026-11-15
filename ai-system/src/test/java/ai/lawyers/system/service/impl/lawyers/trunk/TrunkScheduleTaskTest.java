package ai.lawyers.system.service.impl.lawyers.trunk;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import ai.lawyers.system.service.lawyers.cluster.RedisLeaderLock;
import ai.lawyers.system.service.lawyers.trunk.ITrunkMonitorService;

/**
 * N7：{@link TrunkScheduleTask} 线路监控定时任务测试。
 *
 * <p>覆盖四类任务在单主锁内执行、锁名与 TTL 口径、未抢到锁时不触发业务，
 * 以及业务异常被吞掉不影响调度线程。</p>
 *
 * @author ai-lawyers
 */
class TrunkScheduleTaskTest
{
    private ITrunkMonitorService trunkMonitorService;

    private RedisLeaderLock leaderLock;

    private TrunkScheduleTask task;

    @BeforeEach
    void setUp()
    {
        trunkMonitorService = mock(ITrunkMonitorService.class);
        leaderLock = mock(RedisLeaderLock.class);
        task = new TrunkScheduleTask();
        ReflectionTestUtils.setField(task, "trunkMonitorService", trunkMonitorService);
        ReflectionTestUtils.setField(task, "leaderLock", leaderLock);
    }

    @Test
    void healthCheckRunsUnderLeaderLock()
    {
        runUnderLock();

        task.healthCheck();

        verify(leaderLock).tryRun(eq("job:trunk-health"), eq(Duration.ofSeconds(60)), any(Runnable.class));
        verify(trunkMonitorService).healthCheckAll();
    }

    @Test
    void recoverCircuitRunsUnderLeaderLock()
    {
        runUnderLock();

        task.recoverCircuit();

        verify(leaderLock).tryRun(eq("job:trunk-circuit-recover"), eq(Duration.ofSeconds(60)), any(Runnable.class));
        verify(trunkMonitorService).recoverCircuitBreakers();
    }

    @Test
    void collectMetricsRunsUnderLeaderLock()
    {
        runUnderLock();

        task.collectMetrics();

        verify(leaderLock).tryRun(eq("job:trunk-metrics"), eq(Duration.ofMinutes(2)), any(Runnable.class));
        verify(trunkMonitorService).collectMetrics();
    }

    @Test
    void evaluateAlarmRunsUnderLeaderLock()
    {
        runUnderLock();

        task.evaluateAlarm();

        verify(leaderLock).tryRun(eq("job:trunk-alarm"), eq(Duration.ofMinutes(5)), any(Runnable.class));
        verify(trunkMonitorService).evaluateQualityAlarm();
    }

    @Test
    void skipsAllTasksWhenLockNotAcquired()
    {
        when(leaderLock.tryRun(any(), any(), any())).thenReturn(false);

        task.healthCheck();
        task.recoverCircuit();
        task.collectMetrics();
        task.evaluateAlarm();

        verifyNoInteractions(trunkMonitorService);
    }

    @Test
    void healthCheckSwallowsServiceException()
    {
        runUnderLock();
        doThrow(new RuntimeException("probe failed")).when(trunkMonitorService).healthCheckAll();

        task.healthCheck();

        verify(trunkMonitorService).healthCheckAll();
    }

    @Test
    void collectMetricsSwallowsServiceException()
    {
        runUnderLock();
        doThrow(new RuntimeException("aggregate failed")).when(trunkMonitorService).collectMetrics();

        task.collectMetrics();

        verify(trunkMonitorService).collectMetrics();
    }

    @Test
    void alarmEvaluationSwallowsServiceException()
    {
        runUnderLock();
        doThrow(new RuntimeException("alarm failed")).when(trunkMonitorService).evaluateQualityAlarm();

        task.evaluateAlarm();

        verify(trunkMonitorService).evaluateQualityAlarm();
        verify(trunkMonitorService, never()).healthCheckAll();
    }

    /** 模拟单主锁获取成功并同步执行任务体 */
    private void runUnderLock()
    {
        when(leaderLock.tryRun(any(), any(), any())).thenAnswer(invocation ->
        {
            ((Runnable) invocation.getArgument(2)).run();
            return true;
        });
    }
}
