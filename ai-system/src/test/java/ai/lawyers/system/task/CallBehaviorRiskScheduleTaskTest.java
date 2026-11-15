package ai.lawyers.system.task;

import static org.assertj.core.api.Assertions.assertThatCode;
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

import ai.lawyers.system.service.lawyers.IAiCallBehaviorRiskService;
import ai.lawyers.system.service.lawyers.cluster.RedisLeaderLock;

/**
 * 呼叫行为风控定时任务测试：总开关、集群锁单主执行、扫描异常吞掉不外抛。
 *
 * @author ai-lawyers
 */
class CallBehaviorRiskScheduleTaskTest
{
    private IAiCallBehaviorRiskService riskService;

    private RedisLeaderLock leaderLock;

    private CallBehaviorRiskScheduleTask task;

    @BeforeEach
    void setUp()
    {
        riskService = mock(IAiCallBehaviorRiskService.class);
        leaderLock = mock(RedisLeaderLock.class);
        task = new CallBehaviorRiskScheduleTask();
        ReflectionTestUtils.setField(task, "riskService", riskService);
        ReflectionTestUtils.setField(task, "leaderLock", leaderLock);
        ReflectionTestUtils.setField(task, "enabled", true);
    }

    @Test
    void 开关关闭时不参与竞选也不扫描()
    {
        ReflectionTestUtils.setField(task, "enabled", false);

        task.scan();

        verifyNoInteractions(leaderLock);
        verifyNoInteractions(riskService);
    }

    @Test
    void 取得集群锁后执行扫描()
    {
        when(leaderLock.tryRun(anyString(), any(Duration.class), any(Runnable.class))).thenAnswer(invocation ->
        {
            invocation.getArgument(2, Runnable.class).run();
            return true;
        });
        when(riskService.scanRisk()).thenReturn(2);

        task.scan();

        verify(riskService).scanRisk();
    }

    @Test
    void 未取得集群锁时不扫描()
    {
        when(leaderLock.tryRun(anyString(), any(Duration.class), any(Runnable.class))).thenReturn(false);

        task.scan();

        verify(riskService, never()).scanRisk();
    }

    @Test
    void 扫描异常被吞掉不影响调度()
    {
        when(leaderLock.tryRun(anyString(), any(Duration.class), any(Runnable.class))).thenAnswer(invocation ->
        {
            invocation.getArgument(2, Runnable.class).run();
            return true;
        });
        when(riskService.scanRisk()).thenThrow(new IllegalStateException("聚合失败"));

        assertThatCode(() -> task.scan()).doesNotThrowAnyException();
        verify(riskService).scanRisk();
    }
}
