package ai.lawyers.system.service.impl.lawyers.trunk;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.test.util.ReflectionTestUtils;
import ai.lawyers.system.domain.lawyers.trunk.AiCallTrunk;
import ai.lawyers.system.domain.lawyers.trunk.AiTrunkAlarm;
import ai.lawyers.system.domain.lawyers.trunk.AiTrunkMetric;
import ai.lawyers.system.enums.CarrierEnum;
import ai.lawyers.system.enums.TrunkHealthEnum;
import ai.lawyers.system.mapper.lawyers.trunk.AiCallDialLogMapper;
import ai.lawyers.system.mapper.lawyers.trunk.AiCallTrunkMapper;
import ai.lawyers.system.mapper.lawyers.trunk.AiTrunkAlarmMapper;
import ai.lawyers.system.mapper.lawyers.trunk.AiTrunkMetricMapper;
import ai.lawyers.system.service.lawyers.trunk.gateway.CallGatewayFactory;
import ai.lawyers.system.service.lawyers.trunk.gateway.GatewayHealth;
import ai.lawyers.system.service.lawyers.trunk.gateway.ICallGatewayAdapter;

/**
 * 线路监控服务单测。
 *
 * <p>覆盖健康探测与熔断（失败计数、冷却期恢复）、呼叫成功/失败的熔断联动、
 * 分钟级指标聚合与质量告警（并发占比、接通率、MOS）、告警抑制与恢复、
 * 无可用线路/队列溢出/流式积压等系统告警、告警处理，以及实时看板数据组装。</p>
 *
 * @author ai-lawyers
 */
class TrunkMonitorServiceImplTest
{
    private TrunkMonitorServiceImpl service;

    @Mock private AiCallTrunkMapper trunkMapper;
    @Mock private AiCallDialLogMapper dialLogMapper;
    @Mock private AiTrunkMetricMapper metricMapper;
    @Mock private AiTrunkAlarmMapper alarmMapper;
    @Mock private CallGatewayFactory gatewayFactory;
    @Mock private ICallGatewayAdapter adapter;

    @BeforeEach
    void setUp()
    {
        MockitoAnnotations.openMocks(this);
        service = new TrunkMonitorServiceImpl();
        ReflectionTestUtils.setField(service, "trunkMapper", trunkMapper);
        ReflectionTestUtils.setField(service, "dialLogMapper", dialLogMapper);
        ReflectionTestUtils.setField(service, "metricMapper", metricMapper);
        ReflectionTestUtils.setField(service, "alarmMapper", alarmMapper);
        ReflectionTestUtils.setField(service, "gatewayFactory", gatewayFactory);
        ReflectionTestUtils.setField(service, "circuitCooldownSeconds", 120);
        ReflectionTestUtils.setField(service, "alarmSuppressMinutes", 10);
        ReflectionTestUtils.setField(service, "minSampleForAlarm", 20);
        ReflectionTestUtils.setField(service, "concurrentAlarmThreshold", 90);
        ReflectionTestUtils.setField(service, "mosThreshold", 3.0);
    }

    // ---------------- 健康探测 ----------------

    @Test
    void healthCheckAll_noTrunks_returns()
    {
        when(trunkMapper.selectAllEnabledTrunks()).thenReturn(Collections.emptyList());

        service.healthCheckAll();

        verifyNoInteractions(gatewayFactory);
    }

    @Test
    void healthCheckAll_singleFailure_isolated()
    {
        AiCallTrunk t1 = trunk(1L, TrunkHealthEnum.NORMAL.getCode());
        AiCallTrunk t2 = trunk(2L, TrunkHealthEnum.NORMAL.getCode());
        when(trunkMapper.selectAllEnabledTrunks()).thenReturn(Arrays.asList(t1, t2));
        when(gatewayFactory.get(t1)).thenThrow(new RuntimeException("factory down"));
        when(gatewayFactory.get(t2)).thenReturn(adapter);
        when(adapter.checkHealth(t2)).thenReturn(GatewayHealth.up(100));

        service.healthCheckAll();

        verify(trunkMapper).updateHealthStatus(2L, TrunkHealthEnum.NORMAL.getCode(), 0);
    }

    @Test
    void healthCheck_circuitOpen_skips()
    {
        AiCallTrunk trunk = trunk(1L, TrunkHealthEnum.CIRCUIT_OPEN.getCode());

        assertFalse(service.healthCheck(trunk));
        verifyNoInteractions(gatewayFactory);
    }

