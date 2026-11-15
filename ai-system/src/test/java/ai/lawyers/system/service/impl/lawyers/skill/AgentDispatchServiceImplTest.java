package ai.lawyers.system.service.impl.lawyers.skill;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.util.ReflectionTestUtils;
import ai.lawyers.common.core.redis.RedisCache;
import ai.lawyers.system.domain.lawyers.AiCallAgentStatus;
import ai.lawyers.system.domain.lawyers.skill.AiCallQueue;
import ai.lawyers.system.domain.lawyers.skill.AiSkillGroup;
import ai.lawyers.system.domain.lawyers.skill.AiSkillGroupMember;
import ai.lawyers.system.domain.lawyers.skill.AgentLoadStat;
import ai.lawyers.system.domain.lawyers.skill.DispatchContext;
import ai.lawyers.system.domain.lawyers.skill.DispatchResult;
import ai.lawyers.system.mapper.lawyers.AiCallAgentStatusMapper;
import ai.lawyers.system.mapper.lawyers.skill.AiCallQueueMapper;
import ai.lawyers.system.mapper.lawyers.skill.AiSkillGroupMapper;
import ai.lawyers.system.mapper.lawyers.skill.AiSkillGroupMemberMapper;
import ai.lawyers.system.service.lawyers.skill.IAiSkillGroupService;

/**
 * 坐席智能分配服务单测。
 *
 * <p>覆盖技能组校验、按策略排序（轮询/最久未接/最少通话/全员振铃）、原子抢占失败换人、
 * 溢出技能组、入队与既有队列复用、手动分配、踢除排队与坐席空闲后拉取排队者。</p>
 *
 * @author ai-lawyers
 */
class AgentDispatchServiceImplTest
{
    private AgentDispatchServiceImpl service;

    @Mock private AiSkillGroupMapper skillGroupMapper;
    @Mock private AiSkillGroupMemberMapper memberMapper;
    @Mock private AiCallQueueMapper queueMapper;
    @Mock private AiCallAgentStatusMapper agentStatusMapper;
    @Mock private IAiSkillGroupService skillGroupService;
    @Mock private RedisCache redisCache;
    @Mock private RedisTemplate redisTemplate;
    @Mock private ValueOperations valueOperations;

    @BeforeEach
    void setUp()
    {
        MockitoAnnotations.openMocks(this);
        service = new AgentDispatchServiceImpl();
        ReflectionTestUtils.setField(service, "skillGroupMapper", skillGroupMapper);
        ReflectionTestUtils.setField(service, "memberMapper", memberMapper);
        ReflectionTestUtils.setField(service, "queueMapper", queueMapper);
        ReflectionTestUtils.setField(service, "agentStatusMapper", agentStatusMapper);
        ReflectionTestUtils.setField(service, "skillGroupService", skillGroupService);
        ReflectionTestUtils.setField(service, "redisCache", redisCache);
        ReflectionTestUtils.setField(redisCache, "redisTemplate", redisTemplate);
    }

    private static AiSkillGroup group(Long id, String strategy, String status, Long overflowId)
    {
        AiSkillGroup group = new AiSkillGroup();
        group.setGroupId(id);
        group.setGroupName("组" + id);
        group.setStrategy(strategy);
        group.setStatus(status);
        group.setOverflowGroupId(overflowId);
        return group;
    }

    private static AiSkillGroupMember member(Long agentId, Long groupId)
    {
        AiSkillGroupMember member = new AiSkillGroupMember();
        member.setAgentId(agentId);
        member.setGroupId(groupId);
        member.setSkillLevel(1);
        return member;
    }

    private static AiCallAgentStatus agent(Long agentId, String name, String status, String callStatus)
    {
        AiCallAgentStatus agent = new AiCallAgentStatus();
        agent.setAgentId(agentId);
        agent.setUserId(agentId + 100);
        agent.setAgentName(name);
        agent.setStatus(status);
        agent.setCallStatus(callStatus);
        return agent;
    }

    private static AgentLoadStat loadStat(Long agentId, Date lastCall, Integer todayCompleted)
    {
        AgentLoadStat stat = new AgentLoadStat();
        stat.setAgentId(agentId);
        stat.setLastCallStartTime(lastCall);
        stat.setTodayCompleted(todayCompleted);
        return stat;
    }

    private DispatchContext context()
    {
        return DispatchContext.of("s1", 1L, "13800138000");
    }

