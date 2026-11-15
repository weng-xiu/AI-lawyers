package ai.lawyers.system.service.impl.lawyers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.Date;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.test.util.ReflectionTestUtils;
import ai.lawyers.common.exception.ServiceException;
import ai.lawyers.system.domain.lawyers.AiCallAgentStatus;
import ai.lawyers.system.domain.lawyers.AiCallRecord;
import ai.lawyers.system.domain.lawyers.trunk.DialRequest;
import ai.lawyers.system.domain.lawyers.trunk.DialResult;
import ai.lawyers.system.mapper.lawyers.AiCallAgentStatusMapper;
import ai.lawyers.system.service.lawyers.CallEventPublisher;
import ai.lawyers.system.service.lawyers.IAiAiCallSessionService;
import ai.lawyers.system.service.lawyers.IAiCallRecordService;
import ai.lawyers.system.service.lawyers.queue.StatusLogDispatcher;
import ai.lawyers.system.service.lawyers.trunk.ICallDispatchService;

/**
 * 坐席状态机 Service 单测。
 *
 * <p>覆盖签入/签出/置忙空闲、外呼建单与线路下发回滚、保持/恢复/转接/咨询/三方/话后、
 * 挂机与机器人接管/转 IVR 的通话记录收尾、状态流水与事件推送，以及用户-工号绑定同步。</p>
 *
 * @author ai-lawyers
 */
class AiCallAgentStatusServiceImplTest
{
    private static final String PHONE = "13800138000";

    private AiCallAgentStatusServiceImpl service;

    @Mock private AiCallAgentStatusMapper aiCallAgentStatusMapper;
    @Mock private IAiCallRecordService aiCallRecordService;
    @Mock private IAiAiCallSessionService aiAiCallSessionService;
    @Mock private ICallDispatchService callDispatchService;
    @Mock private CallEventPublisher callEventPublisher;
    @Mock private StatusLogDispatcher statusLogDispatcher;

    @BeforeEach
    void setUp()
    {
        MockitoAnnotations.openMocks(this);
        service = new AiCallAgentStatusServiceImpl();
        ReflectionTestUtils.setField(service, "aiCallAgentStatusMapper", aiCallAgentStatusMapper);
        ReflectionTestUtils.setField(service, "aiCallRecordService", aiCallRecordService);
        ReflectionTestUtils.setField(service, "aiAiCallSessionService", aiAiCallSessionService);
        ReflectionTestUtils.setField(service, "callDispatchService", callDispatchService);
        ReflectionTestUtils.setField(service, "callEventPublisher", callEventPublisher);
        ReflectionTestUtils.setField(service, "statusLogDispatcher", statusLogDispatcher);
    }

    // ---------------- 基础委托 ----------------

    @Test
    void selectAndCrud_delegateToMapper()
    {
        AiCallAgentStatus query = new AiCallAgentStatus();
        when(aiCallAgentStatusMapper.selectAiCallAgentStatusList(query))
                .thenReturn(Arrays.asList(agent(1L)));
        when(aiCallAgentStatusMapper.deleteAiCallAgentStatusByAgentIds(any())).thenReturn(2);

        assertEquals(1, service.selectAiCallAgentStatusList(query).size());
        service.selectAiCallAgentStatusByAgentId(1L);
        service.selectAiCallAgentStatusByUserId(100L);
        service.insertAiCallAgentStatus(query);
        service.updateAiCallAgentStatus(query);
        service.deleteAiCallAgentStatusByAgentId(1L);
        assertEquals(2, service.deleteAiCallAgentStatusByAgentIds(new Long[]{1L, 2L}));
        service.selectOnlineAgents();
        service.selectTodayRecordsByAgentId(1L);

        verify(aiCallAgentStatusMapper).selectAiCallAgentStatusByAgentId(1L);
        verify(aiCallAgentStatusMapper).selectAiCallAgentStatusByUserId(100L);
        verify(aiCallAgentStatusMapper).insertAiCallAgentStatus(query);
        verify(aiCallAgentStatusMapper).updateAiCallAgentStatus(query);
        verify(aiCallAgentStatusMapper).deleteAiCallAgentStatusByAgentId(1L);
        verify(aiCallAgentStatusMapper).selectOnlineAgents();
        verify(aiCallAgentStatusMapper).selectTodayRecordsByAgent(1L);
    }

