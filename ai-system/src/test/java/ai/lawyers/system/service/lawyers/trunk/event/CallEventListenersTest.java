package ai.lawyers.system.service.lawyers.trunk.event;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import ai.lawyers.system.domain.lawyers.AiCallAgentStatus;
import ai.lawyers.system.service.lawyers.IAiCallAgentStatusService;
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

/**
 * P10/D1：事件监听器单测——锁定处理器发布事件后的下游动作与原直调语义一致。
 */
class CallEventListenersTest
{
    private CallEventListeners listeners;
    private ICallDispatchService callDispatchService;
    private IAiCallAgentStatusService agentStatusService;
    private InboundCallHandler inboundCallHandler;

    @BeforeEach
    void setUp()
    {
        listeners = new CallEventListeners();
        callDispatchService = mock(ICallDispatchService.class);
        agentStatusService = mock(IAiCallAgentStatusService.class);
        inboundCallHandler = mock(InboundCallHandler.class);
        ReflectionTestUtils.setField(listeners, "callDispatchService", callDispatchService);
        ReflectionTestUtils.setField(listeners, "agentStatusService", agentStatusService);
        ReflectionTestUtils.setField(listeners, "inboundCallHandler", inboundCallHandler);
    }

    @Test
    void inboundBuildsLegacyContextAndDelegates()
    {
        listeners.onInbound(new InboundCallEvent("10.0.0.1", "uuid-in", "13800138000", "12348"));

        ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);
        verify(inboundCallHandler).handleIncomingCall(captor.capture());
        assertThat(captor.getValue().get("host")).isEqualTo("10.0.0.1");
        assertThat(captor.getValue().get("uuid")).isEqualTo("uuid-in");
        assertThat(captor.getValue().get("caller")).isEqualTo("13800138000");
        assertThat(captor.getValue().get("callee")).isEqualTo("12348");
        assertThat(captor.getValue().get("dnis")).isEqualTo("12348");
    }

    @Test
    void inboundSkipsWhenHandlerMissing()
    {
        ReflectionTestUtils.setField(listeners, "inboundCallHandler", null);
        listeners.onInbound(new InboundCallEvent("10.0.0.1", "uuid-in", "1", "2"));
        verify(inboundCallHandler, never()).handleIncomingCall(any());
    }

    @Test
    void stateChangeDelegatesToDispatchStateMachine()
    {
        Map<String, Object> params = new HashMap<>();
        params.put("talkDuration", 30);
        listeners.onCallStateChange(new CallStateChangeEvent("uuid-h", "HANGUP", params));

        verify(callDispatchService).onCallEvent(eq("uuid-h"), eq("HANGUP"), eq(params));
    }

    @Test
    void agentStatusUpdatesOnlyChangedRows()
    {
        AiCallAgentStatus changed = new AiCallAgentStatus();
        changed.setAgentId(7L);
        changed.setStatus("1");
        AiCallAgentStatus same = new AiCallAgentStatus();
        same.setAgentId(8L);
        same.setStatus("2");
        when(agentStatusService.selectAiCallAgentStatusList(any(AiCallAgentStatus.class)))
                .thenReturn(java.util.Arrays.asList(changed, same));

        listeners.onAgentStatusSync(new AgentStatusSyncEvent("1001", "2"));

        verify(agentStatusService).updateAgentStatus(7L, "2");
        verify(agentStatusService, never()).updateAgentStatus(eq(8L), anyString());
    }

    @Test
    void agentStatusEmptyResultDoesNothing()
    {
        when(agentStatusService.selectAiCallAgentStatusList(any(AiCallAgentStatus.class)))
                .thenReturn(Collections.emptyList());
        listeners.onAgentStatusSync(new AgentStatusSyncEvent("1001", "2"));
        verify(agentStatusService, never()).updateAgentStatus(any(), anyString());
    }

    @Test
    void agentStatusExceptionIsSwallowed()
    {
        when(agentStatusService.selectAiCallAgentStatusList(any(AiCallAgentStatus.class)))
                .thenThrow(new RuntimeException("db down"));
        listeners.onAgentStatusSync(new AgentStatusSyncEvent("1001", "2"));
        verify(agentStatusService, never()).updateAgentStatus(any(), anyString());
    }
}
