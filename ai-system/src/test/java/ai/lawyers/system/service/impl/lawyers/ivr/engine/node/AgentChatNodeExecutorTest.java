package ai.lawyers.system.service.impl.lawyers.ivr.engine.node;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayDeque;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import ai.lawyers.system.domain.lawyers.agent.AgentChatResult;
import ai.lawyers.system.domain.lawyers.ivr.AiIvrEdge;
import ai.lawyers.system.domain.lawyers.ivr.AiIvrFlow;
import ai.lawyers.system.domain.lawyers.ivr.AiIvrNode;
import ai.lawyers.system.domain.lawyers.ivr.IvrExecuteRequest;
import ai.lawyers.system.domain.lawyers.ivr.IvrExecuteResult;
import ai.lawyers.system.domain.lawyers.ivr.IvrNodeStep;
import ai.lawyers.system.service.impl.lawyers.ivr.engine.IvrTemplateRenderer;
import ai.lawyers.system.service.lawyers.agent.IAgentChatService;

/**
 * agentChat 智能体对话节点单测：agentId 校验、轮数上限转人工、
 * 首轮欢迎语、对话结果/分类/转人工标记写入与自定义变量名。
 *
 * @author ai-lawyers
 */
@ExtendWith(MockitoExtension.class)
class AgentChatNodeExecutorTest
{
    @Mock
    private IAgentChatService agentChatService;

    private AgentChatNodeExecutor executor;

    @BeforeEach
    void setUp()
    {
        executor = new AgentChatNodeExecutor(new IvrTemplateRenderer(), agentChatService);
    }

    @Test
    void missingAgentIdHandsOffWithoutCallingService()
    {
        Map<String, Object> vars = new HashMap<>();
        IvrNodeContext ctx = context(node("{\"agentId\":0}"), vars);

        AiIvrNode next = executor.execute(ctx);

        assertThat(vars).containsEntry("agentReply", "智能体未正确配置，为您转接人工。")
                .containsEntry("agentHandoff", true)
                .containsEntry("agentTurnCount", 0);
        assertThat(ctx.getResult().getTransferTarget()).isEqualTo("人工坐席");
        assertThat(ctx.getStep().getDetail()).contains("转人工").contains("节点未配置agentId");
        assertThat(next).isNull();
        verify(agentChatService, never())
                .chat(anyLong(), anyString(), any(), any(), any(), any(), any());
    }

    @Test
    void reachesMaxTurnsAndHandsOff()
    {
        Map<String, Object> vars = new HashMap<>();
        vars.put("agentTurnCount", 2);
        IvrNodeContext ctx = context(node("{\"agentId\":1,\"maxTurns\":2}"), vars);

        executor.execute(ctx);

        assertThat(vars).containsEntry("agentReply", "已达最大对话轮数，为您转接人工坐席。")
                .containsEntry("agentHandoff", true)
                .containsEntry("agentTurnCount", 2);
        assertThat(ctx.getResult().getTransferTarget()).isEqualTo("人工坐席");
        assertThat(ctx.getStep().getDetail()).contains("第2轮").contains("达到最大轮数(2)");
    }

    @Test
    void firstTurnWithoutInputPlaysWelcomeWithoutCallingModel()
    {
        Map<String, Object> vars = new HashMap<>();
        IvrNodeContext ctx = context(node("{\"agentId\":1,\"welcome\":\"您好，我是${name}助手\"}"), vars);

        executor.execute(ctx);

        assertThat(vars).containsEntry("agentReply", "您好，我是助手").containsEntry("agentHandoff", false);
        assertThat(vars).containsEntry("agentTurnCount", 0);
        assertThat(ctx.getResult().getTransferTarget()).isNull();
        verify(agentChatService, never())
                .chat(anyLong(), anyString(), any(), any(), any(), any(), any());
    }

    @Test
    void firstTurnWithoutInputOrWelcomeCallsModelWithEmptyInput()
    {
        when(agentChatService.chat(eq(1L), eq("IVR-T"), isNull(), isNull(), eq(10L), eq(1L), isNull()))
                .thenReturn(new AgentChatResult("模型回复", false, null));
        Map<String, Object> vars = new HashMap<>();
        IvrNodeContext ctx = context(node("{\"agentId\":1}"), vars);

        executor.execute(ctx);

        assertThat(vars).containsEntry("agentReply", "模型回复").containsEntry("agentTurnCount", 1);
    }

    @Test
    void consumesQueuedInputAndWritesCategory()
    {
        AgentChatResult chatResult = new AgentChatResult("已为您解答", false, null);
        chatResult.setCategoryId(9L);
        when(agentChatService.chat(1L, "IVR-T", "劳动合同", null, 10L, 1L, null))
                .thenReturn(chatResult);
        Map<String, Object> vars = new HashMap<>();
        IvrNodeContext ctx = context(node("{\"agentId\":1}"), vars, "劳动合同");

        AiIvrNode next = executor.execute(ctx);

        assertThat(vars).containsEntry("agentReply", "已为您解答")
                .containsEntry("agentCategoryId", 9L)
                .containsEntry("agentTurnCount", 1);
        assertThat(ctx.getStep().getAction()).isEqualTo("AGENT_CHAT");
        assertThat(ctx.getStep().getDetail()).contains("第1轮").contains("已为您解答");
        assertThat(next).isNull();
    }