    @Test
    void updateCallMode_agentExists_updates()
    {
        AiCallAgentStatus agent = agent(1L);
        when(aiCallAgentStatusMapper.selectAiCallAgentStatusByAgentId(1L)).thenReturn(agent);
        when(aiCallAgentStatusMapper.updateAiCallAgentStatus(agent)).thenReturn(1);

        assertEquals(1, service.updateCallMode(1L, "1"));
        assertEquals("1", agent.getCallMode());
    }

    @Test
    void updateCallMode_agentMissing_returnsZero()
    {
        assertEquals(0, service.updateCallMode(1L, "1"));
        verify(aiCallAgentStatusMapper, never()).updateAiCallAgentStatus(any());
    }

    // ---------------- 签入 / 签出 / 置忙 ----------------

    @Test
    void agentLogin_byAgentId_success()
    {
        AiCallAgentStatus agent = agent(1L);
        agent.setStatus("0");
        agent.setCallStatus("1");
        when(aiCallAgentStatusMapper.selectAiCallAgentStatusByAgentId(1L)).thenReturn(agent);
        when(aiCallAgentStatusMapper.updateAiCallAgentStatus(agent)).thenReturn(1);

        assertEquals(1, service.agentLogin(1L, null, "10.0.0.8"));

        assertEquals("1", agent.getStatus());
        assertEquals("0", agent.getCallStatus());
        assertNotNull(agent.getLoginTime());
        assertNull(agent.getLogoutTime());
        assertEquals("10.0.0.8", agent.getLastLoginIp());
        verify(aiCallAgentStatusMapper).clearCurrentCall(1L);
        verify(callEventPublisher).publishToUser(eq(100L), eq("AGENT_LOGIN"), any());
        verify(statusLogDispatcher).log(eq(1L), eq(100L), eq("LOGIN"),
                eq("0"), eq("1"), eq("1"), eq("0"), isNull(), any(Date.class));
    }

    @Test
    void agentLogin_byUserId_success()
    {
        AiCallAgentStatus agent = agent(1L);
        when(aiCallAgentStatusMapper.selectAiCallAgentStatusByUserId(100L)).thenReturn(agent);
        when(aiCallAgentStatusMapper.updateAiCallAgentStatus(agent)).thenReturn(1);

        assertEquals(1, service.agentLogin(null, 100L, "10.0.0.9"));

        verify(aiCallAgentStatusMapper).selectAiCallAgentStatusByUserId(100L);
        verify(aiCallAgentStatusMapper, never()).selectAiCallAgentStatusByAgentId(anyLong());
    }

    @Test
    void agentLogin_agentBoundToOtherUser_denied()
    {
        AiCallAgentStatus agent = agent(1L);
        agent.setUserId(999L);
        when(aiCallAgentStatusMapper.selectAiCallAgentStatusByAgentId(1L)).thenReturn(agent);

        assertEquals(-1, service.agentLogin(1L, 100L, "10.0.0.8"));

        verify(aiCallAgentStatusMapper, never()).updateAiCallAgentStatus(any());
        verify(aiCallAgentStatusMapper, never()).clearCurrentCall(anyLong());
    }

    @Test
    void agentLogin_agentMissing_returnsZero()
    {
        assertEquals(0, service.agentLogin(1L, null, "10.0.0.8"));
        assertEquals(0, service.agentLogin(null, null, "10.0.0.8"));
        verify(aiCallAgentStatusMapper).selectAiCallAgentStatusByAgentId(1L);
        verify(aiCallAgentStatusMapper, never()).selectAiCallAgentStatusByUserId(anyLong());
        verify(aiCallAgentStatusMapper, never()).updateAiCallAgentStatus(any());
    }

    @Test
    void agentLogin_updateReturnsZero_noEventAndNoLog()
    {
        AiCallAgentStatus agent = agent(1L);
        when(aiCallAgentStatusMapper.selectAiCallAgentStatusByAgentId(1L)).thenReturn(agent);

        assertEquals(0, service.agentLogin(1L, null, "10.0.0.8"));

        verifyNoInteractions(callEventPublisher, statusLogDispatcher);
    }

