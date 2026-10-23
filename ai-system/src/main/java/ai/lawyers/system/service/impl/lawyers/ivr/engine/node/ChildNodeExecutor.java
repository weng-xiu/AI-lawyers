package ai.lawyers.system.service.impl.lawyers.ivr.engine.node;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.JsonNode;

import ai.lawyers.common.utils.StringUtils;
import ai.lawyers.system.domain.lawyers.ivr.AiIvrFlow;
import ai.lawyers.system.domain.lawyers.ivr.AiIvrNode;
import ai.lawyers.system.domain.lawyers.ivr.IvrExecuteRequest;
import ai.lawyers.system.domain.lawyers.ivr.IvrExecuteResult;
import ai.lawyers.system.domain.lawyers.ivr.IvrNodeStep;
import ai.lawyers.system.service.lawyers.ivr.IAiIvrFlowService;

/**
 * child 子流程节点：按 flowId/flowCode 解析子流程并递归执行（经上下文 flowRunner，
 * 避免反向依赖引擎 Bean 成环）；嵌套深度超过 MAX_CHILD_DEPTH(3) 跳过执行防循环引用。
 *
 * <p>T1-3 子流程冒泡：子流程以挂断/转接终止时，终止标记上抛、父流程立即终止；
 * 正常结束时合并变量后父流程继续。</p>
 *
 * @author ai-lawyers
 */
@Component
public class ChildNodeExecutor implements IvrNodeExecutor
{
    public static final String NODE_TYPE = "child";

    /** 子流程最大嵌套深度，防止循环引用 */
    private static final int MAX_CHILD_DEPTH = 3;
    private static final String CHILD_DEPTH_KEY = "__childDepth";

    private final IAiIvrFlowService flowService;

    public ChildNodeExecutor(IAiIvrFlowService flowService)
    {
        this.flowService = flowService;
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
        IvrExecuteRequest parentRequest = ctx.getRequest();

        JsonNode json = IvrNodeSupport.config(current);
        int depth = IvrNodeSupport.parseInt(variables.get(CHILD_DEPTH_KEY), 0);
        step.setAction("CHILD");
        if (depth >= MAX_CHILD_DEPTH)
        {
            step.setDetail("子流程嵌套深度超过" + MAX_CHILD_DEPTH + "层，跳过执行");
            result.getSteps().add(step);
            return IvrNodeSupport.nextNode(current, ctx.getNodeMap(), ctx.getEdges(), variables, null);
        }
        Long childFlowId = json.path("flowId").asLong(0L);
        String childFlowCode = json.path("flowCode").asText("");
        AiIvrFlow childFlow = null;
        if (childFlowId != null && childFlowId > 0)
        {
            childFlow = flowService.selectAiIvrFlowByFlowId(childFlowId);
        }
        else if (StringUtils.isNotEmpty(childFlowCode))
        {
            AiIvrFlow query = new AiIvrFlow();
            query.setFlowCode(childFlowCode);
            List<AiIvrFlow> flows = flowService.selectAiIvrFlowList(query);
            childFlow = flows == null || flows.isEmpty() ? null : flows.get(0);
        }
        if (childFlow == null)
        {
            step.setDetail("未找到子流程（flowId=" + childFlowId + ", flowCode=" + childFlowCode + "）");
            result.getSteps().add(step);
            return IvrNodeSupport.nextNode(current, ctx.getNodeMap(), ctx.getEdges(), variables, null);
        }
        try
        {
            IvrExecuteRequest subRequest = new IvrExecuteRequest();
            subRequest.setFlowId(childFlow.getFlowId());
            subRequest.setSessionId(parentRequest.getSessionId());
            subRequest.setRecordId(parentRequest.getRecordId());
            subRequest.setCallerNumber(parentRequest.getCallerNumber());
            subRequest.setCalleeNumber(parentRequest.getCalleeNumber());
            subRequest.setInputs(new ArrayList<>(ctx.getInputs()));
            Map<String, Object> subVariables = new LinkedHashMap<>(variables);
            subVariables.remove(CHILD_DEPTH_KEY);
            subVariables.put(CHILD_DEPTH_KEY, depth + 1);
            subRequest.setVariables(subVariables);
            IvrExecuteResult subResult = ctx.getFlowRunner().apply(subRequest);
            if (subResult.getVariables() != null)
            {
                // T1-3 子流程冒泡：先取回终止标记，合并变量后清除内部标记，再决定是否终止父流程
                Object subTerminated = subResult.getVariables().get("__terminated");
                variables.putAll(subResult.getVariables());
                variables.remove("__terminated");
                if (subResult.getTransferTarget() != null)
                {
                    result.setTransferTarget(subResult.getTransferTarget());
                }
                if (subResult.getMatchedIntention() != null)
                {
                    result.setMatchedIntention(subResult.getMatchedIntention());
                    result.setMatchedIntentionName(subResult.getMatchedIntentionName());
                    result.setMatchMethod(subResult.getMatchMethod());
                    result.setCategoryId(subResult.getCategoryId());
                    result.setCategoryName(subResult.getCategoryName());
                }
                variables.remove(CHILD_DEPTH_KEY);
                variables.put("childResult", "flowName=" + subResult.getFlowName()
                        + ",success=" + subResult.isSuccess()
                        + ",terminated=" + (subTerminated == null ? "" : subTerminated)
                        + ",intention=" + (subResult.getMatchedIntention() == null
                        ? "" : subResult.getMatchedIntention()));
                step.setDetail("子流程[" + childFlow.getFlowName() + "]执行"
                        + (subResult.isSuccess() ? "成功" : "失败：" + subResult.getMessage())
                        + (subTerminated != null ? "；子流程" + ("hangup".equals(subTerminated) ? "挂断" : "转接") + "，冒泡终止父流程" : ""));
                result.getSteps().add(step);
                // 子流程以挂断/转接结束时，父流程必须立即终止并上抛语义（L1 修复）
                if (subTerminated != null)
                {
                    return null;
                }
                return IvrNodeSupport.nextNode(current, ctx.getNodeMap(), ctx.getEdges(), variables, null);
            }
        }
        catch (Exception e)
        {
            variables.put("childError", e.getMessage());
            step.setDetail("子流程[" + childFlow.getFlowName() + "]执行异常：" + e.getMessage());
        }
        result.getSteps().add(step);
        return IvrNodeSupport.nextNode(current, ctx.getNodeMap(), ctx.getEdges(), variables, null);
    }
}
