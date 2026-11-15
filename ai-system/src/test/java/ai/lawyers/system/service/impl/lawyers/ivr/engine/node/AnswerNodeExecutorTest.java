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
 * answer 答问节点单测：询问语渲染与兜底、语音输入消费、变量写入与按输入路由。
 *
 * @author ai-lawyers
 */
class AnswerNodeExecutorTest
{
    private AnswerNodeExecutor executor;

    @BeforeEach
    void setUp()
    {
        executor = new AnswerNodeExecutor(new IvrTemplateRenderer());
    }

    @Test
    void consumesVoiceInputAndWritesVariables()
    {
        Map<String, Object> vars = new HashMap<>();
        IvrNodeContext ctx = context(node("{\"jqrask\":\"请问您贵姓？\"}"), vars, "张三");

        AiIvrNode next = executor.execute(ctx);

        assertThat(vars).containsEntry("lastSay", "请问您贵姓？")
                .containsEntry("dtmf", "张三")
                .containsEntry("lastInput", "张三");
        assertThat(ctx.getStep().getAction()).isEqualTo("ASR_INPUT");
        assertThat(ctx.getStep().getDetail()).contains("询问语：请问您贵姓？").contains("语音输入：张三");
        assertThat(next).isNull();
    }

    @Test
    void fallsBackToPromptWhenJqraskMissing()
    {
        Map<String, Object> vars = new HashMap<>();
        IvrNodeContext ctx = context(node("{\"prompt\":\"请描述您的问题\"}"), vars, "合同纠纷");

        executor.execute(ctx);

        assertThat(vars).containsEntry("lastSay", "请描述您的问题");
    }

    @Test
    void rendersTemplateInAskText()
    {
        Map<String, Object> vars = new HashMap<>();
        vars.put("company", "律所");
        IvrNodeContext ctx = context(node("{\"jqrask\":\"欢迎致电${company}\"}"), vars, "你好");

        executor.execute(ctx);

        assertThat(vars).containsEntry("lastSay", "欢迎致电律所");
    }

    @Test
    void missingInputWritesEmptyValues()
    {
        Map<String, Object> vars = new HashMap<>();
        IvrNodeContext ctx = context(node("{\"jqrask\":\"请说\"}"), vars, null);

        AiIvrNode next = executor.execute(ctx);

        assertThat(vars).containsEntry("dtmf", "").containsEntry("lastInput", "");
        assertThat(ctx.getStep().getDetail()).contains("语音输入：");
        assertThat(next).isNull();
    }

    @Test
    void dtmfTrimmedButLastInputKeepsRaw()
    {
        Map<String, Object> vars = new HashMap<>();
        IvrNodeContext ctx = context(node("{}"), vars, "  劳动仲裁  ");

        executor.execute(ctx);

        assertThat(vars).containsEntry("dtmf", "劳动仲裁")
                .containsEntry("lastInput", "  劳动仲裁  ");
    }

    @Test
    void routesByMatchingInputLabel()
    {
        Map<Long, AiIvrNode> nodeMap = new HashMap<>();
        nodeMap.put(2L, node(2L));
        List<AiIvrEdge> edges = Collections.singletonList(edge(2L, "转人工"));
        Map<String, Object> vars = new HashMap<>();
        IvrNodeContext ctx = context(node("{}"), vars, "转人工", edges, nodeMap);

        AiIvrNode next = executor.execute(ctx);

        assertThat(next.getNodeId()).isEqualTo(2L);
    }

    @Test
    void emptyAskTextOmitsPromptInDetail()
    {
        Map<String, Object> vars = new HashMap<>();
        IvrNodeContext ctx = context(node("{}"), vars, "任意");

        executor.execute(ctx);

        assertThat(vars).doesNotContainKey("lastSay");
        assertThat(ctx.getStep().getDetail()).startsWith("语音输入：");
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
        node.setNodeType("answer");
        node.setNodeName("答问节点");
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
