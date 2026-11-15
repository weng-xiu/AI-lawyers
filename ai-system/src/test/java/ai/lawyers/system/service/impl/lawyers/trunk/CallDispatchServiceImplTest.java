package ai.lawyers.system.service.impl.lawyers.trunk;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.test.util.ReflectionTestUtils;
import ai.lawyers.system.domain.lawyers.trunk.AiCallDialLog;
import ai.lawyers.system.domain.lawyers.trunk.AiCallTrunk;
import ai.lawyers.system.domain.lawyers.trunk.AiNumberSegment;
import ai.lawyers.system.domain.lawyers.trunk.DialRequest;
import ai.lawyers.system.domain.lawyers.trunk.DialResult;
import ai.lawyers.system.enums.CarrierEnum;
import ai.lawyers.system.enums.DialStatusEnum;
import ai.lawyers.system.enums.TrunkHealthEnum;
import ai.lawyers.system.mapper.lawyers.trunk.AiCallDialLogMapper;
import ai.lawyers.system.mapper.lawyers.trunk.AiCallTrunkMapper;
import ai.lawyers.system.service.lawyers.queue.CallEventDispatcher;
import ai.lawyers.system.service.lawyers.trunk.ICarrierRouteService;
import ai.lawyers.system.service.lawyers.trunk.ITrunkMonitorService;
import ai.lawyers.system.service.lawyers.trunk.gateway.CallGatewayFactory;
import ai.lawyers.system.service.lawyers.trunk.gateway.ICallGatewayAdapter;

/**
 * 呼叫调度服务单测。
 *
 * <p>覆盖号码校验、全局并发、运营商识别与选路、指定线路与去重、CPS 限速、线路并发占位、
 * 网关缺失/异常/失败后的故障切换、成功回写与指标、排队入队与队列满拒绝、
 * 挂断资源释放，以及呼叫事件（振铃/接通/挂断/失败）的日志落库与终态幂等。</p>
 *
 * @author ai-lawyers
 */
class CallDispatchServiceImplTest
{
    private static final String CALLEE = "13800138000";

    private CallDispatchServiceImpl service;

    @Mock private ICarrierRouteService carrierRouteService;
    @Mock private CallGatewayFactory gatewayFactory;
    @Mock private AiCallTrunkMapper trunkMapper;
    @Mock private AiCallDialLogMapper dialLogMapper;
    @Mock private CallEventDispatcher callEventDispatcher;
    @Mock private ITrunkMonitorService trunkMonitorService;
    @Mock private ClusterDispatchState clusterState;
    @Mock private ICallGatewayAdapter adapter1;
    @Mock private ICallGatewayAdapter adapter2;

    @BeforeEach
    void setUp()
    {
        MockitoAnnotations.openMocks(this);
        service = new CallDispatchServiceImpl();
        ReflectionTestUtils.setField(service, "carrierRouteService", carrierRouteService);
        ReflectionTestUtils.setField(service, "gatewayFactory", gatewayFactory);
        ReflectionTestUtils.setField(service, "trunkMapper", trunkMapper);
        ReflectionTestUtils.setField(service, "dialLogMapper", dialLogMapper);
        ReflectionTestUtils.setField(service, "callEventDispatcher", callEventDispatcher);
        ReflectionTestUtils.setField(service, "trunkMonitorService", trunkMonitorService);
        ReflectionTestUtils.setField(service, "clusterState", clusterState);
        ReflectionTestUtils.setField(service, "maxGlobalConcurrent", 200);
        ReflectionTestUtils.setField(service, "queueCapacity", 2000);
        ReflectionTestUtils.setField(service, "queueTimeoutMs", 60_000L);
        ReflectionTestUtils.setField(service, "scanIntervalMs", 10L);
        ReflectionTestUtils.setField(service, "queueEnabled", false);
        service.init();
    }

    // ---------------- 号码校验与并发上限 ----------------

    @Test
    void dial_invalidNumber_rejected()
    {
        DialResult result = service.dial(request("abc"));

        assertEquals("INVALID_NUMBER", result.getErrorCode());
        assertFalse(result.isSuccess());
        verifyNoInteractions(carrierRouteService, gatewayFactory);
    }

    @Test
    void dial_globalFull_rejected()
    {
        ReflectionTestUtils.setField(service, "maxGlobalConcurrent", 0);

        DialResult result = service.dial(request(CALLEE));

        assertEquals("NO_AVAILABLE_TRUNK", result.getErrorCode());
        assertTrue(result.getMessage().contains("全局并发上限"));
        verifyNoInteractions(carrierRouteService);
    }