    @Test
    void healthCheck_noAdapter_marksFault()
    {
        AiCallTrunk trunk = trunk(1L, TrunkHealthEnum.NORMAL.getCode());
        trunk.setFailCount(0);
        when(gatewayFactory.get(trunk)).thenReturn(null);

        assertFalse(service.healthCheck(trunk));

        verify(trunkMapper).updateHealthStatus(1L, TrunkHealthEnum.FAULT.getCode(), 1);
        ArgumentCaptor<AiTrunkAlarm> captor = ArgumentCaptor.forClass(AiTrunkAlarm.class);
        verify(alarmMapper).insertAiTrunkAlarm(captor.capture());
        assertEquals("TRUNK_DOWN", captor.getValue().getAlarmType());
        assertEquals("3", captor.getValue().getAlarmLevel());
        assertTrue(captor.getValue().getAlarmContent().contains("未找到网关适配器"));
    }

    @Test
    void healthCheck_unknownStatus_probesAndRecovers()
    {
        AiCallTrunk trunk = trunk(1L, TrunkHealthEnum.UNKNOWN.getCode());
        when(gatewayFactory.get(trunk)).thenReturn(adapter);
        when(adapter.checkHealth(trunk)).thenReturn(GatewayHealth.up(80));

        assertTrue(service.healthCheck(trunk));

        verify(trunkMapper).updateHealthStatus(1L, TrunkHealthEnum.NORMAL.getCode(), 0);
        verify(alarmMapper).recoverAlarms(1L, "TRUNK_DOWN");
    }

    @Test
    void healthCheck_degradedStaysDegraded()
    {
        AiCallTrunk trunk = trunk(1L, TrunkHealthEnum.DEGRADED.getCode());
        when(gatewayFactory.get(trunk)).thenReturn(adapter);
        when(adapter.checkHealth(trunk)).thenReturn(GatewayHealth.up(60));

        assertTrue(service.healthCheck(trunk));

        verify(trunkMapper).updateHealthStatus(1L, TrunkHealthEnum.DEGRADED.getCode(), 0);
        verify(alarmMapper, never()).recoverAlarms(anyLong(), anyString());
    }

    @Test
    void healthCheck_unreachable_raisesAlarm()
    {
        AiCallTrunk trunk = trunk(1L, TrunkHealthEnum.NORMAL.getCode());
        trunk.setFailCount(2);
        when(gatewayFactory.get(trunk)).thenReturn(adapter);
        when(adapter.checkHealth(trunk)).thenReturn(GatewayHealth.down("connect timeout"));

        assertFalse(service.healthCheck(trunk));

        verify(trunkMapper).updateHealthStatus(1L, TrunkHealthEnum.FAULT.getCode(), 3);
        ArgumentCaptor<AiTrunkAlarm> captor = ArgumentCaptor.forClass(AiTrunkAlarm.class);
        verify(alarmMapper).insertAiTrunkAlarm(captor.capture());
        assertTrue(captor.getValue().getAlarmContent().contains("connect timeout"));
        assertEquals("3", captor.getValue().getMetricValue());
        assertEquals("5", captor.getValue().getThresholdValue());
    }

    @Test
    void healthCheck_reachThreshold_opensCircuit()
    {
        AiCallTrunk trunk = trunk(1L, TrunkHealthEnum.NORMAL.getCode());
        trunk.setFailCount(4);
        when(gatewayFactory.get(trunk)).thenReturn(adapter);
        when(adapter.checkHealth(trunk)).thenReturn(GatewayHealth.down("timeout"));

        service.healthCheck(trunk);

        verify(trunkMapper).updateHealthStatus(1L, TrunkHealthEnum.CIRCUIT_OPEN.getCode(), 5);
        ArgumentCaptor<AiTrunkAlarm> captor = ArgumentCaptor.forClass(AiTrunkAlarm.class);
        verify(alarmMapper).insertAiTrunkAlarm(captor.capture());
        assertEquals("4", captor.getValue().getAlarmLevel());
        assertTrue(captor.getValue().getAlarmContent().contains("连续失败 5 次"));
        assertTrue(captor.getValue().getAlarmContent().contains("timeout"));
    }