    @Test
    void agentLogout_success()
    {
        AiCallAgentStatus agent = agent(1L);
        agent.setStatus("1");
        agent.setCallStatus("1");
        when(aiCallAgentStatusMapper.selectAiCallAgentStatusByAgentId(1L)).thenReturn(agent);
        when(aiCallAgentStatusMapper.updateAiCallAgentStatus(agent)).thenReturn(1);

        assertEquals(1, service.agentLogout(1L, null));

        assertEquals("0", agent.getStatus());
        assertEquals("0", agent.getCallStatus());
        assertNotNull(agent.getLogoutTime());
        verify(aiCallAgentStatusMapper).clearCurrentCall(1L);
        verify(callEventPublisher).publishToUser(eq(100L), eq("AGENT_LOGOUT"), any());
        verify(statusLogDispatcher).log(eq(1L), eq(100L), eq("LOGOUT"),
                eq("1"), eq("0"), eq("1"), eq("0"), isNull(), any(Date.class));
    }

    @Test
    void agentLogout_agentMissing_returnsZero()
    {
        assertEquals(0, service.agentLogout(1L, null));
        verify(aiCallAgentStatusMapper, never()).updateAiCallAgentStatus(any());
    }

    @Test
    void updateAgentStatus_offline_clearsCurrentCall()
    {
        AiCallAgentStatus agent = agent(1L);
        agent.setCallStatus("1");
        when(aiCallAgentStatusMapper.selectAiCallAgentStatusByAgentId(1L)).thenReturn(agent);
        when(aiCallAgentStatusMapper.updateAiCallAgentStatus(agent)).thenReturn(1);

        assertEquals(1, service.updateAgentStatus(1L, "0"));

        assertEquals("0", agent.getStatus());
        assertEquals("0", agent.getCallStatus());
        assertNotNull(agent.getLogoutTime());
        verify(aiCallAgentStatusMapper).clearCurrentCall(1L);
    }

    @Test
    void updateAgentStatus_online_setsLoginTime()
    {
        AiCallAgentStatus agent = agent(1L);
        agent.setLogoutTime(new Date(0L));
        when(aiCallAgentStatusMapper.selectAiCallAgentStatusByAgentId(1L)).thenReturn(agent);
        when(aiCallAgentStatusMapper.updateAiCallAgentStatus(agent)).thenReturn(1);

        assertEquals(1, service.updateAgentStatus(1L, "1"));

        assertEquals("1", agent.getStatus());
        assertNotNull(agent.getLoginTime());
        assertNull(agent.getLogoutTime());
        verify(aiCallAgentStatusMapper, never()).clearCurrentCall(anyLong());
    }

    @Test
    void updateAgentStatus_agentMissing_returnsZero()
    {
        assertEquals(0, service.updateAgentStatus(1L, "0"));
        verify(aiCallAgentStatusMapper, never()).updateAiCallAgentStatus(any());
    }

    // ---------------- 外呼 ----------------

    @Test
    void makeCall_agentMissing_returnsNull()
    {
        assertNull(service.makeCall(1L, PHONE));
        verifyNoInteractions(aiCallRecordService, callDispatchService, aiAiCallSessionService);
    }