    @Test
    void dial_noCandidates_raisesAlarm()
    {
        when(carrierRouteService.recognizeSegment(CALLEE)).thenReturn(null);
        when(carrierRouteService.selectCandidateTrunks(CarrierEnum.UNKNOWN.getCode(), null))
                .thenReturn(Collections.emptyList());

        DialResult result = service.dial(request(CALLEE));

        assertEquals("NO_AVAILABLE_TRUNK", result.getErrorCode());
        verify(trunkMonitorService).raiseNoTrunkAlarm(CarrierEnum.UNKNOWN.getCode());
    }

    // ---------------- 选路与下发 ----------------

    @Test
    void dial_success_recordsLogAndHoldsTrunk()
    {
        AiCallTrunk trunk = trunk(1L, "T1", CarrierEnum.CHINA_MOBILE.getCode());
        AiNumberSegment segment = segment();
        DialRequest request = request("+86 138-0013-8000");
        request.setCallerNumber("4008001234");
        request.setRecordId(77L);
        request.setAgentId(9L);
        request.setCreateBy("admin");
        request.setRemark("回访");

        when(carrierRouteService.recognizeSegment("+86 138-0013-8000")).thenReturn(segment);
        when(carrierRouteService.selectCandidateTrunks(CarrierEnum.CHINA_MOBILE.getCode(), null))
                .thenReturn(Collections.singletonList(trunk));
        when(trunkMapper.tryAcquireConcurrent(1L)).thenReturn(1);
        when(gatewayFactory.get(trunk)).thenReturn(adapter1);
        when(adapter1.originate(trunk, request)).thenReturn(DialResult.ok("uuid-1"));
        doAnswer(invocation -> {
            AiCallDialLog inserted = invocation.getArgument(0);
            inserted.setLogId(500L);
            return 1;
        }).when(dialLogMapper).insertAiCallDialLog(any());

        DialResult result = service.dial(request);

        assertTrue(result.isSuccess());
        assertEquals("uuid-1", result.getCallUuid());
        assertEquals(Long.valueOf(1L), result.getTrunkId());
        assertEquals("T1", result.getTrunkCode());
        assertEquals(CarrierEnum.CHINA_MOBILE.getCode(), result.getTrunkCarrier());
        assertEquals(CarrierEnum.CHINA_MOBILE.getCode(), result.getCalleeCarrier());
        assertEquals(0, result.getFailoverCount());
        assertEquals("T1", result.getFailoverTrunks());
        assertEquals("CARRIER_MATCH", result.getRouteStrategy());
        assertEquals(Long.valueOf(500L), result.getLogId());
        assertEquals(0L, result.getQueueWaitMs());

        ArgumentCaptor<AiCallDialLog> captor = ArgumentCaptor.forClass(AiCallDialLog.class);
        verify(dialLogMapper).insertAiCallDialLog(captor.capture());
        AiCallDialLog log = captor.getValue();
        // 下发成功后服务会把临时占位号回写为网关返回的真实 callUuid
        assertEquals("uuid-1", log.getCallUuid());
        assertEquals(CALLEE, log.getCalleeNumber());
        assertEquals("4008001234", log.getCallerNumber());
        assertEquals(CarrierEnum.CHINA_MOBILE.getCode(), log.getCalleeCarrier());
        assertEquals("广东", log.getCalleeProvince());
        assertEquals("深圳", log.getCalleeCity());
        assertEquals("SIP", log.getLineType());
        assertEquals(DialStatusEnum.DIALING.getCode(), log.getDialStatus());
        assertEquals("0", log.getIsConnected());
        assertEquals("admin", log.getCreateBy());
        assertEquals("回访", log.getRemark());
        assertEquals(Long.valueOf(77L), log.getRecordId());
        assertEquals(Long.valueOf(9L), log.getAgentId());

        verify(dialLogMapper).updateCallUuid(500L, "uuid-1");
        verify(trunkMapper, never()).releaseConcurrent(anyLong());
    }

    @Test
    void dial_assignTrunkCode_usesAssignedAndDedups()
    {
        AiCallTrunk assigned = trunk(1L, "T1", CarrierEnum.CHINA_MOBILE.getCode());
        AiCallTrunk other = trunk(2L, "T2", CarrierEnum.CHINA_MOBILE.getCode());
        DialRequest request = request(CALLEE);
        request.setAssignTrunkCode("T1");

        when(carrierRouteService.recognizeSegment(CALLEE)).thenReturn(segment());
        when(carrierRouteService.getTrunkByCode("T1")).thenReturn(assigned);
        when(carrierRouteService.selectCandidateTrunks(CarrierEnum.CHINA_MOBILE.getCode(), null))
                .thenReturn(Arrays.asList(assigned, other));
        when(trunkMapper.tryAcquireConcurrent(1L)).thenReturn(1);
        when(gatewayFactory.get(assigned)).thenReturn(adapter1);
        when(adapter1.originate(assigned, request)).thenReturn(DialResult.ok("uuid-2"));

        DialResult result = service.dial(request);

        assertTrue(result.isSuccess());
        assertEquals(Long.valueOf(1L), result.getTrunkId());
        assertEquals("ASSIGN_TRUNK", result.getRouteStrategy());
        ArgumentCaptor<AiCallDialLog> captor = ArgumentCaptor.forClass(AiCallDialLog.class);
        verify(dialLogMapper).insertAiCallDialLog(captor.capture());
        assertEquals("T1", captor.getValue().getFailoverTrunks());
    }

