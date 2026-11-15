package ai.lawyers.system.task;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
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
import org.springframework.test.util.ReflectionTestUtils;

import ai.lawyers.system.domain.lawyers.AiCallTicket;
import ai.lawyers.system.mapper.lawyers.AiCallTicketMapper;
import ai.lawyers.system.service.lawyers.CallEventPublisher;
import ai.lawyers.system.service.lawyers.cluster.RedisLeaderLock;

/**
 * 工单 SLA 超时提醒任务测试：打标幂等、到人/广播分流、单条失败不中断与锁边界。
 */
class TicketSlaScheduleTaskTest
{
    private TicketSlaScheduleTask task;
    private AiCallTicketMapper ticketMapper;
    private CallEventPublisher eventPublisher;
    private RedisLeaderLock leaderLock;

    @BeforeEach
    void setUp()
    {
        task = new TicketSlaScheduleTask();
        ticketMapper = mock(AiCallTicketMapper.class);
        eventPublisher = mock(CallEventPublisher.class);
        leaderLock = mock(RedisLeaderLock.class);
        ReflectionTestUtils.setField(task, "aiCallTicketMapper", ticketMapper);
        ReflectionTestUtils.setField(task, "callEventPublisher", eventPublisher);
        ReflectionTestUtils.setField(task, "leaderLock", leaderLock);
    }

    private static AiCallTicket ticket(Long id, Long assignUserId)
    {
        AiCallTicket t = new AiCallTicket();
        t.setTicketId(id);
        t.setTicketNo("T" + id);
        t.setTitle("咨询工单" + id);
        t.setPriority("1");
        t.setCallerNumber("13800000000");
        t.setAssignUserId(assignUserId);
        t.setAssignUserName(assignUserId == null ? null : "坐席" + assignUserId);
        t.setDueTime(new Date(System.currentTimeMillis() - 60_000L));
        return t;
    }

    @Test
    void scan_runsUnderClusterLock()
    {
        task.scanOvertimeTickets();

        verify(leaderLock).tryRun(eq("job:ticket-sla"), any(Duration.class), any(Runnable.class));
        verifyNoInteractions(ticketMapper);
    }

    @Test
    void scan_emptyList_noEvent()
    {
        runInsideLock();
        when(ticketMapper.selectOvertimePendingTickets()).thenReturn(Collections.emptyList());

        task.scanOvertimeTickets();

        verifyNoInteractions(eventPublisher);
    }

    @Test
    void scan_assignedTicket_marksAndPushesToUser()
    {
        runInsideLock();
        when(ticketMapper.selectOvertimePendingTickets()).thenReturn(Arrays.asList(ticket(1L, 9L)));
        when(ticketMapper.markOvertime(eq(1L), any(Date.class))).thenReturn(1);

        task.scanOvertimeTickets();

        verify(ticketMapper).markOvertime(eq(1L), any(Date.class));
        verify(eventPublisher).publishToUser(eq(9L), eq("TICKET_OVERTIME"), argThat(data ->
                ((java.util.Map<?, ?>) data).get("ticketNo").equals("T1")));
        verify(eventPublisher, never()).broadcast(any(), any());
    }

    @Test
    void scan_unassignedTicket_broadcasts()
    {
        runInsideLock();
        when(ticketMapper.selectOvertimePendingTickets()).thenReturn(Arrays.asList(ticket(2L, null)));
        when(ticketMapper.markOvertime(eq(2L), any(Date.class))).thenReturn(1);

        task.scanOvertimeTickets();

        verify(eventPublisher).broadcast(eq("TICKET_OVERTIME"), any());
        verify(eventPublisher, never()).publishToUser(any(), any(), any());
    }

    @Test
    void scan_markAlreadyDone_noEvent()
    {
        runInsideLock();
        when(ticketMapper.selectOvertimePendingTickets()).thenReturn(Arrays.asList(ticket(3L, 9L)));
        when(ticketMapper.markOvertime(eq(3L), any(Date.class))).thenReturn(0);

        task.scanOvertimeTickets();

        verifyNoInteractions(eventPublisher);
    }

    @Test
    void scan_oneTicketThrows_continuesOthers()
    {
        runInsideLock();
        when(ticketMapper.selectOvertimePendingTickets())
                .thenReturn(Arrays.asList(ticket(4L, null), ticket(5L, null)));
        when(ticketMapper.markOvertime(eq(4L), any(Date.class))).thenThrow(new IllegalStateException("lock timeout"));
        when(ticketMapper.markOvertime(eq(5L), any(Date.class))).thenReturn(1);

        task.scanOvertimeTickets();

        verify(eventPublisher).broadcast(eq("TICKET_OVERTIME"), any());
    }

    @Test
    void scan_queryThrows_swallowed()
    {
        runInsideLock();
        when(ticketMapper.selectOvertimePendingTickets()).thenThrow(new IllegalStateException("db down"));

        task.scanOvertimeTickets();

        verifyNoInteractions(eventPublisher);
    }

    @Test
    void scan_publisherMissing_marksWithoutError()
    {
        ReflectionTestUtils.setField(task, "callEventPublisher", null);
        runInsideLock();
        when(ticketMapper.selectOvertimePendingTickets()).thenReturn(Arrays.asList(ticket(6L, null)));
        when(ticketMapper.markOvertime(eq(6L), any(Date.class))).thenReturn(1);

        task.scanOvertimeTickets();

        verify(ticketMapper).markOvertime(eq(6L), any(Date.class));
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
