package ai.lawyers.system.task;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import ai.lawyers.common.core.redis.RedisCache;
import ai.lawyers.system.domain.lawyers.AiCallTicket;
import ai.lawyers.system.domain.lawyers.AiSlaPolicy;
import ai.lawyers.system.mapper.SysUserMapper;
import ai.lawyers.system.mapper.lawyers.AiCallTicketMapper;
import ai.lawyers.system.service.lawyers.CallEventPublisher;
import ai.lawyers.system.service.lawyers.IAiSlaPolicyService;
import ai.lawyers.system.service.lawyers.cluster.RedisLeaderLock;

/**
 * F9 SLA 逐级升级任务测试：级别计算与封顶、Redis 去重标记、到人/广播回退与异常隔离。
 */
class SlaEscalationScheduleTaskTest
{
    private SlaEscalationScheduleTask task;
    private AiCallTicketMapper ticketMapper;
    private SysUserMapper sysUserMapper;
    private IAiSlaPolicyService slaPolicyService;
    private RedisLeaderLock leaderLock;
    private CallEventPublisher eventPublisher;
    private RedisCache redisCache;

    @BeforeEach
    void setUp()
    {
        task = new SlaEscalationScheduleTask();
        ticketMapper = mock(AiCallTicketMapper.class);
        sysUserMapper = mock(SysUserMapper.class);
        slaPolicyService = mock(IAiSlaPolicyService.class);
        leaderLock = mock(RedisLeaderLock.class);
        eventPublisher = mock(CallEventPublisher.class);
        redisCache = mock(RedisCache.class);
        ReflectionTestUtils.setField(task, "ticketMapper", ticketMapper);
        ReflectionTestUtils.setField(task, "sysUserMapper", sysUserMapper);
        ReflectionTestUtils.setField(task, "slaPolicyService", slaPolicyService);
        ReflectionTestUtils.setField(task, "leaderLock", leaderLock);
        ReflectionTestUtils.setField(task, "callEventPublisher", eventPublisher);
        ReflectionTestUtils.setField(task, "redisCache", redisCache);
    }

    private static AiCallTicket ticket(Long id, long overdueMinutes)
    {
        AiCallTicket t = new AiCallTicket();
        t.setTicketId(id);
        t.setTicketNo("T" + id);
        t.setTitle("工单" + id);
        t.setPriority("2");
        t.setCallerNumber("13900000000");
        t.setDueTime(new Date(System.currentTimeMillis() - overdueMinutes * 60_000L));
        return t;
    }

    private static AiSlaPolicy policy(String roles, Integer resolveMinutes)
    {
        AiSlaPolicy p = new AiSlaPolicy();
        p.setEscalateRoles(roles);
        p.setResolveMinutes(resolveMinutes);
        return p;
    }

    @Test
    void scan_runsUnderClusterLock()
    {
        task.scanEscalations();

        verify(leaderLock).tryRun(eq("job:sla-escalation"), any(Duration.class), any(Runnable.class));
        verifyNoInteractions(ticketMapper);
    }

    @Test
    void scan_emptyTickets_noop()
    {
        runInsideLock();
        when(ticketMapper.selectEscalatableTickets()).thenReturn(Collections.emptyList());

        task.scanEscalations();

        verifyNoInteractions(slaPolicyService, eventPublisher, redisCache);
    }

    @Test
    void scan_queryThrows_swallowed()
    {
        runInsideLock();
        when(ticketMapper.selectEscalatableTickets()).thenThrow(new IllegalStateException("db down"));

        task.scanEscalations();

        verifyNoInteractions(slaPolicyService, eventPublisher);
    }

    @Test
    void scan_policyMissing_skips()
    {
        runInsideLock();
        when(ticketMapper.selectEscalatableTickets()).thenReturn(Arrays.asList(ticket(1L, 30)));
        when(slaPolicyService.matchPolicy("TICKET", "2")).thenReturn(null);

        task.scanEscalations();

        verifyNoInteractions(eventPublisher, redisCache);
    }

    @Test
    void scan_rolesEmpty_skips()
    {
        runInsideLock();
        when(ticketMapper.selectEscalatableTickets()).thenReturn(Arrays.asList(ticket(1L, 30)));
        when(slaPolicyService.matchPolicy("TICKET", "2")).thenReturn(policy(" ", 10));

        task.scanEscalations();

        verifyNoInteractions(eventPublisher, redisCache);
    }

