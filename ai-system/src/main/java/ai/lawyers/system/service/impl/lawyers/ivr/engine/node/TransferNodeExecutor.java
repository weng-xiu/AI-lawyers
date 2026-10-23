package ai.lawyers.system.service.impl.lawyers.ivr.engine.node;

import java.util.Map;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.JsonNode;

import ai.lawyers.common.utils.StringUtils;
import ai.lawyers.system.domain.lawyers.ivr.AiIvrNode;
import ai.lawyers.system.domain.lawyers.ivr.IvrExecuteResult;
import ai.lawyers.system.domain.lawyers.ivr.IvrNodeStep;
import ai.lawyers.system.domain.lawyers.skill.DispatchContext;
import ai.lawyers.system.domain.lawyers.skill.DispatchResult;
import ai.lawyers.system.service.lawyers.skill.IAgentDispatchService;

/**
 * agent/transfer 转接节点：静态目标直接转接；dispatchMode=dispatch 时按技能组
 * 或 agentChat 写入的分类动态分配坐席（B5），无空闲坐席可排队（沿排队连线继续）。
 *
 * @author ai-lawyers
 */
@Component
public class TransferNodeExecutor implements IvrNodeExecutor
{
    public static final String NODE_AGENT = "agent";
    public static final String NODE_TRANSFER = "transfer";

    private final IAgentDispatchService agentDispatchService;

    public TransferNodeExecutor(IAgentDispatchService agentDispatchService)
    {
        this.agentDispatchService = agentDispatchService;
    }

    @Override
    public String[] supportedTypes()
    {
        return new String[] { NODE_AGENT, NODE_TRANSFER };
    }

    @Override
    public AiIvrNode execute(IvrNodeContext ctx)
    {
        AiIvrNode current = ctx.getCurrent();
        Map<String, Object> variables = ctx.getVariables();
        IvrNodeStep step = ctx.getStep();
        IvrExecuteResult result = ctx.getResult();
        String type = current.getNodeType() == null ? "" : current.getNodeType().toLowerCase();
        boolean isAgentNode = NODE_AGENT.equals(type);

        String target;
        JsonNode tcfg = IvrNodeSupport.config(current);
        String dispatchMode = tcfg.has("dispatchMode") ? tcfg.get("dispatchMode").asText() : "static";
        if ("dispatch".equals(dispatchMode))
        {
            // B5 智能队列分配：按 groupId 或 B1 写入的 agentCategoryId 动态选坐席
            target = dispatchAgent(tcfg, ctx, isAgentNode);
            if (target == null)
            {
                // 排队中：沿排队连线（变量 dispatchQueued=true）继续，不终止流程
                variables.put("dispatchQueued", true);
                result.getSteps().add(step);
                return IvrNodeSupport.nextNode(current, ctx.getNodeMap(), ctx.getEdges(), variables, null);
            }
            variables.put("dispatchQueued", false);
        }
        else
        {
            target = transferTarget(current);
        }
        result.setTransferTarget(target);
        step.setAction("TRANSFER");
        step.setDetail((isAgentNode ? "转人工坐席：" : "转外线：") + target);
        result.getSteps().add(step);
        // T1-3 标记终止原因，供子流程冒泡判断
        variables.put("__terminated", "transfer");
        return null;
    }

    /**
     * B5 智能队列分配：按技能组动态选择坐席。
     *
     * <p>节点配置支持：groupId（固定技能组）、categoryVar（从流程变量取咨询分类ID，默认
     * agentCategoryId，承接 B1 agentChat 结果）、enqueueIfNoAgent（无空闲是否排队，默认 true）。
     * 分配成功返回坐席标识；若排队中返回 null（调用方沿排队连线继续）。</p>
     */
    private String dispatchAgent(JsonNode cfg, IvrNodeContext ctx, boolean isAgentNode)
    {
        Map<String, Object> variables = ctx.getVariables();
        IvrNodeStep step = ctx.getStep();

        Long groupId = cfg.has("groupId") && !cfg.get("groupId").isNull() ? cfg.get("groupId").asLong() : null;
        String categoryVar = cfg.has("categoryVar") && StringUtils.isNotEmpty(cfg.get("categoryVar").asText())
                ? cfg.get("categoryVar").asText() : "agentCategoryId";
        boolean enqueue = !cfg.has("enqueueIfNoAgent") || cfg.get("enqueueIfNoAgent").asBoolean(true);

        DispatchContext dispatchCtx = DispatchContext.of(
                ctx.getSessionId(), ctx.getRequest().getRecordId(), ctx.getRequest().getCallerNumber());
        dispatchCtx.setEnqueueIfNoAgent(enqueue);
        Object prio = variables.get("dispatchPriority");
        if (prio instanceof Number)
        {
            dispatchCtx.setPriority(((Number) prio).intValue());
        }

        DispatchResult dr;
        if (groupId != null)
        {
            dr = agentDispatchService.dispatch(groupId, dispatchCtx);
        }
        else
        {
            Object catObj = variables.get(categoryVar);
            Long categoryId = catObj instanceof Number ? ((Number) catObj).longValue() : null;
            if (categoryId == null)
            {
                // 未指定技能组且无分类信息，回退到静态目标
                String fallback = cfg.has("fallbackTarget") ? cfg.get("fallbackTarget").asText() : "人工坐席";
                step.setAction("TRANSFER");
                step.setDetail("智能分配未指定技能组/分类，回退到：" + fallback);
                return fallback;
            }
            dr = agentDispatchService.dispatchByCategory(categoryId, dispatchCtx);
        }

        // 回写流程变量，供后续节点/SpEL 使用
        variables.put("dispatchSuccess", dr.isSuccess());
        variables.put("dispatchMessage", dr.getMessage());
        if (dr.getGroupId() != null)
        {
            variables.put("dispatchGroupId", dr.getGroupId());
            variables.put("dispatchGroupName", dr.getGroupName());
        }
        if (dr.isSuccess())
        {
            variables.put("dispatchAgentId", dr.getAgentId());
            variables.put("dispatchAgentName", dr.getAgentName());
            step.setAction("TRANSFER");
            step.setDetail("智能分配坐席：" + dr.getAgentName()
                    + "（" + dr.getGroupName() + "，策略：" + dr.getStrategy() + "）");
            return "坐席ID " + dr.getAgentId();
        }
        else if (dr.getQueueId() != null)
        {
            variables.put("dispatchQueueId", dr.getQueueId());
            variables.put("dispatchQueuePosition", dr.getQueuePosition());
            step.setAction("QUEUE");
            step.setDetail(dr.getMessage() + "（技能组：" + dr.getGroupName() + "）");
            return null;
        }
        else
        {
            step.setAction("TRANSFER");
            step.setDetail("智能分配失败：" + dr.getMessage());
            return "人工坐席";
        }
    }

    private String transferTarget(AiIvrNode node)
    {
        JsonNode json = IvrNodeSupport.config(node);
        if (json.has("agentExtension"))
        {
            return "坐席分机 " + json.get("agentExtension").asText();
        }
        if (json.has("agentId"))
        {
            return "坐席ID " + json.get("agentId").asText();
        }
        if (json.has("number"))
        {
            return json.get("number").asText();
        }
        if (json.has("target"))
        {
            return json.get("target").asText();
        }
        return "人工坐席";
    }
}
