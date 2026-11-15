package ai.lawyers.system.service.impl.lawyers.ivr.engine.node;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Collections;
import java.util.Deque;
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
 * start 开始节点单测：START 步骤记录与沿默认连线前进。
 *
 * @author ai-lawyers
 */
class StartNodeExecutorTest
{
    private StartNodeExecutor executor;

    @BeforeEach
    void setUp()
    {
        executor = new StartNodeExecutor();
    }

    @Test
    void supportedTypesOnlyStart()
    {
        assertThat(StartNodeExecutor.NODE_TYPE).isEqualTo("start");
        assertThat(executor.supportedTypes()).containsExactly("start");
    }

    @Test
    void executeRecordsStartStep()
    {
        IvrNodeContext ctx = context(new HashMap<>(), Collections.emptyList(), new HashMap<>());

        AiIvrNode next = executor.execute(ctx);

        assertThat(ctx.getStep().getAction()).isEqualTo("START");
        assertThat(ctx.getStep().getDetail()).isEqualTo("流程开始");
        assertThat(ctx.getResult().getSteps()).containsExactly(ctx.getStep());
        assertThat(next).isNull();
    }

    @Test
    void executeMovesToDefaultEdgeTarget()
    {
        AiIvrNode target = node(2L);
        Map<Long, AiIvrNode> nodeMap = Collections.singletonMap(2L, target);
        List<AiIvrEdge> edges = Collections.singletonList(edge(2L));

        AiIvrNode next = executor.execute(context(new HashMap<>(), edges, nodeMap));

        assertThat(next).isSameAs(target);
    }

    @Test
    void executeFollowsConditionalEdgeWhenMatched()
    {
        AiIvrNode target = node(3L);
        Map<Long, AiIvrNode> nodeMap = Collections.singletonMap(3L, target);
        AiIvrEdge matched = edge(3L);
        matched.setConditionExpr("vip == true");
        List<AiIvrEdge> edges = Arrays.asList(edge(2L), matched);
        Map<String, Object> vars = new HashMap<>();
        vars.put("vip", true);

        AiIvrNode next = executor.execute(context(vars, edges, nodeMap));

        assertThat(next).isSameAs(target);
    }

    @Test
    void executeDoesNotConsumeInputs()
    {
        Deque<String> inputs = new ArrayDeque<>(Collections.singletonList("1"));
        IvrNodeContext ctx = new IvrNodeContext(new HashMap<>(), Collections.emptyList(),
                new HashMap<>(), inputs, new IvrExecuteRequest(), "IVR-T", new AiIvrFlow(),
                new IvrExecuteResult(), r -> null);
        ctx.setCurrent(node(1L));
        ctx.setStep(new IvrNodeStep());

        executor.execute(ctx);

        assertThat(inputs).containsExactly("1");
    }

    private IvrNodeContext context(Map<String, Object> vars, List<AiIvrEdge> edges,
                                   Map<Long, AiIvrNode> nodeMap)
    {
        IvrNodeContext ctx = new IvrNodeContext(nodeMap, edges, vars, new ArrayDeque<>(),
                new IvrExecuteRequest(), "IVR-T", new AiIvrFlow(), new IvrExecuteResult(), r -> null);
        ctx.setCurrent(node(1L));
        ctx.setStep(new IvrNodeStep());
        return ctx;
    }

    private AiIvrNode node(Long nodeId)
    {
        AiIvrNode node = new AiIvrNode();
        node.setNodeId(nodeId);
        node.setFlowId(10L);
        node.setNodeType(StartNodeExecutor.NODE_TYPE);
        node.setNodeName("开始");
        return node;
    }

    private AiIvrEdge edge(Long targetId)
    {
        AiIvrEdge edge = new AiIvrEdge();
        edge.setEdgeId(1L);
        edge.setSourceNodeId(1L);
        edge.setTargetNodeId(targetId);
        edge.setSortOrder(1);
        return edge;
    }
}
