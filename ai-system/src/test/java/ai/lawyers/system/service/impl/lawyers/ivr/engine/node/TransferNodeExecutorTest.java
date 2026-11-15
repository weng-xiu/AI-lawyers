package ai.lawyers.system.service.impl.lawyers.ivr.engine.node;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import ai.lawyers.system.domain.lawyers.ivr.AiIvrEdge;
import ai.lawyers.system.domain.lawyers.ivr.AiIvrNode;
import ai.lawyers.system.domain.lawyers.ivr.IvrExecuteRequest;
import ai.lawyers.system.domain.lawyers.ivr.IvrExecuteResult;
import ai.lawyers.system.domain.lawyers.ivr.IvrNodeStep;
import ai.lawyers.system.domain.lawyers.skill.DispatchContext;
import ai.lawyers.system.domain.lawyers.skill.DispatchResult;
import ai.lawyers.system.service.lawyers.skill.IAgentDispatchService;

/**
 * B5：{@link TransferNodeExecutor} 转接节点执行器测试。
 *
 * <p>覆盖静态转接目标解析优先级（分机/坐席ID/号码/目标/默认）、智能分配（固定技能组、
 * 分类回退、自定义分类变量、优先级透传、排队续行、分配失败回落）以及流程变量回写
 * 与终止标记。</p>
 *
 * @author ai-lawyers
 */
class TransferNodeExecutorTest
{
    private IAgentDispatchService agentDispatchService;

    private TransferNodeExecutor executor;

    private final Map<Long, AiIvrNode> nodeMap = new HashMap<>();

    private final List<AiIvrEdge> edges = new ArrayList<>();

    @BeforeEach
    void setUp()
    {
        agentDispatchService = mock(IAgentDispatchService.class);
        executor = new TransferNodeExecutor(agentDispatchService);
        nodeMap.clear();
        edges.clear();
    }

    @Test
    void supportsAgentAndTransferNodes()
    {
        assertThat(executor.supportedTypes())
                .containsExactly(TransferNodeExecutor.NODE_AGENT, TransferNodeExecutor.NODE_TRANSFER);
    }

    // ---------- 静态目标 ----------

    @Test
    void staticAgentExtensionWins()
    {
        AiIvrNode node = node("agent", "{\"agentExtension\":\"8001\",\"agentId\":7,\"number\":\"12345\"}");
        IvrNodeContext ctx = context(node);

        AiIvrNode next = executor.execute(ctx);

        assertThat(next).isNull();
        assertThat(ctx.getResult().getTransferTarget()).isEqualTo("坐席分机 8001");
        assertThat(ctx.getStep().getAction()).isEqualTo("TRANSFER");
        assertThat(ctx.getStep().getDetail()).isEqualTo("转人工坐席：坐席分机 8001");
        assertThat(ctx.getVariables()).containsEntry("__terminated", "transfer");
        verifyNoInteractions(agentDispatchService);
    }

    @Test
    void staticAgentIdUsedWhenExtensionAbsent()
    {
        IvrNodeContext ctx = context(node("agent", "{\"agentId\":7}"));

        executor.execute(ctx);

        assertThat(ctx.getResult().getTransferTarget()).isEqualTo("坐席ID 7");
    }

    @Test
    void staticNumberFallsBackToOutboundLabel()
    {
        IvrNodeContext ctx = context(node("transfer", "{\"number\":\"12345\"}"));

        executor.execute(ctx);

        assertThat(ctx.getResult().getTransferTarget()).isEqualTo("12345");
        assertThat(ctx.getStep().getDetail()).isEqualTo("转外线：12345");
    }

    @Test
    void staticTargetSupported()
    {
        IvrNodeContext ctx = context(node("transfer", "{\"target\":\"总机\"}"));

        executor.execute(ctx);

        assertThat(ctx.getResult().getTransferTarget()).isEqualTo("总机");
    }

    @Test
    void unknownOrInvalidConfigFallsBackToManualSeat()
    {
        IvrNodeContext blank = context(node("agent", "{\"foo\":\"bar\"}"));
        executor.execute(blank);
        assertThat(blank.getResult().getTransferTarget()).isEqualTo("人工坐席");

        IvrNodeContext invalid = context(node("agent", "not-json"));
        executor.execute(invalid);
        assertThat(invalid.getResult().getTransferTarget()).isEqualTo("人工坐席");
    }

    @Test
    void nullNodeTypeTreatedAsTransfer()
    {
        IvrNodeContext ctx = context(node(null, "{\"number\":\"95588\"}"));

        executor.execute(ctx);

        assertThat(ctx.getStep().getDetail()).isEqualTo("转外线：95588");
    }

