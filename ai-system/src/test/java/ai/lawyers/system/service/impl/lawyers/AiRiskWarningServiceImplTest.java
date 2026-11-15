package ai.lawyers.system.service.impl.lawyers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.test.util.ReflectionTestUtils;
import ai.lawyers.common.exception.ServiceException;
import ai.lawyers.system.domain.lawyers.AiCallRecord;
import ai.lawyers.system.domain.lawyers.AiCallTicket;
import ai.lawyers.system.domain.lawyers.AiExternalOrg;
import ai.lawyers.system.domain.lawyers.AiRiskWarning;
import ai.lawyers.system.domain.lawyers.AiRiskWarningRule;
import ai.lawyers.system.domain.lawyers.AiTicketTransfer;
import ai.lawyers.system.mapper.lawyers.AiCallRecordMapper;
import ai.lawyers.system.mapper.lawyers.AiExternalOrgMapper;
import ai.lawyers.system.mapper.lawyers.AiRiskWarningMapper;
import ai.lawyers.system.mapper.lawyers.AiRiskWarningRuleMapper;
import ai.lawyers.system.service.lawyers.IAiCallTicketService;
import ai.lawyers.system.service.lawyers.IAiTicketTransferService;
import ai.lawyers.system.service.lawyers.queue.MessageNotifyDispatcher;

/**
 * F3 风险预警 Service 单测。
 *
 * <p>覆盖命中规则补全与快照、站内信广播（成功/失败隔离）、一键转办校验链
 * （幂等、条线/机构/状态）、自动建单字段（通话回填/截断）与转办回写。</p>
 *
 * @author ai-lawyers
 */
class AiRiskWarningServiceImplTest
{
    private static final String PHONE = "13800138000";

    private AiRiskWarningServiceImpl service;

    @Mock private AiRiskWarningMapper warningMapper;
    @Mock private AiRiskWarningRuleMapper ruleMapper;
    @Mock private AiExternalOrgMapper externalOrgMapper;
    @Mock private AiCallRecordMapper callRecordMapper;
    @Mock private IAiCallTicketService ticketService;
    @Mock private IAiTicketTransferService transferService;
    @Mock private MessageNotifyDispatcher messageNotifyDispatcher;

    @BeforeEach
    void setUp()
    {
        MockitoAnnotations.openMocks(this);
        service = new AiRiskWarningServiceImpl();
        ReflectionTestUtils.setField(service, "aiRiskWarningMapper", warningMapper);
        ReflectionTestUtils.setField(service, "aiRiskWarningRuleMapper", ruleMapper);
        ReflectionTestUtils.setField(service, "aiExternalOrgMapper", externalOrgMapper);
        ReflectionTestUtils.setField(service, "aiCallRecordMapper", callRecordMapper);
        ReflectionTestUtils.setField(service, "aiCallTicketService", ticketService);
        ReflectionTestUtils.setField(service, "aiTicketTransferService", transferService);
        ReflectionTestUtils.setField(service, "messageNotifyDispatcher", messageNotifyDispatcher);
    }

    // ---------------- 查询/透传 ----------------

    @Test
    void selectAndSimpleWrites_delegate()
    {
        AiRiskWarning warning = new AiRiskWarning();
        List<AiRiskWarning> list = Collections.singletonList(warning);
        when(warningMapper.selectAiRiskWarningByWarningId(1L)).thenReturn(warning);
        when(warningMapper.selectAiRiskWarningList(any())).thenReturn(list);
        when(warningMapper.updateAiRiskWarning(warning)).thenReturn(1);
        Long[] ids = {1L, 2L};
        when(warningMapper.deleteAiRiskWarningByWarningIds(ids)).thenReturn(2);
        List<AiExternalOrg> orgs = Collections.singletonList(new AiExternalOrg());
        when(externalOrgMapper.selectTransferOptions("LEGAL_AID")).thenReturn(orgs);

        assertSame(warning, service.selectAiRiskWarningByWarningId(1L));
        assertEquals(list, service.selectAiRiskWarningList(new AiRiskWarning()));
        assertEquals(1, service.updateAiRiskWarning(warning));
        assertEquals(2, service.deleteAiRiskWarningByWarningIds(ids));
        assertEquals(orgs, service.selectTransferOrgs("LEGAL_AID"));
    }

    // ---------------- 新增预警 ----------------