    @Test
    void makeCall_success_insertsRecordAndStartsCall()
    {
        AiCallAgentStatus agent = agent(1L);
        agent.setStatus("1");
        agent.setCallStatus("0");
        when(aiCallAgentStatusMapper.selectAiCallAgentStatusByAgentId(1L)).thenReturn(agent);
        when(aiCallRecordService.insertAiCallRecord(any())).thenAnswer(invocation -> {
            AiCallRecord inserted = invocation.getArgument(0);
            inserted.setRecordId(200L);
            return 1;
        });
        when(callDispatchService.dialWithQueue(any())).thenReturn(DialResult.ok("uuid-1"));

        AiCallAgentStatus result = service.makeCall(1L, PHONE);

        assertSame(agent, result);
        assertEquals("1", agent.getCallStatus());
        assertEquals("2", agent.getStatus());
        assertEquals(PHONE, agent.getCurrentCallPhone());
        assertEquals(Long.valueOf(200L), agent.getCurrentCallId());
        assertNotNull(agent.getCallStartTime());

        ArgumentCaptor<AiCallRecord> captor = ArgumentCaptor.forClass(AiCallRecord.class);
        verify(aiCallRecordService).insertAiCallRecord(captor.capture());
        AiCallRecord record = captor.getValue();
        assertEquals(PHONE, record.getCallerNumber());
        assertEquals(Long.valueOf(1L), record.getAgentId());
        assertEquals("0", record.getStatus());
        assertEquals("张三", record.getCreateBy());
        assertNotNull(record.getCallTime());

        verify(aiCallAgentStatusMapper).updateAiCallAgentStatus(agent);
        verify(callEventPublisher).publishToUser(eq(100L), eq("CALL_START"), any());
        verify(aiAiCallSessionService).startAssistSession(200L, 1L, PHONE, "张三");
        verify(callDispatchService).dialWithQueue(any(DialRequest.class));
        verify(statusLogDispatcher).log(eq(1L), eq(100L), eq("MAKE_CALL"),
                eq("1"), eq("2"), eq("0"), eq("1"), eq(200L), any(Date.class));
    }

    @Test
    void makeCall_dispatchFail_rollsBackAgent()
    {
        AiCallAgentStatus agent = agent(1L);
        agent.setStatus("1");
        when(aiCallAgentStatusMapper.selectAiCallAgentStatusByAgentId(1L)).thenReturn(agent);
        when(aiCallRecordService.insertAiCallRecord(any())).thenAnswer(invocation -> {
            AiCallRecord inserted = invocation.getArgument(0);
            inserted.setRecordId(201L);
            return 1;
        });
        when(callDispatchService.dialWithQueue(any()))
                .thenReturn(DialResult.fail("NO_TRUNK", "无可用线路"));

        service.makeCall(1L, PHONE);

        assertEquals("1", agent.getStatus());
        assertEquals("0", agent.getCallStatus());
        assertNull(agent.getCurrentCallId());
        assertNull(agent.getCurrentCallPhone());
        assertNull(agent.getCallStartTime());
        verify(statusLogDispatcher).log(eq(1L), eq(100L), eq("DISPATCH_FAIL_ROLLBACK"),
                any(), any(), any(), any(), eq(201L), any(Date.class));
    }

    @Test
    void makeCall_dispatchThrows_isolatedAndRollsBack()
    {
        AiCallAgentStatus agent = agent(1L);
        agent.setStatus("1");
        when(aiCallAgentStatusMapper.selectAiCallAgentStatusByAgentId(1L)).thenReturn(agent);
        when(aiCallRecordService.insertAiCallRecord(any())).thenAnswer(invocation -> {
            AiCallRecord inserted = invocation.getArgument(0);
            inserted.setRecordId(202L);
            return 1;
        });
        when(callDispatchService.dialWithQueue(any())).thenThrow(new RuntimeException("gw down"));

        service.makeCall(1L, PHONE);

        assertEquals("1", agent.getStatus());
        assertEquals("0", agent.getCallStatus());
        assertNull(agent.getCurrentCallId());
    }

    @Test
    void dispatchOutbound_nullResult_rollsBack()
    {
        AiCallAgentStatus agent = agent(1L);
        agent.setCurrentCallPhone(PHONE);
        AiCallRecord record = new AiCallRecord();
        record.setRecordId(203L);
        record.setCallerNumber(PHONE);

        service.dispatchOutbound(record, agent);

        assertEquals("1", agent.getStatus());
        assertEquals("0", agent.getCallStatus());
        verify(statusLogDispatcher).log(eq(1L), eq(100L), eq("DISPATCH_FAIL_ROLLBACK"),
                any(), any(), any(), any(), eq(203L), any(Date.class));
    }

    @Test
    void triggerAiAssistAsync_failureSwallowed()
    {
        doThrow(new RuntimeException("ai down")).when(aiAiCallSessionService)
                .startAssistSession(any(), any(), any(), any());

        service.triggerAiAssistAsync(1L, 2L, PHONE, "张三");

        verify(aiAiCallSessionService).startAssistSession(1L, 2L, PHONE, "张三");
    }