    // ---------- 智能分配 ----------

    @Test
    void dispatchByGroupWritesVariablesAndTransfers()
    {
        AiIvrNode node = node("agent", "{\"dispatchMode\":\"dispatch\",\"groupId\":9}");
        IvrNodeContext ctx = context(node);
        ctx.getVariables().put("dispatchPriority", 20);
        when(agentDispatchService.dispatch(eq(9L), any(DispatchContext.class)))
                .thenReturn(DispatchResult.assigned(7L, "小王", 9L, "热线组", "ROUND_ROBIN"));

        AiIvrNode next = executor.execute(ctx);

        assertThat(next).isNull();
        assertThat(ctx.getResult().getTransferTarget()).isEqualTo("坐席ID 7");
        assertThat(ctx.getStep().getAction()).isEqualTo("TRANSFER");
        assertThat(ctx.getStep().getDetail()).isEqualTo("转人工坐席：坐席ID 7");
        assertThat(ctx.getVariables())
                .containsEntry("dispatchSuccess", true)
                .containsEntry("dispatchAgentId", 7L)
                .containsEntry("dispatchAgentName", "小王")
                .containsEntry("dispatchGroupId", 9L)
                .containsEntry("dispatchGroupName", "热线组")
                .containsEntry("__terminated", "transfer");

        ArgumentCaptor<DispatchContext> dispatchCtx = ArgumentCaptor.forClass(DispatchContext.class);
        verify(agentDispatchService).dispatch(eq(9L), dispatchCtx.capture());
        assertThat(dispatchCtx.getValue().getRecordId()).isEqualTo(55L);
        assertThat(dispatchCtx.getValue().getCallerNumber()).isEqualTo("13800138000");
        assertThat(dispatchCtx.getValue().getSessionId()).isEqualTo("sess-1");
        assertThat(dispatchCtx.getValue().getPriority()).isEqualTo(20);
        assertThat(dispatchCtx.getValue().isEnqueueIfNoAgent()).isTrue();
    }

    @Test
    void nonNumericPriorityIsIgnored()
    {
        IvrNodeContext ctx = context(node("agent", "{\"dispatchMode\":\"dispatch\",\"groupId\":9}"));
        ctx.getVariables().put("dispatchPriority", "high");
        when(agentDispatchService.dispatch(eq(9L), any(DispatchContext.class)))
                .thenReturn(DispatchResult.assigned(7L, "小王", 9L, "热线组", "RR"));

        executor.execute(ctx);

        ArgumentCaptor<DispatchContext> dispatchCtx = ArgumentCaptor.forClass(DispatchContext.class);
        verify(agentDispatchService).dispatch(eq(9L), dispatchCtx.capture());
        assertThat(dispatchCtx.getValue().getPriority()).isZero();
    }

    @Test
    void dispatchByCategoryVariable()
    {
        IvrNodeContext ctx = context(node("agent", "{\"dispatchMode\":\"dispatch\"}"));
        ctx.getVariables().put("agentCategoryId", 12L);
        when(agentDispatchService.dispatchByCategory(eq(12L), any(DispatchContext.class)))
                .thenReturn(DispatchResult.assigned(7L, "小王", null, null, "RR"));

        executor.execute(ctx);

        verify(agentDispatchService).dispatchByCategory(eq(12L), any(DispatchContext.class));
        // 技能组信息缺失时不回写分组变量
        assertThat(ctx.getVariables()).doesNotContainKey("dispatchGroupId");
    }

    @Test
    void dispatchHonorsCustomCategoryVariable()
    {
        IvrNodeContext ctx = context(node("agent",
                "{\"dispatchMode\":\"dispatch\",\"categoryVar\":\"catId\"}"));
        ctx.getVariables().put("catId", 15);
        when(agentDispatchService.dispatchByCategory(eq(15L), any(DispatchContext.class)))
                .thenReturn(DispatchResult.assigned(7L, "小王", null, null, "RR"));

        executor.execute(ctx);

        verify(agentDispatchService).dispatchByCategory(eq(15L), any(DispatchContext.class));
    }

    @Test
    void missingGroupAndCategoryFallsBackToConfiguredTarget()
    {
        IvrNodeContext ctx = context(node("agent",
                "{\"dispatchMode\":\"dispatch\",\"fallbackTarget\":\"总机\"}"));

        AiIvrNode next = executor.execute(ctx);

        assertThat(next).isNull();
        assertThat(ctx.getResult().getTransferTarget()).isEqualTo("总机");
        assertThat(ctx.getStep().getAction()).isEqualTo("TRANSFER");
        assertThat(ctx.getStep().getDetail()).isEqualTo("转人工坐席：总机");
        verifyNoInteractions(agentDispatchService);
    }