    @Test
    void insert_enabledRule_fillsTypeLevelAndSuggestion()
    {
        AiRiskWarning warning = new AiRiskWarning();
        warning.setRuleId(3L);
        when(ruleMapper.selectAiRiskWarningRuleByRuleId(3L)).thenReturn(rule("1"));
        when(warningMapper.insertAiRiskWarning(warning)).thenReturn(1);

        assertEquals(1, service.insertAiRiskWarning(warning));

        assertEquals("敏感词", warning.getWarningType());
        assertEquals("1", warning.getWarningLevel());
        assertEquals("LEGAL_AID", warning.getSuggestTransferType());
        assertEquals("0", warning.getStatus());
    }

    @Test
    void insert_disabledOrMissingRule_keepsFieldsAndLevelDefault()
    {
        AiRiskWarning disabled = new AiRiskWarning();
        disabled.setRuleId(4L);
        when(ruleMapper.selectAiRiskWarningRuleByRuleId(4L)).thenReturn(rule("0"));

        AiRiskWarning missing = new AiRiskWarning();
        missing.setRuleId(5L);
        when(ruleMapper.selectAiRiskWarningRuleByRuleId(5L)).thenReturn(null);
        when(warningMapper.insertAiRiskWarning(any())).thenReturn(1);

        service.insertAiRiskWarning(disabled);
        service.insertAiRiskWarning(missing);

        assertNull(disabled.getWarningType());
        assertEquals("3", disabled.getWarningLevel());
        assertNull(missing.getWarningType());
        assertEquals("3", missing.getWarningLevel());
    }

    @Test
    void insert_keepsExplicitFields()
    {
        AiRiskWarning warning = new AiRiskWarning();
        warning.setRuleId(3L);
        warning.setWarningType("人工标注");
        warning.setWarningLevel("2");
        warning.setSuggestTransferType("COURT");
        warning.setStatus("1");
        when(ruleMapper.selectAiRiskWarningRuleByRuleId(3L)).thenReturn(rule("1"));
        when(warningMapper.insertAiRiskWarning(warning)).thenReturn(1);

        service.insertAiRiskWarning(warning);

        assertEquals("人工标注", warning.getWarningType());
        assertEquals("2", warning.getWarningLevel());
        assertEquals("COURT", warning.getSuggestTransferType());
        assertEquals("1", warning.getStatus());
    }

    @Test
    void insert_withoutRuleId_skipsRuleLookup()
    {
        AiRiskWarning warning = new AiRiskWarning();
        when(warningMapper.insertAiRiskWarning(warning)).thenReturn(1);

        service.insertAiRiskWarning(warning);

        verifyNoInteractions(ruleMapper);
        assertEquals("3", warning.getWarningLevel());
    }

    @Test
    void insert_broadcastsInAppMessage()
    {
        AiRiskWarning warning = new AiRiskWarning();
        warning.setWarningId(10L);
        warning.setWarningType("情绪异常");
        warning.setWarningLevel("1");
        warning.setCustomerName("张三");
        warning.setContent("辱骂");
        when(warningMapper.insertAiRiskWarning(warning)).thenReturn(1);

        service.insertAiRiskWarning(warning);

        ArgumentCaptor<String> title = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> content = ArgumentCaptor.forClass(String.class);
        verify(messageNotifyDispatcher).broadcast(eq("3"), title.capture(), content.capture(),
                eq("warning"), eq(10L), eq("system"));
        assertTrue(title.getValue().contains("风险预警（高）：情绪异常"));
        assertTrue(content.getValue().contains("客户：张三"));
        assertTrue(content.getValue().contains("触发内容：辱骂"));
    }

    @Test
    void insert_broadcastFailure_isIsolated()
    {
        AiRiskWarning warning = new AiRiskWarning();
        warning.setWarningId(11L);
        when(warningMapper.insertAiRiskWarning(warning)).thenReturn(1);
        doThrow(new RuntimeException("mq down")).when(messageNotifyDispatcher)
                .broadcast(anyString(), anyString(), anyString(), anyString(), anyLong(), anyString());

        assertEquals(1, service.insertAiRiskWarning(warning));
    }

    @Test
    void insert_noDispatcherOrNoId_doesNotBroadcast()
    {
        AiRiskWarning warning = new AiRiskWarning();
        warning.setWarningId(12L);
        when(warningMapper.insertAiRiskWarning(warning)).thenReturn(1);
        service.insertAiRiskWarning(warning);
        verify(messageNotifyDispatcher).broadcast(anyString(), anyString(), anyString(),
                anyString(), eq(12L), anyString());

        ReflectionTestUtils.setField(service, "messageNotifyDispatcher", null);
        warning.setWarningId(13L);
        service.insertAiRiskWarning(warning);
    }