    // ---------------- 通话控制 ----------------

    @Test
    void holdCall_onlyWhenTalking()
    {
        AiCallAgentStatus agent = agent(1L);
        agent.setCallStatus("1");
        agent.setCurrentCallId(300L);
        when(aiCallAgentStatusMapper.selectAiCallAgentStatusByAgentId(1L)).thenReturn(agent);
        when(aiCallAgentStatusMapper.updateAiCallAgentStatus(agent)).thenReturn(1);

        assertEquals(1, service.holdCall(1L));
        assertEquals("2", agent.getCallStatus());
        verify(statusLogDispatcher).log(eq(1L), eq(100L), eq("HOLD"),
                any(), any(), any(), any(), eq(300L), any(Date.class));
    }

    @Test
    void holdCall_idle_returnsZero()
    {
        AiCallAgentStatus agent = agent(1L);
        agent.setCallStatus("0");
        when(aiCallAgentStatusMapper.selectAiCallAgentStatusByAgentId(1L)).thenReturn(agent);

        assertEquals(0, service.holdCall(1L));
        verify(aiCallAgentStatusMapper, never()).updateAiCallAgentStatus(any());
    }

    @Test
    void resumeCall_onlyWhenHeld()
    {
        AiCallAgentStatus agent = agent(1L);
        agent.setCallStatus("2");
        when(aiCallAgentStatusMapper.selectAiCallAgentStatusByAgentId(1L)).thenReturn(agent);
        when(aiCallAgentStatusMapper.updateAiCallAgentStatus(agent)).thenReturn(1);

        assertEquals(1, service.resumeCall(1L));
        assertEquals("1", agent.getCallStatus());
    }

    @Test
    void resumeCall_talking_returnsZero()
    {
        AiCallAgentStatus agent = agent(1L);
        agent.setCallStatus("1");
        when(aiCallAgentStatusMapper.selectAiCallAgentStatusByAgentId(1L)).thenReturn(agent);

        assertEquals(0, service.resumeCall(1L));
        verify(aiCallAgentStatusMapper, never()).updateAiCallAgentStatus(any());
    }

    @Test
    void transferCall_completesRecordAndResetsAgent()
    {
        AiCallAgentStatus agent = agent(1L);
        agent.setCallStatus("1");
        agent.setStatus("2");
        agent.setCurrentCallId(301L);
        AiCallRecord record = new AiCallRecord();
        record.setRecordId(301L);
        record.setStatus("0");
        record.setCallTime(new Date(System.currentTimeMillis() - 120_000L));
        when(aiCallAgentStatusMapper.selectAiCallAgentStatusByAgentId(1L)).thenReturn(agent);
        when(aiCallRecordService.selectAiCallRecordByRecordId(301L)).thenReturn(record);
        when(aiCallAgentStatusMapper.updateAiCallAgentStatus(agent)).thenReturn(1);

        assertEquals(1, service.transferCall(1L, 9L, "请稍等"));

        assertEquals("2", record.getStatus());
        assertNotNull(record.getEndTime());
        assertTrue(record.getDuration() >= 119);
        assertEquals("0", agent.getCallStatus());
        assertEquals("1", agent.getStatus());
        assertEquals("转接给座席[9]：请稍等", agent.getRemark());
        verify(aiCallRecordService).updateAiCallRecord(record);
        verify(aiCallAgentStatusMapper).clearCurrentCall(1L);
        verify(statusLogDispatcher).log(eq(1L), eq(100L), eq("TRANSFER"),
                any(), any(), any(), any(), eq(301L), any(Date.class));
    }

    @Test
    void transferCall_noCurrentCall_returnsZero()
    {
        AiCallAgentStatus agent = agent(1L);
        when(aiCallAgentStatusMapper.selectAiCallAgentStatusByAgentId(1L)).thenReturn(agent);

        assertEquals(0, service.transferCall(1L, 9L, "请稍等"));
        verifyNoInteractions(aiCallRecordService);
    }