    @Test
    void dial_assignedTrunkDisabled_fallsBackToAuto()
    {
        AiCallTrunk assigned = trunk(1L, "T1", CarrierEnum.CHINA_MOBILE.getCode());
        assigned.setEnableFlag("0");
        AiCallTrunk other = trunk(2L, "T2", CarrierEnum.CHINA_MOBILE.getCode());
        DialRequest request = request(CALLEE);
        request.setAssignTrunkCode("T1");

        when(carrierRouteService.recognizeSegment(CALLEE)).thenReturn(segment());
        when(carrierRouteService.getTrunkByCode("T1")).thenReturn(assigned);
        when(carrierRouteService.selectCandidateTrunks(CarrierEnum.CHINA_MOBILE.getCode(), null))
                .thenReturn(Collections.singletonList(other));
        when(trunkMapper.tryAcquireConcurrent(2L)).thenReturn(1);
        when(gatewayFactory.get(other)).thenReturn(adapter2);
        when(adapter2.originate(other, request)).thenReturn(DialResult.ok("uuid-3"));

        DialResult result = service.dial(request);

        assertTrue(result.isSuccess());
        assertEquals(Long.valueOf(2L), result.getTrunkId());
    }

    @Test
    void dial_cpsLimited_returnsCpsError()
    {
        AiCallTrunk trunk = trunk(1L, "T1", CarrierEnum.CHINA_MOBILE.getCode());
        trunk.setCpsLimit(1);
        when(clusterState.isClusterEnabled()).thenReturn(true);
        when(clusterState.getGlobalConcurrent()).thenReturn(0);
        when(clusterState.acquireCps(1L, 1)).thenReturn(false);
        when(carrierRouteService.recognizeSegment(CALLEE)).thenReturn(segment());
        when(carrierRouteService.selectCandidateTrunks(CarrierEnum.CHINA_MOBILE.getCode(), null))
                .thenReturn(Collections.singletonList(trunk));

        DialResult result = service.dial(request(CALLEE));

        assertEquals("CPS_LIMIT", result.getErrorCode());
        verify(trunkMapper, never()).tryAcquireConcurrent(anyLong());
        verifyNoInteractions(gatewayFactory);
    }

    @Test
    void dial_trunkBusy_failoverToNextSuccess()
    {
        AiCallTrunk t1 = trunk(1L, "T1", CarrierEnum.CHINA_MOBILE.getCode());
        AiCallTrunk t2 = trunk(2L, "T2", CarrierEnum.CHINA_MOBILE.getCode());
        DialRequest request = request(CALLEE);
        when(carrierRouteService.recognizeSegment(CALLEE)).thenReturn(segment());
        when(carrierRouteService.selectCandidateTrunks(CarrierEnum.CHINA_MOBILE.getCode(), null))
                .thenReturn(Arrays.asList(t1, t2));
        when(trunkMapper.tryAcquireConcurrent(1L)).thenReturn(0);
        when(trunkMapper.tryAcquireConcurrent(2L)).thenReturn(1);
        when(gatewayFactory.get(t2)).thenReturn(adapter2);
        when(adapter2.originate(t2, request)).thenReturn(DialResult.ok("uuid-4"));

        DialResult result = service.dial(request);

        assertTrue(result.isSuccess());
        assertEquals(Long.valueOf(2L), result.getTrunkId());
        assertEquals(1, result.getFailoverCount());
        assertEquals("T1>T2", result.getFailoverTrunks());
        assertEquals("FAILOVER", result.getRouteStrategy());
        verify(trunkMapper, never()).releaseConcurrent(1L);
    }

    @Test
    void dial_globalAcquireFail_releasesTrunk()
    {
        AiCallTrunk trunk = trunk(1L, "T1", CarrierEnum.CHINA_MOBILE.getCode());
        when(clusterState.isClusterEnabled()).thenReturn(true);
        when(clusterState.getGlobalConcurrent()).thenReturn(0);
        when(clusterState.acquireGlobalConcurrent(200)).thenReturn(0L);
        when(carrierRouteService.recognizeSegment(CALLEE)).thenReturn(segment());
        when(carrierRouteService.selectCandidateTrunks(CarrierEnum.CHINA_MOBILE.getCode(), null))
                .thenReturn(Collections.singletonList(trunk));
        when(trunkMapper.tryAcquireConcurrent(1L)).thenReturn(1);

        DialResult result = service.dial(request(CALLEE));

        assertEquals("NO_AVAILABLE_TRUNK", result.getErrorCode());
        verify(trunkMapper).releaseConcurrent(1L);
        verifyNoInteractions(gatewayFactory);
    }

