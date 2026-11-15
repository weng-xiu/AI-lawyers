package ai.lawyers.system.service.impl.lawyers.trunk;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import ai.lawyers.system.domain.lawyers.AiCallAgentStatus;
import ai.lawyers.system.domain.lawyers.AiCallRecord;
import ai.lawyers.system.domain.lawyers.AiHotspotSuppress;
import ai.lawyers.system.domain.lawyers.skill.AiSkillGroup;
import ai.lawyers.system.domain.lawyers.skill.DispatchContext;
import ai.lawyers.system.domain.lawyers.skill.DispatchResult;
import ai.lawyers.system.domain.lawyers.trunk.AiCallTrunk;
import ai.lawyers.system.mapper.lawyers.trunk.AiCallTrunkMapper;
import ai.lawyers.system.service.lawyers.CallEventPublisher;
import ai.lawyers.system.service.lawyers.IAiCallAgentStatusService;
import ai.lawyers.system.service.lawyers.IAiCallBlacklistService;
import ai.lawyers.system.service.lawyers.IAiCallRecordService;
import ai.lawyers.system.service.lawyers.IAiHotspotSuppressService;
import ai.lawyers.system.service.lawyers.metrics.HotlineMetrics;
import ai.lawyers.system.service.lawyers.queue.CallEventDispatcher;
import ai.lawyers.system.service.lawyers.skill.IAgentDispatchService;
import ai.lawyers.system.service.lawyers.skill.IAiSkillGroupService;
import ai.lawyers.system.service.lawyers.trunk.gateway.CallGatewayFactory;
import ai.lawyers.system.service.lawyers.trunk.gateway.ICallGatewayAdapter;
import ai.lawyers.system.service.lawyers.trunk.gateway.esl.EslEventBridgeService;
import ai.lawyers.system.service.lawyers.voice.robot.RobotFrontDoorDecider;

/**
 * P1/P3：{@link DefaultInboundCallHandler} 入站呼叫处理编排测试。
 *
 * <p>覆盖开关短路、黑名单/高频 REJECT 挂断（含挂断异常与桥接缺失）、
 * PRIORITY 降权透传、话单字段与 DNIS 回退、机器人前置分流、坐席分配后的
 * 话单归属/弹屏推送/分机桥接（分机回退、坐席缺失、线路查询异常）、
 * 技能组按 DNIS 命中与默认组、排队广播以及异常兜底。</p>
 *
 * @author ai-lawyers
 */
class DefaultInboundCallHandlerTest
{
    private static final String UUID = "u1";

    private static final String CALLER = "13800138000";

    private static final String DNIS = "4008001234";

    private static final Long RECORD_ID = 55L;

    private IAiCallRecordService callRecordService;

    private IAgentDispatchService agentDispatchService;

    private IAiSkillGroupService skillGroupService;

    private IAiCallAgentStatusService agentStatusService;

    private CallGatewayFactory gatewayFactory;

    private AiCallTrunkMapper trunkMapper;

    private IAiCallBlacklistService blacklistService;

    private IAiHotspotSuppressService hotspotSuppressService;

    private EslEventBridgeService eslEventBridgeService;

    private CallEventPublisher callEventPublisher;

    private CallEventDispatcher callEventDispatcher;

    private HotlineMetrics metrics;

    private RobotFrontDoorDecider frontDoorDecider;

    private DefaultInboundCallHandler handler;