    @Test
    void healthCheck_alarmSuppressed_notInserted()
    {
        AiCallTrunk trunk = trunk(1L, TrunkHealthEnum.NORMAL.getCode());
        when(gatewayFactory.get(trunk)).thenReturn(adapter);
        when(adapter.checkHealth(trunk)).thenReturn(GatewayHealth.down("down"));
        when(alarmMapper.selectActiveSameAlarm(eq(1L), eq("TRUNK_DOWN"), any(Date.class)))
                .thenReturn(new AiTrunkAlarm());

        service.healthCheck(trunk);

        verify(alarmMapper, never()).insertAiTrunkAlarm(any());
        verify(trunkMapper).updateHealthStatus(1L, TrunkHealthEnum.FAULT.getCode(), 1);
    }

    @Test
    void healthCheck_alarmWriteFailure_swallowed()
    {
        AiCallTrunk trunk = trunk(1L, TrunkHealthEnum.NORMAL.getCode());
        when(gatewayFactory.get(trunk)).thenReturn(adapter);
        when(adapter.checkHealth(trunk)).thenReturn(GatewayHealth.down("down"));
        when(alarmMapper.insertAiTrunkAlarm(any())).thenThrow(new RuntimeException("db down"));

        assertFalse(service.healthCheck(trunk));
    }

    // ---------------- 呼叫结果联动 ----------------

    @Test
    void onCallSuccess_trunkHealthy_doesNothing()
    {
        service.onCallSuccess(null);
        verifyNoInteractions(trunkMapper);

        service.onCallSuccess(trunk(1L, TrunkHealthEnum.NORMAL.getCode()));
        verify(trunkMapper, never()).updateHealthStatus(anyLong(), anyString(), anyInt());
    }

    @Test
    void onCallSuccess_trunkAbnormal_recovers()
    {
        service.onCallSuccess(trunk(1L, TrunkHealthEnum.FAULT.getCode()));

        verify(alarmMapper).recoverAlarms(1L, "TRUNK_DOWN");
        verify(alarmMapper).recoverAlarms(1L, "CIRCUIT_OPEN");
    }

    @Test
    void onCallFailed_belowThreshold_noAction()
    {
        AiCallTrunk trunk = trunk(1L, TrunkHealthEnum.NORMAL.getCode());
        AiCallTrunk latest = trunk(1L, TrunkHealthEnum.NORMAL.getCode());
        latest.setFailCount(3);
        when(trunkMapper.selectAiCallTrunkByTrunkId(1L)).thenReturn(latest);

        service.onCallFailed(trunk);

        verify(trunkMapper, never()).updateHealthStatus(anyLong(), anyString(), anyInt());
        verify(alarmMapper, never()).insertAiTrunkAlarm(any());
    }

    @Test
    void onCallFailed_reachThreshold_opensCircuit()
    {
        AiCallTrunk trunk = trunk(1L, TrunkHealthEnum.NORMAL.getCode());
        AiCallTrunk latest = trunk(1L, TrunkHealthEnum.NORMAL.getCode());
        latest.setFailCount(5);
        when(trunkMapper.selectAiCallTrunkByTrunkId(1L)).thenReturn(latest);

        service.onCallFailed(trunk);

        verify(trunkMapper).updateHealthStatus(1L, TrunkHealthEnum.CIRCUIT_OPEN.getCode(), 5);
        ArgumentCaptor<AiTrunkAlarm> captor = ArgumentCaptor.forClass(AiTrunkAlarm.class);
        verify(alarmMapper).insertAiTrunkAlarm(captor.capture());
        assertEquals("CIRCUIT_OPEN", captor.getValue().getAlarmType());
        assertTrue(captor.getValue().getAlarmTitle().contains("熔断"));
    }

    @Test
    void onCallFailed_alreadyOpen_skips()
    {
        AiCallTrunk trunk = trunk(1L, TrunkHealthEnum.CIRCUIT_OPEN.getCode());
        AiCallTrunk latest = trunk(1L, TrunkHealthEnum.CIRCUIT_OPEN.getCode());
        latest.setFailCount(9);
        when(trunkMapper.selectAiCallTrunkByTrunkId(1L)).thenReturn(latest);

        service.onCallFailed(trunk);

        verify(trunkMapper, never()).updateHealthStatus(anyLong(), anyString(), anyInt());
    }

    // ---------------- 熔断恢复 ----------------

