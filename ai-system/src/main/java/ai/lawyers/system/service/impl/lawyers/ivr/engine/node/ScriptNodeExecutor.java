package ai.lawyers.system.service.impl.lawyers.ivr.engine.node;

import java.util.HashMap;
import java.util.Map;

import org.springframework.context.expression.MapAccessor;
import org.springframework.expression.Expression;
import org.springframework.expression.spel.support.SimpleEvaluationContext;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.JsonNode;

import ai.lawyers.common.utils.StringUtils;
import ai.lawyers.system.domain.lawyers.ivr.AiIvrNode;
import ai.lawyers.system.domain.lawyers.ivr.IvrNodeStep;

/**
 * script 脚本节点：JDK 17 迁移后 Nashorn 已删除，脚本按 SpEL 表达式求值
 * （与条件节点一致，复用现有安全沙箱），结果写入 resultVar（默认 scriptResult）。
 *
 * @author ai-lawyers
 */
@Component
public class ScriptNodeExecutor implements IvrNodeExecutor
{
    public static final String NODE_TYPE = "script";

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
        String language = json.path("scriptType").asText(json.path("language").asText("js"));
        String script = json.path("script").asText("");
        String resultVar = json.path("resultVar").asText("scriptResult");
        step.setAction("SCRIPT");
        if (StringUtils.isEmpty(script))
        {
            step.setDetail("脚本节点未配置脚本内容");
            ctx.getResult().getSteps().add(step);
            return IvrNodeSupport.nextNode(current, ctx.getNodeMap(), ctx.getEdges(), variables, null);
        }
        if ("groovy".equalsIgnoreCase(language))
        {
            variables.put("scriptError", "当前环境不支持Groovy，请使用SpEL表达式");
            step.setDetail("脚本执行失败：当前环境不支持Groovy，请使用SpEL表达式");
            ctx.getResult().getSteps().add(step);
            return IvrNodeSupport.nextNode(current, ctx.getNodeMap(), ctx.getEdges(), variables, null);
        }
        try
        {
            // JDK 17 迁移：Nashorn 已删除，改用 SpEL 表达式（与条件节点一致，复用现有安全沙箱）
            Expression expression = IvrNodeSupport.SPEL_PARSER.parseExpression(script);
            SimpleEvaluationContext context = SimpleEvaluationContext.forReadOnlyDataBinding()
                    .withRootObject(variables == null ? new HashMap<>() : variables)
                    .build();
            context.getPropertyAccessors().add(0, new MapAccessor());
            if (variables != null)
            {
                variables.forEach(context::setVariable);
            }
            Object value = expression.getValue(context);
            variables.put(resultVar, value == null ? "" : String.valueOf(value));
            step.setDetail("脚本执行成功，结果已写入变量" + resultVar);
        }
        catch (Exception e)
        {
            variables.put("scriptError", e.getMessage());
            step.setDetail("脚本执行失败：" + e.getMessage());
        }
        ctx.getResult().getSteps().add(step);
        return IvrNodeSupport.nextNode(current, ctx.getNodeMap(), ctx.getEdges(), variables, null);
    }
}
