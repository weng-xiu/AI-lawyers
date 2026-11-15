package ai.lawyers.system.service.impl.lawyers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.test.util.ReflectionTestUtils;
import ai.lawyers.common.utils.sign.PiiCryptoUtils;
import ai.lawyers.system.domain.lawyers.AiCallAgentStatus;
import ai.lawyers.system.domain.lawyers.AiCallLedger;
import ai.lawyers.system.domain.lawyers.AiCallRecord;
import ai.lawyers.system.domain.lawyers.AiCallTicket;
import ai.lawyers.system.mapper.lawyers.AiCallLedgerMapper;
import ai.lawyers.system.service.lawyers.IAiCallAgentStatusService;
import ai.lawyers.system.service.lawyers.IAiCallRecordService;
import ai.lawyers.system.service.lawyers.IAiCallTicketService;
import ai.lawyers.system.service.lawyers.IPiiSearchTokenService;

/**
 * 咨询台账 Service 单测。
 *
 * <p>覆盖号码盲 token 派生、写前 PII 加密与 token 重建、台账号生成、
 * 话单自动填充（坐席/律师回退、时长换算）、转办工单（幂等/回写）与模板清单。</p>
 *
 * @author ai-lawyers
 */
class AiCallLedgerServiceImplTest
{
    private static final String PHONE = "13800138000";

    private AiCallLedgerServiceImpl service;

    @Mock private AiCallLedgerMapper ledgerMapper;
    @Mock private IPiiSearchTokenService piiSearchTokenService;
    @Mock private IAiCallRecordService recordService;
    @Mock private IAiCallTicketService ticketService;
    @Mock private IAiCallAgentStatusService agentStatusService;

    @BeforeEach
    void setUp()
    {
        MockitoAnnotations.openMocks(this);
        service = new AiCallLedgerServiceImpl();
        ReflectionTestUtils.setField(service, "aiCallLedgerMapper", ledgerMapper);
        ReflectionTestUtils.setField(service, "piiSearchTokenService", piiSearchTokenService);
        ReflectionTestUtils.setField(service, "aiCallRecordService", recordService);
        ReflectionTestUtils.setField(service, "aiCallTicketService", ticketService);
        ReflectionTestUtils.setField(service, "aiCallAgentStatusService", agentStatusService);
    }

    // ---------------- 查询与 token 派生 ----------------

    @Test
    void selectQueries_delegate()
    {
        AiCallLedger ledger = new AiCallLedger();
        List<AiCallLedger> list = Collections.singletonList(ledger);
        when(ledgerMapper.selectAiCallLedgerByLedgerId(1L)).thenReturn(ledger);
        when(ledgerMapper.selectAiCallLedgerByLedgerNo("DJ1")).thenReturn(ledger);
        when(ledgerMapper.selectAiCallLedgerList(any())).thenReturn(list);

        assertSame(ledger, service.selectAiCallLedgerByLedgerId(1L));
        assertSame(ledger, service.selectAiCallLedgerByLedgerNo("DJ1"));
        assertEquals(list, service.selectAiCallLedgerList(new AiCallLedger()));
    }

    @Test
    void selectList_withPhone_derivesTokenGroups()
    {
        AiCallLedger query = new AiCallLedger();
        query.setCallerPhone(PHONE);
        when(ledgerMapper.selectAiCallLedgerList(query)).thenReturn(Collections.emptyList());

        service.selectAiCallLedgerList(query);

        assertNotNull(query.getTokenGroups());
        assertEquals(PiiCryptoUtils.tokenGroups(PHONE), query.getTokenGroups());
        verify(ledgerMapper).selectAiCallLedgerList(query);
    }

    @Test
    void selectList_existingTokenGroupsNotOverwritten()
    {
        AiCallLedger query = new AiCallLedger();
        query.setCallerPhone(PHONE);
        List<List<String>> preset = new ArrayList<>();
        preset.add(Collections.singletonList("t"));
        query.setTokenGroups(preset);
        when(ledgerMapper.selectAiCallLedgerList(query)).thenReturn(Collections.emptyList());

        service.selectAiCallLedgerList(query);

        assertSame(preset, query.getTokenGroups());
    }

    @Test
    void selectList_withoutPhone_leavesTokenGroupsNull()
    {
        AiCallLedger query = new AiCallLedger();
        when(ledgerMapper.selectAiCallLedgerList(query)).thenReturn(Collections.emptyList());

        service.selectAiCallLedgerList(query);

        assertNull(query.getTokenGroups());
    }