    @Test
    void recoverCircuitBreakers_noTrunks_returns()
    {
        when(trunkMapper.selectAllEnabledTrunks()).thenReturn(null);

        service.recoverCircuitBreakers();

        verifyNoInteractions(gatewayFactory);
    }

    @Test
    void recoverCircuitBreakers_withinCooldown_skips()
    {
        AiCallTrunk trunk = trunk(1L, TrunkHealthEnum.CIRCUIT_OPEN.getCode());
        trunk.setCircuitOpenTime(new Date());
        when(trunkMapper.selectAllEnabledTrunks()).thenReturn(Collections.singletonList(trunk));

        service.recoverCircuitBreakers();

        verifyNoInteractions(gatewayFactory);
        verify(trunkMapper, never()).updateHealthStatus(anyLong(), anyString(), anyInt());
    }

    @Test
    void recoverCircuitBreakers_probeSuccess_closeCircuit()
    {
        AiCallTrunk trunk = trunk(1L, TrunkHealthEnum.CIRCUIT_OPEN.getCode());
        trunk.setCircuitOpenTime(new Date(System.currentTimeMillis() - 200_000L));
        when(trunkMapper.selectAllEnabledTrunks()).thenReturn(Collections.singletonList(trunk));
        when(gatewayFactory.get(trunk)).thenReturn(adapter);
        when(adapter.checkHealth(trunk)).thenReturn(GatewayHealth.up(30));

        service.recoverCircuitBreakers();

        verify(trunkMapper).updateHealthStatus(1L, TrunkHealthEnum.NORMAL.getCode(), 0);
        verify(alarmMapper).recoverAlarms(1L, "TRUNK_DOWN");
        verify(alarmMapper).recoverAlarms(1L, "CIRCUIT_OPEN");
    }

    @Test
    void recoverCircuitBreakers_probeFail_keepsOpen()
    {
        AiCallTrunk trunk = trunk(1L, TrunkHealthEnum.CIRCUIT_OPEN.getCode());
        trunk.setCircuitOpenTime(new Date(System.currentTimeMillis() - 200_000L));
        trunk.setFailCount(7);
        when(trunkMapper.selectAllEnabledTrunks()).thenReturn(Collections.singletonList(trunk));
        when(gatewayFactory.get(trunk)).thenReturn(adapter);
        when(adapter.checkHealth(trunk)).thenReturn(GatewayHealth.down("still down"));

        service.recoverCircuitBreakers();

        verify(trunkMapper).updateHealthStatus(1L, TrunkHealthEnum.CIRCUIT_OPEN.getCode(), 7);
        verify(alarmMapper, never()).recoverAlarms(anyLong(), anyString());
    }

    // ---------------- 指标聚合 ----------------

    @Test
    void collectMetrics_noRows_returns()
    {
        when(dialLogMapper.aggregateByTrunk(any(Date.class), any(Date.class)))
                .thenReturn(Collections.emptyList());

        service.collectMetrics();

        verify(metricMapper, never()).saveOrUpdate(any());
    }

    @Test
    void collectMetrics_invalidRow_skipped()
    {
        Map<String, Object> row = new HashMap<>();
        row.put("trunkId", "bad");
        when(dialLogMapper.aggregateByTrunk(any(Date.class), any(Date.class)))
                .thenReturn(Collections.singletonList(row));

        service.collectMetrics();

        verify(metricMapper, never()).saveOrUpdate(any());
    }