    @Test
    void insert_rowsZero_doesNotBroadcast()
    {
        AiRiskWarning warning = new AiRiskWarning();
        warning.setWarningId(14L);
        when(warningMapper.insertAiRiskWarning(warning)).thenReturn(0);

        assertEquals(0, service.insertAiRiskWarning(warning));
        verifyNoInteractions(messageNotifyDispatcher);
    }

    // ---------------- 一键转办 ----------------

    @Test
    void transfer_nullWarningId_throws()
    {
        assertThrows(ServiceException.class, () -> service.transferByWarning(null, 1L, null, "admin"));
    }

    @Test
    void transfer_warningNotFound_throws()
    {
        when(warningMapper.selectByIdForUpdate(1L)).thenReturn(null);

        assertThrows(ServiceException.class, () -> service.transferByWarning(1L, 2L, null, "admin"));
        verifyNoInteractions(ticketService);
    }

    @Test
    void transfer_alreadyTransferred_returnsExisting()
    {
        AiRiskWarning warning = warning(1L);
        warning.setTransferId(88L);
        AiTicketTransfer existing = new AiTicketTransfer();
        existing.setTransferId(88L);
        when(warningMapper.selectByIdForUpdate(1L)).thenReturn(warning);
        when(transferService.selectTransferById(88L)).thenReturn(existing);

        assertSame(existing, service.transferByWarning(1L, 2L, null, "admin"));
        verifyNoInteractions(ticketService);
    }

    @Test
    void transfer_transferIdDangling_continuesToCreate()
    {
        AiRiskWarning warning = warning(1L);
        warning.setTransferId(88L);
        when(warningMapper.selectByIdForUpdate(1L)).thenReturn(warning);
        when(transferService.selectTransferById(88L)).thenReturn(null);
        givenTransferableOrg(200L);
        givenTicketInsertAssignsId(500L);
        AiTicketTransfer transfer = new AiTicketTransfer();
        transfer.setTransferId(900L);
        when(transferService.transferOut(500L, 200L, "备注", "admin")).thenReturn(transfer);
        when(warningMapper.updateTransferRef(1L, 900L)).thenReturn(1);

        assertSame(transfer, service.transferByWarning(1L, 200L, "备注", "admin"));
    }

    @Test
    void transfer_noSuggestedType_throws()
    {
        AiRiskWarning warning = warning(1L);
        warning.setSuggestTransferType(null);
        when(warningMapper.selectByIdForUpdate(1L)).thenReturn(warning);

        assertThrows(ServiceException.class, () -> service.transferByWarning(1L, 200L, null, "admin"));
        verifyNoInteractions(ticketService);
    }

    @Test
    void transfer_noTargetOrgAndNoRule_throws()
    {
        AiRiskWarning warning = warning(1L);
        warning.setRuleId(null);
        when(warningMapper.selectByIdForUpdate(1L)).thenReturn(warning);

        assertThrows(ServiceException.class, () -> service.transferByWarning(1L, null, null, "admin"));
        verifyNoInteractions(ticketService);
    }

    @Test
    void transfer_usesRuleDefaultOrgWhenOrgMissing()
    {
        AiRiskWarning warning = warning(1L);
        warning.setRuleId(3L);
        AiRiskWarningRule rule = rule("1");
        rule.setSuggestOrgId(200L);
        when(warningMapper.selectByIdForUpdate(1L)).thenReturn(warning);
        when(ruleMapper.selectAiRiskWarningRuleByRuleId(3L)).thenReturn(rule);
        givenTransferableOrg(200L);
        givenTicketInsertAssignsId(500L);
        AiTicketTransfer transfer = new AiTicketTransfer();
        transfer.setTransferId(901L);
        when(transferService.transferOut(anyLong(), anyLong(), any(), anyString())).thenReturn(transfer);
        when(warningMapper.updateTransferRef(1L, 901L)).thenReturn(1);

        assertSame(transfer, service.transferByWarning(1L, null, null, "admin"));

        verify(transferService).transferOut(500L, 200L, null, "admin");
    }

