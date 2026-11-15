package ai.lawyers.system.service.impl.lawyers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.test.util.ReflectionTestUtils;
import ai.lawyers.common.exception.ServiceException;
import ai.lawyers.system.domain.lawyers.AiCallTicket;
import ai.lawyers.system.domain.lawyers.AiExternalOrg;
import ai.lawyers.system.domain.lawyers.AiTicketTransfer;
import ai.lawyers.system.domain.lawyers.AiUnifiedSession;
import ai.lawyers.system.mapper.lawyers.AiCallTicketMapper;
import ai.lawyers.system.mapper.lawyers.AiExternalOrgMapper;
import ai.lawyers.system.mapper.lawyers.AiTicketTransferMapper;
import ai.lawyers.system.mapper.lawyers.AiUnifiedSessionMapper;
import ai.lawyers.system.service.lawyers.IAiCallTicketService;
import ai.lawyers.system.service.lawyers.TicketFlowService;

/**
 * 工单跨域转办 Service 单测。
 *
 * <p>覆盖转出校验链/幂等/线下转人工/API 推送失败退避、回调定位与状态映射/终态幂等、
 * 外部来单去重建单与会话索引、重试队列分流与异常隔离。</p>
 *
 * @author ai-lawyers
 */
class AiTicketTransferServiceImplTest
{
    private static final String PHONE = "13800138000";

    private AiTicketTransferServiceImpl service;

    @Mock private AiTicketTransferMapper transferMapper;
    @Mock private AiCallTicketMapper ticketMapper;
    @Mock private AiExternalOrgMapper orgMapper;
    @Mock private AiUnifiedSessionMapper sessionMapper;
    @Mock private IAiCallTicketService ticketService;
    @Mock private TicketFlowService ticketFlowService;

    @BeforeEach
    void setUp()
    {
        MockitoAnnotations.openMocks(this);
        service = new AiTicketTransferServiceImpl();
        ReflectionTestUtils.setField(service, "transferMapper", transferMapper);
        ReflectionTestUtils.setField(service, "ticketMapper", ticketMapper);
        ReflectionTestUtils.setField(service, "orgMapper", orgMapper);
        ReflectionTestUtils.setField(service, "sessionMapper", sessionMapper);
        ReflectionTestUtils.setField(service, "ticketService", ticketService);
        ReflectionTestUtils.setField(service, "ticketFlowService", ticketFlowService);
        ReflectionTestUtils.setField(service, "callbackBaseUrl", "https://law.example.com");
    }

    // ---------------- 转出 ----------------

    @Test
    void transferOut_nullArgs_throws()
    {
        assertThrows(ServiceException.class, () -> service.transferOut(null, 1L, null, "admin"));
        assertThrows(ServiceException.class, () -> service.transferOut(1L, null, null, "admin"));
    }

    @Test
    void transferOut_ticketNotFound_throws()
    {
        when(ticketMapper.selectAiCallTicketByTicketId(1L)).thenReturn(null);

        assertThrows(ServiceException.class, () -> service.transferOut(1L, 2L, null, "admin"));
    }

    @Test
    void transferOut_flowValidationFailure_propagates()
    {
        when(ticketMapper.selectAiCallTicketByTicketId(1L)).thenReturn(ticket(1L));
        when(ticketFlowService.currentRoleKeys()).thenReturn(Collections.emptyList());
        when(ticketFlowService.validateAction(anyString(), any(), anyString(), any()))
                .thenThrow(new ServiceException("当前状态不允许转办"));

        assertThrows(ServiceException.class, () -> service.transferOut(1L, 2L, null, "admin"));
        verifyNoInteractions(orgMapper);
    }

    @Test
    void transferOut_orgMissingOrDisabled_throws()
    {
        when(ticketMapper.selectAiCallTicketByTicketId(1L)).thenReturn(ticket(1L));
        givenFlowAllowed();

        when(orgMapper.selectAiExternalOrgByOrgId(2L)).thenReturn(null);
        assertThrows(ServiceException.class, () -> service.transferOut(1L, 2L, null, "admin"));

        AiExternalOrg disabled = org("API", "1");
        when(orgMapper.selectAiExternalOrgByOrgId(2L)).thenReturn(disabled);
        assertThrows(ServiceException.class, () -> service.transferOut(1L, 2L, null, "admin"));

        verify(transferMapper, never()).insertAiTicketTransfer(any());
    }

