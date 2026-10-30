package ai.lawyers.system.service.impl.lawyers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.test.util.ReflectionTestUtils;
import ai.lawyers.common.core.domain.entity.SysUser;
import ai.lawyers.system.domain.lawyers.AiCallTicket;
import ai.lawyers.system.domain.lawyers.AiTicketFlowDefinition;
import ai.lawyers.system.domain.lawyers.AiTicketTransfer;
import ai.lawyers.system.mapper.SysUserMapper;
import ai.lawyers.system.mapper.lawyers.AiCallTicketMapper;
import ai.lawyers.system.mapper.lawyers.AiTicketTransferMapper;
import ai.lawyers.system.service.lawyers.TicketFlowService;
import ai.lawyers.system.service.lawyers.queue.MessageNotifyDispatcher;
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
    @Mock private AiTicketTransferMapper transferMapper;
    @Mock private TicketFlowService ticketFlowService;
    @Mock private TicketVectorService ticketVectorService;
    @Mock private MessageNotifyDispatcher messageNotifyDispatcher;
    @Mock private SysUserMapper sysUserMapper;

    @BeforeEach
    void setUp()
    {
        MockitoAnnotations.openMocks(this);
        service = new AiCallTicketServiceImpl();
        ReflectionTestUtils.setField(service, "aiCallTicketMapper", ticketMapper);
        ReflectionTestUtils.setField(service, "aiTicketTransferMapper", transferMapper);
        ReflectionTestUtils.setField(service, "ticketFlowService", ticketFlowService);
        ReflectionTestUtils.setField(service, "ticketVectorService", ticketVectorService);
        ReflectionTestUtils.setField(service, "messageNotifyDispatcher", messageNotifyDispatcher);
        ReflectionTestUtils.setField(service, "sysUserMapper", sysUserMapper);
        // 部分用例（再分派/工单不存在）不读角色，用 lenient 避免严格桩冗余报错
        lenient().when(ticketFlowService.currentRoleKeys()).thenReturn(Arrays.asList("agent"));
        lenient().when(transferMapper.selectAiTicketTransferList(any()))
                .thenReturn(Collections.emptyList());
    }

    private void givenTicket(String status)
    {
        givenTicket(status, null, null, null);
    }

    private void givenTicket(String status, String callerNumber, String title, String assignUserName)
    {
        AiCallTicket t = new AiCallTicket();
        t.setTicketId(TID);
        t.setStatus(status);
        t.setCallerNumber(callerNumber);
        t.setTitle(title);
        t.setAssignUserName(assignUserName);
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

    // ---------------- F10：工单进度推送（站内信） ----------------

    private SysUser matchedUser(Long userId)
    {
        SysUser user = new SysUser();
        user.setUserId(userId);
        user.setUserName("portal-user");
        return user;
    }

    @Test
    void accept_withMatchedCaller_notifiesProgress()
    {
        givenTicket("0", "13800000000", "法律咨询工单", "张律师");
        allow("0", "start");
        when(ticketMapper.updateTicketProcess(eq(TID), any(), any(), any())).thenReturn(1);
        when(sysUserMapper.checkPhoneUnique("13800000000")).thenReturn(matchedUser(1001L));

        assertEquals(1, service.updateTicketProcess(TID, "已受理", null, null));

        verify(messageNotifyDispatcher).notify(eq(1001L), eq("5"),
                eq("工单受理提醒：法律咨询工单"), contains("处理人：张律师"),
                eq("ticket"), eq(TID), eq("system"));
    }

    @Test
    void complete_withMatchedCaller_notifiesProgress()
    {
        givenTicket("1", "13800000000", "法律咨询工单", null);
        allow("1", "complete");
        when(ticketMapper.updateTicketStatus(TID, "2")).thenReturn(1);
        when(sysUserMapper.checkPhoneUnique("13800000000")).thenReturn(matchedUser(1001L));

        assertEquals(1, service.updateTicketStatus(TID, "2"));

        verify(messageNotifyDispatcher).notify(eq(1001L), eq("5"),
                eq("工单办结提醒：法律咨询工单"), contains("已办结"),
                eq("ticket"), eq(TID), eq("system"));
    }

    @Test
    void progressPush_noMatchedAccount_silent()
    {
        givenTicket("0", "13800000000", "法律咨询工单", null);
        allow("0", "start");
        when(ticketMapper.updateTicketProcess(eq(TID), any(), any(), any())).thenReturn(1);
        when(sysUserMapper.checkPhoneUnique("13800000000")).thenReturn(null);

        assertEquals(1, service.updateTicketProcess(TID, "已受理", null, null));

        verifyNoInteractions(messageNotifyDispatcher);
    }

    @Test
    void progressPush_dispatcherFailure_doesNotBreakFlow()
    {
        givenTicket("0", "13800000000", "法律咨询工单", null);
        allow("0", "start");
        when(ticketMapper.updateTicketProcess(eq(TID), any(), any(), any())).thenReturn(1);
        when(sysUserMapper.checkPhoneUnique("13800000000")).thenReturn(matchedUser(1001L));
        doThrow(new RuntimeException("mq down")).when(messageNotifyDispatcher)
                .notify(any(), any(), any(), any(), any(), any(), any());

        assertEquals(1, service.updateTicketProcess(TID, "已受理", null, null));
    }

    // ---------------- P1-6：外派纳入统一时间线 ----------------

    private static Date at(String s)
    {
        try
        {
            return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").parse(s);
        }
        catch (ParseException e)
        {
            throw new RuntimeException(e);
        }
    }

    private static boolean hasTitle(List<Map<String, Object>> nodes, String title)
    {
        return nodes.stream().anyMatch(n -> title.equals(n.get("title")));
    }

    @Test
    void timeline_outTransfer_initiationAndDoneCallback()
    {
        givenTicket("2");
        AiTicketTransfer tr = new AiTicketTransfer();
        tr.setDirection("OUT");
        tr.setOrgName("市法律援助中心");
        tr.setExternalStatus("DONE");
        tr.setExternalTicketNo("FW-88");
        tr.setTransferTime(at("2026-10-02 09:00:00"));
        tr.setCallbackTime(at("2026-10-02 15:00:00"));
        when(transferMapper.selectAiTicketTransferList(any())).thenReturn(Arrays.asList(tr));

        List<Map<String, Object>> nodes = service.selectTicketTimeline(TID);

        assertTrue(hasTitle(nodes, "跨域转出"));
        assertTrue(hasTitle(nodes, "转出办结"));
        // 时间升序：转出发起早于办结回调
        int startIdx = 0;
        int doneIdx = 0;
        for (int i = 0; i < nodes.size(); i++)
        {
            if ("跨域转出".equals(nodes.get(i).get("title"))) { startIdx = i; }
            if ("转出办结".equals(nodes.get(i).get("title"))) { doneIdx = i; }
        }
        assertTrue(startIdx < doneIdx);
    }

    @Test
    void timeline_inTransfer_rejectedCallback_showsAbnormal()
    {
        givenTicket("1");
        AiTicketTransfer tr = new AiTicketTransfer();
        tr.setDirection("IN");
        tr.setExternalType("12345");
        tr.setExternalStatus("REJECTED");
        tr.setFailReason("材料不全");
        tr.setTransferTime(at("2026-10-02 09:00:00"));
        tr.setCallbackTime(at("2026-10-02 10:00:00"));
        when(transferMapper.selectAiTicketTransferList(any())).thenReturn(Arrays.asList(tr));

        List<Map<String, Object>> nodes = service.selectTicketTimeline(TID);

        assertTrue(hasTitle(nodes, "外部转入"));
        assertTrue(hasTitle(nodes, "转入异常"));
        Map<String, Object> abnormal = nodes.stream()
                .filter(n -> "转入异常".equals(n.get("title"))).findFirst().orElseThrow();
        assertTrue(String.valueOf(abnormal.get("description")).contains("材料不全"));
    }

    @Test
    void timeline_noTransfer_keepsBaseNodes()
    {
        givenTicket("0");
        List<Map<String, Object>> nodes = service.selectTicketTimeline(TID);
        assertTrue(hasTitle(nodes, "工单创建"));
        assertTrue(!hasTitle(nodes, "跨域转出"));
    }
}