    @Test
    void dial_noAdapter_marksFailedAndFailover()
    {
        AiCallTrunk t1 = trunk(1L, "T1", CarrierEnum.CHINA_MOBILE.getCode());
        AiCallTrunk t2 = trunk(2L, "T2", CarrierEnum.CHINA_MOBILE.getCode());
        DialRequest request = request(CALLEE);
        when(carrierRouteService.recognizeSegment(CALLEE)).thenReturn(segment());
        when(carrierRouteService.selectCandidateTrunks(CarrierEnum.CHINA_MOBILE.getCode(), null))
                .thenReturn(Arrays.asList(t1, t2));
        when(trunkMapper.tryAcquireConcurrent(1L)).thenReturn(1);
        when(trunkMapper.tryAcquireConcurrent(2L)).thenReturn(1);
        when(gatewayFactory.get(t1)).thenReturn(null);
        when(gatewayFactory.get(t2)).thenReturn(adapter2);
        when(adapter2.originate(t2, request)).thenReturn(DialResult.ok("uuid-5"));

        DialResult result = service.dial(request);

        assertTrue(result.isSuccess());
        assertEquals(1, result.getFailoverCount());
        ArgumentCaptor<AiCallDialLog> captor = ArgumentCaptor.forClass(AiCallDialLog.class);
        verify(dialLogMapper, times(2)).insertAiCallDialLog(captor.capture());
        ArgumentCaptor<AiCallDialLog> updateCaptor = ArgumentCaptor.forClass(AiCallDialLog.class);
        verify(dialLogMapper).updateAiCallDialLog(updateCaptor.capture());
        assertEquals(DialStatusEnum.FAILED.getCode(), updateCaptor.getValue().getDialStatus());
        assertTrue(updateCaptor.getValue().getFailReason().contains("NO_ADAPTER"));
        assertNotNull(updateCaptor.getValue().getHangupTime());
    }

    @Test
    void dial_gatewayThrows_failoverToNext()
    {
        AiCallTrunk t1 = trunk(1L, "T1", CarrierEnum.CHINA_MOBILE.getCode());
        AiCallTrunk t2 = trunk(2L, "T2", CarrierEnum.CHINA_MOBILE.getCode());
        DialRequest request = request(CALLEE);
        when(carrierRouteService.recognizeSegment(CALLEE)).thenReturn(segment());
        when(carrierRouteService.selectCandidateTrunks(CarrierEnum.CHINA_MOBILE.getCode(), null))
                .thenReturn(Arrays.asList(t1, t2));
        when(trunkMapper.tryAcquireConcurrent(1L)).thenReturn(1);
        when(trunkMapper.tryAcquireConcurrent(2L)).thenReturn(1);
        when(gatewayFactory.get(t1)).thenReturn(adapter1);
        when(gatewayFactory.get(t2)).thenReturn(adapter2);
        when(adapter1.originate(t1, request)).thenThrow(new RuntimeException("gw down"));
        when(adapter2.originate(t2, request)).thenReturn(DialResult.ok("uuid-6"));

        DialResult result = service.dial(request);

        assertTrue(result.isSuccess());
        assertEquals(1, result.getFailoverCount());
        verify(trunkMapper).markCallFail(1L);
        verify(trunkMonitorService).onCallFailed(t1);
    }

    @Test
    void dial_allFail_returnsLastFailureWithMeta()
    {
        AiCallTrunk trunk = trunk(1L, "T1", CarrierEnum.CHINA_MOBILE.getCode());
        DialRequest request = request(CALLEE);
        when(carrierRouteService.recognizeSegment(CALLEE)).thenReturn(segment());
        when(carrierRouteService.selectCandidateTrunks(CarrierEnum.CHINA_MOBILE.getCode(), null))
                .thenReturn(Collections.singletonList(trunk));
        when(trunkMapper.tryAcquireConcurrent(1L)).thenReturn(1);
        when(gatewayFactory.get(trunk)).thenReturn(adapter1);
        when(adapter1.originate(trunk, request)).thenReturn(DialResult.fail("BUSY", "线路忙"));

        DialResult result = service.dial(request);

        assertFalse(result.isSuccess());
        assertEquals("BUSY", result.getErrorCode());
        assertEquals(CarrierEnum.CHINA_MOBILE.getCode(), result.getCalleeCarrier());
        assertEquals(0, result.getFailoverCount());
        assertEquals("T1", result.getFailoverTrunks());
        verify(trunkMapper).releaseConcurrent(1L);
        verify(trunkMapper).markCallFail(1L);
    }

