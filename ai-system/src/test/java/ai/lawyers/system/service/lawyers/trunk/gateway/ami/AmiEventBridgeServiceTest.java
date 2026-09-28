package ai.lawyers.system.service.lawyers.trunk.gateway.ami;

import java.util.Collections;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.test.util.ReflectionTestUtils;

import ai.lawyers.system.domain.lawyers.AiCallAgentStatus;
import ai.lawyers.system.mapper.lawyers.trunk.AiCallDialLogMapper;
import ai.lawyers.system.service.lawyers.CallEventPublisher;
import ai.lawyers.system.service.lawyers.IAiCallAgentStatusService;
import ai.lawyers.system.service.lawyers.trunk.CallEventIdempotencyGuard;
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

/** B1：AMI 事件桥单测——静态映射器 + 事件分发流程 */
class AmiEventBridgeServiceTest
{
    private AmiEventBridgeService bridge;
    private InboundCallHandler inboundCallHandler;
    private ICallDispatchService callDispatchService;
    private CallEventPublisher callEventPublisher;
    private AiCallDialLogMapper dialLogMapper;
    private CallEventIdempotencyGuard idempotencyGuard;
    private IAiCallAgentStatusService agentStatusService;

    @BeforeEach
    void setUp()
    {
        bridge = new AmiEventBridgeService();
        inboundCallHandler = mock(InboundCallHandler.class);
        callDispatchService = mock(ICallDispatchService.class);
        callEventPublisher = mock(CallEventPublisher.class);
        dialLogMapper = mock(AiCallDialLogMapper.class);
        idempotencyGuard = mock(CallEventIdempotencyGuard.class);
        agentStatusService = mock(IAiCallAgentStatusService.class);

        ReflectionTestUtils.setField(bridge, "inboundCallHandler", inboundCallHandler);
        ReflectionTestUtils.setField(bridge, "callDispatchService", callDispatchService);
        ReflectionTestUtils.setField(bridge, "callEventPublisher", callEventPublisher);
        ReflectionTestUtils.setField(bridge, "dialLogMapper", dialLogMapper);
        ReflectionTestUtils.setField(bridge, "idempotencyGuard", idempotencyGuard);
        ReflectionTestUtils.setField(bridge, "agentStatusService", agentStatusService);
        when(idempotencyGuard.firstSeen(anyString(), anyString(), anyString())).thenReturn(true);
    }

    // ------------------------------------------------------------ 静态映射

    @Test
    void hangupCauseMapping()
    {
        assertThat(AmiEventBridgeService.mapHangupCause("16", "Normal Clearing")).isEqualTo("NORMAL_CLEARING");
        assertThat(AmiEventBridgeService.mapHangupCause("17", "User busy")).isEqualTo("USER_BUSY");
        assertThat(AmiEventBridgeService.mapHangupCause("19", null)).isEqualTo("NO_ANSWER");
        assertThat(AmiEventBridgeService.mapHangupCause("21", "Call Rejected")).isEqualTo("CALL_REJECTED");
        // 未知码回退 Cause-txt 下划线归一
        assertThat(AmiEventBridgeService.mapHangupCause("99", "Interworking, unspecified")).isEqualTo("INTERWORKING_UNSPECIFIED");
        // 无 txt 回退 CAUSE_<code>；全空 UNKNOWN
        assertThat(AmiEventBridgeService.mapHangupCause("99", null)).isEqualTo("CAUSE_99");
        assertThat(AmiEventBridgeService.mapHangupCause(null, null)).isEqualTo("UNKNOWN");
    }

    @Test
    void extensionExtraction()
    {
        assertThat(AmiEventBridgeService.extractExtension("SIP/1001")).isEqualTo("1001");
        assertThat(AmiEventBridgeService.extractExtension("PJSIP/200@from-internal")).isEqualTo("200");
        assertThat(AmiEventBridgeService.extractExtension("Local/300@agents")).isEqualTo("300");
        assertThat(AmiEventBridgeService.extractExtension("1001")).isEqualTo("1001");
        assertThat(AmiEventBridgeService.extractExtension(null)).isNull();
        assertThat(AmiEventBridgeService.extractExtension("SIP/")).isNull();
    }

