package ai.lawyers.system.service.impl.lawyers.ivr.engine.node;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import ai.lawyers.system.domain.lawyers.ivr.AiIvrEdge;
import ai.lawyers.system.domain.lawyers.ivr.AiIvrFlow;
import ai.lawyers.system.domain.lawyers.ivr.AiIvrNode;
import ai.lawyers.system.domain.lawyers.ivr.IvrExecuteRequest;
import ai.lawyers.system.domain.lawyers.ivr.IvrExecuteResult;
import ai.lawyers.system.domain.lawyers.ivr.IvrNodeStep;
import ai.lawyers.system.service.lawyers.ivr.IAiIvrFlowService;

/**
 * T1-3：{@link ChildNodeExecutor} 子流程节点执行器测试。
 *
 * <p>覆盖嵌套深度上限、flowId/flowCode 两种子流程解析、子流程缺失、子请求字段与
 * 深度透传、变量合并与 childResult 摘要、失败/异常降级、子流程挂断/转接冒泡终止
 * 父流程以及意图分类回填。</p>
 *
 * @author ai-lawyers
 */
class ChildNodeExecutorTest
{
    private IAiIvrFlowService flowService;

    private ChildNodeExecutor executor;

    private final AtomicReference<IvrExecuteRequest> subRequest = new AtomicReference<>();

    @BeforeEach
    void setUp()
    {
        flowService = mock(IAiIvrFlowService.class);
        executor = new ChildNodeExecutor(flowService);
        subRequest.set(null);
    }

    @Test
    void supportsChildNode()
    {
        assertThat(executor.supportedTypes()).containsExactly(ChildNodeExecutor.NODE_TYPE);
    }

    @Test
    void skipsWhenDepthLimitReached()
    {
        AiIvrNode current = node("{\"flowId\":5}");
        IvrNodeContext ctx = context(current, new HashMap<>(), result -> null);
        ctx.getVariables().put("__childDepth", 3);

        AiIvrNode next = executor.execute(ctx);

        assertThat(next).isSameAs(ctx.getNodeMap().get(2L));
        assertThat(ctx.getStep().getAction()).isEqualTo("CHILD");
        assertThat(ctx.getStep().getDetail()).isEqualTo("子流程嵌套深度超过3层，跳过执行");
        assertThat(ctx.getResult().getSteps()).containsExactly(ctx.getStep());
        verify(flowService, never()).selectAiIvrFlowByFlowId(any());
    }

    @Test
    void resolvesChildByFlowIdAndMergesVariables()
    {
        AiIvrFlow child = flow(5L, "子流程A");
        when(flowService.selectAiIvrFlowByFlowId(5L)).thenReturn(child);
        Map<String, Object> subVariables = new HashMap<>();
        subVariables.put("age", 30);
        IvrExecuteResult subResult = new IvrExecuteResult();
        subResult.setFlowName("子流程A");
        subResult.setSuccess(true);
        subResult.setVariables(subVariables);
        IvrNodeContext ctx = context(node("{\"flowId\":5}"), new HashMap<>(), result ->
        {
            subRequest.set(result);
            return subResult;
        });
        ctx.getVariables().put("city", "杭州");
        ctx.getInputs().add("我要咨询");

        AiIvrNode next = executor.execute(ctx);

        assertThat(next).isSameAs(ctx.getNodeMap().get(2L));
        assertThat(ctx.getVariables())
                .containsEntry("age", 30)
                .containsEntry("city", "杭州")
                .doesNotContainKey("__childDepth")
                .containsKey("childResult");
        assertThat(ctx.getVariables().get("childResult").toString())
                .contains("flowName=子流程A")
                .contains("success=true");
        assertThat(ctx.getStep().getDetail()).isEqualTo("子流程[子流程A]执行成功");

        IvrExecuteRequest sub = subRequest.get();
        assertThat(sub.getFlowId()).isEqualTo(5L);
        assertThat(sub.getSessionId()).isEqualTo("sess-1");
        assertThat(sub.getRecordId()).isEqualTo(55L);
        assertThat(sub.getCallerNumber()).isEqualTo("13800138000");
        assertThat(sub.getCalleeNumber()).isEqualTo("4008001234");
        assertThat(sub.getInputs()).containsExactly("我要咨询");
        assertThat(sub.getVariables()).containsEntry("__childDepth", 1).containsEntry("city", "杭州");
    }