    @Test
    void dispatch_groupMissing_fails()
    {
        when(skillGroupMapper.selectAiSkillGroupByGroupId(1L)).thenReturn(null);

        assertEquals("技能组不存在或已停用", service.dispatch(1L, context()).getMessage());
    }

    @Test
    void dispatch_groupDisabled_fails()
    {
        when(skillGroupMapper.selectAiSkillGroupByGroupId(1L)).thenReturn(group(1L, "round_robin", "0", null));

        assertEquals("技能组不存在或已停用", service.dispatch(1L, context()).getMessage());
    }

    @Test
    void dispatch_noAgentAndNoEnqueue_fails()
    {
        when(skillGroupMapper.selectAiSkillGroupByGroupId(1L)).thenReturn(group(1L, "all_ring", "1", null));
        when(memberMapper.selectAvailableByGroupId(1L)).thenReturn(Collections.emptyList());
        DispatchContext ctx = context();
        ctx.setEnqueueIfNoAgent(false);

        assertEquals("技能组[组1]暂无空闲坐席", service.dispatch(1L, ctx).getMessage());
        verify(queueMapper, never()).insertAiCallQueue(any());
    }

    @Test
    void dispatch_noAgent_enqueuesAtTail()
    {
        when(skillGroupMapper.selectAiSkillGroupByGroupId(1L)).thenReturn(group(1L, "all_ring", "1", null));
        when(memberMapper.selectAvailableByGroupId(1L)).thenReturn(Collections.emptyList());
        when(queueMapper.selectQueuingBySessionId("s1")).thenReturn(null);
        when(queueMapper.countBefore(eq(1L), any(Date.class), eq(0))).thenReturn(2);
        when(queueMapper.countQueuingByGroupId(1L)).thenReturn(5);

        DispatchResult result = service.dispatch(1L, context());

        assertFalse(result.isSuccess());
        assertEquals(3, result.getQueuePosition());
        assertEquals(5, result.getQueueSize());
        verify(queueMapper).insertAiCallQueue(any(AiCallQueue.class));
    }

    @Test
    void dispatch_existingQueue_reusesAndReportsPosition()
    {
        AiCallQueue existing = new AiCallQueue();
        existing.setQueueId(9L);
        existing.setEnqueueTime(new Date());
        existing.setPriority(2);
        when(skillGroupMapper.selectAiSkillGroupByGroupId(1L)).thenReturn(group(1L, "all_ring", "1", null));
        when(memberMapper.selectAvailableByGroupId(1L)).thenReturn(Collections.emptyList());
        when(queueMapper.selectQueuingBySessionId("s1")).thenReturn(existing);
        when(queueMapper.countBefore(eq(1L), any(Date.class), eq(2))).thenReturn(0);
        when(queueMapper.countQueuingByGroupId(1L)).thenReturn(1);

        DispatchResult result = service.dispatch(1L, context());

        assertEquals(9L, result.getQueueId());
        assertEquals(1, result.getQueuePosition());
        verify(queueMapper, never()).insertAiCallQueue(any());
    }

    @Test
    void dispatch_singleAgent_assignsAndUpdatesStatus()
    {
        when(skillGroupMapper.selectAiSkillGroupByGroupId(1L)).thenReturn(group(1L, "all_ring", "1", null));
        when(memberMapper.selectAvailableByGroupId(1L))
            .thenReturn(Collections.singletonList(member(5L, 1L)));
        when(agentStatusMapper.occupyAgentIfFree(5L)).thenReturn(1);
        when(agentStatusMapper.selectAiCallAgentStatusByAgentId(5L))
            .thenReturn(agent(5L, "小王", "1", "0"));
        when(queueMapper.selectQueuingBySessionId("s1")).thenReturn(null);

        DispatchResult result = service.dispatch(1L, context());

        assertTrue(result.isSuccess());
        assertEquals(5L, result.getAgentId());
        assertEquals("小王", result.getAgentName());
        assertEquals("all_ring", result.getStrategy());
        ArgumentCaptor<AiCallQueue> queueCaptor = ArgumentCaptor.forClass(AiCallQueue.class);
        verify(queueMapper).insertAiCallQueue(queueCaptor.capture());
        assertEquals("1", queueCaptor.getValue().getQueueStatus());
        assertEquals(0, queueCaptor.getValue().getWaitDuration());
        ArgumentCaptor<AiCallAgentStatus> agentCaptor = ArgumentCaptor.forClass(AiCallAgentStatus.class);
        verify(agentStatusMapper).updateAiCallAgentStatus(agentCaptor.capture());
        assertEquals("1", agentCaptor.getValue().getCallStatus());
        assertEquals("13800138000", agentCaptor.getValue().getCurrentCallPhone());
    }

