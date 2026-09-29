package ai.lawyers.system.service.lawyers.trunk.gateway.esl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import ai.lawyers.system.service.lawyers.trunk.CallEventIdempotencyGuard;
import ai.lawyers.system.service.lawyers.trunk.event.CallEvent;
import ai.lawyers.system.service.lawyers.trunk.event.CallEventBus;

/**
 * P3-B4：{@link EslEventBridgeService} 事件幂等接入与 PBX 通道数查询测试。
 *
 * <p>核心场景：一次挂断产生 CHANNEL_HANGUP_COMPLETE + CHANNEL_HANGUP 两个事件，
 * 归一化判重后只允许处理一次（否则 finishCall 双释放并发）。
 * B2 起分发断言统一事件总线入参（消费语义见 CallEventProcessorTest）。</p>
 */
class EslEventDedupTest
{
    private EslEventBridgeService bridge;
    private CallEventIdempotencyGuard guard;
    private CallEventBus callEventBus;

    @BeforeEach
    void setUp()
    {
        bridge = new EslEventBridgeService();
        guard = mock(CallEventIdempotencyGuard.class);
        callEventBus = mock(CallEventBus.class);
        ReflectionTestUtils.setField(bridge, "idempotencyGuard", guard);
        ReflectionTestUtils.setField(bridge, "callEventBus", callEventBus);
    }

    private static EslEvent event(String name, String uuid)
    {
        EslEvent e = new EslEvent();
        e.setEventName(name);
        e.put("Unique-ID", uuid);
        return e;
    }

    @Test
    void hangupCompleteThenHangup_normalized_onlyProcessedOnce()
    {
        // 归一化后两个事件同键：首次放行、第二次判重命中
        when(guard.firstSeen("ESL:h1", "u1", "CHANNEL_HANGUP")).thenReturn(true, false);

        bridge.onEvent("h1", event("CHANNEL_HANGUP_COMPLETE", "u1"));
        bridge.onEvent("h1", event("CHANNEL_HANGUP", "u1"));

        // 仅首次放行投递 HANGUP 事件
        ArgumentCaptor<CallEvent> captor = ArgumentCaptor.forClass(CallEvent.class);
        verify(callEventBus, times(1)).dispatch(captor.capture());
        assertThat(captor.getValue().getEventName()).isEqualTo(CallEvent.HANGUP);
        assertThat(captor.getValue().getSessionId()).isEqualTo("u1");
        assertThat(captor.getValue().getSource()).isEqualTo("ESL:h1");
        // 两次都以归一化事件名判重
        verify(guard, times(2)).firstSeen("ESL:h1", "u1", "CHANNEL_HANGUP");
    }

    @Test
    void dtmf_neverDeduped()
    {
        bridge.onEvent("h1", event("DTMF", "u1"));
        bridge.onEvent("h1", event("DTMF", "u1"));

        // DTMF 同一通话可合法重复（多次按键），不参与判重
        verify(guard, never()).firstSeen(anyString(), anyString(), anyString());
        // 两次按键都投递 DTMF 事件
        ArgumentCaptor<CallEvent> captor = ArgumentCaptor.forClass(CallEvent.class);
        verify(callEventBus, times(2)).dispatch(captor.capture());
        assertThat(captor.getAllValues())
                .allSatisfy(e -> assertThat(e.getEventName()).isEqualTo(CallEvent.DTMF));
    }

    @Test
    void guardAbsent_processesNormally()
    {
        ReflectionTestUtils.setField(bridge, "idempotencyGuard", null);

        bridge.onEvent("h1", event("CHANNEL_ANSWER", "u1"));
        bridge.onEvent("h1", event("CHANNEL_ANSWER", "u1"));

        // 无守卫时退化为旧行为（不判重），兼容单机等场景
        ArgumentCaptor<CallEvent> captor = ArgumentCaptor.forClass(CallEvent.class);
        verify(callEventBus, times(2)).dispatch(captor.capture());
        assertThat(captor.getAllValues())
                .allSatisfy(e -> assertThat(e.getEventName()).isEqualTo(CallEvent.ANSWERED));
    }

    @Test
    void parseChannelCount_cases()
    {
        assertThat(EslEventBridgeService.parseChannelCount("\n3 total.\n")).isEqualTo(3);
        assertThat(EslEventBridgeService.parseChannelCount("12 total")).isEqualTo(12);
        assertThat(EslEventBridgeService.parseChannelCount("0 total.")).isEqualTo(0);
        assertThat(EslEventBridgeService.parseChannelCount("+OK")).isEqualTo(-1);
        assertThat(EslEventBridgeService.parseChannelCount(null)).isEqualTo(-1);
        assertThat(EslEventBridgeService.parseChannelCount("")).isEqualTo(-1);
    }

    @Test
    @SuppressWarnings("unchecked")
    void queryPbxChannelCount_sumsConnectedClients()
    {
        FreeSwitchEslInboundClient c1 = mock(FreeSwitchEslInboundClient.class);
        when(c1.isConnected()).thenReturn(true);
        when(c1.sendCommand(anyString())).thenReturn("2 total.");
        FreeSwitchEslInboundClient c2 = mock(FreeSwitchEslInboundClient.class);
        when(c2.isConnected()).thenReturn(false); // 断连节点跳过
        FreeSwitchEslInboundClient c3 = mock(FreeSwitchEslInboundClient.class);
        when(c3.isConnected()).thenReturn(true);
        when(c3.sendCommand(anyString())).thenReturn("\n5 total.\n");

        Map<String, FreeSwitchEslInboundClient> clients = (Map<String, FreeSwitchEslInboundClient>)
                ReflectionTestUtils.getField(bridge, "clients");
        clients.put("h1:8021", c1);
        clients.put("h2:8021", c2);
        clients.put("h3:8021", c3);

        assertThat(bridge.queryPbxChannelCount()).isEqualTo(7);
    }

    @Test
    @SuppressWarnings("unchecked")
    void queryPbxChannelCount_allDown_returnsMinus1()
    {
        FreeSwitchEslInboundClient c1 = mock(FreeSwitchEslInboundClient.class);
        when(c1.isConnected()).thenReturn(false);

        Map<String, FreeSwitchEslInboundClient> clients = (Map<String, FreeSwitchEslInboundClient>)
                ReflectionTestUtils.getField(bridge, "clients");
        clients.put("h1:8021", c1);

        assertThat(bridge.queryPbxChannelCount()).isEqualTo(-1);
    }
}
