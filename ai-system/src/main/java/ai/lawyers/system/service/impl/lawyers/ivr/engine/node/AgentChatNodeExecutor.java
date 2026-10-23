package ai.lawyers.system.service.impl.lawyers.ivr.engine.node;

import java.util.Map;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.JsonNode;

import ai.lawyers.common.utils.StringUtils;
import ai.lawyers.system.domain.lawyers.agent.AgentChatResult;
import ai.lawyers.system.domain.lawyers.ivr.AiIvrFlow;
import ai.lawyers.system.domain.lawyers.ivr.AiIvrNode;
import ai.lawyers.system.domain.lawyers.ivr.IvrExecuteResult;
import ai.lawyers.system.domain.lawyers.ivr.IvrNodeStep;
import ai.lawyers.system.service.impl.lawyers.ivr.engine.IvrTemplateRenderer;
import ai.lawyers.system.service.lawyers.agent.IAgentChatService;

/**
 * agentChat 智能体对话节点：多轮对话（maxTurns 上限强制转人工），
 * 首轮无输入播报欢迎语不调用模型；回复与转人工标记写入结果变量。
 *
 * @author ai-lawyers
 */
@Component
public class AgentChatNodeExecutor implements IvrNodeExecutor
{
    public static final String NODE_TYPE = "agentchat";

    private final IvrTemplateRenderer templateRenderer;
    private final IAgentChatService agentChatService;

    public AgentChatNodeExecutor(IvrTemplateRenderer templateRenderer,
                                 IAgentChatService agentChatService)
    {
        this.templateRenderer = templateRenderer;
        this.agentChatService = agentChatService;
    }

    @Override
    public String[] supportedTypes()
    {
        return new String[] { NODE_TYPE };
    }

    @Override
    public AiIvrNode execute(IvrNodeContext ctx)
    {
        AiIvrNode current = ctx.getCurrent();
        Map<String, Object> variables = ctx.getVariables();
        IvrNodeStep step = ctx.getStep();
        IvrExecuteResult result = ctx.getResult();
        AiIvrFlow flow = ctx.getFlow();

        JsonNode json = IvrNodeSupport.config(current);
        long agentId = json.path("agentId").asLong(0L);
        String welcome = templateRenderer.render(json.path("welcome").asText(""), variables);
        int maxTurns = json.path("maxTurns").asInt(5);
        String resultVar = json.path("resultVar").asText("agentReply");
        String handoffVar = json.path("handoffVar").asText("agentHandoff");

        // 累计本轮会话在该流程中的对话轮数，达到上限强制转人工
        int turnCount = variables.get("agentTurnCount") instanceof Number
                ? ((Number) variables.get("agentTurnCount")).intValue() : 0;

        String reply;
        boolean handoff;
        String reason;

        if (agentId <= 0L)
        {
            reply = "智能体未正确配置，为您转接人工。";
            handoff = true;
            reason = "节点未配置agentId";
        }
        else if (turnCount >= maxTurns)
        {
            reply = "已达最大对话轮数，为您转接人工坐席。";
            handoff = true;
            reason = "达到最大轮数(" + maxTurns + ")";
        }
        else
        {
            // 取用户输入：优先消费输入队列，兜底取上一个 answer 节点写入的 lastInput
            String userInput = IvrNodeSupport.pollInput(ctx.getInputs());
            if (StringUtils.isEmpty(userInput) && variables.get("lastInput") != null)
            {
                userInput = String.valueOf(variables.get("lastInput"));
            }
            // 首轮无输入时直接播报欢迎语，不调用模型
            if (StringUtils.isEmpty(userInput) && StringUtils.isNotEmpty(welcome))
            {
                reply = welcome;
                handoff = false;
                reason = null;
            }
            else
            {
                turnCount++;
                AgentChatResult chatResult = agentChatService.chat(agentId, ctx.getSessionId(), userInput,
                        ctx.getRequest().getRecordId(), flow == null ? null : flow.getFlowId(),
                        current.getNodeId(), ctx.getRequest().getCallerNumber());
                reply = chatResult.getReply();
                handoff = chatResult.isHandoff();
                reason = chatResult.getReason();
                if (chatResult.getCategoryId() != null)
                {
                    variables.put("agentCategoryId", chatResult.getCategoryId());
                }
            }
        }

        variables.put(resultVar, reply);
        variables.put(handoffVar, handoff);
        variables.put("agentTurnCount", turnCount);
        if (handoff)
        {
            result.setTransferTarget("人工坐席");
        }

        step.setAction("AGENT_CHAT");
        String detail = "智能体回复（第" + turnCount + "轮）：" + IvrNodeSupport.truncate(reply, 200);
        if (handoff)
        {
            detail += " [转人工" + (StringUtils.isNotEmpty(reason) ? "：" + reason : "") + "]";
        }
        step.setDetail(detail);
        result.getSteps().add(step);
        return IvrNodeSupport.nextNode(current, ctx.getNodeMap(), ctx.getEdges(), variables, null);
    }
}