    @Test
    void dial_failoverDisabled_stopsAfterFirst()
    {
        AiCallTrunk t1 = trunk(1L, "T1", CarrierEnum.CHINA_MOBILE.getCode());
        AiCallTrunk t2 = trunk(2L, "T2", CarrierEnum.CHINA_MOBILE.getCode());
        DialRequest request = request(CALLEE);
        request.setAllowFailover(false);
        when(carrierRouteService.recognizeSegment(CALLEE)).thenReturn(segment());
        when(carrierRouteService.selectCandidateTrunks(CarrierEnum.CHINA_MOBILE.getCode(), null))
                .thenReturn(Arrays.asList(t1, t2));
        when(trunkMapper.tryAcquireConcurrent(1L)).thenReturn(1);
        when(gatewayFactory.get(t1)).thenReturn(adapter1);
        when(adapter1.originate(t1, request)).thenReturn(DialResult.fail("BUSY", "线路忙"));

        DialResult result = service.dial(request);

        assertFalse(result.isSuccess());
        assertEquals(0, result.getFailoverCount());
        verify(trunkMapper, never()).tryAcquireConcurrent(2L);
        verify(gatewayFactory, never()).get(t2);
    }

    @Test
    void dial_maxFailoverNull_defaultsToTwo()
    {
        AiCallTrunk t1 = trunk(1L, "T1", CarrierEnum.CHINA_MOBILE.getCode());
        AiCallTrunk t2 = trunk(2L, "T2", CarrierEnum.CHINA_MOBILE.getCode());
        AiCallTrunk t3 = trunk(3L, "T3", CarrierEnum.CHINA_MOBILE.getCode());
        DialRequest request = request(CALLEE);
        request.setMaxFailover(null);
        when(carrierRouteService.recognizeSegment(CALLEE)).thenReturn(segment());
        when(carrierRouteService.selectCandidateTrunks(CarrierEnum.CHINA_MOBILE.getCode(), null))
                .thenReturn(Arrays.asList(t1, t2, t3));
        when(trunkMapper.tryAcquireConcurrent(anyLong())).thenReturn(1);
        when(gatewayFactory.get(any())).thenReturn(adapter1);
        when(adapter1.originate(any(), any())).thenReturn(DialResult.fail("BUSY", "线路忙"));

        DialResult result = service.dial(request);

        assertFalse(result.isSuccess());
        assertEquals(2, result.getFailoverCount());
        assertEquals("T1>T2>T3", result.getFailoverTrunks());
        verify(dialLogMapper, times(3)).insertAiCallDialLog(any());
    }

    // ---------------- 排队 ----------------

    @Test
    void dialWithQueue_disabled_delegatesToDial()
    {
        DialResult result = service.dialWithQueue(request("abc"));
        assertEquals("INVALID_NUMBER", result.getErrorCode());
    }

    @Test
    void dialWithQueue_directSuccess_returnsDirect()
    {
        AiCallTrunk trunk = trunk(1L, "T1", CarrierEnum.CHINA_MOBILE.getCode());
        DialRequest request = request(CALLEE);
        when(carrierRouteService.recognizeSegment(CALLEE)).thenReturn(segment());
        when(carrierRouteService.selectCandidateTrunks(CarrierEnum.CHINA_MOBILE.getCode(), null))
                .thenReturn(Collections.singletonList(trunk));
        when(trunkMapper.tryAcquireConcurrent(1L)).thenReturn(1);
        when(gatewayFactory.get(trunk)).thenReturn(adapter1);
        when(adapter1.originate(trunk, request)).thenReturn(DialResult.ok("uuid-7"));
        ReflectionTestUtils.setField(service, "queueEnabled", true);

        DialResult result = service.dialWithQueue(request);

        assertTrue(result.isSuccess());
        assertEquals("uuid-7", result.getCallUuid());
        assertEquals(0, service.getQueueSize());
    }

    @Test
    void dialWithQueue_queueFull_rejected()
    {
        ReflectionTestUtils.setField(service, "queueEnabled", true);
        ReflectionTestUtils.setField(service, "maxGlobalConcurrent", 0);
        ReflectionTestUtils.setField(service, "queueCapacity", 0);

        DialResult result = service.dialWithQueue(request(CALLEE));

        assertEquals("QUEUE_FULL", result.getErrorCode());
        verify(trunkMonitorService).raiseQueueOverflowAlarm(0, 0);
        verifyNoInteractions(carrierRouteService);
    }