    @Test
    void consultCall_talking_setsConsultStatus()
    {
        AiCallAgentStatus agent = agent(1L);
        agent.setCallStatus("1");
        agent.setCurrentCallId(302L);
        when(aiCallAgentStatusMapper.selectAiCallAgentStatusByAgentId(1L)).thenReturn(agent);
        when(aiCallAgentStatusMapper.updateAiCallAgentStatus(agent)).thenReturn(1);

        assertEquals(1, service.consultCall(1L, 9L));
        assertEquals("3", agent.getCallStatus());
        assertEquals("咨询座席[9]", agent.getRemark());
        verify(statusLogDispatcher).log(eq(1L), eq(100L), eq("CONSULT"),
                any(), any(), any(), any(), eq(302L), any(Date.class));
    }

    @Test
    void consultCall_notTalking_returnsZero()
    {
        AiCallAgentStatus agent = agent(1L);
        agent.setCallStatus("2");
        when(aiCallAgentStatusMapper.selectAiCallAgentStatusByAgentId(1L)).thenReturn(agent);

        assertEquals(0, service.consultCall(1L, 9L));
    }

    @Test
    void threeWayCall_talking_setsThreeWayStatus()
    {
        AiCallAgentStatus agent = agent(1L);
        agent.setCallStatus("1");
        when(aiCallAgentStatusMapper.selectAiCallAgentStatusByAgentId(1L)).thenReturn(agent);
        when(aiCallAgentStatusMapper.updateAiCallAgentStatus(agent)).thenReturn(1);

        assertEquals(1, service.threeWayCall(1L, 9L));
        assertEquals("4", agent.getCallStatus());
        assertEquals("三方通话加入座席[9]", agent.getRemark());
    }

    @Test
    void afterWork_setsWrapUp()
    {
        AiCallAgentStatus agent = agent(1L);
        agent.setStatus("1");
        when(aiCallAgentStatusMapper.selectAiCallAgentStatusByAgentId(1L)).thenReturn(agent);
        when(aiCallAgentStatusMapper.updateAiCallAgentStatus(agent)).thenReturn(1);

        assertEquals(1, service.afterWork(1L));
        assertEquals("5", agent.getCallStatus());
        assertEquals("2", agent.getStatus());
    }

    @Test
    void afterWork_agentMissing_returnsZero()
    {
        assertEquals(0, service.afterWork(1L));
        verify(aiCallAgentStatusMapper, never()).updateAiCallAgentStatus(any());
    }

    // ---------------- 挂机 / 机器人 / IVR ----------------

    @Test
    void hangup_endsRecordAndResetsAgent()
    {
        AiCallAgentStatus agent = agent(1L);
        agent.setCallStatus("1");
        agent.setStatus("2");
        agent.setCurrentCallId(400L);
        AiCallRecord record = new AiCallRecord();
        record.setRecordId(400L);
        record.setStatus("0");
        record.setCallTime(new Date(System.currentTimeMillis() - 60_000L));
        when(aiCallAgentStatusMapper.selectAiCallAgentStatusByAgentId(1L)).thenReturn(agent);
        when(aiCallRecordService.selectAiCallRecordByRecordId(400L)).thenReturn(record);
        when(aiCallAgentStatusMapper.updateAiCallAgentStatus(agent)).thenReturn(1);

        assertEquals(1, service.hangup(1L));

        assertEquals("1", record.getStatus());
        assertNotNull(record.getEndTime());
        assertTrue(record.getDuration() >= 59);
        assertEquals("0", agent.getCallStatus());
        assertEquals("1", agent.getStatus());
        verify(aiCallAgentStatusMapper).clearCurrentCall(1L);
        verify(callEventPublisher).publishToUser(eq(100L), eq("CALL_END"), any());
        verify(statusLogDispatcher).log(eq(1L), eq(100L), eq("HANGUP"),
                any(), any(), any(), any(), eq(400L), any(Date.class));
    }

