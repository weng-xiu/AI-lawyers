package ai.lawyers.system.service.impl.lawyers.ivr.engine.node;

import java.util.Map;

import org.springframework.stereotype.Component;

import ai.lawyers.common.utils.StringUtils;
import ai.lawyers.system.domain.lawyers.ivr.AiIvrNode;
import ai.lawyers.system.domain.lawyers.ivr.IvrNodeStep;
import ai.lawyers.system.service.impl.lawyers.ivr.engine.IvrTemplateRenderer;

/**
 * answer 答问（ASR 收声）节点：播报询问语后消费一路语音输入，
 * 写入 dtmf/lastInput 并按输入值路由。
 *
 * @author ai-lawyers
 */
@Component
public class AnswerNodeExecutor implements IvrNodeExecutor
{
    public static final String NODE_TYPE = "answer";

    private final IvrTemplateRenderer templateRenderer;

    public AnswerNodeExecutor(IvrTemplateRenderer templateRenderer)
    {
        this.templateRenderer = templateRenderer;
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

        String askText = templateRenderer.render(IvrNodeSupport.configText(current, "jqrask"), variables);
        if (StringUtils.isEmpty(askText))
        {
            askText = templateRenderer.render(IvrNodeSupport.configText(current, "prompt"), variables);
        }
        if (StringUtils.isNotEmpty(askText))
        {
            variables.put("lastSay", askText);
        }
        String voiceInput = IvrNodeSupport.pollInput(ctx.getInputs());
        variables.put("dtmf", voiceInput == null ? "" : voiceInput.trim());
        variables.put("lastInput", voiceInput == null ? "" : voiceInput);
        step.setAction("ASR_INPUT");
        step.setDetail((StringUtils.isEmpty(askText) ? "" : "询问语：" + askText + "；")
                + "语音输入：" + (voiceInput == null ? "" : voiceInput));
        ctx.getResult().getSteps().add(step);
        return IvrNodeSupport.nextNode(current, ctx.getNodeMap(), ctx.getEdges(), variables,
                voiceInput == null ? null : voiceInput.trim());
    }
}