    @Test
    void fallsBackToLastInputWhenQueueEmpty()
    {
        when(agentChatService.chat(1L, "IVR-T", "上次输入", null, 10L, 1L, null))
                .thenReturn(new AgentChatResult("好的", false, null));
        Map<String, Object> vars = new HashMap<>();
        vars.put("lastInput", "上次输入");
        IvrNodeContext ctx = context(node("{\"agentId\":1}"), vars);

        executor.execute(ctx);

        verify(agentChatService).chat(1L, "IVR-T", "上次输入", null, 10L, 1L, null);
    }

    @Test
    void modelHandoffSetsTransferTargetAndDetail()
    {
        when(agentChatService.chat(1L, "IVR-T", "复杂问题", null, 10L, 1L, null))
                .thenReturn(new AgentChatResult("建议转人工", true, "超纲"));
        Map<String, Object> vars = new HashMap<>();
        IvrNodeContext ctx = context(node("{\"agentId\":1}"), vars, "复杂问题");

        executor.execute(ctx);

        assertThat(vars).containsEntry("agentHandoff", true);
        assertThat(ctx.getResult().getTransferTarget()).isEqualTo("人工坐席");
        assertThat(ctx.getStep().getDetail()).contains("转人工：超纲");
    }

    @Test
    void supportsCustomResultAndHandoffVariables()
    {
        when(agentChatService.chat(1L, "IVR-T", "你好", null, 10L, 1L, null))
                .thenReturn(new AgentChatResult("您好", false, null));
        Map<String, Object> vars = new HashMap<>();
        IvrNodeContext ctx = context(node("{\"agentId\":1,\"resultVar\":\"reply\","
                + "\"handoffVar\":\"needHuman\"}"), vars, "你好");

        executor.execute(ctx);

        assertThat(vars).containsEntry("reply", "您好").containsEntry("needHuman", false)
                .doesNotContainKey("agentReply").doesNotContainKey("agentHandoff");
    }

    @Test
    void nonNumericTurnCountTreatedAsZero()
    {
        when(agentChatService.chat(1L, "IVR-T", "你好", null, 10L, 1L, null))
                .thenReturn(new AgentChatResult("您好", false, null));
        Map<String, Object> vars = new HashMap<>();
        vars.put("agentTurnCount", "不是数字");
        IvrNodeContext ctx = context(node("{\"agentId\":1}"), vars, "你好");

        executor.execute(ctx);

        assertThat(vars).containsEntry("agentTurnCount", 1);
    }

    @Test
    void continuesToNextNodeByDefaultEdge()
    {
        when(agentChatService.chat(1L, "IVR-T", "你好", null, 10L, 1L, null))
                .thenReturn(new AgentChatResult("您好", false, null));
        Map<Long, AiIvrNode> nodeMap = new HashMap<>();
        nodeMap.put(2L, node("{}", 2L));
        AiIvrEdge edge = new AiIvrEdge();
        edge.setEdgeId(1L);
        edge.setSourceNodeId(1L);
        edge.setTargetNodeId(2L);
        edge.setSortOrder(1);
        Map<String, Object> vars = new HashMap<>();
        IvrNodeContext ctx = context(node("{\"agentId\":1}"), vars, Collections.singletonList(edge),
                nodeMap, "你好");

        AiIvrNode next = executor.execute(ctx);

        assertThat(next.getNodeId()).isEqualTo(2L);
    }

    private IvrNodeContext context(AiIvrNode node, Map<String, Object> vars, String... inputs)
    {
        return context(node, vars, Collections.emptyList(), new HashMap<>(),
                inputs.length == 0 ? null : inputs[0]);
    }

    private IvrNodeContext context(AiIvrNode node, Map<String, Object> vars, List<AiIvrEdge> edges,
                                   Map<Long, AiIvrNode> nodeMap, String input)
    {
        Deque<String> queue = new ArrayDeque<>();
        if (input != null)
        {
            queue.add(input);
        }
        AiIvrFlow flow = new AiIvrFlow();
        flow.setFlowId(10L);
        IvrNodeContext ctx = new IvrNodeContext(nodeMap, edges, vars, queue,
                new IvrExecuteRequest(), "IVR-T", flow, new IvrExecuteResult(), r -> null);
        ctx.setCurrent(node);
        ctx.setStep(new IvrNodeStep());
        return ctx;
    }

    private AiIvrNode node(String config)
    {
        return node(config, 1L);
    }

    private AiIvrNode node(String config, Long nodeId)
    {
        AiIvrNode node = new AiIvrNode();
        node.setNodeId(nodeId);
        node.setFlowId(10L);
        node.setNodeType("agentchat");
        node.setNodeName("智能体对话");
        node.setNodeConfig(config);
        return node;
    }
}
