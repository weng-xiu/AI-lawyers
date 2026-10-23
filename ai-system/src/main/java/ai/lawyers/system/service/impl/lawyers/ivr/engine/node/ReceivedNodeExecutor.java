package ai.lawyers.system.service.impl.lawyers.ivr.engine.node;

import java.util.Map;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.JsonNode;

import ai.lawyers.common.utils.StringUtils;
import ai.lawyers.system.domain.lawyers.ivr.AiIvrNode;
import ai.lawyers.system.domain.lawyers.ivr.IvrNodeStep;
import ai.lawyers.system.service.impl.lawyers.ivr.engine.IvrTemplateRenderer;

/**
 * received 收号（DTMF）节点：播报收号语后消费一路按键输入，
 * 按 endKey/maxDigits 规整写入 dtmf/receivedDigits/lastInput 并按输入值路由。
 *
 * @author ai-lawyers
 */
@Component
public class ReceivedNodeExecutor implements IvrNodeExecutor
{
    public static final String NODE_TYPE = "received";

    private final IvrTemplateRenderer templateRenderer;

    public ReceivedNodeExecutor(IvrTemplateRenderer templateRenderer)
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

        String receivedPrompt = templateRenderer.render(IvrNodeSupport.configText(current, "jqrask"), variables);
        if (StringUtils.isEmpty(receivedPrompt))
        {
            receivedPrompt = templateRenderer.render(IvrNodeSupport.configText(current, "prompt"), variables);
        }
        if (StringUtils.isNotEmpty(receivedPrompt))
        {
            variables.put("lastSay", receivedPrompt);
        }
        String receivedInput = IvrNodeSupport.pollInput(ctx.getInputs());
        String digits = receivedInput == null ? "" : receivedInput.trim();
        JsonNode receivedConfig = IvrNodeSupport.config(current);
        String endKey = receivedConfig.path("endKey").asText(
                receivedConfig.path("end").asText("#"));
        if (StringUtils.isNotEmpty(endKey) && digits.endsWith(endKey))
        {
            digits = digits.substring(0, digits.length() - endKey.length());
        }
        int maxDigits = receivedConfig.path("maxDigits").asInt(-1);
        if (maxDigits > 0 && digits.length() > maxDigits)
        {
            digits = digits.substring(0, maxDigits);
        }
        variables.put("dtmf", digits);
        variables.put("receivedDigits", digits);
        variables.put("lastInput", digits);
        step.setAction("DTMF_INPUT");
        step.setDetail((StringUtils.isEmpty(receivedPrompt) ? "" : "收号语：" + receivedPrompt + "；")
                + "按键输入：" + digits);
        ctx.getResult().getSteps().add(step);
        return IvrNodeSupport.nextNode(current, ctx.getNodeMap(), ctx.getEdges(), variables, digits);
    }
}