    @Test
    void collectMetrics_persistsMetricAndRefreshesQuality()
    {
        Map<String, Object> row = new HashMap<>();
        row.put("trunkId", 1L);
        row.put("trunkCode", "T1");
        row.put("carrier", CarrierEnum.CHINA_MOBILE.getCode());
        row.put("totalCalls", 10);
        row.put("connectedCalls", 8);
        row.put("failedCalls", 2);
        row.put("connectRate", "80.00");
        row.put("acd", 30);
        row.put("totalDuration", 300L);
        row.put("avgRingDuration", 5);
        row.put("avgMos", "4.10");
        row.put("avgPacketLoss", "0.20");
        AiCallTrunk trunk = trunk(1L, TrunkHealthEnum.NORMAL.getCode());
        trunk.setCurrentConcurrent(3);
        when(dialLogMapper.aggregateByTrunk(any(Date.class), any(Date.class)))
                .thenReturn(Collections.singletonList(row));
        when(trunkMapper.selectAiCallTrunkByTrunkId(1L)).thenReturn(trunk);

        service.collectMetrics();

        ArgumentCaptor<AiTrunkMetric> metricCaptor = ArgumentCaptor.forClass(AiTrunkMetric.class);
        verify(metricMapper).saveOrUpdate(metricCaptor.capture());
        AiTrunkMetric metric = metricCaptor.getValue();
        assertEquals(Long.valueOf(1L), metric.getTrunkId());
        assertEquals("T1", metric.getTrunkCode());
        assertEquals("1", metric.getStatDimension());
        assertEquals(10, metric.getTotalCalls());
        assertEquals(8, metric.getConnectedCalls());
        assertEquals(2, metric.getFailedCalls());
        assertEquals(new BigDecimal("80.00"), metric.getConnectRate());
        assertEquals(new BigDecimal("80.00"), metric.getAsr());
        assertEquals(Integer.valueOf(30), metric.getAcd());
        assertEquals(Long.valueOf(300L), metric.getTotalDuration());
        assertEquals(Integer.valueOf(5), metric.getAvgRingDuration());
        assertEquals(Integer.valueOf(3), metric.getMaxConcurrent());
        assertEquals(new BigDecimal("4.10"), metric.getAvgMos());
        assertEquals(new BigDecimal("0.20"), metric.getAvgPacketLoss());

        ArgumentCaptor<AiCallTrunk> trunkCaptor = ArgumentCaptor.forClass(AiCallTrunk.class);
        verify(trunkMapper).updateQualityMetric(trunkCaptor.capture());
        assertEquals(new BigDecimal("80.00"), trunkCaptor.getValue().getConnectRate());
        assertEquals(Long.valueOf(1L), trunkCaptor.getValue().getTrunkId());
        assertEquals(new BigDecimal("4.10"), trunkCaptor.getValue().getMos());
    }

    // ---------------- 质量告警 ----------------

    @Test
    void evaluateQualityAlarm_trunkFailureIsolated()
    {
        AiCallTrunk t1 = trunk(1L, TrunkHealthEnum.NORMAL.getCode());
        AiCallTrunk t2 = trunk(2L, TrunkHealthEnum.NORMAL.getCode());
        when(trunkMapper.selectAllEnabledTrunks()).thenReturn(Arrays.asList(t1, t2));
        when(dialLogMapper.aggregateOneTrunk(eq(1L), any(Date.class), any(Date.class)))
                .thenThrow(new RuntimeException("agg down"));
        when(dialLogMapper.aggregateOneTrunk(eq(2L), any(Date.class), any(Date.class)))
                .thenReturn(null);

        service.evaluateQualityAlarm();

        verify(dialLogMapper).aggregateOneTrunk(eq(2L), any(Date.class), any(Date.class));
    }

    @Test
    void evaluateQualityAlarm_concurrentFull_raises()
    {
        AiCallTrunk trunk = trunk(1L, TrunkHealthEnum.NORMAL.getCode());
        trunk.setMaxConcurrent(10);
        trunk.setCurrentConcurrent(9);
        when(trunkMapper.selectAllEnabledTrunks()).thenReturn(Collections.singletonList(trunk));
        when(dialLogMapper.aggregateOneTrunk(eq(1L), any(Date.class), any(Date.class)))
                .thenReturn(null);

        service.evaluateQualityAlarm();

        ArgumentCaptor<AiTrunkAlarm> captor = ArgumentCaptor.forClass(AiTrunkAlarm.class);
        verify(alarmMapper).insertAiTrunkAlarm(captor.capture());
        assertEquals("CONCURRENT_FULL", captor.getValue().getAlarmType());
        assertEquals("90%", captor.getValue().getMetricValue());
        assertEquals("90%", captor.getValue().getThresholdValue());
    }

    @Test
    void evaluateQualityAlarm_concurrentRecovered()
    {
        AiCallTrunk trunk = trunk(1L, TrunkHealthEnum.NORMAL.getCode());
        trunk.setMaxConcurrent(10);
        trunk.setCurrentConcurrent(5);
        when(trunkMapper.selectAllEnabledTrunks()).thenReturn(Collections.singletonList(trunk));
        when(dialLogMapper.aggregateOneTrunk(eq(1L), any(Date.class), any(Date.class)))
                .thenReturn(null);

        service.evaluateQualityAlarm();

        verify(alarmMapper).recoverAlarms(1L, "CONCURRENT_FULL");
        verify(alarmMapper, never()).insertAiTrunkAlarm(any());
    }

