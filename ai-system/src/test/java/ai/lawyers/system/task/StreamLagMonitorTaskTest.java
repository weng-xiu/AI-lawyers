package ai.lawyers.system.task;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.LinkedHashSet;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import ai.lawyers.system.service.lawyers.cluster.RedisLeaderLock;
import ai.lawyers.system.service.lawyers.queue.StreamQueueService;
import ai.lawyers.system.service.lawyers.trunk.ITrunkMonitorService;

/**
 * Stream 队列积压/死信监控任务测试：开关与集群锁守卫、积压持续时长判定、
 * 同一积压事件仅告警一次、恢复后重新告警、查询异常不误判、死信阈值、
 * 单队列异常隔离。
 *
 * @author ai-lawyers
 */
class StreamLagMonitorTaskTest
{
    private RedisLeaderLock leaderLock;

    private StreamQueueService streamQueueService;

    private ITrunkMonitorService trunkMonitorService;

    private StreamLagMonitorTask task;

    @BeforeEach
    void setUp()
    {
        leaderLock = mock(RedisLeaderLock.class);
        streamQueueService = mock(StreamQueueService.class);
        trunkMonitorService = mock(ITrunkMonitorService.class);
        task = new StreamLagMonitorTask();
        ReflectionTestUtils.setField(task, "leaderLock", leaderLock);
        ReflectionTestUtils.setField(task, "streamQueueService", streamQueueService);
        ReflectionTestUtils.setField(task, "trunkMonitorService", trunkMonitorService);
        ReflectionTestUtils.setField(task, "enabled", true);
        ReflectionTestUtils.setField(task, "lagThreshold", 1000L);
        ReflectionTestUtils.setField(task, "lagDurationMs", 300000L);
        ReflectionTestUtils.setField(task, "dlqThreshold", 1L);
        when(leaderLock.tryRun(anyString(), any(Duration.class), any(Runnable.class)))
                .thenAnswer(invocation ->
                {
                    invocation.getArgument(2, Runnable.class).run();
                    return true;
                });
        when(streamQueueService.registeredQueues()).thenReturn(queues("call-event"));
        when(streamQueueService.pendingCount("call-event")).thenReturn(0L);
        when(streamQueueService.deadLetterCount("call-event")).thenReturn(0L);
    }

    private static Set<String> queues(String... names)
    {
        Set<String> set = new LinkedHashSet<>();
        for (String name : names)
        {
            set.add(name);
        }
        return set;
    }

    @Test
    void 开关关闭时不扫描()
    {
        ReflectionTestUtils.setField(task, "enabled", false);

        task.scan();

        verifyNoInteractions(leaderLock);
        verifyNoInteractions(streamQueueService);
    }

    @Test
    void 未取得集群锁时不扫描()
    {
        when(leaderLock.tryRun(anyString(), any(Duration.class), any(Runnable.class))).thenReturn(false);

        task.scan();

        verify(streamQueueService, never()).pendingCount(anyString());
        verifyNoInteractions(trunkMonitorService);
    }

    @Test
    void 积压低于阈值不告警()
    {
        when(streamQueueService.pendingCount("call-event")).thenReturn(999L);

        task.scan();

        verifyNoInteractions(trunkMonitorService);
    }

    @Test
    void 积压超阈值但未达持续时长不告警()
    {
        when(streamQueueService.pendingCount("call-event")).thenReturn(1500L);

        task.scan();

        verifyNoInteractions(trunkMonitorService);
    }

    @Test
    void 积压持续超时后告警且同一事件仅一次()
    {
        when(streamQueueService.pendingCount("call-event")).thenReturn(1500L);
        ReflectionTestUtils.setField(task, "lagDurationMs", 0L);

        task.scan();
        task.scan();

        verify(trunkMonitorService, times(1)).raiseStreamLagAlarm("call-event", 1500L, 1000L);
    }

    @Test
    void 积压恢复后再次超阈值重新告警()
    {
        ReflectionTestUtils.setField(task, "lagDurationMs", 0L);
        when(streamQueueService.pendingCount("call-event")).thenReturn(1500L);
        task.scan();

        when(streamQueueService.pendingCount("call-event")).thenReturn(10L);
        task.scan();

        when(streamQueueService.pendingCount("call-event")).thenReturn(1500L);
        task.scan();

        verify(trunkMonitorService, times(2)).raiseStreamLagAlarm("call-event", 1500L, 1000L);
    }

    @Test
    void 积压查询异常不告警也不重置状态()
    {
        when(streamQueueService.pendingCount("call-event")).thenReturn(-1L);

        task.scan();

        // 查询异常仅跳过本队列，死信判定仍执行（死信 0 不达阈值）
        verifyNoInteractions(trunkMonitorService);
    }

    @Test
    void 死信达到阈值告警()
    {
        when(streamQueueService.deadLetterCount("call-event")).thenReturn(2L);

        task.scan();

        verify(trunkMonitorService).raiseStreamDlqAlarm("call-event", 2L, 1L);
    }

    @Test
    void 死信低于阈值不告警()
    {
        task.scan();

        verify(trunkMonitorService, never()).raiseStreamDlqAlarm(anyString(), anyLong(), anyLong());
    }

    @Test
    void 单队列异常不影响其他队列()
    {
        when(streamQueueService.registeredQueues()).thenReturn(queues("bad", "good"));
        when(streamQueueService.pendingCount("bad")).thenThrow(new IllegalStateException("redis down"));
        when(streamQueueService.pendingCount("good")).thenReturn(5000L);
        when(streamQueueService.deadLetterCount("good")).thenReturn(0L);
        ReflectionTestUtils.setField(task, "lagDurationMs", 0L);

        task.scan();

        verify(trunkMonitorService).raiseStreamLagAlarm(eq("good"), eq(5000L), eq(1000L));
        verify(trunkMonitorService, never()).raiseStreamLagAlarm(eq("bad"), anyLong(), anyLong());
    }
}