    @Test
    void transferOut_idempotentHit_returnsExisting()
    {
        when(ticketMapper.selectAiCallTicketByTicketId(1L)).thenReturn(ticket(1L));
        givenFlowAllowed();
        when(orgMapper.selectAiExternalOrgByOrgId(2L)).thenReturn(org("MANUAL", "0"));
        AiTicketTransfer existing = new AiTicketTransfer();
        existing.setTransferId(9L);
        when(transferMapper.selectByIdempotentKey("OUT:1:2")).thenReturn(existing);

        assertSame(existing, service.transferOut(1L, 2L, null, "admin"));
        verify(transferMapper, never()).insertAiTicketTransfer(any());
    }

    @Test
    void transferOut_manualMode_marksManualAndBuildsPayload()
    {
        when(ticketMapper.selectAiCallTicketByTicketId(1L)).thenReturn(ticket(1L));
        givenFlowAllowed();
        when(orgMapper.selectAiExternalOrgByOrgId(2L)).thenReturn(org("MANUAL", "0"));

        AiTicketTransfer transfer = service.transferOut(1L, 2L, "  ", "admin");

        assertEquals("3", transfer.getTransferStatus());
        assertEquals("OUT", transfer.getDirection());
        assertEquals("PENDING", transfer.getExternalStatus());
        assertEquals("OUT:1:2", transfer.getIdempotentKey());
        assertEquals(0, transfer.getRetryCount());
        assertNull(transfer.getRemark());
        assertNotNull(transfer.getRequestPayload());
        assertTrue(transfer.getRequestPayload().contains("138****8000"));
        assertTrue(transfer.getRequestPayload().contains("callbackUrl"));
        assertTrue(!transfer.getRequestPayload().contains(PHONE));
        verify(ticketMapper).updateExternalInfo(eq(1L), eq("LEGAL_AID"), eq(2L), eq(null),
                eq("PENDING"), any(), any(), eq("OUT"));
        verify(transferMapper).insertAiTicketTransfer(transfer);
    }

    @Test
    void transferOut_apiMode_pushFailure_schedulesRetry()
    {
        when(ticketMapper.selectAiCallTicketByTicketId(1L)).thenReturn(ticket(1L));
        givenFlowAllowed();
        AiExternalOrg org = org("API", "0");
        org.setApiUrl("http://127.0.0.1:1/ticket");
        when(orgMapper.selectAiExternalOrgByOrgId(2L)).thenReturn(org);
        doAnswer(invocation -> {
            ((AiTicketTransfer) invocation.getArgument(0)).setTransferId(300L);
            return 1;
        }).when(transferMapper).insertAiTicketTransfer(any());

        AiTicketTransfer transfer = service.transferOut(1L, 2L, "备注", "admin");

        assertEquals("备注", transfer.getRemark());
        verify(transferMapper).updatePushResult(eq(300L), eq("2"),
                anyString(), eq(1), any());
    }

    // ---------------- 回调 ----------------

    @Test
    void handleCallback_emptyStatus_throws()
    {
        assertThrows(ServiceException.class, () -> service.handleCallback("k", "T", "E", "", "{}"));
    }

    @Test
    void handleCallback_notLocated_returnsNull()
    {
        when(transferMapper.selectByIdempotentKey("k")).thenReturn(null);
        when(transferMapper.selectByExternalTicketNo("T", "E")).thenReturn(null);

        assertNull(service.handleCallback("k", "T", "E", "DONE", "{}"));
        verify(transferMapper, never()).updateCallbackResult(any(), any(), any(), any(), any(), any());
    }

    @Test
    void handleCallback_terminalSuccess_isIdempotent()
    {
        AiTicketTransfer transfer = new AiTicketTransfer();
        transfer.setTransferId(3L);
        transfer.setTransferStatus("1");
        transfer.setExternalStatus("DONE");
        when(transferMapper.selectByIdempotentKey("k")).thenReturn(transfer);

        assertSame(transfer, service.handleCallback("k", null, null, "DONE", "{}"));
        verify(transferMapper, never()).updateCallbackResult(any(), any(), any(), any(), any(), any());
    }

