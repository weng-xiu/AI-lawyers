package ai.lawyers.system.service.impl.lawyers.ivr.engine.node;

import java.util.Map;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.JsonNode;

import ai.lawyers.common.utils.StringUtils;
import ai.lawyers.system.domain.lawyers.ivr.AiIvrNode;
import ai.lawyers.system.domain.lawyers.ivr.IvrNodeStep;
import ai.lawyers.system.service.impl.lawyers.ivr.engine.IvrTemplateRenderer;

/**
 * variable 变量赋值节点：支持 variables 数组批量赋值或 name/value 单变量赋值，
 * 值经 ${表达式} 渲染；整体为 ${key} 形式时直接引用变量值。
 *
 * @author ai-lawyers
 */
@Component
public class VariableNodeExecutor implements IvrNodeExecutor
{
    public static final String NODE_TYPE = "variable";

    private final IvrTemplateRenderer templateRenderer;

    public VariableNodeExecutor(IvrTemplateRenderer templateRenderer)
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

        assignVariable(current, variables);
        step.setAction("VARIABLE");
        step.setDetail("变量赋值：" + current.getNodeName());
        ctx.getResult().getSteps().add(step);
        return IvrNodeSupport.nextNode(current, ctx.getNodeMap(), ctx.getEdges(), variables, null);
    }

    private void assignVariable(AiIvrNode node, Map<String, Object> variables)
    {
        JsonNode json = IvrNodeSupport.config(node);
        JsonNode list = json.path("variables");
        if (list.isArray() && list.size() > 0)
        {
            for (JsonNode item : list)
            {
                assignOne(item.path("key").asText(""), item.path("val").asText(""), variables);
            }
            return;
        }
        assignOne(json.path("name").asText(""), json.path("value").asText(""), variables);
    }

    private void assignOne(String name, String value, Map<String, Object> variables)
    {
        if (StringUtils.isEmpty(name))
        {
            return;
        }
        value = templateRenderer.render(value, variables);
        if (value.startsWith("${") && value.endsWith("}"))
        {
            String key = value.substring(2, value.length() - 1);
            variables.put(name, variables.get(key));
        }
        else
        {
            variables.put(name, value);
        }
    }
}
