package ai.lawyers.system.service.impl.lawyers.ivr.engine.node;

import org.springframework.stereotype.Component;

import ai.lawyers.system.domain.lawyers.ivr.AiIvrNode;
import ai.lawyers.system.domain.lawyers.ivr.IvrNodeStep;

/**
 * start 开始节点：记录 START 步骤后沿默认连线前进。
 *
 * @author ai-lawyers
 */
@Component
public class StartNodeExecutor implements IvrNodeExecutor
{
    public static final String NODE_TYPE = "start";

    @Override
    public String[] supportedTypes()
    {
        return new String[] { NODE_TYPE };
    }

    @Override
    public AiIvrNode execute(IvrNodeContext ctx)
    {
        IvrNodeStep step = ctx.getStep();
        step.setAction("START");
        step.setDetail("流程开始");
        ctx.getResult().getSteps().add(step);
        return IvrNodeSupport.nextNode(ctx.getCurrent(), ctx.getNodeMap(), ctx.getEdges(),
                ctx.getVariables(), null);
    }
}
