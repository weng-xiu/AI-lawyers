package ai.lawyers.system.service.impl.lawyers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.test.util.ReflectionTestUtils;
import ai.lawyers.system.domain.lawyers.AiCallTicket;
import ai.lawyers.system.domain.lawyers.AiTicketFlowDefinition;
import ai.lawyers.system.mapper.lawyers.AiCallTicketMapper;
import ai.lawyers.system.service.lawyers.TicketFlowService;
import ai.lawyers.system.service.lawyers.rag.TicketVectorService;

/**
 * P1-6：工单状态机接入单测——受理/办结/归档必须经 TicketFlowService 合法性+角色校验，
 * 校验失败一律拒绝且不落库；办结兼容未受理(0)工单自动补 start。
 *
 * @author ai-lawyers
 */
class AiCallTicketServiceImplTest
{
    private static final Long TID = 10L;

    private AiCallTicketServiceImpl service;

    @Mock private AiCallTicketMapper ticketMapper;
    @Mock private TicketFlowService ticketFlowService;
    @Mock private TicketVectorService ticketVectorService;

    @BeforeEach
    void setUp()
    {
        MockitoAnnotations.openMocks(this);
        service = new AiCallTicketServiceImpl();
        ReflectionTestUtils.setField(service, "aiCallTicketMapper", ticketMapper);
        ReflectionTestUtils.setField(service, "ticketFlowService", ticketFlowService);
        ReflectionTestUtils.setField(service, "ticketVectorService", ticketVectorService);
        // 部分用例（再分派/工单不存在）不读角色，用 lenient 避免严格桩冗余报错
        lenient().when(ticketFlowService.currentRoleKeys()).thenReturn(Arrays.asList("agent"));
    }

    private void givenTicket(String status)
    {
        AiCallTicket t = new AiCallTicket();
        t.setTicketId(TID);
        t.setStatus(status);
        when(ticketMapper.selectAiCallTicketByTicketId(TID)).thenReturn(t);
    }

    /** 桩：状态机对指定 (from,action) 放行并返回规则（validateAction 非 void） */
    private void allow(String from, String action)
    {
        when(ticketFlowService.validateAction(eq("HOTLINE"), eq(from), eq(action), anyList()))
                .thenReturn(new AiTicketFlowDefinition());
    }

    // ---------------- 受理 ----------------

    @Test
    void process_status0_validatesStartAndUpdates()
    {
        givenTicket("0");
        allow("0", "start");
        when(ticketMapper.updateTicketProcess(eq(TID), any(), any(), any())).thenReturn(1);

        assertEquals(1, service.updateTicketProcess(TID, "已受理", null, null));

        verify(ticketFlowService).validateAction(eq("HOTLINE"), eq("0"), eq("start"), anyList());
        verify(ticketMapper).updateTicketProcess(eq(TID), any(), any(), any());
    }

    @Test
    void process_status1_skipsStartValidation()
    {
        givenTicket("1");
        when(ticketMapper.updateTicketProcess(eq(TID), any(), any(), any())).thenReturn(1);

        service.updateTicketProcess(TID, "再分派", 5L, "班长");

        verify(ticketFlowService, never()).validateAction(any(), any(), any(), anyList());
    }

    @Test
    void process_status0_startForbidden_throwsAndNoUpdate()
    {
        givenTicket("0");
        doThrow(new IllegalArgumentException("无权受理")).when(ticketFlowService)
                .validateAction(eq("HOTLINE"), eq("0"), eq("start"), anyList());

        assertThrows(IllegalArgumentException.class,
                () -> service.updateTicketProcess(TID, "x", null, null));
        verify(ticketMapper, never()).updateTicketProcess(any(), any(), any(), any());
    }

    // ---------------- 办结 ----------------

    @Test
    void complete_status1_validatesCompleteAndIndexes()
    {
        givenTicket("1");
        allow("1", "complete");
        when(ticketMapper.updateTicketStatus(TID, "2")).thenReturn(1);

        assertEquals(1, service.updateTicketStatus(TID, "2"));

        verify(ticketFlowService).validateAction(eq("HOTLINE"), eq("1"), eq("complete"), anyList());
        verify(ticketVectorService).indexTicketAsync(TID);
    }

    @Test
    void complete_status0_autoStartsThenCompletes()
    {
        givenTicket("0");
        allow("0", "start");
        allow("1", "complete");
        when(ticketMapper.updateTicketStatus(TID, "1")).thenReturn(1);
        when(ticketMapper.updateTicketStatus(TID, "2")).thenReturn(1);

        service.updateTicketStatus(TID, "2");

        verify(ticketMapper).updateTicketStatus(TID, "1");
        verify(ticketMapper).updateTicketStatus(TID, "2");
    }

    @Test
    void complete_status0_startForbidden_throwsNoComplete()
    {
        givenTicket("0");
        doThrow(new IllegalArgumentException("无权")).when(ticketFlowService)
                .validateAction(eq("HOTLINE"), eq("0"), eq("start"), anyList());

        assertThrows(IllegalArgumentException.class,
                () -> service.updateTicketStatus(TID, "2"));
        verify(ticketMapper, never()).updateTicketStatus(TID, "2");
    }

    // ---------------- 归档 ----------------

    @Test
    void archive_status2_validatesArchive()
    {
        givenTicket("2");
        allow("2", "archive");
        when(ticketMapper.archiveTicket(TID)).thenReturn(1);

        assertEquals(1, service.archiveTicket(TID));
        verify(ticketFlowService).validateAction(eq("HOTLINE"), eq("2"), eq("archive"), anyList());
    }

    @Test
    void archive_status1_rejectedNoArchive()
    {
        givenTicket("1");
        doThrow(new IllegalArgumentException("未办结不可归档")).when(ticketFlowService)
                .validateAction(eq("HOTLINE"), eq("1"), eq("archive"), anyList());

        assertThrows(IllegalArgumentException.class, () -> service.archiveTicket(TID));
        verify(ticketMapper, never()).archiveTicket(TID);
    }

    // ---------------- 工单不存在 ----------------

    @Test
    void ticketMissing_throws()
    {
        when(ticketMapper.selectAiCallTicketByTicketId(TID)).thenReturn(null);
        assertThrows(IllegalArgumentException.class, () -> service.archiveTicket(TID));
    }
}
