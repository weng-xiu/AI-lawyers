package ai.lawyers.system.service.impl.lawyers.ivr.engine.node;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayDeque;
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
import ai.lawyers.system.service.impl.lawyers.ivr.engine.IvrTemplateRenderer;

/**
 * variable 变量赋值节点单测：数组批量赋值、单变量赋值、模板渲染与空变量名忽略。
 *
 * @author ai-lawyers
 */
class VariableNodeExecutorTest
{
    private VariableNodeExecutor executor;

    @BeforeEach
    void setUp()
    {
        executor = new VariableNodeExecutor(new IvrTemplateRenderer());
    }

    @Test
    void assignsFromVariablesArray()
    {
        Map<String, Object> vars = new HashMap<>();
        vars.put("c", "来源");
        AiIvrNode node = node("{\"variables\":[{\"key\":\"a\",\"val\":\"固定\"},"
                + "{\"key\":\"b\",\"val\":\"${c}-后缀\"}]}");

        AiIvrNode next = executor.execute(context(node, vars));

        assertThat(vars).containsEntry("a", "固定").containsEntry("b", "来源-后缀");
        assertThat(next).isNull();
    }

    @Test
    void assignsSingleNameValue()
    {
        Map<String, Object> vars = new HashMap<>();
        vars.put("input", "用户输入");
        AiIvrNode node = node("{\"name\":\"echo\",\"value\":\"收到：${input}\"}");

        executor.execute(context(node, vars));

        assertThat(vars).containsEntry("echo", "收到：用户输入");
    }

    @Test
    void resolvesIndirectVariableReference()
    {
        Map<String, Object> vars = new HashMap<>();
        vars.put("ref", "${target}");
        vars.put("target", "最终值");
        AiIvrNode node = node("{\"name\":\"out\",\"value\":\"${ref}\"}");

        executor.execute(context(node, vars));

        assertThat(vars).containsEntry("out", "最终值");
    }

    @Test
    void missingVariableRendersEmpty()
    {
        Map<String, Object> vars = new HashMap<>();
        AiIvrNode node = node("{\"name\":\"out\",\"value\":\"${nothing}\"}");

        executor.execute(context(node, vars));

        assertThat(vars).containsEntry("out", "");
    }

    @Test
    void emptyNameIgnored()
    {
        Map<String, Object> vars = new HashMap<>();
        AiIvrNode node = node("{\"variables\":[{\"key\":\"\",\"val\":\"x\"}]}");

        executor.execute(context(node, vars));

        assertThat(vars).isEmpty();
    }

    @Test
    void invalidConfigTreatedAsEmpty()
    {
        Map<String, Object> vars = new HashMap<>();
        AiIvrNode node = node("not-a-json");

        AiIvrNode next = executor.execute(context(node, vars));

        assertThat(next).isNull();
        assertThat(vars).isEmpty();
    }

    @Test
    void recordsStepAndContinuesToNextNode()
    {
        AiIvrNode node = node("{\"name\":\"out\",\"value\":\"v\"}");
        node.setNodeName("赋值节点");
        Map<Long, AiIvrNode> nodeMap = new HashMap<>();
        AiIvrNode target = node("{}");
        target.setNodeId(2L);
        nodeMap.put(2L, target);
        AiIvrEdge edge = new AiIvrEdge();
        edge.setEdgeId(1L);
        edge.setSourceNodeId(1L);
        edge.setTargetNodeId(2L);
        Map<String, Object> vars = new HashMap<>();
        IvrNodeContext ctx = context(node, vars, Collections.singletonList(edge), nodeMap);

        AiIvrNode next = executor.execute(ctx);

        assertThat(next.getNodeId()).isEqualTo(2L);
        assertThat(ctx.getStep().getAction()).isEqualTo("VARIABLE");
        assertThat(ctx.getStep().getDetail()).contains("赋值节点");
        assertThat(ctx.getResult().getSteps()).hasSize(1);
    }

    private IvrNodeContext context(AiIvrNode node, Map<String, Object> vars)
    {
        return context(node, vars, Collections.emptyList(), new HashMap<>());
    }

    private IvrNodeContext context(AiIvrNode node, Map<String, Object> vars,
                                   List<AiIvrEdge> edges, Map<Long, AiIvrNode> nodeMap)
    {
        IvrNodeContext ctx = new IvrNodeContext(nodeMap, edges, vars, new ArrayDeque<>(),
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
        node.setNodeType("variable");
        node.setNodeName("变量赋值");
        node.setNodeConfig(config);
        return node;
    }
}