    @Test
    void dialWithQueue_noTrunk_enqueues()
    {
        ReflectionTestUtils.setField(service, "queueEnabled", true);
        DialRequest request = request(CALLEE);
        when(carrierRouteService.recognizeSegment(CALLEE)).thenReturn(segment());
        when(carrierRouteService.selectCandidateTrunks(CarrierEnum.CHINA_MOBILE.getCode(), null))
                .thenReturn(Collections.emptyList());

        DialResult result = service.dialWithQueue(request);

        assertTrue(result.isSuccess());
        assertNull(result.getCallUuid());
        assertEquals(DialStatusEnum.QUEUING.getCode(), result.getDialStatus());
        assertTrue(result.getMessage().contains("已进入呼叫队列"));
        assertEquals(1, service.getQueueSize());
    }

    @Test
    void dialWithQueue_nonQueueError_notQueued()
    {
        ReflectionTestUtils.setField(service, "queueEnabled", true);

        DialResult result = service.dialWithQueue(request("12"));

        assertEquals("INVALID_NUMBER", result.getErrorCode());
        assertEquals(0, service.getQueueSize());
    }

    @Test
    void dialWithQueue_clusterEnqueueFail_fallsBackToDial()
    {
        ReflectionTestUtils.setField(service, "queueEnabled", true);
        DialRequest request = request(CALLEE);
        when(clusterState.isClusterEnabled()).thenReturn(true);
        when(clusterState.getGlobalConcurrent()).thenReturn(0);
        when(clusterState.queueSize()).thenReturn(0L);
        when(clusterState.enqueue(request, 2000)).thenReturn(false);
        when(carrierRouteService.recognizeSegment(CALLEE)).thenReturn(segment());
        when(carrierRouteService.selectCandidateTrunks(CarrierEnum.CHINA_MOBILE.getCode(), null))
                .thenReturn(Collections.emptyList());

        DialResult result = service.dialWithQueue(request);

        assertEquals("NO_AVAILABLE_TRUNK", result.getErrorCode());
        verify(clusterState).enqueue(request, 2000);
        verify(trunkMonitorService, times(2)).raiseNoTrunkAlarm(CarrierEnum.CHINA_MOBILE.getCode());
    }

    // ---------------- 挂断 ----------------

    @Test
    void hangup_heldTrunk_releasesResources()
    {
        AiCallTrunk trunk = trunk(1L, "T1", CarrierEnum.CHINA_MOBILE.getCode());
        DialRequest request = request(CALLEE);
        when(carrierRouteService.recognizeSegment(CALLEE)).thenReturn(segment());
        when(carrierRouteService.selectCandidateTrunks(CarrierEnum.CHINA_MOBILE.getCode(), null))
                .thenReturn(Collections.singletonList(trunk));
        when(trunkMapper.tryAcquireConcurrent(1L)).thenReturn(1);
        when(gatewayFactory.get(trunk)).thenReturn(adapter1);
        when(adapter1.originate(trunk, request)).thenReturn(DialResult.ok("uuid-8"));
        when(trunkMapper.selectAiCallTrunkByTrunkId(1L)).thenReturn(trunk);
        when(adapter1.hangup(trunk, "uuid-8")).thenReturn(true);
        service.dial(request);

        assertTrue(service.hangup("uuid-8"));

        verify(adapter1).hangup(trunk, "uuid-8");
        verify(trunkMapper).releaseConcurrent(1L);
        assertEquals(0, service.getGlobalConcurrent());
    }

    @Test
    void hangup_notHeld_looksUpDialLog()
    {
        AiCallTrunk trunk = trunk(2L, "T2", CarrierEnum.CHINA_MOBILE.getCode());
        AiCallDialLog dialLog = dialLog(600L, 2L);
        when(dialLogMapper.selectByCallUuid("uuid-9")).thenReturn(dialLog);
        when(trunkMapper.selectAiCallTrunkByTrunkId(2L)).thenReturn(trunk);
        when(gatewayFactory.get(trunk)).thenReturn(adapter2);
        when(adapter2.hangup(trunk, "uuid-9")).thenReturn(false);

        assertFalse(service.hangup("uuid-9"));

        verify(trunkMapper).releaseConcurrent(2L);
    }

    @Test
    void hangup_unknownCall_returnsFalse()
    {
        assertFalse(service.hangup("uuid-x"));
        verify(trunkMapper, never()).releaseConcurrent(anyLong());
    }

    @Test
    void hangup_trunkMissing_returnsFalseWithoutRelease()
    {
        when(dialLogMapper.selectByCallUuid("uuid-10")).thenReturn(dialLog(601L, 3L));

        assertFalse(service.hangup("uuid-10"));

        verify(trunkMapper, never()).releaseConcurrent(anyLong());
        verifyNoInteractions(gatewayFactory);
    }

    // ---------------- 呼叫事件 ----------------

    @Test
    void onCallEvent_blankUuid_ignored()
    {
        service.onCallEvent(null, "RINGING", Collections.emptyMap());
        service.onCallEvent("", "RINGING", Collections.emptyMap());
        verifyNoInteractions(dialLogMapper, callEventDispatcher);
    }

