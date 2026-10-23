package ai.lawyers.system.service.impl.lawyers.ivr.engine.node;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.JsonNode;

import ai.lawyers.common.utils.StringUtils;
import ai.lawyers.system.domain.lawyers.ivr.AiIvrNode;
import ai.lawyers.system.domain.lawyers.ivr.IvrNodeStep;
import ai.lawyers.system.domain.lawyers.sms.SmsResult;
import ai.lawyers.system.service.impl.lawyers.ivr.engine.IvrTemplateRenderer;
import ai.lawyers.system.service.lawyers.sms.ISmsService;

/**
 * B6 短信通知节点：给来电者发送短信（排队告知 / 工单编号 / 文书链接等）。
 *
 * <p>节点配置支持：templateId（短信模板ID）、phoneVar（收件号码变量，默认 callerNumber，
 * 若流程变量取不到则视为字面号码）、params（模板变量，支持 ${表达式} 渲染）。
 * 短信失败不阻断流程，写入 smsStatus/smsError 后沿下一节点继续。</p>
 *
 * @author ai-lawyers
 */
@Component
public class SmsNodeExecutor implements IvrNodeExecutor
{
    public static final String NODE_TYPE = "sms";

    private final IvrTemplateRenderer templateRenderer;
    private final ISmsService smsService;

    public SmsNodeExecutor(IvrTemplateRenderer templateRenderer, ISmsService smsService)
    {
        this.templateRenderer = templateRenderer;
        this.smsService = smsService;
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

        JsonNode json = IvrNodeSupport.config(current);
        long templateId = json.path("templateId").asLong(0L);
        String phoneVar = json.path("phoneVar").asText("callerNumber");

        Object phoneObj = variables.get(phoneVar);
        String phone = phoneObj == null ? phoneVar : String.valueOf(phoneObj);
        if (StringUtils.isEmpty(phone))
        {
            phone = ctx.getRequest().getCallerNumber();
        }

        Map<String, String> params = new LinkedHashMap<>();
        JsonNode paramsNode = json.path("params");
        if (paramsNode.isTextual() && StringUtils.isNotEmpty(paramsNode.asText()))
        {
            try
            {
                paramsNode = IvrNodeSupport.MAPPER.readTree(paramsNode.asText());
            }
            catch (Exception e)
            {
                paramsNode = IvrNodeSupport.MAPPER.createObjectNode();
            }
        }
        if (paramsNode.isObject())
        {
            Iterator<String> names = paramsNode.fieldNames();
            while (names.hasNext())
            {
                String name = names.next();
                params.put(name, templateRenderer.render(paramsNode.path(name).asText(""), variables));
            }
        }

        step.setAction("SMS");
        if (templateId <= 0L)
        {
            variables.put("smsStatus", "fail");
            variables.put("smsError", "未配置短信模板");
            step.setDetail("短信节点未配置templateId");
            ctx.getResult().getSteps().add(step);
            return IvrNodeSupport.nextNode(current, ctx.getNodeMap(), ctx.getEdges(), variables, null);
        }

        try
        {
            SmsResult smsResult = smsService.send(phone, templateId, params,
                    ctx.getSessionId(), ctx.getRequest().getRecordId());
            variables.put("smsStatus", smsResult.isSuccess() ? "success" : "fail");
            variables.put("smsMsgId", smsResult.getMsgId());
            variables.put("smsContent", smsResult.getContent());
            variables.put("smsError", smsResult.isSuccess() ? "" : smsResult.getMessage());
            step.setDetail("发送短信至 " + phone + "：" + (smsResult.isSuccess()
                    ? "成功（" + smsResult.getMsgId() + "）" : "失败（" + smsResult.getMessage() + "）"));
        }
        catch (Exception e)
        {
            variables.put("smsStatus", "fail");
            variables.put("smsError", e.getMessage());
            step.setDetail("短信发送异常：" + e.getMessage());
        }
        ctx.getResult().getSteps().add(step);
        return IvrNodeSupport.nextNode(current, ctx.getNodeMap(), ctx.getEdges(), variables, null);
    }
}
