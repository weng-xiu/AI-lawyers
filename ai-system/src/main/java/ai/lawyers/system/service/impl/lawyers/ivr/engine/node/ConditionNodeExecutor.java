package ai.lawyers.system.service.impl.lawyers.ivr.engine.node;

import org.springframework.stereotype.Component;

import ai.lawyers.system.domain.lawyers.ivr.AiIvrEdge;
import ai.lawyers.system.domain.lawyers.ivr.AiIvrNode;
import ai.lawyers.system.domain.lawyers.ivr.IvrNodeStep;

/**
 * condition 条件分支节点：按 SpEL 条件路由出线并记录命中标签。
 *
 * @author ai-lawyers
 */
@Component
public class ConditionNodeExecutor implements IvrNodeExecutor
{
    public static final String NODE_TYPE = "condition";

    @Override
    public String[] supportedTypes()
    {
        return new String[] { NODE_TYPE };
    }

    @Override
    public AiIvrNode execute(IvrNodeContext ctx)
    {
        AiIvrNode current = ctx.getCurrent();
        IvrNodeStep step = ctx.getStep();

        AiIvrEdge chosen = IvrNodeSupport.routeEdge(current, ctx.getEdges(), ctx.getVariables(), null);
        String chosenLabel = chosen == null ? "无命中(默认/无连线)" : IvrNodeSupport.labelOf(chosen);
        step.setAction("CONDITION");
        step.setDetail("条件分支：" + chosenLabel);
        ctx.getResult().getSteps().add(step);
        return chosen == null ? null : ctx.getNodeMap().get(chosen.getTargetNodeId());
    }
}
