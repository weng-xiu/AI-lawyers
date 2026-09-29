package ai.lawyers.system.service.lawyers.trunk.event;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import ai.lawyers.system.domain.lawyers.AiCallAgentStatus;
import ai.lawyers.system.domain.lawyers.AiCallRecord;
import ai.lawyers.system.domain.lawyers.AiCallTicket;
import ai.lawyers.system.domain.lawyers.trunk.AiCallDialLog;
import ai.lawyers.system.mapper.lawyers.AiCallRecordMapper;
import ai.lawyers.system.mapper.lawyers.AiCallTicketMapper;
import ai.lawyers.system.mapper.lawyers.trunk.AiCallDialLogMapper;
import ai.lawyers.system.service.lawyers.CallEventPublisher;
import ai.lawyers.system.service.lawyers.IAiCallAgentStatusService;
import ai.lawyers.system.service.lawyers.IAiCallTicketService;
import ai.lawyers.system.service.lawyers.metrics.HotlineMetrics;
import ai.lawyers.system.service.lawyers.queue.QualityTranscribeDispatcher;
import ai.lawyers.system.service.lawyers.trunk.ICallDispatchService;
import ai.lawyers.system.service.lawyers.trunk.gateway.esl.InboundCallHandler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** B2：统一事件处理器单测——各归一事件的消费语义与两侧桥原行为对齐 */
class CallEventProcessorTest
{
    private CallEventProcessor processor;
    private ICallDispatchService callDispatchService;
    private IAiCallAgentStatusService agentStatusService;
    private InboundCallHandler inboundCallHandler;
    private CallEventPublisher callEventPublisher;
    private IAiCallTicketService callTicketService;
    private AiCallTicketMapper callTicketMapper;
    private AiCallRecordMapper callRecordMapper;
    private AiCallDialLogMapper dialLogMapper;
    private QualityTranscribeDispatcher qualityTranscribeDispatcher;
    private HotlineMetrics metrics;

    @BeforeEach
    void setUp()
    {
        processor = new CallEventProcessor();
        callDispatchService = mock(ICallDispatchService.class);
        agentStatusService = mock(IAiCallAgentStatusService.class);
        inboundCallHandler = mock(InboundCallHandler.class);
        callEventPublisher = mock(CallEventPublisher.class);
        callTicketService = mock(IAiCallTicketService.class);
        callTicketMapper = mock(AiCallTicketMapper.class);
        callRecordMapper = mock(AiCallRecordMapper.class);
        dialLogMapper = mock(AiCallDialLogMapper.class);
        qualityTranscribeDispatcher = mock(QualityTranscribeDispatcher.class);
        metrics = mock(HotlineMetrics.class);

        ReflectionTestUtils.setField(processor, "callDispatchService", callDispatchService);
        ReflectionTestUtils.setField(processor, "agentStatusService", agentStatusService);
        ReflectionTestUtils.setField(processor, "inboundCallHandler", inboundCallHandler);
        ReflectionTestUtils.setField(processor, "callEventPublisher", callEventPublisher);
        ReflectionTestUtils.setField(processor, "callTicketService", callTicketService);
        ReflectionTestUtils.setField(processor, "callTicketMapper", callTicketMapper);
        ReflectionTestUtils.setField(processor, "callRecordMapper", callRecordMapper);
        ReflectionTestUtils.setField(processor, "dialLogMapper", dialLogMapper);
        ReflectionTestUtils.setField(processor, "qualityTranscribeDispatcher", qualityTranscribeDispatcher);
        ReflectionTestUtils.setField(processor, "metrics", metrics);
    }