    @Test
    void evaluateQualityAlarm_sampleTooSmall_skipsRate()
    {
        AiCallTrunk trunk = trunk(1L, TrunkHealthEnum.NORMAL.getCode());
        Map<String, Object> stat = new HashMap<>();
        stat.put("totalCalls", 10);
        stat.put("connectRate", "10.00");
        when(trunkMapper.selectAllEnabledTrunks()).thenReturn(Collections.singletonList(trunk));
        when(dialLogMapper.aggregateOneTrunk(eq(1L), any(Date.class), any(Date.class)))
                .thenReturn(stat);

        service.evaluateQualityAlarm();

        verify(alarmMapper, never()).insertAiTrunkAlarm(any());
        verify(trunkMapper, never()).updateHealthStatus(anyLong(), anyString(), anyInt());
    }

    @Test
    void evaluateQualityAlarm_lowConnectRate_degrades()
    {
        AiCallTrunk trunk = trunk(1L, TrunkHealthEnum.NORMAL.getCode());
        Map<String, Object> stat = new HashMap<>();
        stat.put("totalCalls", 30);
        stat.put("connectRate", "50.00");
        stat.put("avgMos", "4.00");
        when(trunkMapper.selectAllEnabledTrunks()).thenReturn(Collections.singletonList(trunk));
        when(dialLogMapper.aggregateOneTrunk(eq(1L), any(Date.class), any(Date.class)))
                .thenReturn(stat);

        service.evaluateQualityAlarm();

        verify(trunkMapper).updateHealthStatus(1L, TrunkHealthEnum.DEGRADED.getCode(), 0);
        ArgumentCaptor<AiTrunkAlarm> captor = ArgumentCaptor.forClass(AiTrunkAlarm.class);
        verify(alarmMapper).insertAiTrunkAlarm(captor.capture());
        assertEquals("CONNECT_RATE_LOW", captor.getValue().getAlarmType());
        assertEquals("50.00%", captor.getValue().getMetricValue());
        verify(alarmMapper).recoverAlarms(1L, "MOS_LOW");
    }

    @Test
    void evaluateQualityAlarm_lowMos_raisesAndRecoversRate()
    {
        AiCallTrunk trunk = trunk(1L, TrunkHealthEnum.DEGRADED.getCode());
        Map<String, Object> stat = new HashMap<>();
        stat.put("totalCalls", 30);
        stat.put("connectRate", "90.00");
        stat.put("avgMos", "2.50");
        when(trunkMapper.selectAllEnabledTrunks()).thenReturn(Collections.singletonList(trunk));
        when(dialLogMapper.aggregateOneTrunk(eq(1L), any(Date.class), any(Date.class)))
                .thenReturn(stat);

        service.evaluateQualityAlarm();

        verify(alarmMapper).recoverAlarms(1L, "CONNECT_RATE_LOW");
        ArgumentCaptor<AiTrunkAlarm> captor = ArgumentCaptor.forClass(AiTrunkAlarm.class);
        verify(alarmMapper).insertAiTrunkAlarm(captor.capture());
        assertEquals("MOS_LOW", captor.getValue().getAlarmType());
        verify(trunkMapper).updateHealthStatus(1L, TrunkHealthEnum.NORMAL.getCode(), 0);
    }

    // ---------------- 系统级告警 ----------------

    @Test
    void raiseNoTrunkAlarm_fields()
    {
        service.raiseNoTrunkAlarm(CarrierEnum.CHINA_MOBILE.getCode());

        ArgumentCaptor<AiTrunkAlarm> captor = ArgumentCaptor.forClass(AiTrunkAlarm.class);
        verify(alarmMapper).insertAiTrunkAlarm(captor.capture());
        AiTrunkAlarm alarm = captor.getValue();
        assertEquals("NO_AVAILABLE_TRUNK", alarm.getAlarmType());
        assertEquals("4", alarm.getAlarmLevel());
        assertEquals(CarrierEnum.CHINA_MOBILE.getCode(), alarm.getCarrier());
        assertEquals("0", alarm.getAlarmStatus());
        assertTrue(alarm.getAlarmTitle().contains("无可用线路"));
        assertNull(alarm.getTrunkId());
    }