    @BeforeEach
    void setUp()
    {
        callRecordService = mock(IAiCallRecordService.class);
        agentDispatchService = mock(IAgentDispatchService.class);
        skillGroupService = mock(IAiSkillGroupService.class);
        agentStatusService = mock(IAiCallAgentStatusService.class);
        gatewayFactory = mock(CallGatewayFactory.class);
        trunkMapper = mock(AiCallTrunkMapper.class);
        blacklistService = mock(IAiCallBlacklistService.class);
        hotspotSuppressService = mock(IAiHotspotSuppressService.class);
        eslEventBridgeService = mock(EslEventBridgeService.class);
        callEventPublisher = mock(CallEventPublisher.class);
        callEventDispatcher = mock(CallEventDispatcher.class);
        metrics = mock(HotlineMetrics.class);
        frontDoorDecider = mock(RobotFrontDoorDecider.class);
        // 话单落库后由服务层回填主键，模拟该行为
        doAnswer(invocation ->
        {
            ((AiCallRecord) invocation.getArgument(0)).setRecordId(RECORD_ID);
            return 0;
        }).when(callRecordService).insertAiCallRecord(any(AiCallRecord.class));

        handler = new DefaultInboundCallHandler();
        ReflectionTestUtils.setField(handler, "callRecordService", callRecordService);
        ReflectionTestUtils.setField(handler, "agentDispatchService", agentDispatchService);
        ReflectionTestUtils.setField(handler, "skillGroupService", skillGroupService);
        ReflectionTestUtils.setField(handler, "agentStatusService", agentStatusService);
        ReflectionTestUtils.setField(handler, "gatewayFactory", gatewayFactory);
        ReflectionTestUtils.setField(handler, "trunkMapper", trunkMapper);
        ReflectionTestUtils.setField(handler, "blacklistService", blacklistService);
        ReflectionTestUtils.setField(handler, "hotspotSuppressService", hotspotSuppressService);
        ReflectionTestUtils.setField(handler, "eslEventBridgeService", eslEventBridgeService);
        ReflectionTestUtils.setField(handler, "callEventPublisher", callEventPublisher);
        ReflectionTestUtils.setField(handler, "callEventDispatcher", callEventDispatcher);
        ReflectionTestUtils.setField(handler, "metrics", metrics);
        ReflectionTestUtils.setField(handler, "frontDoorDecider", frontDoorDecider);
        ReflectionTestUtils.setField(handler, "defaultGroupId", 1L);
        ReflectionTestUtils.setField(handler, "inboundEnabled", true);
        ReflectionTestUtils.setField(handler, "hotspotEnabled", true);
    }

    // ---------- 开关 ----------

    @Test
    void ignoresCallWhenDisabled()
    {
        ReflectionTestUtils.setField(handler, "inboundEnabled", false);

        handler.handleIncomingCall(ctx());

        verifyNoInteractions(callRecordService, blacklistService, hotspotSuppressService,
                agentDispatchService, callEventPublisher, metrics);
    }

    // ---------- 黑名单 ----------

    @Test
    void blacklistedCallerIsHungUpWithoutRecord()
    {
        when(blacklistService.isBlacklisted(CALLER)).thenReturn(true);

        handler.handleIncomingCall(ctx());

        verify(metrics).incrementCall("inbound");
        verify(metrics).incrementInboundIntercept("blacklist");
        verify(eslEventBridgeService).hangupCall("10.0.0.1", UUID);
        verify(callRecordService, never()).insertAiCallRecord(any());
        verifyNoInteractions(agentDispatchService);
    }

    @Test
    void blacklistHangupFailureIsSwallowed()
    {
        when(blacklistService.isBlacklisted(CALLER)).thenReturn(true);
        doThrow(new IllegalStateException("esl down")).when(eslEventBridgeService).hangupCall(any(), any());

        handler.handleIncomingCall(ctx());

        verify(callRecordService, never()).insertAiCallRecord(any());
    }

    @Test
    void blacklistWorksWithoutEslBridge()
    {
        ReflectionTestUtils.setField(handler, "eslEventBridgeService", null);
        when(blacklistService.isBlacklisted(CALLER)).thenReturn(true);

        handler.handleIncomingCall(ctx());

        verify(metrics).incrementInboundIntercept("blacklist");
        verify(callRecordService, never()).insertAiCallRecord(any());
    }

    // ---------- 高频置底 ----------

    @Test
    void hotspotRejectIsHungUpAndCounted()
    {
        when(hotspotSuppressService.matchInbound(CALLER, null, UUID, DNIS)).thenReturn(suppress("REJECT", 20));

        handler.handleIncomingCall(ctx());

        verify(metrics).incrementInboundIntercept("hotspot_reject");
        verify(eslEventBridgeService).hangupCall("10.0.0.1", UUID);
        verify(callRecordService, never()).insertAiCallRecord(any());
    }

    @Test
    void hotspotRejectHangupFailureIsSwallowed()
    {
        when(hotspotSuppressService.matchInbound(CALLER, null, UUID, DNIS)).thenReturn(suppress("REJECT", 20));
        doThrow(new IllegalStateException("esl down")).when(eslEventBridgeService).hangupCall(any(), any());

        handler.handleIncomingCall(ctx());

        verify(callRecordService, never()).insertAiCallRecord(any());
    }