    @Test
    void transfer_orgNotFoundOrDisabledOrMismatched_throws()
    {
        AiRiskWarning warning = warning(1L);
        when(warningMapper.selectByIdForUpdate(1L)).thenReturn(warning);

        when(externalOrgMapper.selectAiExternalOrgByOrgId(200L)).thenReturn(null);
        assertThrows(ServiceException.class, () -> service.transferByWarning(1L, 200L, null, "admin"));

        AiExternalOrg disabled = org(200L, "LEGAL_AID", "1");
        when(externalOrgMapper.selectAiExternalOrgByOrgId(200L)).thenReturn(disabled);
        assertThrows(ServiceException.class, () -> service.transferByWarning(1L, 200L, null, "admin"));

        AiExternalOrg mismatched = org(200L, "COURT", "0");
        when(externalOrgMapper.selectAiExternalOrgByOrgId(200L)).thenReturn(mismatched);
        assertThrows(ServiceException.class, () -> service.transferByWarning(1L, 200L, null, "admin"));

        verifyNoInteractions(ticketService);
    }

    @Test
    void transfer_callSource_fillsTicketFromRecord()
    {
        AiRiskWarning warning = warning(1L);
        warning.setSourceType("通话");
        warning.setSourceId(20L);
        AiCallRecord record = new AiCallRecord();
        record.setRecordId(30L);
        record.setCallerNumber(PHONE);
        record.setCallerName("李四");
        when(warningMapper.selectByIdForUpdate(1L)).thenReturn(warning);
        when(callRecordMapper.selectAiCallRecordByRecordId(20L)).thenReturn(record);
        givenTransferableOrg(200L);
        givenTicketInsertAssignsId(500L);
        AiTicketTransfer transfer = new AiTicketTransfer();
        transfer.setTransferId(902L);
        when(transferService.transferOut(anyLong(), anyLong(), any(), anyString())).thenReturn(transfer);
        when(warningMapper.updateTransferRef(anyLong(), anyLong())).thenReturn(1);

        service.transferByWarning(1L, 200L, "请尽快联系", "admin");

        ArgumentCaptor<AiCallTicket> captor = ArgumentCaptor.forClass(AiCallTicket.class);
        verify(ticketService).insertAiCallTicket(captor.capture());
        AiCallTicket ticket = captor.getValue();
        assertEquals(30L, ticket.getRecordId());
        assertEquals(PHONE, ticket.getCallerNumber());
        assertEquals("张三", ticket.getCallerName());
        assertTrue(ticket.getContent().contains("来电号码：" + PHONE));
        assertTrue(ticket.getContent().contains("坐席备注：请尽快联系"));
    }

    @Test
    void transfer_recordLookupFailure_fallsBackToWarningCustomer()
    {
        AiRiskWarning warning = warning(1L);
        warning.setSourceType("通话");
        warning.setSourceId(21L);
        warning.setCustomerName("王五");
        when(warningMapper.selectByIdForUpdate(1L)).thenReturn(warning);
        when(callRecordMapper.selectAiCallRecordByRecordId(21L))
                .thenThrow(new RuntimeException("db down"));
        givenTransferableOrg(200L);
        givenTicketInsertAssignsId(500L);
        AiTicketTransfer transfer = new AiTicketTransfer();
        transfer.setTransferId(903L);
        when(transferService.transferOut(anyLong(), anyLong(), any(), anyString())).thenReturn(transfer);
        when(warningMapper.updateTransferRef(anyLong(), anyLong())).thenReturn(1);

        service.transferByWarning(1L, 200L, null, "admin");

        ArgumentCaptor<AiCallTicket> captor = ArgumentCaptor.forClass(AiCallTicket.class);
        verify(ticketService).insertAiCallTicket(captor.capture());
        assertNull(captor.getValue().getRecordId());
        assertEquals("王五", captor.getValue().getCallerName());
    }

    @Test
    void transfer_nonCallSource_skipsRecordLookup()
    {
        AiRiskWarning warning = warning(1L);
        warning.setSourceType("图文");
        warning.setSourceId(22L);
        when(warningMapper.selectByIdForUpdate(1L)).thenReturn(warning);
        givenTransferableOrg(200L);
        givenTicketInsertAssignsId(500L);
        AiTicketTransfer transfer = new AiTicketTransfer();
        transfer.setTransferId(904L);
        when(transferService.transferOut(anyLong(), anyLong(), any(), anyString())).thenReturn(transfer);
        when(warningMapper.updateTransferRef(anyLong(), anyLong())).thenReturn(1);

        service.transferByWarning(1L, 200L, null, "admin");

        verifyNoInteractions(callRecordMapper);
    }