    @Test
    void scan_resolveMinutesInvalid_skips()
    {
        runInsideLock();
        when(ticketMapper.selectEscalatableTickets()).thenReturn(Arrays.asList(ticket(1L, 30)));
        when(slaPolicyService.matchPolicy("TICKET", "2")).thenReturn(policy("supervisor", null));

        task.scanEscalations();

        verifyNoInteractions(eventPublisher, redisCache);
    }

    @Test
    void scan_externalType_usedForPolicyMatch()
    {
        runInsideLock();
        AiCallTicket t = ticket(1L, 30);
        t.setExternalType("MEDIATION");
        when(ticketMapper.selectEscalatableTickets()).thenReturn(Arrays.asList(t));
        when(slaPolicyService.matchPolicy("MEDIATION", "2")).thenReturn(policy("supervisor", 10));
        when(redisCache.hasKey(anyString())).thenReturn(false);
        when(sysUserMapper.selectUserIdsByRoleKey("supervisor")).thenReturn(Arrays.asList(7L));

        task.scanEscalations();

        verify(slaPolicyService).matchPolicy("MEDIATION", "2");
    }

    @Test
    void scan_firstPeriod_escalatesToFirstRole()
    {
        runInsideLock();
        when(ticketMapper.selectEscalatableTickets()).thenReturn(Arrays.asList(ticket(1L, 5)));
        when(slaPolicyService.matchPolicy("TICKET", "2")).thenReturn(policy("supervisor,manager", 10));
        when(redisCache.hasKey("sla:escalated:1:1")).thenReturn(false);
        when(sysUserMapper.selectUserIdsByRoleKey("supervisor")).thenReturn(Arrays.asList(7L, 8L));

        task.scanEscalations();

        verify(eventPublisher).publishToUser(eq(7L), eq("TICKET_ESCALATE"), argThat(data ->
                Integer.valueOf(1).equals(map(data).get("level"))
                        && "supervisor".equals(map(data).get("escalateRole"))));
        verify(eventPublisher).publishToUser(eq(8L), eq("TICKET_ESCALATE"), any());
        verify(redisCache).setCacheObject(eq("sla:escalated:1:1"), eq("supervisor"),
                eq(Integer.valueOf(7)), eq(TimeUnit.DAYS));
    }

    @Test
    void scan_secondPeriod_escalatesToSecondRole()
    {
        runInsideLock();
        when(ticketMapper.selectEscalatableTickets()).thenReturn(Arrays.asList(ticket(2L, 15)));
        when(slaPolicyService.matchPolicy("TICKET", "2")).thenReturn(policy("supervisor,manager", 10));
        when(redisCache.hasKey("sla:escalated:2:2")).thenReturn(false);
        when(sysUserMapper.selectUserIdsByRoleKey("manager")).thenReturn(Arrays.asList(9L));

        task.scanEscalations();

        verify(eventPublisher).publishToUser(eq(9L), eq("TICKET_ESCALATE"), argThat(data ->
                Integer.valueOf(2).equals(map(data).get("level"))
                        && "manager".equals(map(data).get("escalateRole"))));
    }

    @Test
    void scan_overdueBeyondRoles_clampedToLastLevel()
    {
        runInsideLock();
        when(ticketMapper.selectEscalatableTickets()).thenReturn(Arrays.asList(ticket(3L, 10_000)));
        when(slaPolicyService.matchPolicy("TICKET", "2")).thenReturn(policy("supervisor,manager", 10));
        when(redisCache.hasKey("sla:escalated:3:2")).thenReturn(false);
        when(sysUserMapper.selectUserIdsByRoleKey("manager")).thenReturn(Arrays.asList(9L));

        task.scanEscalations();

        verify(eventPublisher).publishToUser(eq(9L), eq("TICKET_ESCALATE"), argThat(data ->
                Integer.valueOf(2).equals(map(data).get("level"))));
        verify(redisCache).setCacheObject(eq("sla:escalated:3:2"), eq("manager"),
                eq(Integer.valueOf(7)), eq(TimeUnit.DAYS));
    }

    @Test
    void scan_markerExists_skipsDuplicate()
    {
        runInsideLock();
        when(ticketMapper.selectEscalatableTickets()).thenReturn(Arrays.asList(ticket(4L, 5)));
        when(slaPolicyService.matchPolicy("TICKET", "2")).thenReturn(policy("supervisor", 10));
        when(redisCache.hasKey("sla:escalated:4:1")).thenReturn(true);

        task.scanEscalations();

        verifyNoInteractions(eventPublisher);
        verify(redisCache, never()).setCacheObject(anyString(), any(), anyInt(), any());
    }

