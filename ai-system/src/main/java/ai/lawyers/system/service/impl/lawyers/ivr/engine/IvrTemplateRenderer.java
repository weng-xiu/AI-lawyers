package ai.lawyers.system.service.impl.lawyers.ivr.engine;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.expression.MapAccessor;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.SimpleEvaluationContext;
import org.springframework.stereotype.Component;
import ai.lawyers.common.utils.StringUtils;

/**
 * P2-14：IVR {@code ${表达式}} SpEL 模板渲染器。
 *
 * <p>从 {@code IvrEngineServiceImpl} 抽出的叶子工具，无状态；表达式在只读数据绑定上下文中求值，
 * 禁止类型引用(T(...))/构造器/方法调用/赋值。</p>
 *
 * @author ai-lawyers
 */
@Component
public class IvrTemplateRenderer
{
    private static final Logger log = LoggerFactory.getLogger(IvrTemplateRenderer.class);

    private static final SpelExpressionParser SPEL_PARSER = new SpelExpressionParser();

    private static final Pattern TEMPLATE_PATTERN = Pattern.compile("\\$\\{([^}]+)}");

    /**
     * 渲染 {@code ${表达式}} 模板，表达式按 SpEL 在流程变量上下文中求值。
     */
    public String render(String template, Map<String, Object> variables)
    {
        if (StringUtils.isEmpty(template) || !template.contains("${"))
        {
            return template;
        }
        Matcher matcher = TEMPLATE_PATTERN.matcher(template);
        StringBuffer sb = new StringBuffer();
        while (matcher.find())
        {
            Object value = evalExpr(matcher.group(1), variables);
            matcher.appendReplacement(sb, Matcher.quoteReplacement(value == null ? "" : String.valueOf(value)));
        }
        matcher.appendTail(sb);
        return sb.toString();
    }

    private Object evalExpr(String expr, Map<String, Object> variables)
    {
        try
        {
            // 安全收口(S3)：只读数据绑定上下文，禁止类型引用(T(...))/构造器/方法调用/赋值，杜绝 RCE；
            // 追加 MapAccessor 以支持 ${#var} 之外的 Map 键属性式访问
            SimpleEvaluationContext context = SimpleEvaluationContext.forReadOnlyDataBinding().build();
            context.getPropertyAccessors().add(0, new MapAccessor());
            if (variables != null)
            {
                variables.forEach(context::setVariable);
            }
            return SPEL_PARSER.parseExpression(expr).getValue(context);
        }
        catch (Exception e)
        {
            log.debug("SpEL模板表达式解析失败 expr={} error={}", expr, e.getMessage());
            return null;
        }
    }
}