    @Test
    void incrementsParentDepth()
    {
        AiIvrFlow child = flow(5L, "子流程A");
        when(flowService.selectAiIvrFlowByFlowId(5L)).thenReturn(child);
        IvrExecuteResult subResult = new IvrExecuteResult();
        subResult.setSuccess(true);
        subResult.setVariables(new HashMap<>());
        IvrNodeContext ctx = context(node("{\"flowId\":5}"), new HashMap<>(), result ->
        {
            subRequest.set(result);
            return subResult;
        });
        ctx.getVariables().put("__childDepth", 1);

        executor.execute(ctx);

        assertThat(subRequest.get().getVariables()).containsEntry("__childDepth", 2);
    }

    @Test
    void resolvesChildByFlowCode()
    {
        AiIvrFlow child = flow(6L, "按编码子流程");
        when(flowService.selectAiIvrFlowList(any(AiIvrFlow.class)))
                .thenReturn(Arrays.asList(child));
        IvrExecuteResult subResult = new IvrExecuteResult();
        subResult.setSuccess(true);
        subResult.setVariables(new HashMap<>());
        IvrNodeContext ctx = context(node("{\"flowCode\":\"SUB\"}"), new HashMap<>(), result -> subResult);

        executor.execute(ctx);

        verify(flowService).selectAiIvrFlowList(any(AiIvrFlow.class));
        assertThat(ctx.getStep().getDetail()).isEqualTo("子流程[按编码子流程]执行成功");
    }

    @Test
    void missingChildContinuesParentFlow()
    {
        when(flowService.selectAiIvrFlowList(any(AiIvrFlow.class))).thenReturn(new ArrayList<>());
        IvrNodeContext ctx = context(node("{\"flowCode\":\"SUB\"}"), new HashMap<>(), result -> null);

        AiIvrNode next = executor.execute(ctx);

        assertThat(next).isSameAs(ctx.getNodeMap().get(2L));
        assertThat(ctx.getStep().getDetail()).contains("未找到子流程").contains("flowCode=SUB");
        assertThat(ctx.getResult().getSteps()).containsExactly(ctx.getStep());
    }

    @Test
    void flowIdPreferredOverFlowCode()
    {
        when(flowService.selectAiIvrFlowByFlowId(5L)).thenReturn(null);
        IvrNodeContext ctx = context(node("{\"flowId\":5,\"flowCode\":\"SUB\"}"), new HashMap<>(), result -> null);

        executor.execute(ctx);

        verify(flowService, never()).selectAiIvrFlowList(any());
        assertThat(ctx.getStep().getDetail()).contains("flowId=5");
    }

    @Test
    void childFailureKeepsParentAlive()
    {
        AiIvrFlow child = flow(5L, "子流程A");
        when(flowService.selectAiIvrFlowByFlowId(5L)).thenReturn(child);
        IvrExecuteResult subResult = new IvrExecuteResult();
        subResult.setSuccess(false);
        subResult.setMessage("节点缺失");
        subResult.setVariables(new HashMap<>());
        IvrNodeContext ctx = context(node("{\"flowId\":5}"), new HashMap<>(), result -> subResult);

        AiIvrNode next = executor.execute(ctx);

        assertThat(next).isSameAs(ctx.getNodeMap().get(2L));
        assertThat(ctx.getStep().getDetail()).isEqualTo("子流程[子流程A]执行失败：节点缺失");
    }

    @Test
    void childExceptionIsRecordedAndParentContinues()
    {
        AiIvrFlow child = flow(5L, "子流程A");
        when(flowService.selectAiIvrFlowByFlowId(5L)).thenReturn(child);
        IvrNodeContext ctx = context(node("{\"flowId\":5}"), new HashMap<>(), result ->
        {
            throw new IllegalStateException("runner down");
        });

        AiIvrNode next = executor.execute(ctx);

        assertThat(next).isSameAs(ctx.getNodeMap().get(2L));
        assertThat(ctx.getVariables()).containsEntry("childError", "runner down");
        assertThat(ctx.getStep().getDetail()).isEqualTo("子流程[子流程A]执行异常：runner down");
    }