    @Test
    void handleCallback_done_truncatesPayloadAndUpdatesBothTables()
    {
        AiTicketTransfer transfer = new AiTicketTransfer();
        transfer.setTransferId(3L);
        transfer.setTicketId(1L);
        transfer.setTransferStatus("0");
        when(transferMapper.selectByIdempotentKey("k")).thenReturn(transfer);
        StringBuilder payload = new StringBuilder();
        for (int i = 0; i < 2500; i++)
        {
            payload.append("p");
        }

        AiTicketTransfer result = service.handleCallback("k", "T", "EXT-1", "DONE", payload.toString());

        assertSame(transfer, result);
        assertEquals("DONE", result.getExternalStatus());
        assertEquals("1", result.getTransferStatus());
        verify(transferMapper).updateCallbackResult(eq(3L), eq("EXT-1"), eq("DONE"), eq("1"),
                anyString(), any());
        verify(ticketMapper).updateExternalCallback(eq(1L), eq("EXT-1"), eq("DONE"), any());
        ArgumentCaptor<String> payloadCaptor = ArgumentCaptor.forClass(String.class);
        verify(transferMapper).updateCallbackResult(anyLong(), anyString(), anyString(),
                anyString(), payloadCaptor.capture(), any());
        assertEquals(2000, payloadCaptor.getValue().length());
    }

    @Test
    void handleCallback_fallbackByExternalTicketNo_mapsRejectedToFailed()
    {
        AiTicketTransfer transfer = new AiTicketTransfer();
        transfer.setTransferId(3L);
        transfer.setTicketId(1L);
        when(transferMapper.selectByIdempotentKey("k")).thenReturn(null);
        when(transferMapper.selectByExternalTicketNo("T", "E")).thenReturn(transfer);

        AiTicketTransfer result = service.handleCallback("k", "T", "E", "REJECTED", "{}");

        assertEquals("2", result.getTransferStatus());
        verify(transferMapper).updateCallbackResult(eq(3L), eq("E"), eq("REJECTED"), eq("2"),
                anyString(), any());
    }

    // ---------------- 外部来单 ----------------

    @Test
    void receiveInbound_missingKeys_throws()
    {
        assertThrows(ServiceException.class,
                () -> service.receiveInbound("", "E", "k", null, null, null, null, null, null));
        assertThrows(ServiceException.class,
                () -> service.receiveInbound("T", null, "k", null, null, null, null, null, null));
    }

    @Test
    void receiveInbound_idempotentOrDuplicate_returnsExisting()
    {
        AiTicketTransfer existing = new AiTicketTransfer();
        existing.setTransferId(8L);
        when(transferMapper.selectByIdempotentKey("k")).thenReturn(existing);

        assertSame(existing, service.receiveInbound("T", "E", "k", null, null, null, null, null, null));

        AiTicketTransfer dup = new AiTicketTransfer();
        dup.setTransferId(9L);
        when(transferMapper.selectByIdempotentKey("IN:T:E")).thenReturn(null);
        when(transferMapper.selectByExternalTicketNo("T", "E")).thenReturn(dup);

        assertSame(dup, service.receiveInbound("T", "E", null, null, null, null, null, null, null));
        verifyNoInteractions(ticketService);
    }