    // ---------------- 新增/修改 ----------------

    @Test
    void insert_generatesLedgerNo_encryptsPhoneAndRebuildsTokens()
    {
        AiCallLedger ledger = new AiCallLedger();
        ledger.setCallerPhone(PHONE);
        givenInsertAssignsId(55L);

        assertEquals(1, service.insertAiCallLedger(ledger));

        assertNotNull(ledger.getLedgerNo());
        assertTrue(ledger.getLedgerNo().startsWith("DJ"));
        assertTrue(PiiCryptoUtils.isEncrypted(ledger.getCallerPhone()));
        assertEquals(PHONE, PiiCryptoUtils.decrypt(ledger.getCallerPhone()));
        verify(piiSearchTokenService).rebuild(IPiiSearchTokenService.OWNER_CALL_LEDGER, 55L, PHONE);
    }

    @Test
    void insert_keepsProvidedLedgerNo_andEncryptsIdCard()
    {
        AiCallLedger ledger = new AiCallLedger();
        ledger.setLedgerNo("DJ-EXIST");
        ledger.setCallerIdCard("110101199001011234");
        givenInsertAssignsId(56L);

        assertEquals(1, service.insertAiCallLedger(ledger));

        assertEquals("DJ-EXIST", ledger.getLedgerNo());
        assertTrue(PiiCryptoUtils.isEncrypted(ledger.getCallerIdCard()));
        assertEquals("110101199001011234", PiiCryptoUtils.decrypt(ledger.getCallerIdCard()));
        verifyNoInteractions(piiSearchTokenService);
    }

    @Test
    void insert_tokenRebuildFailure_isIsolated()
    {
        AiCallLedger ledger = new AiCallLedger();
        ledger.setCallerPhone(PHONE);
        givenInsertAssignsId(57L);
        doThrow(new RuntimeException("token table down"))
                .when(piiSearchTokenService).rebuild(anyString(), anyLong(), anyString());

        assertEquals(1, service.insertAiCallLedger(ledger));
    }

    @Test
    void insert_withoutTokenService_stillPersists()
    {
        ReflectionTestUtils.setField(service, "piiSearchTokenService", null);
        AiCallLedger ledger = new AiCallLedger();
        ledger.setCallerPhone(PHONE);
        givenInsertAssignsId(58L);

        assertEquals(1, service.insertAiCallLedger(ledger));
    }

    @Test
    void update_rebuildsTokensWithPlainPhone()
    {
        AiCallLedger ledger = new AiCallLedger();
        ledger.setLedgerId(9L);
        ledger.setCallerPhone(PHONE);
        when(ledgerMapper.updateAiCallLedger(ledger)).thenReturn(1);

        assertEquals(1, service.updateAiCallLedger(ledger));

        assertTrue(PiiCryptoUtils.isEncrypted(ledger.getCallerPhone()));
        verify(piiSearchTokenService).rebuild(IPiiSearchTokenService.OWNER_CALL_LEDGER, 9L, PHONE);
    }

    @Test
    void update_withoutPhone_doesNotRebuildTokens()
    {
        AiCallLedger ledger = new AiCallLedger();
        ledger.setLedgerId(9L);
        when(ledgerMapper.updateAiCallLedger(ledger)).thenReturn(1);

        assertEquals(1, service.updateAiCallLedger(ledger));
        verifyNoInteractions(piiSearchTokenService);
    }

    @Test
    void delete_delegates()
    {
        Long[] ids = {1L, 2L};
        when(ledgerMapper.deleteAiCallLedgerByLedgerId(1L)).thenReturn(1);
        when(ledgerMapper.deleteAiCallLedgerByLedgerIds(ids)).thenReturn(2);

        assertEquals(1, service.deleteAiCallLedgerByLedgerId(1L));
        assertEquals(2, service.deleteAiCallLedgerByLedgerIds(ids));
    }

    @Test
    void generateLedgerNo_hasDjPrefixAndExpectedLength()
    {
        String no = service.generateLedgerNo();

        assertTrue(no.startsWith("DJ"));
        assertEquals(22, no.length());
    }

    // ---------------- 模板 ----------------

