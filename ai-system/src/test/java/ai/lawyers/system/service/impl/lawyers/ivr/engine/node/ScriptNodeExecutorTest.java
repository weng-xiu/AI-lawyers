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

/**
 * script 脚本节点单测：空脚本、Groovy 降级提示、SpEL 求值与失败兜底。
 *
 * @author ai-lawyers
 */
class ScriptNodeExecutorTest
{
    private ScriptNodeExecutor executor;

    @BeforeEach
    void setUp()
    {
        executor = new ScriptNodeExecutor();
    }

    @Test
    void emptyScriptContinuesWithoutEvaluation()
    {
        Map<String, Object> vars = new HashMap<>();
        IvrNodeContext ctx = context(node("{}"), vars);

        AiIvrNode next = executor.execute(ctx);

        assertThat(next).isNull();
        assertThat(ctx.getStep().getAction()).isEqualTo("SCRIPT");
        assertThat(ctx.getStep().getDetail()).contains("未配置脚本内容");
        assertThat(vars).doesNotContainKey("scriptResult");
    }

    @Test
    void groovyIsRejectedWithHint()
    {
        Map<String, Object> vars = new HashMap<>();
        IvrNodeContext ctx = context(node("{\"scriptType\":\"groovy\",\"script\":\"1+1\"}"), vars);

        executor.execute(ctx);

        assertThat(String.valueOf(vars.get("scriptError"))).contains("不支持Groovy");
        assertThat(ctx.getStep().getDetail()).contains("脚本执行失败");
    }

    @Test
    void evaluatesSpelIntoDefaultResultVar()
    {
        Map<String, Object> vars = new HashMap<>();
        vars.put("n", 2);
        IvrNodeContext ctx = context(node("{\"script\":\"n * 3\"}"), vars);

        executor.execute(ctx);

        assertThat(vars).containsEntry("scriptResult", "6");
        assertThat(vars).doesNotContainKey("scriptError");
        assertThat(ctx.getStep().getDetail()).contains("脚本执行成功");
    }

    @Test
    void evaluatesSpelIntoCustomResultVar()
    {
        Map<String, Object> vars = new HashMap<>();
        vars.put("n", 1);
        IvrNodeContext ctx = context(node("{\"script\":\"n + 1\",\"resultVar\":\"total\"}"), vars);

        executor.execute(ctx);

        assertThat(vars).containsEntry("total", "2").doesNotContainKey("scriptResult");
    }

    @Test
    void nullResultBecomesEmptyString()
    {
        Map<String, Object> vars = new HashMap<>();
        IvrNodeContext ctx = context(node("{\"script\":\"null\"}"), vars);

        executor.execute(ctx);

        assertThat(vars).containsEntry("scriptResult", "");
    }

    @Test
    void evaluationFailureWritesScriptError()
    {
        Map<String, Object> vars = new HashMap<>();
        vars.put("n", 1);
        IvrNodeContext ctx = context(node("{\"script\":\"n / 0\"}"), vars);

        executor.execute(ctx);

        assertThat(vars.get("scriptError")).isNotNull();
        assertThat(ctx.getStep().getDetail()).contains("脚本执行失败");
        assertThat(vars).doesNotContainKey("scriptResult");
    }

    @Test
    void continuesToNextNodeAfterSuccess()
    {
        Map<Long, AiIvrNode> nodeMap = new HashMap<>();
        AiIvrNode target = node("{}");
        target.setNodeId(2L);
        nodeMap.put(2L, target);
        AiIvrEdge edge = new AiIvrEdge();
        edge.setEdgeId(1L);
        edge.setSourceNodeId(1L);
        edge.setTargetNodeId(2L);
        Map<String, Object> vars = new HashMap<>();
        IvrNodeContext ctx = context(node("{\"script\":\"1 + 1\"}"), vars,
                Collections.singletonList(edge), nodeMap);

        AiIvrNode next = executor.execute(ctx);

        assertThat(next.getNodeId()).isEqualTo(2L);
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
        node.setNodeType("script");
        node.setNodeName("脚本节点");
        node.setNodeConfig(config);
        return node;
    }
}
