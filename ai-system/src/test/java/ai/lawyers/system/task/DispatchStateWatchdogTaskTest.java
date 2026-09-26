package ai.lawyers.system.task;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.Collections;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import ai.lawyers.system.domain.lawyers.trunk.AiCallDialLog;
import ai.lawyers.system.domain.lawyers.trunk.AiCallTrunk;
import ai.lawyers.system.enums.DialStatusEnum;
import ai.lawyers.system.mapper.lawyers.trunk.AiCallDialLogMapper;
import ai.lawyers.system.mapper.lawyers.trunk.AiCallTrunkMapper;
import ai.lawyers.system.service.impl.lawyers.trunk.ClusterDispatchState;
import ai.lawyers.system.service.lawyers.cluster.RedisLeaderLock;

/**
 * P3-C1：调度状态看门狗单测。
 *
 * <p>使用真实 {@link RedisLeaderLock} 但置 enabled=false（tryRun 直接执行），
 * 验证：DB 线路并发合计口径的漂移矫正入参、终态/孤儿 holder 被清理、
 * 在途呼叫 holder 保留；集群开关关闭或看门狗关闭时零交互。</p>
 *
 * @author ai-lawyers
 */
class DispatchStateWatchdogTaskTest
{
    private ClusterDispatchState clusterState;

    private AiCallTrunkMapper trunkMapper;

    private AiCallDialLogMapper dialLogMapper;

    private RedisLeaderLock leaderLock;

    private DispatchStateWatchdogTask task;

    @BeforeEach
    void setUp()
    {
        clusterState = mock(ClusterDispatchState.class);
        trunkMapper = mock(AiCallTrunkMapper.class);
        dialLogMapper = mock(AiCallDialLogMapper.class);

        leaderLock = new RedisLeaderLock();
        ReflectionTestUtils.setField(leaderLock, "enabled", false);

        task = new DispatchStateWatchdogTask();
        ReflectionTestUtils.setField(task, "watchdogEnabled", true);
        ReflectionTestUtils.setField(task, "inflightMargin", 8);
        ReflectionTestUtils.setField(task, "clusterState", clusterState);
        ReflectionTestUtils.setField(task, "trunkMapper", trunkMapper);
        ReflectionTestUtils.setField(task, "dialLogMapper", dialLogMapper);
        ReflectionTestUtils.setField(task, "leaderLock", leaderLock);
    }

    @Test
    void scan_clusterMode_reconcilesAndCleansStaleHolders()
    {
        when(clusterState.isClusterEnabled()).thenReturn(true);

        // 线路 DB 并发合计：10 + 20 = 30
        AiCallTrunk t1 = new AiCallTrunk();
        t1.setCurrentConcurrent(10);
        AiCallTrunk t2 = new AiCallTrunk();
        t2.setCurrentConcurrent(20);
        when(trunkMapper.selectAllEnabledTrunks()).thenReturn(Arrays.asList(t1, t2));

        when(clusterState.reconcileGlobalConcurrent(30, 8)).thenReturn(10L);

        // holder：终态话单 / 在途话单 / 孤儿（查无话单）
        when(clusterState.scanTrunkHolders())
                .thenReturn(Arrays.asList("stale-uuid", "active-uuid", "orphan-uuid"));
        AiCallDialLog staleLog = new AiCallDialLog();
        staleLog.setDialStatus(DialStatusEnum.HANGUP.getCode());
        AiCallDialLog activeLog = new AiCallDialLog();
        activeLog.setDialStatus(DialStatusEnum.ANSWERED.getCode());
        when(dialLogMapper.selectByCallUuid("stale-uuid")).thenReturn(staleLog);
        when(dialLogMapper.selectByCallUuid("active-uuid")).thenReturn(activeLog);
        when(dialLogMapper.selectByCallUuid("orphan-uuid")).thenReturn(null);

        task.scan();

        // 漂移矫正：权威值=30、余量=8
        verify(clusterState, times(1)).reconcileGlobalConcurrent(30, 8);
        // 终态/孤儿 holder 清理；在途 holder 保留
        verify(clusterState, times(1)).releaseTrunk("stale-uuid");
        verify(clusterState, times(1)).releaseTrunk("orphan-uuid");
        verify(clusterState, never()).releaseTrunk("active-uuid");
    }

    @Test
    void scan_clusterMode_noDriftOrHolders_noReleases()
    {
        when(clusterState.isClusterEnabled()).thenReturn(true);
        when(trunkMapper.selectAllEnabledTrunks()).thenReturn(Collections.emptyList());
        when(clusterState.reconcileGlobalConcurrent(0, 8)).thenReturn(0L);
        when(clusterState.scanTrunkHolders()).thenReturn(Collections.emptyList());

        task.scan();

        verify(clusterState, never()).releaseTrunk(org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void scan_clusterDisabled_skipsEverything()
    {
        when(clusterState.isClusterEnabled()).thenReturn(false);

        task.scan();

        verify(clusterState, never()).reconcileGlobalConcurrent(anyInt(), anyInt());
        verify(clusterState, never()).scanTrunkHolders();
        verify(trunkMapper, never()).selectAllEnabledTrunks();
    }

    @Test
    void scan_watchdogDisabled_skipsEverything()
    {
        ReflectionTestUtils.setField(task, "watchdogEnabled", false);
        when(clusterState.isClusterEnabled()).thenReturn(true);

        task.scan();

        verify(clusterState, never()).reconcileGlobalConcurrent(anyInt(), anyInt());
        verify(trunkMapper, never()).selectAllEnabledTrunks();
    }
}