    @Test
    void selectLedgerTemplates_returnsSixTemplates()
    {
        List<Map<String, Object>> templates = service.selectLedgerTemplates();

        assertEquals(6, templates.size());
        for (Map<String, Object> tpl : templates)
        {
            assertNotNull(tpl.get("templateId"));
            assertNotNull(tpl.get("templateName"));
            assertEquals("电话咨询", tpl.get("sourceChannel"));
            assertNotNull(tpl.get("contentTemplate"));
        }
        assertEquals("1", templates.get(0).get("templateId"));
    }

    // ---------------- 话单自动填充 ----------------

    @Test
    void autoFill_recordNotFound_returnsEmptyLedger()
    {
        when(recordService.selectAiCallRecordByRecordId(1L)).thenReturn(null);

        AiCallLedger ledger = service.autoFillByRecordId(1L);

        assertNull(ledger.getRecordId());
        verifyNoInteractions(agentStatusService);
    }

    @Test
    void autoFill_usesAgentUserAsDefaultLawyer()
    {
        AiCallRecord record = record(3L, 130, 7L, "坐席A");
        AiCallAgentStatus agent = new AiCallAgentStatus();
        agent.setUserId(100L);
        agent.setNickName("张律师");
        when(recordService.selectAiCallRecordByRecordId(3L)).thenReturn(record);
        when(agentStatusService.selectAiCallAgentStatusByAgentId(7L)).thenReturn(agent);

        AiCallLedger ledger = service.autoFillByRecordId(3L);

        assertEquals(3L, ledger.getRecordId());
        assertEquals("张三", ledger.getCallerName());
        assertEquals(PHONE, ledger.getCallerPhone());
        assertEquals("北京市朝阳区", ledger.getCallerAddress());
        assertEquals(10L, ledger.getCategoryId());
        assertEquals("民事纠纷", ledger.getCategoryName());
        assertEquals("咨询内容", ledger.getConsultContent());
        assertEquals("解答内容", ledger.getLawyerAnswer());
        assertEquals(100L, ledger.getLawyerId());
        assertEquals("张律师", ledger.getLawyerName());
        assertEquals("1", ledger.getServiceType());
        assertEquals("电话咨询", ledger.getSourceChannel());
        assertEquals(2, ledger.getConsultDuration());
    }

    @Test
    void autoFill_shortDuration_roundsUpToOneMinute()
    {
        AiCallRecord record = record(4L, 30, null, "坐席B");
        when(recordService.selectAiCallRecordByRecordId(4L)).thenReturn(record);

        AiCallLedger ledger = service.autoFillByRecordId(4L);

        assertEquals(1, ledger.getConsultDuration());
        assertEquals("坐席B", ledger.getLawyerName());
        assertNull(ledger.getLawyerId());
        verifyNoInteractions(agentStatusService);
    }

    @Test
    void autoFill_zeroDuration_leavesDurationNull()
    {
        AiCallRecord record = record(5L, 0, null, "坐席C");
        when(recordService.selectAiCallRecordByRecordId(5L)).thenReturn(record);

        assertNull(service.autoFillByRecordId(5L).getConsultDuration());
    }

    @Test
    void autoFill_agentWithoutUserId_fallsBackToAgentName()
    {
        AiCallRecord record = record(6L, null, 8L, "坐席D");
        AiCallAgentStatus agent = new AiCallAgentStatus();
        agent.setNickName("无账号坐席");
        when(recordService.selectAiCallRecordByRecordId(6L)).thenReturn(record);
        when(agentStatusService.selectAiCallAgentStatusByAgentId(8L)).thenReturn(agent);

        AiCallLedger ledger = service.autoFillByRecordId(6L);

        assertNull(ledger.getLawyerId());
        assertEquals("坐席D", ledger.getLawyerName());
    }

    @Test
    void autoFill_agentMissing_fallsBackToAgentName()
    {
        AiCallRecord record = record(7L, null, 9L, "坐席E");
        when(recordService.selectAiCallRecordByRecordId(7L)).thenReturn(record);
        when(agentStatusService.selectAiCallAgentStatusByAgentId(9L)).thenReturn(null);

        assertEquals("坐席E", service.autoFillByRecordId(7L).getLawyerName());
    }

    // ---------------- 转办工单 ----------------