    @Test
    void hangup_noCurrentCall_skipsRecord()
    {
        AiCallAgentStatus agent = agent(1L);
        when(aiCallAgentStatusMapper.selectAiCallAgentStatusByAgentId(1L)).thenReturn(agent);
        when(aiCallAgentStatusMapper.updateAiCallAgentStatus(agent)).thenReturn(1);

        assertEquals(1, service.hangup(1L));

        verify(aiCallRecordService, never()).selectAiCallRecordByRecordId(anyLong());
        verify(aiCallAgentStatusMapper).clearCurrentCall(1L);
    }

    @Test
    void robotTakeover_appendsRemarkAndReleasesAgent()
    {
        AiCallAgentStatus agent = agent(1L);
        agent.setCurrentCallId(401L);
        AiCallRecord record = new AiCallRecord();
        record.setRecordId(401L);
        record.setRemark("已有备注");
        when(aiCallAgentStatusMapper.selectAiCallAgentStatusByAgentId(1L)).thenReturn(agent);
        when(aiCallRecordService.selectAiCallRecordByRecordId(401L)).thenReturn(record);
        when(aiCallAgentStatusMapper.updateAiCallAgentStatus(agent)).thenReturn(1);

        assertEquals(1, service.robotTakeover(1L));

        assertEquals("已有备注；座席[1]已转交机器人接管", record.getRemark());
        assertEquals("0", agent.getCallStatus());
        assertEquals("1", agent.getStatus());
        verify(aiCallRecordService).updateAiCallRecord(record);
        verify(aiCallAgentStatusMapper).clearCurrentCall(1L);
        verify(statusLogDispatcher).log(eq(1L), eq(100L), eq("ROBOT_TAKEOVER"),
                any(), any(), any(), any(), eq(401L), any(Date.class));
    }

    @Test
    void robotTakeover_noCurrentCall_returnsZero()
    {
        AiCallAgentStatus agent = agent(1L);
        when(aiCallAgentStatusMapper.selectAiCallAgentStatusByAgentId(1L)).thenReturn(agent);

        assertEquals(0, service.robotTakeover(1L));
        verifyNoInteractions(aiCallRecordService);
    }

    @Test
    void ivrTransfer_appendsRemarkAndClosesRecord()
    {
        AiCallAgentStatus agent = agent(1L);
        agent.setCurrentCallId(402L);
        AiCallRecord record = new AiCallRecord();
        record.setRecordId(402L);
        record.setRemark("旧备注");
        record.setCallTime(new Date(System.currentTimeMillis() - 30_000L));
        when(aiCallAgentStatusMapper.selectAiCallAgentStatusByAgentId(1L)).thenReturn(agent);
        when(aiCallRecordService.selectAiCallRecordByRecordId(402L)).thenReturn(record);
        when(aiCallAgentStatusMapper.updateAiCallAgentStatus(agent)).thenReturn(1);

        assertEquals(1, service.ivrTransfer(1L, "ivr-3"));

        assertEquals("2", record.getStatus());
        assertEquals("旧备注；转IVR节点[ivr-3]", record.getRemark());
        assertNotNull(record.getEndTime());
        assertTrue(record.getDuration() >= 29);
        assertEquals("0", agent.getCallStatus());
        verify(aiCallAgentStatusMapper).clearCurrentCall(1L);
    }

    // ---------------- 用户-工号绑定同步 ----------------

    @Test
    void syncAgentFromUser_nullUserId_noop()
    {
        service.syncAgentFromUser(null, 1L, "张三", "8001", "0", "admin");
        verifyNoInteractions(aiCallAgentStatusMapper);
    }

    @Test
    void syncAgentFromUser_noAgentButOldBinding_releasesOld()
    {
        AiCallAgentStatus old = agent(5L);
        when(aiCallAgentStatusMapper.selectAiCallAgentStatusByUserId(100L)).thenReturn(old);

        service.syncAgentFromUser(100L, null, null, null, null, "admin");

        verify(aiCallAgentStatusMapper).releaseUserIdByAgentId(5L);
        verify(aiCallAgentStatusMapper, never()).updateAiCallAgentStatus(any());
    }

    @Test
    void syncAgentFromUser_noAgentNoOldBinding_noop()
    {
        service.syncAgentFromUser(100L, null, null, null, null, "admin");
        verify(aiCallAgentStatusMapper, never()).releaseUserIdByAgentId(anyLong());
        verify(aiCallAgentStatusMapper, never()).updateAiCallAgentStatus(any());
    }