    @Test
    void dispatch_occupyFails_triesNextCandidate()
    {
        when(skillGroupMapper.selectAiSkillGroupByGroupId(1L)).thenReturn(group(1L, "all_ring", "1", null));
        when(memberMapper.selectAvailableByGroupId(1L))
            .thenReturn(Arrays.asList(member(5L, 1L), member(6L, 1L)));
        when(agentStatusMapper.occupyAgentIfFree(5L)).thenReturn(0);
        when(agentStatusMapper.occupyAgentIfFree(6L)).thenReturn(1);
        when(agentStatusMapper.selectAiCallAgentStatusByAgentId(6L))
            .thenReturn(agent(6L, "小李", "1", "0"));

        DispatchResult result = service.dispatch(1L, context());

        assertTrue(result.isSuccess());
        assertEquals(6L, result.getAgentId());
    }

    @Test
    void dispatch_occupyAllFail_enqueues()
    {
        when(skillGroupMapper.selectAiSkillGroupByGroupId(1L)).thenReturn(group(1L, "all_ring", "1", null));
        when(memberMapper.selectAvailableByGroupId(1L))
            .thenReturn(Collections.singletonList(member(5L, 1L)));
        when(agentStatusMapper.occupyAgentIfFree(5L)).thenReturn(0);
        when(queueMapper.selectQueuingBySessionId("s1")).thenReturn(null);
        when(queueMapper.countBefore(anyLong(), any(Date.class), any())).thenReturn(0);
        when(queueMapper.countQueuingByGroupId(1L)).thenReturn(1);

        DispatchResult result = service.dispatch(1L, context());

        assertFalse(result.isSuccess());
        assertEquals(1, result.getQueuePosition());
    }

    @Test
    void dispatch_overflow_usesOverflowGroup()
    {
        when(skillGroupMapper.selectAiSkillGroupByGroupId(1L)).thenReturn(group(1L, "all_ring", "1", 2L));
        when(skillGroupMapper.selectAiSkillGroupByGroupId(2L)).thenReturn(group(2L, "all_ring", "1", null));
        when(memberMapper.selectAvailableByGroupId(1L)).thenReturn(Collections.emptyList());
        when(memberMapper.selectAvailableByGroupId(2L))
            .thenReturn(Collections.singletonList(member(7L, 2L)));
        when(agentStatusMapper.occupyAgentIfFree(7L)).thenReturn(1);
        when(agentStatusMapper.selectAiCallAgentStatusByAgentId(7L))
            .thenReturn(agent(7L, "小张", "1", "0"));

        DispatchResult result = service.dispatch(1L, context());

        assertTrue(result.isSuccess());
        assertEquals(2L, result.getGroupId());
        assertEquals(7L, result.getAgentId());
    }

    @Test
    void dispatch_overflowDisabled_enqueuesInOriginalGroup()
    {
        when(skillGroupMapper.selectAiSkillGroupByGroupId(1L)).thenReturn(group(1L, "all_ring", "1", 2L));
        when(memberMapper.selectAvailableByGroupId(1L)).thenReturn(Collections.emptyList());
        when(queueMapper.selectQueuingBySessionId("s1")).thenReturn(null);
        when(queueMapper.countBefore(anyLong(), any(Date.class), any())).thenReturn(0);
        when(queueMapper.countQueuingByGroupId(1L)).thenReturn(1);
        DispatchContext ctx = context();
        ctx.setAllowOverflow(false);

        DispatchResult result = service.dispatch(1L, ctx);

        assertEquals(1L, result.getGroupId());
        verify(skillGroupMapper, never()).selectAiSkillGroupByGroupId(2L);
    }

    @Test
    void dispatch_roundRobin_rotatesCandidates()
    {
        when(skillGroupMapper.selectAiSkillGroupByGroupId(1L)).thenReturn(group(1L, null, "1", null));
        when(memberMapper.selectAvailableByGroupId(1L))
            .thenReturn(Arrays.asList(member(5L, 1L), member(6L, 1L)));
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.increment("skill:rr:seq:1")).thenReturn(1L);
        when(agentStatusMapper.occupyAgentIfFree(6L)).thenReturn(1);
        when(agentStatusMapper.selectAiCallAgentStatusByAgentId(6L))
            .thenReturn(agent(6L, "小李", "1", "0"));