    @Test
    void raiseQueueOverflowAlarm_fields()
    {
        service.raiseQueueOverflowAlarm(2000, 2000);

        ArgumentCaptor<AiTrunkAlarm> captor = ArgumentCaptor.forClass(AiTrunkAlarm.class);
        verify(alarmMapper).insertAiTrunkAlarm(captor.capture());
        assertEquals("QUEUE_OVERFLOW", captor.getValue().getAlarmType());
        assertEquals("3", captor.getValue().getAlarmLevel());
        assertEquals("2000", captor.getValue().getMetricValue());
        assertEquals("2000", captor.getValue().getThresholdValue());
    }

    @Test
    void raiseStreamLagAlarm_fields()
    {
        service.raiseStreamLagAlarm("call-event", 120, 100);

        ArgumentCaptor<AiTrunkAlarm> captor = ArgumentCaptor.forClass(AiTrunkAlarm.class);
        verify(alarmMapper).insertAiTrunkAlarm(captor.capture());
        assertEquals("STREAM_LAG", captor.getValue().getAlarmType());
        assertEquals("3", captor.getValue().getAlarmLevel());
        assertEquals("120", captor.getValue().getMetricValue());
        assertTrue(captor.getValue().getAlarmContent().contains("call-event"));
    }

    @Test
    void raiseStreamDlqAlarm_fields()
    {
        service.raiseStreamDlqAlarm("call-event", 10, 5);

        ArgumentCaptor<AiTrunkAlarm> captor = ArgumentCaptor.forClass(AiTrunkAlarm.class);
        verify(alarmMapper).insertAiTrunkAlarm(captor.capture());
        assertEquals("STREAM_DLQ", captor.getValue().getAlarmType());
        assertEquals("2", captor.getValue().getAlarmLevel());
        assertEquals("10", captor.getValue().getMetricValue());
    }

    @Test
    void activeAlarms_delegates()
    {
        when(alarmMapper.selectActiveAlarms()).thenReturn(Collections.singletonList(new AiTrunkAlarm()));

        assertEquals(1, service.activeAlarms().size());
    }

    @Test
    void handleAlarm_recoversWhenDone()
    {
        service.handleAlarm(9L, "2", "admin", "已处理");

        ArgumentCaptor<AiTrunkAlarm> captor = ArgumentCaptor.forClass(AiTrunkAlarm.class);
        verify(alarmMapper).updateAiTrunkAlarm(captor.capture());
        AiTrunkAlarm alarm = captor.getValue();
        assertEquals(Long.valueOf(9L), alarm.getAlarmId());
        assertEquals("2", alarm.getAlarmStatus());
        assertEquals("admin", alarm.getHandleBy());
        assertEquals("已处理", alarm.getHandleRemark());
        assertNotNull(alarm.getRecoverTime());
        assertNotNull(alarm.getHandleTime());
    }

    @Test
    void handleAlarm_processing_keepsRecoverTimeNull()
    {
        service.handleAlarm(9L, "1", "admin", null);

        ArgumentCaptor<AiTrunkAlarm> captor = ArgumentCaptor.forClass(AiTrunkAlarm.class);
        verify(alarmMapper).updateAiTrunkAlarm(captor.capture());
        assertNull(captor.getValue().getRecoverTime());
        assertNull(captor.getValue().getHandleRemark());
    }

    // ---------------- 实时看板 ----------------

    @Test
    void realtimeTrunkStatus_mapsFields()
    {
        AiCallTrunk trunk = trunk(1L, TrunkHealthEnum.DEGRADED.getCode());
        trunk.setMaxConcurrent(10);
        trunk.setCurrentConcurrent(5);
        when(trunkMapper.selectAllEnabledTrunks()).thenReturn(Collections.singletonList(trunk));

        List<Map<String, Object>> rows = service.realtimeTrunkStatus();

        assertEquals(1, rows.size());
        Map<String, Object> row = rows.get(0);
        assertEquals(Long.valueOf(1L), row.get("trunkId"));
        assertEquals(5, row.get("currentConcurrent"));
        assertEquals(10, row.get("maxConcurrent"));
        assertEquals(50, row.get("concurrentUsage"));
        assertEquals("亚健康", row.get("healthName"));
    }