    @Test
    void syncAgentFromUser_sameAgent_updatesConfigOnly()
    {
        AiCallAgentStatus old = agent(1L);
        old.setStatus("1");
        old.setCallMode("0");
        when(aiCallAgentStatusMapper.selectAiCallAgentStatusByUserId(100L)).thenReturn(old);

        service.syncAgentFromUser(100L, 1L, "李四", "8002", null, "admin");

        assertEquals("李四", old.getAgentName());
        assertEquals("8002", old.getSipExtension());
        assertEquals("0", old.getCallMode());
        assertEquals("admin", old.getUpdateBy());
        assertEquals("1", old.getStatus());
        verify(aiCallAgentStatusMapper).updateAiCallAgentStatus(old);
        verify(aiCallAgentStatusMapper, never()).insertAiCallAgentStatus(any());
    }

    @Test
    void syncAgentFromUser_targetBoundToOtherUser_throws()
    {
        AiCallAgentStatus target = agent(2L);
        target.setUserId(999L);
        when(aiCallAgentStatusMapper.selectAiCallAgentStatusByAgentId(2L)).thenReturn(target);

        assertThrows(ServiceException.class,
                () -> service.syncAgentFromUser(100L, 2L, "张三", "8001", "0", "admin"));
        verify(aiCallAgentStatusMapper, never()).updateAiCallAgentStatus(any());
    }

    @Test
    void syncAgentFromUser_rebindExistingTarget_releasesOldAndUpdates()
    {
        AiCallAgentStatus old = agent(1L);
        AiCallAgentStatus target = agent(2L);
        target.setUserId(null);
        when(aiCallAgentStatusMapper.selectAiCallAgentStatusByUserId(100L)).thenReturn(old);
        when(aiCallAgentStatusMapper.selectAiCallAgentStatusByAgentId(2L)).thenReturn(target);

        service.syncAgentFromUser(100L, 2L, "张三", "8001", "1", "admin");

        verify(aiCallAgentStatusMapper).releaseUserIdByAgentId(1L);
        assertEquals(Long.valueOf(100L), target.getUserId());
        assertEquals("8001", target.getSipExtension());
        assertEquals("1", target.getCallMode());
        assertEquals("admin", target.getUpdateBy());
        verify(aiCallAgentStatusMapper).updateAiCallAgentStatus(target);
        verify(aiCallAgentStatusMapper, never()).insertAiCallAgentStatus(any());
    }

    @Test
    void syncAgentFromUser_newAgent_insertsDefaults()
    {
        service.syncAgentFromUser(100L, 3L, null, "8003", null, "admin");

        ArgumentCaptor<AiCallAgentStatus> captor = ArgumentCaptor.forClass(AiCallAgentStatus.class);
        verify(aiCallAgentStatusMapper).insertAiCallAgentStatus(captor.capture());
        AiCallAgentStatus created = captor.getValue();
        assertEquals(Long.valueOf(3L), created.getAgentId());
        assertEquals(Long.valueOf(100L), created.getUserId());
        assertEquals("坐席3", created.getAgentName());
        assertEquals("8003", created.getSipExtension());
        assertEquals("0", created.getCallMode());
        assertEquals("0", created.getStatus());
        assertEquals("0", created.getCallStatus());
        assertEquals("admin", created.getCreateBy());
        assertEquals("通过用户管理创建", created.getRemark());
    }

    @Test
    void releaseAgentByUserId_delegates()
    {
        service.releaseAgentByUserId(null);
        verifyNoInteractions(aiCallAgentStatusMapper);

        service.releaseAgentByUserId(100L);
        verify(aiCallAgentStatusMapper).releaseUserIdByUserId(100L);
    }

    // ---------------- 辅助 ----------------

    private AiCallAgentStatus agent(long agentId)
    {
        AiCallAgentStatus agent = new AiCallAgentStatus();
        agent.setAgentId(agentId);
        agent.setUserId(100L);
        agent.setAgentName("张三");
        agent.setStatus("0");
        agent.setCallStatus("0");
        return agent;
    }
}