    @Test
    void inboundTriggersInboundHandlerWithContext()
    {
        CallEvent event = CallEvent.of("ESL:10.0.0.1", CallEvent.INBOUND, "uuid-in");
        event.setCaller("13800138000");
        event.setCallee("12348");
        processor.handle(event);

        ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);
        verify(inboundCallHandler).handleIncomingCall(captor.capture());
        assertThat(captor.getValue().get("host")).isEqualTo("10.0.0.1");
        assertThat(captor.getValue().get("uuid")).isEqualTo("uuid-in");
        assertThat(captor.getValue().get("caller")).isEqualTo("13800138000");
        assertThat(captor.getValue().get("dnis")).isEqualTo("12348");
        verify(callDispatchService, never()).onCallEvent(anyString(), anyString(), any());
    }

    @Test
    void ringingGoesToStateMachine()
    {
        processor.handle(CallEvent.of("AMI:10.0.0.8", CallEvent.RINGING, "uuid-r"));
        verify(callDispatchService).onCallEvent(eq("uuid-r"), eq("RINGING"), any());
    }

    @Test
    void answeredIncrementsMetricPushesAgentAndEntersStateMachine()
    {
        // 拨号日志反查到坐席 → 定向推送
        AiCallDialLog dialLog = new AiCallDialLog();
        dialLog.setAgentId(7L);
        dialLog.setRecordId(11L);
        when(dialLogMapper.selectByCallUuid("uuid-a")).thenReturn(dialLog);
        AiCallAgentStatus agent = new AiCallAgentStatus();
        agent.setAgentId(7L);
        agent.setUserId(99L);
        when(agentStatusService.selectAiCallAgentStatusByAgentId(7L)).thenReturn(agent);

        processor.handle(CallEvent.of("ESL:10.0.0.1", CallEvent.ANSWERED, "uuid-a"));

        verify(metrics).incrementCall("answered");
        ArgumentCaptor<Map<String, Object>> params = ArgumentCaptor.forClass(Map.class);
        verify(callDispatchService).onCallEvent(eq("uuid-a"), eq("ANSWERED"), params.capture());
        assertThat(params.getValue().get("answerTime")).isNotNull();
        verify(callEventPublisher).publishToUser(eq(99L), eq("ANSWERED"), any());
    }

    @Test
    void hangupWithoutTicketFlagDoesNotCreateTicket()
    {
        Map<String, Object> payload = new HashMap<>();
        payload.put("hangupCause", "NORMAL_CLEARING");
        payload.put("talkDuration", 30);
        CallEvent event = CallEvent.of("AMI:10.0.0.8", CallEvent.HANGUP, "uuid-h");
        event.setPayload(payload);
        processor.handle(event);

        ArgumentCaptor<Map<String, Object>> params = ArgumentCaptor.forClass(Map.class);
        verify(callDispatchService).onCallEvent(eq("uuid-h"), eq("HANGUP"), params.capture());
        assertThat(params.getValue().get("hangupCause")).isEqualTo("NORMAL_CLEARING");
        assertThat(params.getValue().get("talkDuration")).isEqualTo(30);
        verify(callTicketService, never()).insertAiCallTicket(any());
        // 有通话时长 → 不计未接
        verify(metrics, never()).incrementCall("abandoned");
    }

    @Test
    void hangupZeroTalkCountsAbandoned()
    {
        Map<String, Object> payload = new HashMap<>();
        payload.put("hangupCause", "NO_ANSWER");
        payload.put("talkDuration", 0);
        CallEvent event = CallEvent.of("AMI:10.0.0.8", CallEvent.HANGUP, "uuid-h0");
        event.setPayload(payload);
        processor.handle(event);

        verify(metrics).incrementCall("abandoned");
    }

    @Test
    void autoTicketFlagCreatesInboundTicket()
    {
        AiCallRecord record = new AiCallRecord();
        record.setRecordId(21L);
        record.setStatus("2");
        record.setCallerNumber("13800138000");
        when(callRecordMapper.selectAiCallRecordByCallUuid("uuid-t")).thenReturn(record);
        when(callTicketMapper.selectAiCallTicketList(any())).thenReturn(Collections.emptyList());
        when(callTicketService.generateTicketNo()).thenReturn("T20260929001");

        Map<String, Object> payload = new HashMap<>();
        payload.put("hangupCause", "NORMAL_CLEARING");
        payload.put("talkDuration", 60);
        payload.put("autoTicket", true);
        CallEvent event = CallEvent.of("ESL:10.0.0.1", CallEvent.HANGUP, "uuid-t");
        event.setPayload(payload);
        processor.handle(event);

        ArgumentCaptor<AiCallTicket> captor = ArgumentCaptor.forClass(AiCallTicket.class);
        verify(callTicketService).insertAiCallTicket(captor.capture());
        assertThat(captor.getValue().getRecordId()).isEqualTo(21L);
        assertThat(captor.getValue().getTicketNo()).isEqualTo("T20260929001");
    }

    @Test
    void autoTicketSkipsWhenTicketExists()
    {
        AiCallRecord record = new AiCallRecord();
        record.setRecordId(21L);
        record.setStatus("2");
        when(callRecordMapper.selectAiCallRecordByCallUuid("uuid-t2")).thenReturn(record);
        when(callTicketMapper.selectAiCallTicketList(any()))
                .thenReturn(Collections.singletonList(new AiCallTicket()));

        Map<String, Object> payload = new HashMap<>();
        payload.put("hangupCause", "NORMAL_CLEARING");
        payload.put("talkDuration", 60);
        payload.put("autoTicket", true);
        CallEvent event = CallEvent.of("ESL:10.0.0.1", CallEvent.HANGUP, "uuid-t2");
        event.setPayload(payload);
        processor.handle(event);

        verify(callTicketService, never()).insertAiCallTicket(any());
    }

    @Test
    void dtmfPushesDigitAsExtra()
    {
        Map<String, Object> payload = new HashMap<>();
        payload.put("digit", "5");
        CallEvent event = CallEvent.of("ESL:10.0.0.1", CallEvent.DTMF, "uuid-d");
        event.setPayload(payload);
        processor.handle(event);

        ArgumentCaptor<Map<String, Object>> data = ArgumentCaptor.forClass(Map.class);
        verify(callEventPublisher).broadcast(eq("DTMF"), data.capture());
        assertThat(data.getValue().get("extra")).isEqualTo("5");
    }

    @Test
    void agentStatusUpdatesOnlyOnChange()
    {
        AiCallAgentStatus agent = new AiCallAgentStatus();
        agent.setAgentId(7L);
        agent.setSipExtension("1001");
        agent.setStatus("1");
        when(agentStatusService.selectAiCallAgentStatusList(any(AiCallAgentStatus.class)))
                .thenReturn(Collections.singletonList(agent));

        Map<String, Object> payload = new HashMap<>();
        payload.put("ext", "1001");
        payload.put("targetStatus", "2");
        CallEvent event = CallEvent.of("AMI:10.0.0.8", CallEvent.AGENT_STATUS, null);
        event.setPayload(payload);
        processor.handle(event);

        verify(agentStatusService).updateAgentStatus(7L, "2");
    }

    @Test
    void agentStatusSameStatusDoesNotUpdate()
    {
        AiCallAgentStatus agent = new AiCallAgentStatus();
        agent.setAgentId(7L);
        agent.setStatus("2");
        when(agentStatusService.selectAiCallAgentStatusList(any(AiCallAgentStatus.class)))
                .thenReturn(Collections.singletonList(agent));

        Map<String, Object> payload = new HashMap<>();
        payload.put("ext", "1001");
        payload.put("targetStatus", "2");
        CallEvent event = CallEvent.of("AMI:10.0.0.8", CallEvent.AGENT_STATUS, null);
        event.setPayload(payload);
        processor.handle(event);

        verify(agentStatusService, never()).updateAgentStatus(any(), anyString());
    }

    @Test
    void recordStopWritesBackAndEnqueuesQuality()
    {
        Map<String, Object> payload = new HashMap<>();
        payload.put("recordPath", "/nonexistent/b2-test.wav");
        payload.put("recordSeconds", 12);
        CallEvent event = CallEvent.of("ESL:10.0.0.1", CallEvent.RECORD_STOP, "uuid-rec");
        event.setRecordId(31L);
        event.setPayload(payload);
        processor.handle(event);

        ArgumentCaptor<AiCallRecord> captor = ArgumentCaptor.forClass(AiCallRecord.class);
        verify(callRecordMapper).updateRecordingInfo(captor.capture());
        assertThat(captor.getValue().getRecordId()).isEqualTo(31L);
        assertThat(captor.getValue().getRecordFile()).isEqualTo("/nonexistent/b2-test.wav");
        assertThat(captor.getValue().getRecordDuration()).isEqualTo(12);
        verify(qualityTranscribeDispatcher).enqueue(31L);
    }

    @Test
    void recordStopWithoutRecordIdIsDropped()
    {
        Map<String, Object> payload = new HashMap<>();
        payload.put("recordPath", "/tmp/x.wav");
        CallEvent event = CallEvent.of("ESL:10.0.0.1", CallEvent.RECORD_STOP, "uuid-rec2");
        event.setPayload(payload);
        processor.handle(event);

        verify(callRecordMapper, never()).updateRecordingInfo(any());
        verify(qualityTranscribeDispatcher, never()).enqueue(any());
    }
}
