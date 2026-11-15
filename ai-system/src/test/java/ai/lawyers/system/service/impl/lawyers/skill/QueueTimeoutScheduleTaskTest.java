package ai.lawyers.system.service.impl.lawyers.skill;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.Arrays;
import java.util.Collections;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import ai.lawyers.system.domain.lawyers.skill.AiCallQueue;
import ai.lawyers.system.domain.lawyers.skill.AiSkillGroup;
import ai.lawyers.system.domain.lawyers.skill.DispatchContext;
import ai.lawyers.system.domain.lawyers.skill.DispatchResult;
import ai.lawyers.system.mapper.lawyers.skill.AiCallQueueMapper;
import ai.lawyers.system.mapper.lawyers.skill.AiSkillGroupMapper;
import ai.lawyers.system.service.lawyers.cluster.RedisLeaderLock;
import ai.lawyers.system.service.lawyers.skill.IAgentDispatchService;

/**
 * L3：{@link QueueTimeoutScheduleTask} 排队超时扫描测试。
 *
 * <p>覆盖单主锁未获取时不扫描、查询为空/异常、条件更新幂等（影响行数 0 视为已被处理）、
 * 无溢出组与溢出组等于自身不转派、转派时上下文标志位（禁止链式溢出/不再入队）与
 * 优先级透传、转派失败可容忍、单条异常不影响其余记录。</p>
 *
 * @author ai-lawyers
 */
class QueueTimeoutScheduleTaskTest
{
    private AiCallQueueMapper queueMapper;

    private AiSkillGroupMapper skillGroupMapper;

    private IAgentDispatchService agentDispatchService;

    private RedisLeaderLock leaderLock;

    private QueueTimeoutScheduleTask task;

    @BeforeEach
    void setUp()
    {
        queueMapper = mock(AiCallQueueMapper.class);
        skillGroupMapper = mock(AiSkillGroupMapper.class);
        agentDispatchService = mock(IAgentDispatchService.class);
        leaderLock = mock(RedisLeaderLock.class);
        task = new QueueTimeoutScheduleTask();
        ReflectionTestUtils.setField(task, "queueMapper", queueMapper);
        ReflectionTestUtils.setField(task, "skillGroupMapper", skillGroupMapper);
        ReflectionTestUtils.setField(task, "agentDispatchService", agentDispatchService);
        ReflectionTestUtils.setField(task, "leaderLock", leaderLock);
    }

    @Test
    void skipsScanWhenLockNotAcquired()
    {
        when(leaderLock.tryRun(anyString(), any(Duration.class), any(Runnable.class))).thenReturn(false);

        task.scanTimeoutQueues();

        verifyNoInteractions(queueMapper, skillGroupMapper, agentDispatchService);
    }

    @Test
    void doesNothingWhenNoTimeoutQueue()
    {
        runUnderLock();
        when(queueMapper.selectTimeoutQueuing()).thenReturn(Collections.emptyList());

        task.scanTimeoutQueues();

        verifyNoInteractions(skillGroupMapper, agentDispatchService);
    }

    @Test
    void stopsScanWhenQueryFails()
    {
        runUnderLock();
        when(queueMapper.selectTimeoutQueuing()).thenThrow(new RuntimeException("db down"));

        task.scanTimeoutQueues();

        verifyNoInteractions(skillGroupMapper, agentDispatchService);
    }

    @Test
    void skipsWhenConditionalUpdateAffectsNoRow()
    {
        runUnderLock();
        when(queueMapper.selectTimeoutQueuing()).thenReturn(Collections.singletonList(queue(1L, 10L)));
        when(queueMapper.markTimeoutOverflow(1L)).thenReturn(0);

        task.scanTimeoutQueues();

        verifyNoInteractions(skillGroupMapper, agentDispatchService);
    }

    @Test
    void noDispatchWhenSkillGroupMissing()
    {
        runUnderLock();
        when(queueMapper.selectTimeoutQueuing()).thenReturn(Collections.singletonList(queue(1L, 10L)));
        when(queueMapper.markTimeoutOverflow(1L)).thenReturn(1);
        when(skillGroupMapper.selectAiSkillGroupByGroupId(10L)).thenReturn(null);

        task.scanTimeoutQueues();

        verifyNoInteractions(agentDispatchService);
    }

    @Test
    void noDispatchWhenOverflowGroupIsSelf()
    {
        runUnderLock();
        when(queueMapper.selectTimeoutQueuing()).thenReturn(Collections.singletonList(queue(1L, 10L)));
        when(queueMapper.markTimeoutOverflow(1L)).thenReturn(1);
        when(skillGroupMapper.selectAiSkillGroupByGroupId(10L)).thenReturn(group(10L));

        task.scanTimeoutQueues();

        verifyNoInteractions(agentDispatchService);
    }