    @Test
    void agentStatusMapping()
    {
        assertThat(AmiEventBridgeService.mapAgentStatus("yes", null)).isEqualTo("2");
        assertThat(AmiEventBridgeService.mapAgentStatus("no", null)).isEqualTo("1");
        assertThat(AmiEventBridgeService.mapAgentStatus(null, "INUSE")).isEqualTo("2");
        assertThat(AmiEventBridgeService.mapAgentStatus(null, "RINGING")).isEqualTo("2");
        assertThat(AmiEventBridgeService.mapAgentStatus(null, "NOT_INUSE")).isEqualTo("1");
        assertThat(AmiEventBridgeService.mapAgentStatus(null, "UNAVAILABLE")).isEqualTo("0");
        // 数值态未知 → 不动坐席状态
        assertThat(AmiEventBridgeService.mapAgentStatus(null, "5")).isNull();
        assertThat(AmiEventBridgeService.mapAgentStatus(null, null)).isNull();
    }

    @Test
    void callFlowEventClassification()
    {
        assertThat(AmiEventBridgeService.isCallFlowEvent("Newchannel")).isTrue();
        assertThat(AmiEventBridgeService.isCallFlowEvent("Dial")).isTrue();
        assertThat(AmiEventBridgeService.isCallFlowEvent("Hangup")).isTrue();
        assertThat(AmiEventBridgeService.isCallFlowEvent("VarSet")).isFalse();
        assertThat(AmiEventBridgeService.isCallFlowEvent("QueueMemberStatus")).isFalse();
        assertThat(AmiEventBridgeService.isCallFlowEvent(null)).isFalse();
    }

    // ------------------------------------------------------------ 分发流程

    @Test
    void inboundNewchannelTriggersInboundHandler()
    {
        AmiEvent event = AmiEventParser.parse("Event: Newchannel\nUniqueid: 1719.1\n"
                + "CallerIDNum: 13800138000\nExten: 12348\nContext: from-pstn\n\n");
        bridge.onEvent("10.0.0.8", event);

        verify(inboundCallHandler).handleIncomingCall(any());
        verify(callDispatchService, never()).onCallEvent(anyString(), anyString(), any());
    }

    @Test
    void outboundNewchannelThenDialRingingUsesAiCallUuid()
    {
        bridge.onEvent("10.0.0.8", AmiEventParser.parse(
                "Event: Newchannel\nUniqueid: 1719.1\nContext: from-internal\nAI_CALL_UUID: uuid-abc\n\n"));
        verify(inboundCallHandler, never()).handleIncomingCall(any());

        bridge.onEvent("10.0.0.8", AmiEventParser.parse(
                "Event: Dial\nUniqueid: 1719.1\nDestUniqueid: 1719.2\nSubEvent: Begin\n\n"));

        verify(callDispatchService).onCallEvent(eq("uuid-abc"), eq("RINGING"), any());
    }

    @Test
    void varSetRegistersLegForLaterEvents()
    {
        bridge.onEvent("10.0.0.8", AmiEventParser.parse(
                "Event: VarSet\nUniqueid: 1719.1\nVariable: AI_CALL_UUID\nValue: uuid-abc\n\n"));
        bridge.onEvent("10.0.0.8", AmiEventParser.parse(
                "Event: Dial\nUniqueid: 1719.1\nDestUniqueid: 1719.2\nSubEvent: Begin\n\n"));
        verify(callDispatchService).onCallEvent(eq("uuid-abc"), eq("RINGING"), any());
    }

