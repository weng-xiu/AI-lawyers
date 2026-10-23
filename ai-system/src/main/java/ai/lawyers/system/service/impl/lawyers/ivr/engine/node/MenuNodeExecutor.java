package ai.lawyers.system.service.impl.lawyers.ivr.engine.node;

import java.util.Map;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.JsonNode;

import ai.lawyers.common.utils.StringUtils;
import ai.lawyers.system.domain.lawyers.ivr.AiIvrNode;
import ai.lawyers.system.domain.lawyers.ivr.IvrNodeStep;

/**
 * menu/dtmf 菜单按键节点：消费一路输入按 endKey/maxDigits 规整为 dtmf 变量，
 * 按按键值匹配连线标签路由（T1-4 多位按键：maxDigits 默认 1 保持旧行为，endKey 默认 #）。
 *
 * @author ai-lawyers
 */
@Component
public class MenuNodeExecutor implements IvrNodeExecutor
{
    public static final String NODE_MENU = "menu";
    public static final String NODE_DTMF = "dtmf";

    @Override
    public String[] supportedTypes()
    {
        return new String[] { NODE_MENU, NODE_DTMF };
    }

    @Override
    public AiIvrNode execute(IvrNodeContext ctx)
    {
        AiIvrNode current = ctx.getCurrent();
        Map<String, Object> variables = ctx.getVariables();
        IvrNodeStep step = ctx.getStep();

        String rawInput = IvrNodeSupport.pollInput(ctx.getInputs());
        String dtmf = rawInput == null ? "" : rawInput.trim();
        // T1-4 menu/dtmf 支持多位按键（maxDigits 默认 1 保持旧行为；endKey 结束键默认 #）
        JsonNode menuCfg = IvrNodeSupport.config(current);
        String menuEndKey = menuCfg.path("endKey").asText(menuCfg.path("end").asText("#"));
        if (StringUtils.isNotEmpty(menuEndKey) && dtmf.endsWith(menuEndKey))
        {
            dtmf = dtmf.substring(0, dtmf.length() - menuEndKey.length());
        }
        int menuMaxDigits = menuCfg.path("maxDigits").asInt(1);
        if (menuMaxDigits > 1)
        {
            if (dtmf.length() > menuMaxDigits)
            {
                dtmf = dtmf.substring(0, menuMaxDigits);
            }
        }
        else if (dtmf.length() > 1)
        {
            dtmf = dtmf.substring(0, 1);
        }
        variables.put("dtmf", dtmf);
        variables.put("lastInput", rawInput == null ? "" : rawInput);
        step.setAction("INPUT");
        step.setDetail("按键/语音输入：" + dtmf);
        ctx.getResult().getSteps().add(step);
        return IvrNodeSupport.nextNode(current, ctx.getNodeMap(), ctx.getEdges(), variables, dtmf);
    }
}
