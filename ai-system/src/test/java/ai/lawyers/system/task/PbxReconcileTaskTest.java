package ai.lawyers.system.task;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.springframework.test.util.ReflectionTestUtils;

import ai.lawyers.system.domain.lawyers.trunk.AiCallDialLog;
import ai.lawyers.system.enums.DialStatusEnum;
import ai.lawyers.system.mapper.lawyers.trunk.AiCallDialLogMapper;
import ai.lawyers.system.service.lawyers.cluster.RedisLeaderLock;
import ai.lawyers.system.service.lawyers.trunk.ICallDispatchService;
import ai.lawyers.system.service.lawyers.trunk.gateway.esl.EslEventBridgeService;

/**
 * P3-B4：{@link PbxReconcileTask} 卡死矫正与通道数对账测试。
 */
class PbxReconcileTaskTest
{
    private PbxReconcileTask task;
    private RedisLeaderLock leaderLock;
    private AiCallDialLogMapper dialLogMapper;
    private ICallDispatchService callDispatchService;
    private EslEventBridgeService eslBridge;

    @BeforeEach
    void setUp()
    {
        task = new PbxReconcileTask();
        leaderLock = mock(RedisLeaderLock.class);
        dialLogMapper = mock(AiCallDialLogMapper.class);
        callDispatchService = mock(ICallDispatchService.class);
        eslBridge = mock(EslEventBridgeService.class);
        ReflectionTestUtils.setField(task, "leaderLock", leaderLock);
        ReflectionTestUtils.setField(task, "dialLogMapper", dialLogMapper);
        ReflectionTestUtils.setField(task, "callDispatchService", callDispatchService);
        ReflectionTestUtils.setField(task, "eslBridge", eslBridge);
        ReflectionTestUtils.setField(task, "enabled", true);
        ReflectionTestUtils.setField(task, "diffThreshold", 5);
        ReflectionTestUtils.setField(task, "staleMinutes", 10);
        ReflectionTestUtils.setField(task, "staleLimit", 50);
    }

    private static AiCallDialLog staleLog(Long logId, String callUuid)
    {
        AiCallDialLog d = new AiCallDialLog();
        d.setLogId(logId);
        d.setCallUuid(callUuid);
        d.setDialStatus(DialStatusEnum.DIALING.getCode());
        d.setCreateTime(new Date(System.currentTimeMillis() - 30 * 60_000L));
        return d;
    }

    @Test
    void reconcile_disabled_skips()
    {
        ReflectionTestUtils.setField(task, "enabled", false);

        task.reconcile();

        verifyNoInteractions(leaderLock);
    }

    @Test
    void reconcile_enabled_runsUnderClusterLock()
    {
        task.reconcile();

        verify(leaderLock).tryRun(eq("job:pbx-reconcile"), any(Duration.class), any(Runnable.class));
        // mock 锁默认不执行任务体，DB 不应有交互
        verify(dialLogMapper, never()).selectStaleActiveDials(any(Date.class), anyInt());
    }

    @Test
    void correctStale_withCallUuid_routesThroughEventChannel()
    {
        when(dialLogMapper.selectStaleActiveDials(any(Date.class), eq(50)))
                .thenReturn(Arrays.asList(staleLog(1L, "u1")));

        task.correctStaleDials();

        // 走标准 FAILED 事件通道（复用 finishCall 释放并发与指标联动）
        verify(callDispatchService).onCallEvent(eq("u1"), eq("FAILED"), anyMap());
        verify(dialLogMapper, never()).updateAiCallDialLog(any());
    }

    @Test
    void correctStale_withoutCallUuid_marksFailedDirectly()
    {
        when(dialLogMapper.selectStaleActiveDials(any(Date.class), eq(50)))
                .thenReturn(Arrays.asList(staleLog(2L, null)));

        task.correctStaleDials();

        verify(dialLogMapper).updateAiCallDialLog(ArgumentMatchers.argThat(
                u -> Long.valueOf(2L).equals(u.getLogId())
                        && DialStatusEnum.FAILED.getCode().equals(u.getDialStatus())));
        verify(callDispatchService, never()).onCallEvent(any(), any(), anyMap());
    }

    @Test
    void correctStale_emptyList_noOps()
    {
        when(dialLogMapper.selectStaleActiveDials(any(Date.class), eq(50)))
                .thenReturn(Collections.emptyList());

        task.correctStaleDials();

        verify(callDispatchService, never()).onCallEvent(any(), any(), anyMap());
        verify(dialLogMapper, never()).updateAiCallDialLog(any());
    }

    @Test
    void channelCount_notEslLeader_skips()
    {
        when(eslBridge.isEslLeader()).thenReturn(false);

        task.reconcileChannelCount();

        verify(eslBridge, never()).queryPbxChannelCount();
        verify(dialLogMapper, never()).countActiveDials();
    }

    @Test
    void channelCount_eslUnreachable_skips()
    {
        when(eslBridge.isEslLeader()).thenReturn(true);
        when(eslBridge.queryPbxChannelCount()).thenReturn(-1);

        task.reconcileChannelCount();

        verify(dialLogMapper, never()).countActiveDials();
    }

    @Test
    void channelCount_overThreshold_completesWithWarn()
    {
        when(eslBridge.isEslLeader()).thenReturn(true);
        when(eslBridge.queryPbxChannelCount()).thenReturn(2);
        when(dialLogMapper.countActiveDials()).thenReturn(10);

        // 偏差 8 > 阈值 5：WARN 告警但不抛异常、不自动修复
        task.reconcileChannelCount();

        verify(dialLogMapper).countActiveDials();
    }

    @Test
    void channelCount_withinThreshold_normal()
    {
        when(eslBridge.isEslLeader()).thenReturn(true);
        when(eslBridge.queryPbxChannelCount()).thenReturn(10);
        when(dialLogMapper.countActiveDials()).thenReturn(8);

        task.reconcileChannelCount();

        verify(dialLogMapper).countActiveDials();
    }
}