    @Test
    void hangupInChildBubblesUpAndStopsParent()
    {
        AiIvrFlow child = flow(5L, "子流程A");
        when(flowService.selectAiIvrFlowByFlowId(5L)).thenReturn(child);
        Map<String, Object> subVariables = new HashMap<>();
        subVariables.put("__terminated", "hangup");
        subVariables.put("reason", "客户挂断");
        IvrExecuteResult subResult = new IvrExecuteResult();
        subResult.setSuccess(true);
        subResult.setVariables(subVariables);
        subResult.setTransferTarget("坐席分机 8001");
        IvrNodeContext ctx = context(node("{\"flowId\":5}"), new HashMap<>(), result -> subResult);

        AiIvrNode next = executor.execute(ctx);

        assertThat(next).isNull();
        assertThat(ctx.getResult().getTransferTarget()).isEqualTo("坐席分机 8001");
        assertThat(ctx.getVariables())
                .doesNotContainKey("__terminated")
                .containsEntry("reason", "客户挂断");
        assertThat(ctx.getVariables().get("childResult").toString()).contains("terminated=hangup");
        assertThat(ctx.getStep().getDetail())
                .contains("子流程挂断，冒泡终止父流程");
    }

    @Test
    void transferInChildBubblesUpAndCopiesIntention()
    {
        AiIvrFlow child = flow(5L, "子流程A");
        when(flowService.selectAiIvrFlowByFlowId(5L)).thenReturn(child);
        Map<String, Object> subVariables = new HashMap<>();
        subVariables.put("__terminated", "transfer");
        IvrExecuteResult subResult = new IvrExecuteResult();
        subResult.setSuccess(true);
        subResult.setVariables(subVariables);
        subResult.setTransferTarget("坐席ID 7");
        subResult.setMatchedIntention("labor");
        subResult.setMatchedIntentionName("劳动纠纷");
        subResult.setMatchMethod("keyword");
        subResult.setCategoryId(9L);
        subResult.setCategoryName("劳动争议");
        IvrNodeContext ctx = context(node("{\"flowId\":5}"), new HashMap<>(), result -> subResult);

        AiIvrNode next = executor.execute(ctx);

        assertThat(next).isNull();
        assertThat(ctx.getResult().getMatchedIntention()).isEqualTo("labor");
        assertThat(ctx.getResult().getMatchedIntentionName()).isEqualTo("劳动纠纷");
        assertThat(ctx.getResult().getMatchMethod()).isEqualTo("keyword");
        assertThat(ctx.getResult().getCategoryId()).isEqualTo(9L);
        assertThat(ctx.getResult().getCategoryName()).isEqualTo("劳动争议");
        assertThat(ctx.getStep().getDetail()).contains("子流程转接，冒泡终止父流程");
    }

    @Test
    void resultWithoutVariablesFallsThrough()
    {
        AiIvrFlow child = flow(5L, "子流程A");
        when(flowService.selectAiIvrFlowByFlowId(5L)).thenReturn(child);
        IvrExecuteResult subResult = new IvrExecuteResult();
        subResult.setVariables(null);
        IvrNodeContext ctx = context(node("{\"flowId\":5}"), new HashMap<>(), result -> subResult);

        AiIvrNode next = executor.execute(ctx);

        assertThat(next).isSameAs(ctx.getNodeMap().get(2L));
        assertThat(ctx.getResult().getSteps()).containsExactly(ctx.getStep());
    }

    private IvrNodeContext context(AiIvrNode current, Map<String, Object> variables,
                                   java.util.function.Function<IvrExecuteRequest, IvrExecuteResult> runner)
    {
        Map<Long, AiIvrNode> nodeMap = new HashMap<>();
        nodeMap.put(1L, current);
        nodeMap.put(2L, node("say", "{}"));
        List<AiIvrEdge> edges = new ArrayList<>();
        edges.add(edge(1L, 2L, null));
        IvrExecuteRequest request = new IvrExecuteRequest();
        request.setSessionId("sess-1");
        request.setRecordId(55L);
        request.setCallerNumber("13800138000");
        request.setCalleeNumber("4008001234");
        IvrNodeStep step = new IvrNodeStep(1L, current.getNodeType(), "子流程", null, null);
        IvrNodeContext ctx = new IvrNodeContext(nodeMap, edges, variables, new ArrayDeque<>(),
                request, "sess-1", null, new IvrExecuteResult(), runner);
        ctx.setCurrent(current);
        ctx.setStep(step);
        return ctx;
    }

    private static AiIvrNode node(String config)
    {
        return node("child", config);
    }

    private static AiIvrNode node(String type, String config)
    {
        AiIvrNode node = new AiIvrNode();
        node.setNodeId(1L);
        node.setNodeType(type);
        node.setNodeConfig(config);
        node.setNodeName("子流程节点");
        return node;
    }

    private static AiIvrFlow flow(Long flowId, String flowName)
    {
        AiIvrFlow flow = new AiIvrFlow();
        flow.setFlowId(flowId);
        flow.setFlowName(flowName);
        return flow;
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