    @Test
    void receiveInbound_createsTicketAndPhoneSession()
    {
        when(transferMapper.selectByIdempotentKey("k")).thenReturn(null);
        when(transferMapper.selectByExternalTicketNo("HOTLINE_12345", "E1")).thenReturn(null);
        when(ticketService.generateTicketNo()).thenReturn("T20260101");
        givenTicketInsertAssignsId(500L);
        StringBuilder content = new StringBuilder();
        for (int i = 0; i < 600; i++)
        {
            content.append("内");
        }

        AiTicketTransfer transfer = service.receiveInbound("HOTLINE_12345", "E1", "k",
                PHONE, "张三", null, content.toString(), null, "payload");

        assertEquals(500L, transfer.getTicketId());
        assertEquals("IN", transfer.getDirection());
        assertEquals("1", transfer.getTransferStatus());
        assertEquals("ACCEPTED", transfer.getExternalStatus());
        assertEquals("external:HOTLINE_12345", transfer.getCreateBy());
        verify(transferMapper).insertAiTicketTransfer(transfer);

        ArgumentCaptor<AiCallTicket> ticketCaptor = ArgumentCaptor.forClass(AiCallTicket.class);
        verify(ticketService).insertAiCallTicket(ticketCaptor.capture());
        AiCallTicket ticket = ticketCaptor.getValue();
        assertEquals("HOTLINE_12345转入诉求", ticket.getTitle());
        assertEquals(500, ticket.getContent().length());
        assertEquals("2", ticket.getPriority());
        assertEquals("0", ticket.getStatus());
        assertEquals("IN", ticket.getDirection());
        assertNotNull(ticket.getTicketNo());

        ArgumentCaptor<AiUnifiedSession> sessionCaptor = ArgumentCaptor.forClass(AiUnifiedSession.class);
        verify(sessionMapper).insertIgnoreAiUnifiedSession(sessionCaptor.capture());
        AiUnifiedSession session = sessionCaptor.getValue();
        assertEquals(PHONE, session.getCallerNumber());
        assertEquals("PHONE", session.getChannelType());
        assertEquals("TICKET", session.getBizType());
        assertEquals("500", session.getBizId());
    }

    @Test
    void receiveInbound_otherChannelAndNoPhone_skipsSession()
    {
        when(transferMapper.selectByIdempotentKey("IN:LEGAL_AID:E2")).thenReturn(null);
        when(transferMapper.selectByExternalTicketNo("LEGAL_AID", "E2")).thenReturn(null);
        when(ticketService.generateTicketNo()).thenReturn("T2");
        givenTicketInsertAssignsId(501L);

        service.receiveInbound("LEGAL_AID", "E2", null, null, null, "标题", "内容", "1", null);

        verifyNoInteractions(sessionMapper);
        ArgumentCaptor<AiCallTicket> captor = ArgumentCaptor.forClass(AiCallTicket.class);
        verify(ticketService).insertAiCallTicket(captor.capture());
        assertEquals("标题", captor.getValue().getTitle());
        assertEquals("1", captor.getValue().getPriority());
        assertEquals("ACCEPTED", captor.getValue().getExternalStatus());
    }

    // ---------------- 重试 ----------------

    @Test
    void retryTransfer_validations()
    {
        when(transferMapper.selectAiTicketTransferByTransferId(1L)).thenReturn(null);
        assertThrows(ServiceException.class, () -> service.retryTransfer(1L));

        AiTicketTransfer inbound = new AiTicketTransfer();
        inbound.setDirection("IN");
        when(transferMapper.selectAiTicketTransferByTransferId(2L)).thenReturn(inbound);
        assertThrows(ServiceException.class, () -> service.retryTransfer(2L));

        AiTicketTransfer outbound = new AiTicketTransfer();
        outbound.setDirection("OUT");
        outbound.setOrgId(9L);
        when(transferMapper.selectAiTicketTransferByTransferId(3L)).thenReturn(outbound);
        when(orgMapper.selectAiExternalOrgByOrgId(9L)).thenReturn(org("MANUAL", "0"));
        assertThrows(ServiceException.class, () -> service.retryTransfer(3L));
    }

    @Test
    void processRetryQueue_empty_returnsZero()
    {
        when(transferMapper.selectRetryPending(any(), anyInt())).thenReturn(Collections.emptyList());

        assertEquals(0, service.processRetryQueue());
    }

    @Test
    void processRetryQueue_nullOrg_marksManual()
    {
        AiTicketTransfer transfer = new AiTicketTransfer();
        transfer.setTransferId(1L);
        transfer.setOrgId(9L);
        when(transferMapper.selectRetryPending(any(), anyInt()))
                .thenReturn(Collections.singletonList(transfer));

        assertEquals(0, service.processRetryQueue());
        verify(transferMapper).updatePushResult(eq(1L), eq("3"),
                eq("协同机构不存在或已删除"), eq(0), eq(null));
    }

