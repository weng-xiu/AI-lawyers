package ai.lawyers.system.service.impl.lawyers.ivr.engine.node;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.JsonNode;

import ai.lawyers.common.utils.StringUtils;
import ai.lawyers.system.domain.lawyers.ivr.AiIvrNode;
import ai.lawyers.system.domain.lawyers.ivr.IvrNodeStep;
import ai.lawyers.system.service.impl.lawyers.ivr.engine.IvrTemplateRenderer;
import ai.lawyers.system.service.lawyers.IAiModelConfigService;
import ai.lawyers.system.service.lawyers.stat.AiModelCallLogRecorder;

/**
 * extract 信息抽取节点：按 fields/keys 配置从文本（配置/lastInput 兜底）中抽取字段，
 * 逐字段写入流程变量，完整 JSON 写入 resultVar（默认 extractResult）；失败不阻断流程。
 *
 * @author ai-lawyers
 */
@Component
public class ExtractNodeExecutor implements IvrNodeExecutor
{
    public static final String NODE_TYPE = "extract";

    private final IvrTemplateRenderer templateRenderer;
    private final IAiModelConfigService modelConfigService;

    public ExtractNodeExecutor(IvrTemplateRenderer templateRenderer,
                               IAiModelConfigService modelConfigService)
    {
        this.templateRenderer = templateRenderer;
        this.modelConfigService = modelConfigService;
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

        String text = templateRenderer.render(IvrNodeSupport.configText(current, "text"), variables);
        if (StringUtils.isEmpty(text))
        {
            text = templateRenderer.render(IvrNodeSupport.configText(current, "message"), variables);
        }
        if (StringUtils.isEmpty(text) && variables.get("lastInput") != null)
        {
            text = String.valueOf(variables.get("lastInput"));
        }
        JsonNode json = IvrNodeSupport.config(current);
        JsonNode fields = json.path("fields");
        if (!fields.isObject())
        {
            fields = json.path("keys");
        }
        JsonNode fieldArray = null;
        if (fields.isArray())
        {
            fieldArray = fields;
            fields = null;
        }
        step.setAction("EXTRACT");
        if ((!IvrNodeSupport.isObject(fields) || fields.size() == 0)
                && (fieldArray == null || fieldArray.size() == 0))
        {
            step.setDetail("信息抽取节点未配置fields/keys，跳过");
            ctx.getResult().getSteps().add(step);
            return IvrNodeSupport.nextNode(current, ctx.getNodeMap(), ctx.getEdges(), variables, null);
        }
        try
        {
            StringBuilder fieldDesc = new StringBuilder();
            List<String> fieldNames = new ArrayList<>();
            if (IvrNodeSupport.isObject(fields))
            {
                Iterator<String> names = fields.fieldNames();
                while (names.hasNext())
                {
                    String name = names.next();
                    fieldNames.add(name);
                    fieldDesc.append(" - ").append(name).append("：")
                            .append(fields.path(name).asText("")).append("\n");
                }
            }
            else
            {
                for (JsonNode item : fieldArray)
                {
                    String name = item.path("name").asText("");
                    fieldNames.add(name);
                    fieldDesc.append(" - ").append(name).append("：")
                            .append(item.path("desc").asText(item.path("description").asText("")))
                            .append("\n");
                }
            }
            String system = "你是信息抽取助手。请从用户提供的文本中提取字段，仅返回JSON对象，"
                    + "字段值缺失时使用空字符串，不要输出任何多余内容。";
            String user = "需要提取的字段：\n" + fieldDesc + "\n原始文本：" + text;
            String raw = modelConfigService.chatJson(system, user, AiModelCallLogRecorder.SCENE_EXTRACT);
            JsonNode extracted = IvrNodeSupport.MAPPER.readTree(IvrNodeSupport.cleanJson(raw));
            int count = 0;
            for (String name : fieldNames)
            {
                JsonNode value = extracted.path(name);
                variables.put(name, value.isMissingNode() || value.isNull() ? ""
                        : value.isValueNode() ? value.asText() : value.toString());
                count++;
            }
            String resultVar = json.path("resultVar").asText("extractResult");
            variables.put(resultVar, extracted.toString());
            step.setDetail("信息抽取成功，共提取" + count + "个字段：" + extracted);
        }
        catch (Exception e)
        {
            variables.put("extractError", e.getMessage());
            step.setDetail("信息抽取失败：" + e.getMessage());
        }
        ctx.getResult().getSteps().add(step);
        return IvrNodeSupport.nextNode(current, ctx.getNodeMap(), ctx.getEdges(), variables, null);
    }
}