    @Test
    void transfer_highLevelTicketPriorityIsHigh()
    {
        AiRiskWarning warning = warning(1L);
        warning.setWarningLevel("1");
        when(warningMapper.selectByIdForUpdate(1L)).thenReturn(warning);
        givenTransferableOrg(200L);
        givenTicketInsertAssignsId(500L);
        AiTicketTransfer transfer = new AiTicketTransfer();
        transfer.setTransferId(905L);
        when(transferService.transferOut(anyLong(), anyLong(), any(), anyString())).thenReturn(transfer);
        when(warningMapper.updateTransferRef(anyLong(), anyLong())).thenReturn(1);

        service.transferByWarning(1L, 200L, null, "admin");

        ArgumentCaptor<AiCallTicket> captor = ArgumentCaptor.forClass(AiCallTicket.class);
        verify(ticketService).insertAiCallTicket(captor.capture());
        assertEquals("1", captor.getValue().getPriority());
        assertEquals("OUT", captor.getValue().getDirection());
        assertEquals(0, captor.getValue().getOvertimeFlag());
    }

    @Test
    void transfer_longFieldsAreTruncated()
    {
        AiRiskWarning warning = warning(1L);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 150; i++)
        {
            sb.append("类");
        }
        warning.setWarningType(sb.toString());
        StringBuilder content = new StringBuilder();
        for (int i = 0; i < 600; i++)
        {
            content.append("内");
        }
        warning.setContent(content.toString());
        when(warningMapper.selectByIdForUpdate(1L)).thenReturn(warning);
        givenTransferableOrg(200L);
        givenTicketInsertAssignsId(500L);
        AiTicketTransfer transfer = new AiTicketTransfer();
        transfer.setTransferId(906L);
        when(transferService.transferOut(anyLong(), anyLong(), any(), anyString())).thenReturn(transfer);
        when(warningMapper.updateTransferRef(anyLong(), anyLong())).thenReturn(1);

        service.transferByWarning(1L, 200L, null, "admin");

        ArgumentCaptor<AiCallTicket> captor = ArgumentCaptor.forClass(AiCallTicket.class);
        verify(ticketService).insertAiCallTicket(captor.capture());
        assertEquals(100, captor.getValue().getTitle().length());
        assertEquals(500, captor.getValue().getContent().length());
    }

    @Test
    void transfer_writeBackMissed_throwsToRollback()
    {
        AiRiskWarning warning = warning(1L);
        when(warningMapper.selectByIdForUpdate(1L)).thenReturn(warning);
        givenTransferableOrg(200L);
        givenTicketInsertAssignsId(500L);
        AiTicketTransfer transfer = new AiTicketTransfer();
        transfer.setTransferId(907L);
        when(transferService.transferOut(anyLong(), anyLong(), any(), anyString())).thenReturn(transfer);
        when(warningMapper.updateTransferRef(1L, 907L)).thenReturn(0);

        assertThrows(ServiceException.class, () -> service.transferByWarning(1L, 200L, null, "admin"));
    }

    // ---------------- 辅助 ----------------

    private void givenTransferableOrg(Long orgId)
    {
        when(externalOrgMapper.selectAiExternalOrgByOrgId(orgId))
                .thenReturn(org(orgId, "LEGAL_AID", "0"));
    }

    private void givenTicketInsertAssignsId(Long ticketId)
    {
        doAnswer(invocation -> {
            ((AiCallTicket) invocation.getArgument(0)).setTicketId(ticketId);
            return 1;
        }).when(ticketService).insertAiCallTicket(any());
    }

    private AiRiskWarning warning(Long warningId)
    {
        AiRiskWarning warning = new AiRiskWarning();
        warning.setWarningId(warningId);
        warning.setWarningType("敏感词");
        warning.setWarningLevel("2");
        warning.setSuggestTransferType("LEGAL_AID");
        warning.setCustomerName("张三");
        warning.setContent("触发内容");
        return warning;
    }

    private AiRiskWarningRule rule(String enabled)
    {
        AiRiskWarningRule rule = new AiRiskWarningRule();
        rule.setRuleId(3L);
        rule.setRuleType("敏感词");
        rule.setRuleLevel("1");
        rule.setSuggestTransferType("LEGAL_AID");
        rule.setIsEnabled(enabled);
        return rule;
    }

    private AiExternalOrg org(Long orgId, String type, String status)
    {
        AiExternalOrg org = new AiExternalOrg();
        org.setOrgId(orgId);
        org.setOrgName("市法律援助中心");
        org.setExternalType(type);
        org.setStatus(status);
        return org;
    }
}
