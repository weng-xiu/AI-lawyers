package ai.lawyers.system.service.impl.lawyers.ivr.engine.node;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.JsonNode;

import ai.lawyers.common.utils.StringUtils;
import ai.lawyers.system.domain.lawyers.ivr.AiIvrNode;
import ai.lawyers.system.domain.lawyers.ivr.IvrNodeStep;
import ai.lawyers.system.service.impl.lawyers.ivr.engine.IvrHttpClient;
import ai.lawyers.system.service.impl.lawyers.ivr.engine.IvrTemplateRenderer;

/**
 * service HTTP 调用节点：渲染 url/headers/body 后发起调用，响应写入 resultVar，
 * 可按 resultPath 取值写入 serviceValue；SSRF 安全策略拦截内网/回环目标（S5）。
 *
 * @author ai-lawyers
 */
@Component
public class ServiceNodeExecutor implements IvrNodeExecutor
{
    public static final String NODE_TYPE = "service";

    private final IvrTemplateRenderer templateRenderer;
    private final IvrHttpClient ivrHttpClient;

    public ServiceNodeExecutor(IvrTemplateRenderer templateRenderer, IvrHttpClient ivrHttpClient)
    {
        this.templateRenderer = templateRenderer;
        this.ivrHttpClient = ivrHttpClient;
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
        String url = templateRenderer.render(json.path("url").asText(""), variables);
        String method = json.path("method").asText("GET").trim().toUpperCase();
        if (StringUtils.isEmpty(method))
        {
            method = "GET";
        }
        int timeoutMs = json.path("timeoutMs").asInt(10000);
        String resultPath = json.path("result").asText("");
        String resultVar = json.path("resultVar").asText("serviceResult");
        Map<String, String> headers = new LinkedHashMap<>();
        JsonNode headerNode = json.path("headers");
        if (headerNode.isTextual() && StringUtils.isNotEmpty(headerNode.asText()))
        {
            try
            {
                headerNode = IvrNodeSupport.MAPPER.readTree(headerNode.asText());
            }
            catch (Exception e)
            {
                headerNode = IvrNodeSupport.MAPPER.createObjectNode();
            }
        }
        if (headerNode.isObject())
        {
            Iterator<String> names = headerNode.fieldNames();
            while (names.hasNext())
            {
                String name = names.next();
                headers.put(name, templateRenderer.render(headerNode.path(name).asText(""), variables));
            }
        }
        String body = null;
        if (json.path("body").isTextual())
        {
            body = templateRenderer.render(json.path("body").asText(), variables);
        }
        else if (json.path("body").isObject() || json.path("body").isArray())
        {
            body = templateRenderer.render(json.path("body").toString(), variables);
        }

        step.setAction("SERVICE");
        if (StringUtils.isEmpty(url))
        {
            variables.put("serviceError", "未配置请求地址");
            step.setDetail("HTTP调用节点未配置url");
            ctx.getResult().getSteps().add(step);
            return IvrNodeSupport.nextNode(current, ctx.getNodeMap(), ctx.getEdges(), variables, null);
        }
        // 安全收口(S5)：SSRF 防护，仅允许 http/https 且目标不得为内网/回环/保留地址
        String ssrfError = ivrHttpClient.checkUrlAllowed(url);
        if (ssrfError != null)
        {
            variables.put("serviceError", ssrfError);
            step.setDetail("HTTP调用被安全策略拦截：" + ssrfError);
            ctx.getResult().getSteps().add(step);
            return IvrNodeSupport.nextNode(current, ctx.getNodeMap(), ctx.getEdges(), variables, null);
        }
        try
        {
            String responseText = ivrHttpClient.httpCall(url, method, headers, body, timeoutMs);
            variables.put(resultVar, responseText);
            if (StringUtils.isNotEmpty(resultPath))
            {
                Object value = ivrHttpClient.jsonPath(responseText, resultPath);
                variables.put("serviceValue", value == null ? "" : String.valueOf(value));
            }
            step.setDetail("HTTP调用成功：" + method + " " + url
                    + "，响应" + responseText.length() + "字节"
                    + (StringUtils.isEmpty(resultPath) ? "" : "，取值路径=" + resultPath));
        }
        catch (Exception e)
        {
            variables.put("serviceError", e.getMessage());
            step.setDetail("HTTP调用失败：" + method + " " + url + "，错误=" + e.getMessage());
        }
        ctx.getResult().getSteps().add(step);
        return IvrNodeSupport.nextNode(current, ctx.getNodeMap(), ctx.getEdges(), variables, null);
    }
}