    @Test
    void realtimeTrunkStatus_emptyAndZeroMax()
    {
        when(trunkMapper.selectAllEnabledTrunks()).thenReturn(null);
        assertTrue(service.realtimeTrunkStatus().isEmpty());

        AiCallTrunk trunk = trunk(1L, TrunkHealthEnum.FAULT.getCode());
        trunk.setMaxConcurrent(0);
        when(trunkMapper.selectAllEnabledTrunks()).thenReturn(Collections.singletonList(trunk));
        assertEquals(0, service.realtimeTrunkStatus().get(0).get("concurrentUsage"));
    }

    @Test
    void realtimeCarrierStat_mergesOverviewAndCallStat()
    {
        Map<String, Object> overview = new HashMap<>();
        overview.put("carrier", CarrierEnum.CHINA_MOBILE.getCode());
        overview.put("maxConcurrent", 100);
        overview.put("trunkCount", 3);
        Map<String, Object> today = new HashMap<>();
        today.put("carrier", CarrierEnum.CHINA_MOBILE.getCode());
        today.put("totalCalls", 50);
        Map<String, Object> other = new HashMap<>();
        other.put("carrier", CarrierEnum.CHINA_UNICOM.getCode());
        other.put("totalCalls", 8);
        when(trunkMapper.selectCarrierOverview()).thenReturn(Collections.singletonList(overview));
        when(dialLogMapper.aggregateByCarrier(any(Date.class), any(Date.class)))
                .thenReturn(Arrays.asList(today, other));

        List<Map<String, Object>> rows = service.realtimeCarrierStat();

        assertEquals(2, rows.size());
        assertEquals(50, rows.get(0).get("todayTotalCalls"));
        assertEquals(100, rows.get(0).get("maxConcurrent"));
        assertEquals(CarrierEnum.CHINA_UNICOM.getCode(), rows.get(1).get("carrier"));
        assertEquals(8, rows.get(1).get("todayTotalCalls"));
    }

    @Test
    void todayOverview_aggregates()
    {
        AiCallTrunk normal = trunk(1L, TrunkHealthEnum.NORMAL.getCode());
        normal.setMaxConcurrent(10);
        normal.setCurrentConcurrent(5);
        AiCallTrunk fault = trunk(2L, TrunkHealthEnum.FAULT.getCode());
        fault.setMaxConcurrent(10);
        fault.setCurrentConcurrent(5);
        AiCallTrunk circuit = trunk(3L, TrunkHealthEnum.CIRCUIT_OPEN.getCode());
        circuit.setMaxConcurrent(0);
        circuit.setCurrentConcurrent(0);
        when(trunkMapper.selectAllEnabledTrunks()).thenReturn(Arrays.asList(normal, fault, circuit));
        when(alarmMapper.selectActiveAlarms()).thenReturn(Collections.singletonList(new AiTrunkAlarm()));

        Map<String, Object> overview = service.todayOverview();

        assertEquals(3, overview.get("trunkTotal"));
        assertEquals(1, overview.get("trunkNormal"));
        assertEquals(2, overview.get("trunkFault"));
        assertEquals(20, overview.get("maxConcurrent"));
        assertEquals(10, overview.get("currentConcurrent"));
        assertEquals(50, overview.get("concurrentUsage"));
        assertEquals(1, overview.get("activeAlarmCount"));
    }

    @Test
    void todayOverview_noTrunks_returnsZeros()
    {
        when(trunkMapper.selectAllEnabledTrunks()).thenReturn(null);
        when(alarmMapper.selectActiveAlarms()).thenReturn(null);

        Map<String, Object> overview = service.todayOverview();

        assertEquals(0, overview.get("trunkTotal"));
        assertEquals(0, overview.get("concurrentUsage"));
        assertEquals(0, overview.get("activeAlarmCount"));
    }

    // ---------------- 辅助 ----------------

    private AiCallTrunk trunk(long trunkId, String healthStatus)
    {
        AiCallTrunk trunk = new AiCallTrunk();
        trunk.setTrunkId(trunkId);
        trunk.setTrunkCode("T" + trunkId);
        trunk.setTrunkName("线路" + trunkId);
        trunk.setCarrier(CarrierEnum.CHINA_MOBILE.getCode());
        trunk.setHealthStatus(healthStatus);
        trunk.setFailCount(0);
        trunk.setMaxConcurrent(10);
        trunk.setCurrentConcurrent(0);
        return trunk;
    }
}