    @Test
    void onCallEvent_unknownUuid_ignored()
    {
        service.onCallEvent("uuid-x", "RINGING", Collections.emptyMap());
        verifyNoInteractions(callEventDispatcher);
    }

    @Test
    void onCallEvent_finalState_ignored()
    {
        AiCallDialLog dialLog = dialLog(602L, 1L);
        dialLog.setDialStatus(DialStatusEnum.HANGUP.getCode());
        when(dialLogMapper.selectByCallUuid("uuid-11")).thenReturn(dialLog);

        service.onCallEvent("uuid-11", "HANGUP", Collections.emptyMap());

        verifyNoInteractions(callEventDispatcher);
        verify(dialLogMapper, never()).updateAiCallDialLog(any());
    }

    @Test
    void onCallEvent_ringing_updatesAsync()
    {
        when(dialLogMapper.selectByCallUuid("uuid-12")).thenReturn(dialLog(603L, 1L));

        service.onCallEvent("uuid-12", "ringing", Collections.emptyMap());

        ArgumentCaptor<AiCallDialLog> captor = ArgumentCaptor.forClass(AiCallDialLog.class);
        verify(callEventDispatcher).updateDialLogAsync(captor.capture());
        assertEquals(Long.valueOf(603L), captor.getValue().getLogId());
        assertEquals(DialStatusEnum.RINGING.getCode(), captor.getValue().getDialStatus());
        assertNotNull(captor.getValue().getRingTime());
    }

    @Test
    void onCallEvent_answered_computesRingDuration()
    {
        AiCallDialLog dialLog = dialLog(604L, 1L);
        dialLog.setRingTime(new Date(System.currentTimeMillis() - 5_000L));
        when(dialLogMapper.selectByCallUuid("uuid-13")).thenReturn(dialLog);

        service.onCallEvent("uuid-13", "ANSWERED", Collections.emptyMap());

        ArgumentCaptor<AiCallDialLog> captor = ArgumentCaptor.forClass(AiCallDialLog.class);
        verify(callEventDispatcher).updateDialLogAsync(captor.capture());
        assertEquals(DialStatusEnum.ANSWERED.getCode(), captor.getValue().getDialStatus());
        assertEquals("1", captor.getValue().getIsConnected());
        assertNotNull(captor.getValue().getAnswerTime());
        assertTrue(captor.getValue().getRingDuration() >= 4);
    }

    @Test
    void onCallEvent_hangupConnected_recordsMetrics()
    {
        AiCallDialLog dialLog = dialLog(605L, 1L);
        dialLog.setDialTime(new Date(System.currentTimeMillis() - 120_000L));
        dialLog.setRingTime(new Date(System.currentTimeMillis() - 100_000L));
        dialLog.setAnswerTime(new Date(System.currentTimeMillis() - 90_000L));
        AiCallTrunk trunk = trunk(1L, "T1", CarrierEnum.CHINA_MOBILE.getCode());
        when(dialLogMapper.selectByCallUuid("uuid-14")).thenReturn(dialLog);
        when(trunkMapper.selectAiCallTrunkByTrunkId(1L)).thenReturn(trunk);

        Map<String, Object> params = new HashMap<>();
        params.put("talkDuration", "42");
        params.put("hangupCause", "NORMAL_CLEARING");
        params.put("sipCode", "200");
        params.put("mos", "4.2");
        params.put("packetLoss", "0.5");
        params.put("jitter", "10");
        params.put("rtt", "30");
        params.put("recordFile", "rec.wav");

        service.onCallEvent("uuid-14", "HANGUP", params);

        ArgumentCaptor<AiCallDialLog> captor = ArgumentCaptor.forClass(AiCallDialLog.class);
        verify(dialLogMapper).updateAiCallDialLog(captor.capture());
        AiCallDialLog update = captor.getValue();
        assertEquals(DialStatusEnum.HANGUP.getCode(), update.getDialStatus());
        assertEquals("1", update.getIsConnected());
        assertEquals(42, update.getTalkDuration());
        assertEquals(42, update.getBillDuration());
        assertNotNull(update.getHangupTime());
        assertTrue(update.getTotalDuration() >= 119);
        assertTrue(update.getRingDuration() >= 9);
        assertEquals("NORMAL_CLEARING", update.getHangupCause());
        assertEquals(Integer.valueOf(200), update.getSipCode());
        assertEquals(new BigDecimal("4.2"), update.getMos());
        assertEquals(new BigDecimal("0.5"), update.getPacketLoss());
        assertEquals(Integer.valueOf(10), update.getJitter());
        assertEquals(Integer.valueOf(30), update.getRtt());
        assertEquals("rec.wav", update.getRecordFile());

        verify(trunkMapper).markCallSuccess(1L, 42);
        verify(trunkMonitorService).onCallSuccess(trunk);
        verify(trunkMapper).releaseConcurrent(1L);
    }

