package ai.lawyers.system.service.impl.lawyers.ivr.engine.node;

import org.springframework.stereotype.Component;

import ai.lawyers.common.utils.StringUtils;
import ai.lawyers.system.domain.lawyers.ivr.AiIvrNode;
import ai.lawyers.system.domain.lawyers.ivr.IvrNodeStep;

/**
 * hangup 挂断节点：记录 HANGUP 步骤并标记终止原因（供子流程冒泡判断），流程终止。
 *
 * @author ai-lawyers
 */
@Component
public class HangupNodeExecutor implements IvrNodeExecutor
{
    public static final String NODE_TYPE = "hangup";

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
        step.setAction("HANGUP");
        step.setDetail("挂断" + (StringUtils.isNotEmpty(current.getNodeName()) ? "（" + current.getNodeName() + "）" : ""));
        ctx.getResult().getSteps().add(step);
        // T1-3 标记终止原因，供子流程冒泡判断
        ctx.getVariables().put("__terminated", "hangup");
        return null;
    }
}