    @Test
    void hotspotPriorityLowersDispatchPriority()
    {
        when(hotspotSuppressService.matchInbound(CALLER, null, UUID, DNIS)).thenReturn(suppress("PRIORITY", 20));
        when(agentDispatchService.dispatch(eq(1L), any(DispatchContext.class)))
                .thenReturn(DispatchResult.queued(33L, 2, 5, 1L, "默认组"));

        handler.handleIncomingCall(ctx());

        ArgumentCaptor<DispatchContext> dctx = ArgumentCaptor.forClass(DispatchContext.class);
        verify(agentDispatchService).dispatch(eq(1L), dctx.capture());
        assertThat(dctx.getValue().getPriority()).isEqualTo(20);
        assertThat(dctx.getValue().getRecordId()).isEqualTo(RECORD_ID);
        assertThat(dctx.getValue().getCallerNumber()).isEqualTo(CALLER);
    }

    @Test
    void hotspotPriorityWithoutLevelUsesSinkFloor()
    {
        when(hotspotSuppressService.matchInbound(CALLER, null, UUID, DNIS)).thenReturn(suppress("PRIORITY", null));
        when(agentDispatchService.dispatch(eq(1L), any(DispatchContext.class)))
                .thenReturn(DispatchResult.queued(33L, 1, 1, 1L, "默认组"));

        handler.handleIncomingCall(ctx());

        ArgumentCaptor<DispatchContext> dctx = ArgumentCaptor.forClass(DispatchContext.class);
        verify(agentDispatchService).dispatch(eq(1L), dctx.capture());
        assertThat(dctx.getValue().getPriority()).isEqualTo(-100);
    }

    @Test
    void hotspotDisabledSkipsMatch()
    {
        ReflectionTestUtils.setField(handler, "hotspotEnabled", false);
        when(agentDispatchService.dispatch(eq(1L), any(DispatchContext.class)))
                .thenReturn(DispatchResult.queued(33L, 1, 1, 1L, "默认组"));

        handler.handleIncomingCall(ctx());

        verify(hotspotSuppressService, never()).matchInbound(any(), any(), any(), any());
        verify(agentDispatchService).dispatch(eq(1L), any(DispatchContext.class));
    }

    // ---------- 话单与分流 ----------

    @Test
    void createsRecordAndFallsBackToDnisKey()
    {
        Map<String, Object> ctx = ctx();
        ctx.remove("callee");
        ctx.put("dnis", DNIS);
        when(agentDispatchService.dispatch(eq(1L), any(DispatchContext.class)))
                .thenReturn(DispatchResult.queued(33L, 1, 1, 1L, "默认组"));

        handler.handleIncomingCall(ctx);

        ArgumentCaptor<AiCallRecord> record = ArgumentCaptor.forClass(AiCallRecord.class);
        verify(callRecordService).insertAiCallRecord(record.capture());
        assertThat(record.getValue().getCallerNumber()).isEqualTo(CALLER);
        assertThat(record.getValue().getCallUuid()).isEqualTo(UUID);
        assertThat(record.getValue().getStatus()).isEqualTo("0");
        assertThat(record.getValue().getRemark()).contains("dnis=" + DNIS);
        assertThat(record.getValue().getCreateBy()).isEqualTo("esl-inbound");
        verify(metrics).incrementCall("inbound");
    }

    @Test
    void robotTakeoverSkipsDispatch()
    {
        when(frontDoorDecider.shouldRobotTakeOver(CALLER)).thenReturn(true);

        handler.handleIncomingCall(ctx());

        verify(metrics).incrementCall("robot_front_door");
        ArgumentCaptor<Map> data = ArgumentCaptor.forClass(Map.class);
        verify(callEventPublisher).broadcast(eq("ROBOT_TAKEOVER"), data.capture());
        assertThat(data.getValue()).containsEntry("recordId", RECORD_ID);
        verifyNoInteractions(agentDispatchService);
    }

    // ---------- 坐席分配 ----------