    @Test
    void scan_noRoleUsers_broadcastsFallback()
    {
        runInsideLock();
        when(ticketMapper.selectEscalatableTickets()).thenReturn(Arrays.asList(ticket(5L, 5)));
        when(slaPolicyService.matchPolicy("TICKET", "2")).thenReturn(policy("supervisor", 10));
        when(redisCache.hasKey(anyString())).thenReturn(false);
        when(sysUserMapper.selectUserIdsByRoleKey("supervisor")).thenReturn(Collections.emptyList());

        task.scanEscalations();

        verify(eventPublisher).broadcast(eq("TICKET_ESCALATE"), any());
        verify(eventPublisher, never()).publishToUser(any(), any(), any());
    }

    @Test
    void scan_roleResolveThrows_broadcastsFallback()
    {
        runInsideLock();
        when(ticketMapper.selectEscalatableTickets()).thenReturn(Arrays.asList(ticket(6L, 5)));
        when(slaPolicyService.matchPolicy("TICKET", "2")).thenReturn(policy("supervisor", 10));
        when(redisCache.hasKey(anyString())).thenReturn(false);
        when(sysUserMapper.selectUserIdsByRoleKey("supervisor")).thenThrow(new IllegalStateException("db down"));

        task.scanEscalations();

        verify(eventPublisher).broadcast(eq("TICKET_ESCALATE"), any());
    }

    @Test
    void scan_blankTargetRole_skips()
    {
        runInsideLock();
        when(ticketMapper.selectEscalatableTickets()).thenReturn(Arrays.asList(ticket(7L, 5)));
        when(slaPolicyService.matchPolicy("TICKET", "2")).thenReturn(policy(" ,manager", 10));
        when(redisCache.hasKey(anyString())).thenReturn(false);

        task.scanEscalations();

        verifyNoInteractions(eventPublisher);
        verify(redisCache, never()).setCacheObject(anyString(), any(), anyInt(), any());
    }

    @Test
    void scan_publisherMissing_stillMarksDedup()
    {
        ReflectionTestUtils.setField(task, "callEventPublisher", null);
        runInsideLock();
        when(ticketMapper.selectEscalatableTickets()).thenReturn(Arrays.asList(ticket(8L, 5)));
        when(slaPolicyService.matchPolicy("TICKET", "2")).thenReturn(policy("supervisor", 10));
        when(redisCache.hasKey(anyString())).thenReturn(false);

        task.scanEscalations();

        verify(redisCache).setCacheObject(eq("sla:escalated:8:1"), eq("supervisor"),
                eq(Integer.valueOf(7)), eq(TimeUnit.DAYS));
    }

    @Test
    void scan_redisMissing_stillPublishes()
    {
        ReflectionTestUtils.setField(task, "redisCache", null);
        runInsideLock();
        when(ticketMapper.selectEscalatableTickets()).thenReturn(Arrays.asList(ticket(9L, 5)));
        when(slaPolicyService.matchPolicy("TICKET", "2")).thenReturn(policy("supervisor", 10));
        when(sysUserMapper.selectUserIdsByRoleKey("supervisor")).thenReturn(Arrays.asList(7L));

        task.scanEscalations();

        verify(eventPublisher).publishToUser(eq(7L), eq("TICKET_ESCALATE"), any());
    }

    @Test
    void scan_oneTicketThrows_continuesOthers()
    {
        runInsideLock();
        AiCallTicket broken = ticket(10L, 5);
        broken.setDueTime(null);
        AiCallTicket normal = ticket(11L, 5);
        when(ticketMapper.selectEscalatableTickets()).thenReturn(Arrays.asList(broken, normal));
        when(slaPolicyService.matchPolicy("TICKET", "2")).thenReturn(policy("supervisor", 10));
        when(redisCache.hasKey(anyString())).thenReturn(false);
        when(sysUserMapper.selectUserIdsByRoleKey("supervisor")).thenReturn(Arrays.asList(7L));

        task.scanEscalations();

        verify(eventPublisher).publishToUser(eq(7L), eq("TICKET_ESCALATE"), argThat(data ->
                "T11".equals(map(data).get("ticketNo"))));
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> map(Object data)
    {
        return (Map<String, Object>) data;
    }

    /** 模拟抢到锁：执行 tryRun 传入的任务体 */
    private void runInsideLock()
    {
        doAnswer(inv -> {
            inv.getArgument(2, Runnable.class).run();
            return true;
        }).when(leaderLock).tryRun(any(), any(), any());
    }
}