    @Test
    void onCallEvent_failedNotConnected_usesParamStatus()
    {
        AiCallDialLog dialLog = dialLog(606L, 2L);
        dialLog.setDialTime(new Date(System.currentTimeMillis() - 30_000L));
        AiCallTrunk trunk = trunk(2L, "T2", CarrierEnum.CHINA_MOBILE.getCode());
        when(dialLogMapper.selectByCallUuid("uuid-15")).thenReturn(dialLog);
        when(trunkMapper.selectAiCallTrunkByTrunkId(2L)).thenReturn(trunk);

        Map<String, Object> params = new HashMap<>();
        params.put("dialStatus", DialStatusEnum.REJECTED.getCode());
        params.put("failReason", "用户拒接");

        service.onCallEvent("uuid-15", "FAILED", params);

        ArgumentCaptor<AiCallDialLog> captor = ArgumentCaptor.forClass(AiCallDialLog.class);
        verify(dialLogMapper).updateAiCallDialLog(captor.capture());
        AiCallDialLog update = captor.getValue();
        assertEquals(DialStatusEnum.REJECTED.getCode(), update.getDialStatus());
        assertEquals("0", update.getIsConnected());
        assertEquals(0, update.getTalkDuration());
        assertEquals("用户拒接", update.getFailReason());
        assertTrue(update.getTotalDuration() >= 29);

        verify(trunkMapper).markCallFail(2L);
        verify(trunkMonitorService).onCallFailed(trunk);
        verify(trunkMapper, never()).markCallSuccess(anyLong(), anyInt());
    }

    @Test
    void onCallEvent_hangupWithoutTrunk_onlyReleasesCall()
    {
        when(dialLogMapper.selectByCallUuid("uuid-16")).thenReturn(dialLog(607L, null));

        service.onCallEvent("uuid-16", "HANGUP", Collections.emptyMap());

        verify(trunkMapper, never()).releaseConcurrent(anyLong());
        verify(trunkMapper, never()).markCallSuccess(anyLong(), anyInt());
        verify(trunkMapper, never()).markCallFail(anyLong());
        verify(trunkMapper, never()).selectAiCallTrunkByTrunkId(anyLong());
    }

    @Test
    void onCallEvent_unknownEventType_ignored()
    {
        when(dialLogMapper.selectByCallUuid("uuid-17")).thenReturn(dialLog(608L, 1L));

        service.onCallEvent("uuid-17", "MEDIA", Collections.emptyMap());

        verifyNoInteractions(callEventDispatcher);
        verify(dialLogMapper, never()).updateAiCallDialLog(any());
    }

    // ---------------- 并发与队列读数 ----------------

    @Test
    void queueAndConcurrent_clusterReadsFromState()
    {
        when(clusterState.isClusterEnabled()).thenReturn(true);
        when(clusterState.queueSize()).thenReturn(7L);
        when(clusterState.getGlobalConcurrent()).thenReturn(3);

        assertEquals(7, service.getQueueSize());
        assertEquals(3, service.getGlobalConcurrent());
    }

    @Test
    void queueAndConcurrent_localReadsFromMemory()
    {
        assertEquals(0, service.getQueueSize());
        assertEquals(0, service.getGlobalConcurrent());
    }

    // ---------------- 辅助 ----------------

    private DialRequest request(String callee)
    {
        DialRequest request = new DialRequest();
        request.setCalleeNumber(callee);
        return request;
    }

    private AiCallTrunk trunk(long trunkId, String code, String carrier)
    {
        AiCallTrunk trunk = new AiCallTrunk();
        trunk.setTrunkId(trunkId);
        trunk.setTrunkCode(code);
        trunk.setCarrier(carrier);
        trunk.setLineType("SIP");
        trunk.setVendor("mock");
        trunk.setEnableFlag("1");
        trunk.setHealthStatus(TrunkHealthEnum.NORMAL.getCode());
        return trunk;
    }

    private AiNumberSegment segment()
    {
        AiNumberSegment segment = new AiNumberSegment();
        segment.setCarrier(CarrierEnum.CHINA_MOBILE.getCode());
        segment.setProvince("广东");
        segment.setCity("深圳");
        return segment;
    }

    private AiCallDialLog dialLog(long logId, Long trunkId)
    {
        AiCallDialLog dialLog = new AiCallDialLog();
        dialLog.setLogId(logId);
        dialLog.setTrunkId(trunkId);
        dialLog.setDialStatus(DialStatusEnum.DIALING.getCode());
        dialLog.setIsConnected("0");
        dialLog.setDialTime(new Date());
        return dialLog;
    }
}