    @Test
    void missingCategoryUsesDefaultManualSeat()
    {
        IvrNodeContext ctx = context(node("agent", "{\"dispatchMode\":\"dispatch\"}"));

        executor.execute(ctx);

        assertThat(ctx.getResult().getTransferTarget()).isEqualTo("人工坐席");
    }

    @Test
    void queuedDispatchContinuesAlongQueueEdge()
    {
        AiIvrNode node = node("agent", "{\"dispatchMode\":\"dispatch\",\"groupId\":9}");
        IvrNodeContext ctx = context(node);
        AiIvrNode queuedNode = node(2L, "say", "{}");
        nodeMap.put(2L, queuedNode);
        edges.add(edge(1L, 2L, null));
        when(agentDispatchService.dispatch(eq(9L), any(DispatchContext.class)))
                .thenReturn(DispatchResult.queued(33L, 2, 5, 9L, "热线组"));

        AiIvrNode next = executor.execute(ctx);

        assertThat(next).isSameAs(queuedNode);
        assertThat(ctx.getVariables())
                .containsEntry("dispatchQueued", true)
                .containsEntry("dispatchQueueId", 33L)
                .containsEntry("dispatchQueuePosition", 2);
        assertThat(ctx.getStep().getAction()).isEqualTo("QUEUE");
        assertThat(ctx.getStep().getDetail()).contains("第2位").contains("技能组：热线组");
        assertThat(ctx.getResult().getSteps()).containsExactly(ctx.getStep());
        assertThat(ctx.getResult().getTransferTarget()).isNull();
    }

    @Test
    void queueDisabledIsPassedToDispatcher()
    {
        IvrNodeContext ctx = context(node("agent",
                "{\"dispatchMode\":\"dispatch\",\"groupId\":9,\"enqueueIfNoAgent\":false}"));
        when(agentDispatchService.dispatch(eq(9L), any(DispatchContext.class)))
                .thenReturn(DispatchResult.queued(33L, 1, 1, 9L, "热线组"));

        executor.execute(ctx);

        ArgumentCaptor<DispatchContext> dispatchCtx = ArgumentCaptor.forClass(DispatchContext.class);
        verify(agentDispatchService).dispatch(eq(9L), dispatchCtx.capture());
        assertThat(dispatchCtx.getValue().isEnqueueIfNoAgent()).isFalse();
    }

    @Test
    void dispatchFailureFallsBackToManualSeat()
    {
        IvrNodeContext ctx = context(node("agent", "{\"dispatchMode\":\"dispatch\",\"groupId\":9}"));
        when(agentDispatchService.dispatch(eq(9L), any(DispatchContext.class)))
                .thenReturn(DispatchResult.failed("无可用坐席"));

        executor.execute(ctx);

        assertThat(ctx.getResult().getTransferTarget()).isEqualTo("人工坐席");
        assertThat(ctx.getStep().getAction()).isEqualTo("TRANSFER");
        assertThat(ctx.getStep().getDetail()).isEqualTo("转人工坐席：人工坐席");
        assertThat(ctx.getVariables()).containsEntry("dispatchSuccess", false);
    }

    private IvrNodeContext context(AiIvrNode node)
    {
        IvrExecuteRequest request = new IvrExecuteRequest();
        request.setRecordId(55L);
        request.setCallerNumber("13800138000");
        IvrExecuteResult result = new IvrExecuteResult();
        IvrNodeStep step = new IvrNodeStep(node.getNodeId(), node.getNodeType(), "转接", null, null);
        nodeMap.put(node.getNodeId(), node);
        IvrNodeContext ctx = new IvrNodeContext(nodeMap, edges, new HashMap<>(), new ArrayDeque<>(),
                request, "sess-1", null, result, req -> null);
        ctx.setCurrent(node);
        ctx.setStep(step);
        return ctx;
    }

    private static AiIvrNode node(String type, String config)
    {
        return node(1L, type, config);
    }

    private static AiIvrNode node(Long nodeId, String type, String config)
    {
        AiIvrNode node = new AiIvrNode();
        node.setNodeId(nodeId);
        node.setNodeType(type);
        node.setNodeConfig(config);
        return node;
    }

    private static AiIvrEdge edge(Long source, Long target, String label)
    {
        AiIvrEdge edge = new AiIvrEdge();
        edge.setSourceNodeId(source);
        edge.setTargetNodeId(target);
        edge.setEdgeLabel(label);
        edge.setSortOrder(0);
        return edge;
    }
}