    @Test
    void processRetryQueue_nonApi_marksManual()
    {
        AiTicketTransfer transfer = new AiTicketTransfer();
        transfer.setTransferId(2L);
        transfer.setOrgId(9L);
        transfer.setRetryCount(2);
        when(transferMapper.selectRetryPending(any(), anyInt()))
                .thenReturn(Collections.singletonList(transfer));
        when(orgMapper.selectAiExternalOrgByOrgId(9L)).thenReturn(org("FILE", "0"));

        assertEquals(0, service.processRetryQueue());
        verify(transferMapper).updatePushResult(eq(2L), eq("3"),
                eq("机构对接方式已变更为非API"), eq(2), eq(null));
    }

    @Test
    void processRetryQueue_apiPushFailure_countsAndSchedulesRetry()
    {
        AiTicketTransfer transfer = new AiTicketTransfer();
        transfer.setTransferId(3L);
        transfer.setOrgId(9L);
        transfer.setRetryCount(1);
        transfer.setIdempotentKey("OUT:1:9");
        transfer.setRequestPayload("{\"ticketNo\":\"T1\"}");
        AiExternalOrg org = org("API", "0");
        org.setApiUrl("http://127.0.0.1:1/ticket");
        when(transferMapper.selectRetryPending(any(), anyInt()))
                .thenReturn(Collections.singletonList(transfer));
        when(orgMapper.selectAiExternalOrgByOrgId(9L)).thenReturn(org);

        assertEquals(1, service.processRetryQueue());
        verify(transferMapper).updatePushResult(eq(3L), eq("2"), anyString(), eq(2), any());
    }

    @Test
    void processRetryQueue_orgLookupException_isIsolated()
    {
        AiTicketTransfer transfer = new AiTicketTransfer();
        transfer.setTransferId(4L);
        transfer.setOrgId(10L);
        when(transferMapper.selectRetryPending(any(), anyInt()))
                .thenReturn(Collections.singletonList(transfer));
        when(orgMapper.selectAiExternalOrgByOrgId(10L)).thenThrow(new RuntimeException("db down"));

        assertEquals(0, service.processRetryQueue());
        verify(transferMapper, never()).updatePushResult(any(), any(), any(), any(), any());
    }

    @Test
    void selectQueries_delegate()
    {
        AiTicketTransfer transfer = new AiTicketTransfer();
        List<AiTicketTransfer> list = Arrays.asList(transfer);
        when(transferMapper.selectAiTicketTransferList(any())).thenReturn(list);
        when(transferMapper.selectAiTicketTransferByTransferId(1L)).thenReturn(transfer);

        assertEquals(list, service.selectTransferList(new AiTicketTransfer()));
        assertSame(transfer, service.selectTransferById(1L));
    }

    // ---------------- 辅助 ----------------

    private void givenFlowAllowed()
    {
        when(ticketFlowService.currentRoleKeys()).thenReturn(Collections.emptyList());
        when(ticketFlowService.validateAction(anyString(), any(), anyString(), any()))
                .thenReturn(new ai.lawyers.system.domain.lawyers.AiTicketFlowDefinition());
    }

    private void givenTicketInsertAssignsId(Long ticketId)
    {
        doAnswer(invocation -> {
            ((AiCallTicket) invocation.getArgument(0)).setTicketId(ticketId);
            return 1;
        }).when(ticketService).insertAiCallTicket(any());
    }

    private AiCallTicket ticket(Long ticketId)
    {
        AiCallTicket ticket = new AiCallTicket();
        ticket.setTicketId(ticketId);
        ticket.setTicketNo("T100");
        ticket.setTitle("劳动争议");
        ticket.setContent("拖欠工资");
        ticket.setStatus("1");
        ticket.setCallerNumber(PHONE);
        return ticket;
    }

    private AiExternalOrg org(String accessMode, String status)
    {
        AiExternalOrg org = new AiExternalOrg();
        org.setOrgId(2L);
        org.setOrgName("市法律援助中心");
        org.setExternalType("LEGAL_AID");
        org.setAccessMode(accessMode);
        org.setStatus(status);
        org.setApiUrl("http://127.0.0.1:1/ticket");
        org.setAppId("app-1");
        org.setAppSecret("secret-1");
        return org;
    }
}