    @Test
    void transfer_ledgerNotFound_returnsNull()
    {
        when(ledgerMapper.selectAiCallLedgerByLedgerId(1L)).thenReturn(null);

        assertNull(service.transferToTicket(1L, "admin"));
        verifyNoInteractions(ticketService);
    }

    @Test
    void transfer_existingTicket_returnsWithoutCreating()
    {
        AiCallLedger ledger = new AiCallLedger();
        ledger.setLedgerId(1L);
        ledger.setTicketId(300L);
        when(ledgerMapper.selectAiCallLedgerByLedgerId(1L)).thenReturn(ledger);

        assertEquals(300L, service.transferToTicket(1L, "admin"));
        verifyNoInteractions(ticketService);
        verify(ledgerMapper, never()).updateAiCallLedger(any());
    }

    @Test
    void transfer_createsTicketAndWritesBackId()
    {
        AiCallLedger ledger = new AiCallLedger();
        ledger.setLedgerId(2L);
        ledger.setRecordId(20L);
        ledger.setCallerName("李四");
        ledger.setCallerPhone(PHONE);
        ledger.setCategoryName("劳动咨询");
        ledger.setConsultContent("拖欠工资");
        when(ledgerMapper.selectAiCallLedgerByLedgerId(2L)).thenReturn(ledger);
        doAnswer(invocation -> {
            ((AiCallTicket) invocation.getArgument(0)).setTicketId(500L);
            return 1;
        }).when(ticketService).insertAiCallTicket(any());

        assertEquals(500L, service.transferToTicket(2L, "admin"));

        ArgumentCaptor<AiCallTicket> ticketCaptor = ArgumentCaptor.forClass(AiCallTicket.class);
        verify(ticketService).insertAiCallTicket(ticketCaptor.capture());
        AiCallTicket ticket = ticketCaptor.getValue();
        assertEquals(20L, ticket.getRecordId());
        assertEquals(PHONE, ticket.getCallerNumber());
        assertEquals("李四", ticket.getCallerName());
        assertEquals("劳动咨询转办工单", ticket.getTitle());
        assertEquals("拖欠工资", ticket.getContent());
        assertEquals("2", ticket.getPriority());
        assertEquals("0", ticket.getStatus());
        assertEquals("admin", ticket.getCreateBy());

        ArgumentCaptor<AiCallLedger> ledgerCaptor = ArgumentCaptor.forClass(AiCallLedger.class);
        verify(ledgerMapper).updateAiCallLedger(ledgerCaptor.capture());
        assertEquals(2L, ledgerCaptor.getValue().getLedgerId());
        assertEquals(500L, ledgerCaptor.getValue().getTicketId());
        assertEquals("admin", ledgerCaptor.getValue().getUpdateBy());
    }

    @Test
    void transfer_missingCategory_usesDefaultTitle()
    {
        AiCallLedger ledger = new AiCallLedger();
        ledger.setLedgerId(3L);
        when(ledgerMapper.selectAiCallLedgerByLedgerId(3L)).thenReturn(ledger);
        doAnswer(invocation -> {
            ((AiCallTicket) invocation.getArgument(0)).setTicketId(501L);
            return 1;
        }).when(ticketService).insertAiCallTicket(any());

        assertEquals(501L, service.transferToTicket(3L, "admin"));
        ArgumentCaptor<AiCallTicket> captor = ArgumentCaptor.forClass(AiCallTicket.class);
        verify(ticketService).insertAiCallTicket(captor.capture());
        assertEquals("咨询转办工单", captor.getValue().getTitle());
    }

    // ---------------- 辅助 ----------------

    private void givenInsertAssignsId(Long ledgerId)
    {
        doAnswer(invocation -> {
            ((AiCallLedger) invocation.getArgument(0)).setLedgerId(ledgerId);
            return 1;
        }).when(ledgerMapper).insertAiCallLedger(any());
    }

    private AiCallRecord record(Long recordId, Integer duration, Long agentId, String agentName)
    {
        AiCallRecord record = new AiCallRecord();
        record.setRecordId(recordId);
        record.setCallerName("张三");
        record.setCallerNumber(PHONE);
        record.setCallerAddress("北京市朝阳区");
        record.setCategoryId(10L);
        record.setCategoryName("民事纠纷");
        record.setContent("咨询内容");
        record.setAnswer("解答内容");
        record.setAgentId(agentId);
        record.setAgentName(agentName);
        record.setDuration(duration);
        return record;
    }
}