    @Test
    void assignedAgentGetsScreenPopAndBridge()
    {
        AiCallTrunk trunk = trunk();
        ICallGatewayAdapter gatewayAdapter = mock(ICallGatewayAdapter.class);
        when(agentDispatchService.dispatch(eq(1L), any(DispatchContext.class)))
                .thenReturn(DispatchResult.assigned(7L, "小王", 1L, "默认组", "ROUND_ROBIN"));
        when(agentStatusService.selectAiCallAgentStatusByAgentId(7L)).thenReturn(agent("8001"));
        when(trunkMapper.selectAiCallTrunkList(any())).thenReturn(Collections.singletonList(trunk));
        when(gatewayFactory.get(trunk)).thenReturn(gatewayAdapter);
        when(gatewayAdapter.bridgeToAgent(trunk, UUID, "8001")).thenReturn(true);

        handler.handleIncomingCall(ctx());

        ArgumentCaptor<AiCallRecord> update = ArgumentCaptor.forClass(AiCallRecord.class);
        verify(callEventDispatcher).updateCallRecordAsync(update.capture());
        assertThat(update.getValue().getRecordId()).isEqualTo(RECORD_ID);
        assertThat(update.getValue().getAgentId()).isEqualTo(7L);

        ArgumentCaptor<Map> data = ArgumentCaptor.forClass(Map.class);
        verify(callEventPublisher).publishToUser(eq(3L), eq("INBOUND_RING"), data.capture());
        assertThat(data.getValue())
                .containsEntry("agentName", "小王")
                .containsEntry("extension", "8001")
                .containsEntry("recordId", RECORD_ID);
        verify(gatewayAdapter).bridgeToAgent(trunk, UUID, "8001");
        verify(metrics, never()).incrementCall("queued");
    }

    @Test
    void fallsBackToConventionExtensionAndBroadcast()
    {
        AiCallTrunk trunk = trunk();
        ICallGatewayAdapter gatewayAdapter = mock(ICallGatewayAdapter.class);
        when(agentDispatchService.dispatch(eq(1L), any(DispatchContext.class)))
                .thenReturn(DispatchResult.assigned(7L, "小王", 1L, "默认组", "ROUND_ROBIN"));
        when(agentStatusService.selectAiCallAgentStatusByAgentId(7L)).thenReturn(null);
        when(trunkMapper.selectAiCallTrunkList(any())).thenReturn(Collections.singletonList(trunk));
        when(gatewayFactory.get(trunk)).thenReturn(gatewayAdapter);

        handler.handleIncomingCall(ctx());

        ArgumentCaptor<Map> data = ArgumentCaptor.forClass(Map.class);
        verify(callEventPublisher).broadcast(eq("INBOUND_RING"), data.capture());
        assertThat(data.getValue()).containsEntry("extension", "1007");
        verify(gatewayAdapter).bridgeToAgent(trunk, UUID, "1007");
    }

    @Test
    void blankSipExtensionFallsBackToConvention()
    {
        ICallGatewayAdapter gatewayAdapter = mock(ICallGatewayAdapter.class);
        when(agentDispatchService.dispatch(eq(1L), any(DispatchContext.class)))
                .thenReturn(DispatchResult.assigned(7L, "小王", 1L, "默认组", "ROUND_ROBIN"));
        when(agentStatusService.selectAiCallAgentStatusByAgentId(7L)).thenReturn(agent(" "));
        when(trunkMapper.selectAiCallTrunkList(any())).thenReturn(Collections.singletonList(trunk()));
        when(gatewayFactory.get(any())).thenReturn(gatewayAdapter);

        handler.handleIncomingCall(ctx());

        verify(gatewayAdapter).bridgeToAgent(any(), eq(UUID), eq("1007"));
    }

    @Test
    void trunkQueryFailureDoesNotBreakAssignment()
    {
        when(agentDispatchService.dispatch(eq(1L), any(DispatchContext.class)))
                .thenReturn(DispatchResult.assigned(7L, "小王", 1L, "默认组", "ROUND_ROBIN"));
        when(agentStatusService.selectAiCallAgentStatusByAgentId(7L)).thenReturn(agent("8001"));
        when(trunkMapper.selectAiCallTrunkList(any())).thenThrow(new IllegalStateException("db down"));

        handler.handleIncomingCall(ctx());

        verify(callEventPublisher).publishToUser(eq(3L), eq("INBOUND_RING"), any());
        verifyNoInteractions(gatewayFactory);
    }

    @Test
    void bridgeFailureDoesNotBreakAssignment()
    {
        AiCallTrunk trunk = trunk();
        ICallGatewayAdapter gatewayAdapter = mock(ICallGatewayAdapter.class);
        when(agentDispatchService.dispatch(eq(1L), any(DispatchContext.class)))
                .thenReturn(DispatchResult.assigned(7L, "小王", 1L, "默认组", "ROUND_ROBIN"));
        when(agentStatusService.selectAiCallAgentStatusByAgentId(7L)).thenReturn(agent("8001"));
        when(trunkMapper.selectAiCallTrunkList(any())).thenReturn(Collections.singletonList(trunk));
        when(gatewayFactory.get(trunk)).thenReturn(gatewayAdapter);
        when(gatewayAdapter.bridgeToAgent(any(), any(), any())).thenThrow(new IllegalStateException("bridge down"));

        handler.handleIncomingCall(ctx());

        verify(callEventPublisher).publishToUser(eq(3L), eq("INBOUND_RING"), any());
    }

