package ai.lawyers.system.service.impl.lawyers.ivr.engine.node;

import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.expression.MapAccessor;
import org.springframework.expression.Expression;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.SimpleEvaluationContext;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import ai.lawyers.common.utils.StringUtils;
import ai.lawyers.system.domain.lawyers.ivr.AiIvrEdge;
import ai.lawyers.system.domain.lawyers.ivr.AiIvrNode;

/**
 * IVR 节点执行共享工具：节点配置解析、出线路由（SpEL 条件 → 标签匹配 → 默认连线）、
 * 输入消费与通用小函数。全部无状态静态方法，供各 {@link IvrNodeExecutor} 复用。
 *
 * @author ai-lawyers
 */
final class IvrNodeSupport
{
    private static final Logger log = LoggerFactory.getLogger(IvrNodeSupport.class);

    static final ObjectMapper MAPPER = new ObjectMapper();
    static final SpelExpressionParser SPEL_PARSER = new SpelExpressionParser();

    private IvrNodeSupport()
    {
    }

    static JsonNode config(AiIvrNode node)
    {
        if (node == null || StringUtils.isEmpty(node.getNodeConfig()))
        {
            return MAPPER.createObjectNode();
        }
        try
        {
            return MAPPER.readTree(node.getNodeConfig());
        }
        catch (Exception e)
        {
            return MAPPER.createObjectNode();
        }
    }

    static String configText(AiIvrNode node, String key)
    {
        JsonNode json = config(node);
        JsonNode value = json.get(key);
        if (value == null)
        {
            value = json.get("value");
        }
        return value == null ? null : value.asText();
    }

    static AiIvrNode nextNode(AiIvrNode current, Map<Long, AiIvrNode> nodeMap,
                              List<AiIvrEdge> edges, Map<String, Object> variables, String inputValue)
    {
        AiIvrEdge edge = routeEdge(current, edges, variables, inputValue);
        return edge == null ? null : nodeMap.get(edge.getTargetNodeId());
    }

    /**
     * 条件路由：SpEL 表达式优先，其次按键/语音节点按标签匹配，最后默认连线（无表达式、无标签）。
     */
    static AiIvrEdge routeEdge(AiIvrNode current, List<AiIvrEdge> edges,
                               Map<String, Object> variables, String inputValue)
    {
        List<AiIvrEdge> outgoing = new ArrayList<>();
        for (AiIvrEdge e : edges)
        {
            if (current.getNodeId().equals(e.getSourceNodeId()))
            {
                outgoing.add(e);
            }
        }
        outgoing.sort((a, b) -> Integer.compare(
                a.getSortOrder() == null ? 0 : a.getSortOrder(),
                b.getSortOrder() == null ? 0 : b.getSortOrder()));

        for (AiIvrEdge edge : outgoing)
        {
            if (StringUtils.isNotEmpty(edge.getConditionExpr()))
            {
                if (evalCondition(edge.getConditionExpr(), variables))
                {
                    return edge;
                }
            }
        }
        if (inputValue != null)
        {
            for (AiIvrEdge edge : outgoing)
            {
                if (StringUtils.isEmpty(edge.getConditionExpr()) && inputValue.equals(edge.getEdgeLabel()))
                {
                    return edge;
                }
            }
        }
        for (AiIvrEdge edge : outgoing)
        {
            if (StringUtils.isEmpty(edge.getConditionExpr()) && StringUtils.isEmpty(edge.getEdgeLabel()))
            {
                return edge;
            }
        }
        return null;
    }

    static boolean evalCondition(String expr, Map<String, Object> variables)
    {
        try
        {
            Expression expression = SPEL_PARSER.parseExpression(expr);
            // 安全收口(S3)：只读数据绑定上下文，禁止 T(...) 类型表达式与任意方法调用；
            // 追加 MapAccessor 并以变量 Map 为根对象，支持 root 键属性式访问。
            // Spring 5.2.x 的 SimpleEvaluationContext 无 setRootObject，根对象须在 builder 阶段 withRootObject 传入
            SimpleEvaluationContext context = SimpleEvaluationContext.forReadOnlyDataBinding()
                    .withRootObject(variables == null ? new HashMap<>() : variables)
                    .build();
            context.getPropertyAccessors().add(0, new MapAccessor());
            if (variables != null)
            {
                variables.forEach(context::setVariable);
            }
            Object value = expression.getValue(context);
            return value != null && (Boolean.TRUE.equals(value)
                    || "true".equalsIgnoreCase(String.valueOf(value)));
        }
        catch (Exception e)
        {
            log.debug("SpEL表达式解析失败 expr={} error={}", expr, e.getMessage());
            return false;
        }
    }

    static String pollInput(Deque<String> inputs)
    {
        if (inputs == null || inputs.isEmpty())
        {
            return null;
        }
        return inputs.poll();
    }

    static String cleanJson(String raw)
    {
        if (StringUtils.isEmpty(raw))
        {
            return "{}";
        }
        String text = raw.trim();
        int start = text.indexOf('{');
        int end = text.lastIndexOf('}');
        if (start >= 0 && end > start)
        {
            text = text.substring(start, end + 1);
        }
        return text;
    }

    static int parseInt(Object value, int fallback)
    {
        if (value == null)
        {
            return fallback;
        }
        try
        {
            return Integer.parseInt(String.valueOf(value));
        }
        catch (Exception e)
        {
            return fallback;
        }
    }

    static boolean isObject(JsonNode node)
    {
        return node != null && node.isObject();
    }

    static String labelOf(AiIvrEdge edge)
    {
        if (StringUtils.isNotEmpty(edge.getConditionExpr()))
        {
            return edge.getConditionExpr();
        }
        if (StringUtils.isNotEmpty(edge.getEdgeLabel()))
        {
            return edge.getEdgeLabel();
        }
        return "默认";
    }

    static String truncate(String s, int max)
    {
        if (s == null)
        {
            return null;
        }
        return s.length() > max ? s.substring(0, max) : s;
    }
}