    @Test
    void hangupMapsCauseAndComputesDuration()
    {
        // 通道创建（登记 uuid）→ 接通（登记接通时刻）→ 挂断（Cause: 16）
        bridge.onEvent("10.0.0.8", AmiEventParser.parse(
                "Event: Newchannel\nUniqueid: 1719.1\nAI_CALL_UUID: uuid-abc\n\n"));
        bridge.onEvent("10.0.0.8", AmiEventParser.parse(
                "Event: Newstate\nUniqueid: 1719.1\nChannelStateDesc: Up\n\n"));
        try { Thread.sleep(30); } catch (InterruptedException ignored) { Thread.currentThread().interrupt(); }
        bridge.onEvent("10.0.0.8", AmiEventParser.parse(
                "Event: Hangup\nUniqueid: 1719.1\nCause: 16\nCause-txt: Normal Clearing\n\n"));

        org.mockito.ArgumentCaptor<Map<String, Object>> captor =
                org.mockito.ArgumentCaptor.forClass(Map.class);
        verify(callDispatchService).onCallEvent(eq("uuid-abc"), eq("HANGUP"), captor.capture());
        assertThat(captor.getValue().get("hangupCause")).isEqualTo("NORMAL_CLEARING");
        assertThat((Integer) captor.getValue().get("talkDuration")).isGreaterThanOrEqualTo(0);
        verify(callEventPublisher).broadcast(eq("HANGUP"), any());
    }

    @Test
    void duplicateCallFlowEventIsDropped()
    {
        when(idempotencyGuard.firstSeen(anyString(), anyString(), anyString())).thenReturn(false);
        bridge.onEvent("10.0.0.8", AmiEventParser.parse(
                "Event: Newchannel\nUniqueid: 1719.1\nCallerIDNum: 13800138000\nContext: from-pstn\n\n"));

        verify(inboundCallHandler, never()).handleIncomingCall(any());
        verify(idempotencyGuard).firstSeen(eq("AMI:10.0.0.8"), eq("1719.1"), eq("Newchannel"));
    }

    @Test
    void queueMemberSyncsAgentStatus()
    {
        AiCallAgentStatus agent = new AiCallAgentStatus();
        agent.setAgentId(7L);
        agent.setSipExtension("1001");
        agent.setStatus("1");
        when(agentStatusService.selectAiCallAgentStatusList(any(AiCallAgentStatus.class)))
                .thenReturn(Collections.singletonList(agent));

        bridge.onEvent("10.0.0.8", AmiEventParser.parse(
                "Event: QueueMemberStatus\nQueue: 12348\nInterface: SIP/1001\nPaused: yes\nMemberStatus: INUSE\n\n"));

        verify(agentStatusService).updateAgentStatus(7L, "2");
    }

    @Test
    void queueMemberSameStatusDoesNotUpdate()
    {
        AiCallAgentStatus agent = new AiCallAgentStatus();
        agent.setAgentId(7L);
        agent.setStatus("2");
        when(agentStatusService.selectAiCallAgentStatusList(any(AiCallAgentStatus.class)))
                .thenReturn(Collections.singletonList(agent));

        bridge.onEvent("10.0.0.8", AmiEventParser.parse(
                "Event: QueueMemberStatus\nInterface: PJSIP/1001@trunks\nPaused: yes\n\n"));

        verify(agentStatusService, never()).updateAgentStatus(any(), anyString());
    }

    @Test
    void unknownLegHangupFallsBackToAsteriskUniqueId()
    {
        // 与 ESL 侧对齐：非本平台通道的挂断同样进状态机（uuid=Asterisk UniqueID），
        // 由 dispatch 按拨号日志对账兜底（查不到仅 WARN），不特殊拦截
        bridge.onEvent("10.0.0.8", AmiEventParser.parse(
                "Event: Hangup\nUniqueid: 9999.9\nCause: 16\n\n"));
        verify(callDispatchService).onCallEvent(eq("9999.9"), eq("HANGUP"), any());
    }
}