        DispatchResult result = service.dispatch(1L, context());

        assertEquals(6L, result.getAgentId());
        verify(agentStatusMapper, never()).occupyAgentIfFree(5L);
        verify(redisCache).expire("skill:rr:seq:1", 24, TimeUnit.HOURS);
    }

    @Test
    void dispatch_leastCalls_sortsFewestFirst()
    {
        when(skillGroupMapper.selectAiSkillGroupByGroupId(1L))
            .thenReturn(group(1L, "least_calls", "1", null));
        when(memberMapper.selectAvailableByGroupId(1L))
            .thenReturn(Arrays.asList(member(5L, 1L), member(6L, 1L)));
        when(memberMapper.selectLoadStatsByAgentIds(anyListOfLong()))
            .thenReturn(Arrays.asList(loadStat(5L, new Date(), 9), loadStat(6L, new Date(), 1)));
        when(agentStatusMapper.occupyAgentIfFree(6L)).thenReturn(1);
        when(agentStatusMapper.selectAiCallAgentStatusByAgentId(6L))
            .thenReturn(agent(6L, "小李", "1", "0"));

        assertEquals(6L, service.dispatch(1L, context()).getAgentId());
        verify(agentStatusMapper, never()).occupyAgentIfFree(5L);
    }

    @Test
    void dispatch_leastRecent_nullCallGoesFirst()
    {
        when(skillGroupMapper.selectAiSkillGroupByGroupId(1L))
            .thenReturn(group(1L, "least_recent", "1", null));
        when(memberMapper.selectAvailableByGroupId(1L))
            .thenReturn(Arrays.asList(member(5L, 1L), member(6L, 1L)));
        when(memberMapper.selectLoadStatsByAgentIds(anyListOfLong()))
            .thenReturn(Arrays.asList(loadStat(5L, new Date(), 1), loadStat(6L, null, 1)));
        when(agentStatusMapper.occupyAgentIfFree(6L)).thenReturn(1);
        when(agentStatusMapper.selectAiCallAgentStatusByAgentId(6L))
            .thenReturn(agent(6L, "小李", "1", "0"));

        assertEquals(6L, service.dispatch(1L, context()).getAgentId());
    }

    private static List<Long> anyListOfLong()
    {
        return org.mockito.ArgumentMatchers.anyList();
    }

    @Test
    void dispatchByCategory_groupMissing_fails()
    {
        when(skillGroupService.selectByCategoryId(3L)).thenReturn(null);

        assertTrue(service.dispatchByCategory(3L, context()).getMessage().contains("未找到咨询分类[3]"));

        AiSkillGroup disabled = group(1L, "all_ring", "0", null);
        when(skillGroupService.selectByCategoryId(3L)).thenReturn(disabled);
        when(skillGroupMapper.selectAiSkillGroupByGroupId(1L)).thenReturn(disabled);
        assertTrue(service.dispatchByCategory(3L, context()).getMessage().contains("技能组不存在或已停用"));
    }

    @Test
    void dispatchByCategory_delegates()
    {
        when(skillGroupService.selectByCategoryId(3L)).thenReturn(group(1L, "all_ring", "1", null));
        when(skillGroupMapper.selectAiSkillGroupByGroupId(1L)).thenReturn(group(1L, "all_ring", "1", null));
        when(memberMapper.selectAvailableByGroupId(1L)).thenReturn(Collections.emptyList());
        DispatchContext ctx = context();
        ctx.setEnqueueIfNoAgent(false);

        assertEquals("技能组[组1]暂无空闲坐席", service.dispatchByCategory(3L, ctx).getMessage());
    }

    @Test
    void assignManually_queueMissingOrProcessed_fails()
    {
        when(queueMapper.selectAiCallQueueByQueueId(1L)).thenReturn(null);
        assertEquals("排队记录不存在或已处理", service.assignManually(1L, 5L).getMessage());

        AiCallQueue processed = new AiCallQueue();
        processed.setQueueStatus("1");
        when(queueMapper.selectAiCallQueueByQueueId(2L)).thenReturn(processed);
        assertEquals("排队记录不存在或已处理", service.assignManually(2L, 5L).getMessage());
    }

    @Test
    void assignManually_agentNotFree_fails()
    {
        AiCallQueue queue = new AiCallQueue();
        queue.setQueueStatus("0");
        queue.setGroupId(1L);
        when(queueMapper.selectAiCallQueueByQueueId(1L)).thenReturn(queue);
        when(skillGroupMapper.selectAiSkillGroupByGroupId(1L)).thenReturn(group(1L, "all_ring", "1", null));
        when(agentStatusMapper.selectAiCallAgentStatusByAgentId(5L))
            .thenReturn(agent(5L, "小王", "1", "1"));

        assertEquals("目标坐席不存在或当前不空闲", service.assignManually(1L, 5L).getMessage());
    }

    @Test
    void assignManually_occupyFails_fails()
    {
        AiCallQueue queue = new AiCallQueue();
        queue.setQueueStatus("0");
        queue.setGroupId(1L);
        when(queueMapper.selectAiCallQueueByQueueId(1L)).thenReturn(queue);
        when(skillGroupMapper.selectAiSkillGroupByGroupId(1L)).thenReturn(group(1L, "all_ring", "1", null));
        when(agentStatusMapper.selectAiCallAgentStatusByAgentId(5L))
            .thenReturn(agent(5L, "小王", "1", "0"));
        when(agentStatusMapper.occupyAgentIfFree(5L)).thenReturn(0);

        assertEquals("目标坐席已被其他来电占用", service.assignManually(1L, 5L).getMessage());
    }

    @Test
    void assignManually_success_updatesQueueAndAgent()
    {
        AiCallQueue queue = new AiCallQueue();
        queue.setQueueId(1L);
        queue.setQueueStatus("0");
        queue.setGroupId(1L);
        queue.setRecordId(2L);
        queue.setCallerNumber("138");
        queue.setEnqueueTime(new Date(System.currentTimeMillis() - 3000));
        when(queueMapper.selectAiCallQueueByQueueId(1L)).thenReturn(queue);
        when(skillGroupMapper.selectAiSkillGroupByGroupId(1L)).thenReturn(group(1L, "all_ring", "1", null));
        when(agentStatusMapper.selectAiCallAgentStatusByAgentId(5L))
            .thenReturn(agent(5L, "小王", "1", "0"));
        when(agentStatusMapper.occupyAgentIfFree(5L)).thenReturn(1);

        DispatchResult result = service.assignManually(1L, 5L);

        assertTrue(result.isSuccess());
        assertEquals("manual", result.getStrategy());
        assertEquals(1L, result.getQueueId());
        assertEquals("1", queue.getQueueStatus());
        assertEquals("manual", queue.getStrategyUsed());
        assertTrue(queue.getWaitDuration() >= 2);
        verify(queueMapper).updateAiCallQueue(queue);
        verify(agentStatusMapper).updateAiCallAgentStatus(any(AiCallAgentStatus.class));
    }

    @Test
    void listQueuing_byGroupAndAll()
    {
        List<AiCallQueue> byGroup = Collections.singletonList(new AiCallQueue());
        when(queueMapper.selectQueuingByGroupId(1L)).thenReturn(byGroup);
        assertEquals(byGroup, service.listQueuing(1L));

        when(queueMapper.selectAiCallQueueList(any(AiCallQueue.class))).thenReturn(byGroup);
        assertEquals(byGroup, service.listQueuing(null));
        ArgumentCaptor<AiCallQueue> captor = ArgumentCaptor.forClass(AiCallQueue.class);
        verify(queueMapper).selectAiCallQueueList(captor.capture());
        assertEquals("0", captor.getValue().getQueueStatus());
    }

    @Test
    void kickQueue_missing_returnsZero()
    {
        when(queueMapper.selectAiCallQueueByQueueId(1L)).thenReturn(null);

        assertEquals(0, service.kickQueue(1L, "超时"));
    }

    @Test
    void kickQueue_success_marksAbandoned()
    {
        AiCallQueue queue = new AiCallQueue();
        queue.setQueueStatus("0");
        queue.setSessionId("s1");
        queue.setEnqueueTime(new Date(System.currentTimeMillis() - 1000));
        when(queueMapper.selectAiCallQueueByQueueId(1L)).thenReturn(queue);
        when(queueMapper.updateAiCallQueue(queue)).thenReturn(1);

        assertEquals(1, service.kickQueue(1L, "超时"));
        assertEquals("3", queue.getQueueStatus());
        assertTrue(queue.getAbandonTime() != null);
    }

    @Test
    void tryDispatchNextForAgent_noMembership_returnsNull()
    {
        when(memberMapper.selectMemberList(any(AiSkillGroupMember.class)))
            .thenReturn(Collections.emptyList());

        assertNull(service.tryDispatchNextForAgent(5L));
    }

    @Test
    void tryDispatchNextForAgent_noQueued_returnsNull()
    {
        AiSkillGroupMember membership = member(5L, 1L);
        when(memberMapper.selectMemberList(any(AiSkillGroupMember.class)))
            .thenReturn(Collections.singletonList(membership));
        when(queueMapper.selectQueuingByGroupId(1L)).thenReturn(Collections.emptyList());

        assertNull(service.tryDispatchNextForAgent(5L));
    }

    @Test
    void tryDispatchNextForAgent_agentBusy_returnsNull()
    {
        AiSkillGroupMember membership = member(5L, 1L);
        AiCallQueue queued = new AiCallQueue();
        queued.setEnqueueTime(new Date());
        when(memberMapper.selectMemberList(any(AiSkillGroupMember.class)))
            .thenReturn(Collections.singletonList(membership));
        when(queueMapper.selectQueuingByGroupId(1L)).thenReturn(Collections.singletonList(queued));
        when(agentStatusMapper.selectAiCallAgentStatusByAgentId(5L))
            .thenReturn(agent(5L, "小王", "1", "1"));

        assertNull(service.tryDispatchNextForAgent(5L));
    }

    @Test
    void tryDispatchNextForAgent_success_assignsOldest()
    {
        AiSkillGroupMember membership = member(5L, 1L);
        AiCallQueue queued = new AiCallQueue();
        queued.setQueueId(3L);
        queued.setSessionId("s2");
        queued.setRecordId(4L);
        queued.setCallerNumber("139");
        queued.setGroupId(1L);
        queued.setEnqueueTime(new Date());
        when(memberMapper.selectMemberList(any(AiSkillGroupMember.class)))
            .thenReturn(Collections.singletonList(membership));
        when(queueMapper.selectQueuingByGroupId(1L)).thenReturn(Collections.singletonList(queued));
        when(agentStatusMapper.selectAiCallAgentStatusByAgentId(5L))
            .thenReturn(agent(5L, "小王", "1", "0"));
        when(skillGroupMapper.selectAiSkillGroupByGroupId(1L)).thenReturn(group(1L, "all_ring", "1", null));
        when(agentStatusMapper.occupyAgentIfFree(5L)).thenReturn(1);
        when(queueMapper.selectQueuingBySessionId("s2")).thenReturn(queued);

        DispatchResult result = service.tryDispatchNextForAgent(5L);

        assertTrue(result.isSuccess());
        assertEquals(5L, result.getAgentId());
    }

    @Test
    void dispatch_statusLogDispatcherMissing_stillAssigns()
    {
        when(skillGroupMapper.selectAiSkillGroupByGroupId(1L)).thenReturn(group(1L, "all_ring", "1", null));
        when(memberMapper.selectAvailableByGroupId(1L))
            .thenReturn(Collections.singletonList(member(5L, 1L)));
        when(agentStatusMapper.occupyAgentIfFree(5L)).thenReturn(1);
        when(agentStatusMapper.selectAiCallAgentStatusByAgentId(5L))
            .thenReturn(agent(5L, "小王", "1", "0"));

        assertTrue(service.dispatch(1L, context()).isSuccess());
    }

    @Test
    void dispatch_agentStatusMissing_usesFallbackName()
    {
        when(skillGroupMapper.selectAiSkillGroupByGroupId(1L)).thenReturn(group(1L, "all_ring", "1", null));
        when(memberMapper.selectAvailableByGroupId(1L))
            .thenReturn(Collections.singletonList(member(5L, 1L)));
        when(agentStatusMapper.occupyAgentIfFree(5L)).thenReturn(1);
        when(agentStatusMapper.selectAiCallAgentStatusByAgentId(5L)).thenReturn(null);

        assertEquals("坐席5", service.dispatch(1L, context()).getAgentName());
        verify(agentStatusMapper, never()).updateAiCallAgentStatus(any());
    }
}
