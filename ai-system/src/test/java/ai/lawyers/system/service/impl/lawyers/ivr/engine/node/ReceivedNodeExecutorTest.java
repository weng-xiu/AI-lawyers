package ai.lawyers.system.service.impl.lawyers.ivr.engine.node;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayDeque;
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
import ai.lawyers.system.service.impl.lawyers.ivr.engine.IvrTemplateRenderer;

/**
 * received 收号节点单测：结束键剥离、maxDigits 截断、变量写入与按键路由。
 *
 * @author ai-lawyers
 */
class ReceivedNodeExecutorTest
{
    private ReceivedNodeExecutor executor;

    @BeforeEach
    void setUp()
    {
        executor = new ReceivedNodeExecutor(new IvrTemplateRenderer());
    }

    @Test
    void stripsDefaultEndKeyHash()
    {
        Map<String, Object> vars = new HashMap<>();
        IvrNodeContext ctx = context(node("{\"jqrask\":\"请输入工号\"}"), vars, "123#");

        executor.execute(ctx);

        assertThat(vars).containsEntry("dtmf", "123")
                .containsEntry("receivedDigits", "123")
                .containsEntry("lastInput", "123")
                .containsEntry("lastSay", "请输入工号");
        assertThat(ctx.getStep().getAction()).isEqualTo("DTMF_INPUT");
        assertThat(ctx.getStep().getDetail()).contains("收号语：请输入工号").contains("按键输入：123");
    }

    @Test
    void stripsCustomEndKey()
    {
        Map<String, Object> vars = new HashMap<>();
        IvrNodeContext ctx = context(node("{\"endKey\":\"*\"}"), vars, "88*");

        executor.execute(ctx);

        assertThat(vars).containsEntry("dtmf", "88");
    }

    @Test
    void supportsLegacyEndKeyName()
    {
        Map<String, Object> vars = new HashMap<>();
        IvrNodeContext ctx = context(node("{\"end\":\"#\"}"), vars, "456#");

        executor.execute(ctx);

        assertThat(vars).containsEntry("dtmf", "456");
    }

    @Test
    void emptyEndKeyKeepsSuffix()
    {
        Map<String, Object> vars = new HashMap<>();
        IvrNodeContext ctx = context(node("{\"endKey\":\"\"}"), vars, "12#");

        executor.execute(ctx);

        assertThat(vars).containsEntry("dtmf", "12#");
    }

    @Test
    void truncatesToMaxDigits()
    {
        Map<String, Object> vars = new HashMap<>();
        IvrNodeContext ctx = context(node("{\"maxDigits\":4}"), vars, "12345678#");

        executor.execute(ctx);

        assertThat(vars).containsEntry("dtmf", "1234");
    }

    @Test
    void missingInputWritesEmptyValues()
    {
        Map<String, Object> vars = new HashMap<>();
        IvrNodeContext ctx = context(node("{\"prompt\":\"请按键\"}"), vars, null);

        AiIvrNode next = executor.execute(ctx);

        assertThat(vars).containsEntry("dtmf", "").containsEntry("receivedDigits", "");
        assertThat(ctx.getStep().getDetail()).contains("按键输入：");
        assertThat(next).isNull();
    }

    @Test
    void emptyPromptOmitsPromptInDetailAndLastSay()
    {
        Map<String, Object> vars = new HashMap<>();
        IvrNodeContext ctx = context(node("{}"), vars, "1#");

        executor.execute(ctx);

        assertThat(vars).doesNotContainKey("lastSay");
        assertThat(ctx.getStep().getDetail()).startsWith("按键输入：1");
    }

    @Test
    void routesByMatchingDigitsLabel()
    {
        Map<Long, AiIvrNode> nodeMap = new HashMap<>();
        nodeMap.put(2L, node(2L));
        List<AiIvrEdge> edges = Collections.singletonList(edge(2L, "1"));
        Map<String, Object> vars = new HashMap<>();
        IvrNodeContext ctx = context(node("{}"), vars, "1#", edges, nodeMap);

        AiIvrNode next = executor.execute(ctx);

        assertThat(next.getNodeId()).isEqualTo(2L);
    }

    private IvrNodeContext context(AiIvrNode node, Map<String, Object> vars, String input)
    {
        return context(node, vars, input, Collections.emptyList(), new HashMap<>());
    }

    private IvrNodeContext context(AiIvrNode node, Map<String, Object> vars, String input,
                                   List<AiIvrEdge> edges, Map<Long, AiIvrNode> nodeMap)
    {
        Deque<String> inputs = new ArrayDeque<>();
        if (input != null)
        {
            inputs.add(input);
        }
        IvrNodeContext ctx = new IvrNodeContext(nodeMap, edges, vars, inputs,
                new IvrExecuteRequest(), "IVR-T", new AiIvrFlow(), new IvrExecuteResult(), r -> null);
        ctx.setCurrent(node);
        ctx.setStep(new IvrNodeStep());
        return ctx;
    }

    private AiIvrNode node(String config)
    {
        AiIvrNode node = new AiIvrNode();
        node.setNodeId(1L);
        node.setFlowId(10L);
        node.setNodeType("received");
        node.setNodeName("收号节点");
        node.setNodeConfig(config);
        return node;
    }

    private AiIvrNode node(Long nodeId)
    {
        AiIvrNode node = new AiIvrNode();
        node.setNodeId(nodeId);
        node.setNodeType("say");
        return node;
    }

    private AiIvrEdge edge(Long targetId, String label)
    {
        AiIvrEdge edge = new AiIvrEdge();
        edge.setEdgeId(1L);
        edge.setSourceNodeId(1L);
        edge.setTargetNodeId(targetId);
        edge.setEdgeLabel(label);
        edge.setSortOrder(1);
        return edge;
    }
}
