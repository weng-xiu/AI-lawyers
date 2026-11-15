package ai.lawyers.system.service.impl.lawyers.ivr.engine.node;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ai.lawyers.system.domain.lawyers.ivr.AiIvrEdge;
import ai.lawyers.system.domain.lawyers.ivr.AiIvrFlow;
import ai.lawyers.system.domain.lawyers.ivr.AiIvrNode;
import ai.lawyers.system.domain.lawyers.ivr.IvrExecuteRequest;
import ai.lawyers.system.domain.lawyers.ivr.IvrExecuteResult;
import ai.lawyers.system.domain.lawyers.ivr.IvrNodeStep;

/**
 * condition 条件分支节点单测：SpEL 优先、默认连线兜底、无连线返回 null。
 *
 * @author ai-lawyers
 */
class ConditionNodeExecutorTest
{
    private ConditionNodeExecutor executor;

    @BeforeEach
    void setUp()
    {
        executor = new ConditionNodeExecutor();
    }

    @Test
    void routesByFirstMatchingExpression()
    {
        Map<Long, AiIvrNode> nodeMap = new HashMap<>();
        nodeMap.put(2L, node(2L));
        nodeMap.put(3L, node(3L));
        nodeMap.put(4L, node(4L));
        List<AiIvrEdge> edges = Arrays.asList(
                edge(1, 2, "score > 90", null),
                edge(2, 3, "score > 50", null),
                edge(3, 4, null, null));
        Map<String, Object> vars = new HashMap<>();
        vars.put("score", 95);
        IvrNodeContext ctx = context(node(1L), edges, nodeMap, vars);

        AiIvrNode next = executor.execute(ctx);

        assertThat(next.getNodeId()).isEqualTo(2L);
        assertThat(ctx.getStep().getAction()).isEqualTo("CONDITION");
        assertThat(ctx.getStep().getDetail()).contains("score > 90");
        assertThat(ctx.getResult().getSteps()).containsExactly(ctx.getStep());
    }

    @Test
    void fallsBackToDefaultEdge()
    {
        Map<Long, AiIvrNode> nodeMap = new HashMap<>();
        nodeMap.put(3L, node(3L));
        nodeMap.put(4L, node(4L));
        List<AiIvrEdge> edges = Arrays.asList(
                edge(1, 3, "score > 90", null),
                edge(2, 4, null, null));
        Map<String, Object> vars = new HashMap<>();
        vars.put("score", 10);
        IvrNodeContext ctx = context(node(1L), edges, nodeMap, vars);

        AiIvrNode next = executor.execute(ctx);

        assertThat(next.getNodeId()).isEqualTo(4L);
        assertThat(ctx.getStep().getDetail()).contains("默认");
    }

    @Test
    void labelOnlyEdgeWithoutInputDoesNotMatch()
    {
        Map<Long, AiIvrNode> nodeMap = new HashMap<>();
        nodeMap.put(2L, node(2L));
        List<AiIvrEdge> edges = Collections.singletonList(edge(1, 2, null, "转人工"));
        IvrNodeContext ctx = context(node(1L), edges, nodeMap, new HashMap<>());

        AiIvrNode next = executor.execute(ctx);

        // 条件节点不消费按键输入，仅有标签的连线不会被命中
        assertThat(next).isNull();
        assertThat(ctx.getStep().getDetail()).contains("无命中(默认/无连线)");
    }

    @Test
    void noOutgoingEdge_returnsNull()
    {
        IvrNodeContext ctx = context(node(1L), Collections.emptyList(), new HashMap<>(), new HashMap<>());

        AiIvrNode next = executor.execute(ctx);

        assertThat(next).isNull();
        assertThat(ctx.getStep().getDetail()).contains("无命中(默认/无连线)");
        assertThat(ctx.getResult().getSteps()).hasSize(1);
    }

    @Test
    void invalidExpressionIgnoredFallsBackToDefault()
    {
        Map<Long, AiIvrNode> nodeMap = new HashMap<>();
        nodeMap.put(2L, node(2L));
        nodeMap.put(3L, node(3L));
        List<AiIvrEdge> edges = Arrays.asList(
                edge(1, 2, "score > > 90", null),
                edge(2, 3, null, null));
        IvrNodeContext ctx = context(node(1L), edges, nodeMap, new HashMap<>());

        AiIvrNode next = executor.execute(ctx);

        assertThat(next.getNodeId()).isEqualTo(3L);
    }

    @Test
    void stringTrueExpressionMatches()
    {
        Map<Long, AiIvrNode> nodeMap = new HashMap<>();
        nodeMap.put(2L, node(2L));
        List<AiIvrEdge> edges = Collections.singletonList(edge(1, 2, "'yes' == 'yes'", null));
        IvrNodeContext ctx = context(node(1L), edges, nodeMap, new HashMap<>());

        assertThat(executor.execute(ctx).getNodeId()).isEqualTo(2L);
    }

    private IvrNodeContext context(AiIvrNode current, List<AiIvrEdge> edges,
                                   Map<Long, AiIvrNode> nodeMap, Map<String, Object> vars)
    {
        IvrNodeContext ctx = new IvrNodeContext(nodeMap, edges, vars, new ArrayDeque<>(),
                new IvrExecuteRequest(), "IVR-T", new AiIvrFlow(), new IvrExecuteResult(), r -> null);
        ctx.setCurrent(current);
        ctx.setStep(new IvrNodeStep());
        return ctx;
    }

    private AiIvrNode node(Long nodeId)
    {
        AiIvrNode node = new AiIvrNode();
        node.setNodeId(nodeId);
        node.setNodeName("节点" + nodeId);
        node.setNodeType("condition");
        return node;
    }

    private AiIvrEdge edge(long edgeId, long targetId, String conditionExpr, String label)
    {
        AiIvrEdge edge = new AiIvrEdge();
        edge.setEdgeId(edgeId);
        edge.setSourceNodeId(1L);
        edge.setTargetNodeId(targetId);
        edge.setConditionExpr(conditionExpr);
        edge.setEdgeLabel(label);
        edge.setSortOrder((int) edgeId);
        return edge;
    }
}