    @Test
    void assignedWorksWithoutPublisher()
    {
        ReflectionTestUtils.setField(handler, "callEventPublisher", null);
        AiCallTrunk trunk = trunk();
        ICallGatewayAdapter gatewayAdapter = mock(ICallGatewayAdapter.class);
        when(agentDispatchService.dispatch(eq(1L), any(DispatchContext.class)))
                .thenReturn(DispatchResult.assigned(7L, "小王", 1L, "默认组", "ROUND_ROBIN"));
        when(agentStatusService.selectAiCallAgentStatusByAgentId(7L)).thenReturn(agent("8001"));
        when(trunkMapper.selectAiCallTrunkList(any())).thenReturn(Collections.singletonList(trunk));
        when(gatewayFactory.get(trunk)).thenReturn(gatewayAdapter);

        handler.handleIncomingCall(ctx());

        verify(gatewayAdapter).bridgeToAgent(trunk, UUID, "8001");
    }

    // ---------- 技能组与排队 ----------

    @Test
    void matchesSkillGroupByDnis()
    {
        AiSkillGroup group = new AiSkillGroup();
        group.setGroupId(9L);
        group.setGroupCode(DNIS);
        when(skillGroupService.selectEnabledGroups()).thenReturn(Collections.singletonList(group));
        when(agentDispatchService.dispatch(eq(9L), any(DispatchContext.class)))
                .thenReturn(DispatchResult.queued(33L, 1, 1, 9L, "热线组"));

        handler.handleIncomingCall(ctx());

        verify(agentDispatchService).dispatch(eq(9L), any(DispatchContext.class));
    }

    @Test
    void usesDefaultGroupWhenDnisMissing()
    {
        Map<String, Object> ctx = ctx();
        ctx.remove("callee");
        when(agentDispatchService.dispatch(eq(1L), any(DispatchContext.class)))
                .thenReturn(DispatchResult.queued(33L, 1, 1, 1L, "默认组"));

        handler.handleIncomingCall(ctx);

        verifyNoInteractions(skillGroupService);
        verify(agentDispatchService).dispatch(eq(1L), any(DispatchContext.class));
    }

    @Test
    void queuedCallBroadcastsPosition()
    {
        when(agentDispatchService.dispatch(eq(1L), any(DispatchContext.class)))
                .thenReturn(DispatchResult.queued(33L, 2, 5, 1L, "默认组"));

        handler.handleIncomingCall(ctx());

        verify(metrics).incrementCall("queued");
        ArgumentCaptor<Map> data = ArgumentCaptor.forClass(Map.class);
        verify(callEventPublisher).broadcast(eq("QUEUED"), data.capture());
        assertThat(data.getValue())
                .containsEntry("queueId", 33L)
                .containsEntry("queuePosition", 2)
                .containsEntry("message", "无空闲坐席，已进入排队，当前位置第2位");
    }

    @Test
    void recordInsertFailureIsSwallowed()
    {
        doThrow(new IllegalStateException("db down")).when(callRecordService).insertAiCallRecord(any());

        handler.handleIncomingCall(ctx());

        verify(metrics).incrementCall("inbound");
        verifyNoInteractions(agentDispatchService);
    }

    private static Map<String, Object> ctx()
    {
        Map<String, Object> ctx = new HashMap<>();
        ctx.put("uuid", UUID);
        ctx.put("caller", CALLER);
        ctx.put("callee", DNIS);
        ctx.put("host", "10.0.0.1");
        return ctx;
    }

    private static AiHotspotSuppress suppress(String action, Integer level)
    {
        AiHotspotSuppress suppress = new AiHotspotSuppress();
        suppress.setRuleName("骚扰规则");
        suppress.setAction(action);
        suppress.setPriorityLevel(level);
        return suppress;
    }

    private static AiCallAgentStatus agent(String extension)
    {
        AiCallAgentStatus agent = new AiCallAgentStatus();
        agent.setAgentId(7L);
        agent.setUserId(3L);
        agent.setSipExtension(extension);
        return agent;
    }

    private static AiCallTrunk trunk()
    {
        AiCallTrunk trunk = new AiCallTrunk();
        trunk.setVendor("FREESWITCH");
        trunk.setGatewayHost("10.0.0.1");
        trunk.setTrunkCode("FS-1");
        return trunk;
    }
}