    @Test
    void noDispatchWhenOverflowGroupNotConfigured()
    {
        runUnderLock();
        when(queueMapper.selectTimeoutQueuing()).thenReturn(Collections.singletonList(queue(1L, 10L)));
        when(queueMapper.markTimeoutOverflow(1L)).thenReturn(1);
        when(skillGroupMapper.selectAiSkillGroupByGroupId(10L)).thenReturn(group(null));

        task.scanTimeoutQueues();

        verifyNoInteractions(agentDispatchService);
    }

    @Test
    void dispatchesToOverflowGroupWithoutChainedOverflow()
    {
        runUnderLock();
        AiCallQueue queue = queue(1L, 10L);
        queue.setPriority(5);
        when(queueMapper.selectTimeoutQueuing()).thenReturn(Collections.singletonList(queue));
        when(queueMapper.markTimeoutOverflow(1L)).thenReturn(1);
        when(skillGroupMapper.selectAiSkillGroupByGroupId(10L)).thenReturn(group(20L));
        when(agentDispatchService.dispatch(eq(20L), any(DispatchContext.class)))
                .thenReturn(DispatchResult.assigned(99L, "坐席A", 20L, "溢出组", "longest_idle"));

        task.scanTimeoutQueues();

        ArgumentCaptor<DispatchContext> ctx = ArgumentCaptor.forClass(DispatchContext.class);
        verify(agentDispatchService).dispatch(eq(20L), ctx.capture());
        assertThat(ctx.getValue().getSessionId()).isEqualTo("sess-1");
        assertThat(ctx.getValue().getRecordId()).isEqualTo(100L);
        assertThat(ctx.getValue().getCallerNumber()).isEqualTo("13800000000");
        assertThat(ctx.getValue().getPriority()).isEqualTo(5);
        assertThat(ctx.getValue().isAllowOverflow()).isFalse();
        assertThat(ctx.getValue().isEnqueueIfNoAgent()).isFalse();
    }

    @Test
    void toleratesDispatchFailure()
    {
        runUnderLock();
        when(queueMapper.selectTimeoutQueuing()).thenReturn(Collections.singletonList(queue(1L, 10L)));
        when(queueMapper.markTimeoutOverflow(1L)).thenReturn(1);
        when(skillGroupMapper.selectAiSkillGroupByGroupId(10L)).thenReturn(group(20L));
        when(agentDispatchService.dispatch(eq(20L), any(DispatchContext.class)))
                .thenReturn(DispatchResult.failed("无可用坐席"));

        task.scanTimeoutQueues();

        verify(agentDispatchService).dispatch(eq(20L), any(DispatchContext.class));
    }

    @Test
    void oneBadRecordDoesNotBlockOthers()
    {
        runUnderLock();
        when(queueMapper.selectTimeoutQueuing()).thenReturn(Arrays.asList(queue(1L, 10L), queue(2L, 20L)));
        when(queueMapper.markTimeoutOverflow(1L)).thenThrow(new RuntimeException("db down"));
        when(queueMapper.markTimeoutOverflow(2L)).thenReturn(1);
        when(skillGroupMapper.selectAiSkillGroupByGroupId(20L)).thenReturn(group(30L));
        when(agentDispatchService.dispatch(eq(30L), any(DispatchContext.class)))
                .thenReturn(DispatchResult.assigned(99L, "坐席B", 30L, "溢出组", "longest_idle"));

        task.scanTimeoutQueues();

        verify(skillGroupMapper, never()).selectAiSkillGroupByGroupId(10L);
        verify(agentDispatchService).dispatch(eq(30L), any(DispatchContext.class));
    }

    /** 模拟单主锁获取成功并同步执行任务体 */
    private void runUnderLock()
    {
        when(leaderLock.tryRun(anyString(), any(Duration.class), any(Runnable.class))).thenAnswer(invocation ->
        {
            ((Runnable) invocation.getArgument(2)).run();
            return true;
        });
    }

    private static AiCallQueue queue(Long queueId, Long groupId)
    {
        AiCallQueue queue = new AiCallQueue();
        queue.setQueueId(queueId);
        queue.setSessionId("sess-1");
        queue.setRecordId(100L);
        queue.setCallerNumber("13800000000");
        queue.setGroupId(groupId);
        return queue;
    }

    private static AiSkillGroup group(Long overflowGroupId)
    {
        AiSkillGroup group = new AiSkillGroup();
        group.setOverflowGroupId(overflowGroupId);
        return group;
    }
}
