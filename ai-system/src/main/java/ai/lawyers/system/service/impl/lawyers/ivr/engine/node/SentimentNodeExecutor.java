package ai.lawyers.system.service.impl.lawyers.ivr.engine.node;

import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.JsonNode;

import ai.lawyers.common.utils.StringUtils;
import ai.lawyers.system.domain.lawyers.ivr.AiIvrNode;
import ai.lawyers.system.domain.lawyers.ivr.IvrNodeStep;
import ai.lawyers.system.service.impl.lawyers.ivr.engine.IvrTemplateRenderer;
import ai.lawyers.system.service.lawyers.IAiModelConfigService;
import ai.lawyers.system.service.lawyers.stat.AiModelCallLogRecorder;

/**
 * sentiment 情绪分析节点：对文本（配置/lastInput 兜底）做情绪识别，
 * 模型失败时关键词兜底，结果写入 sentiment 变量。
 *
 * @author ai-lawyers
 */
@Component
public class SentimentNodeExecutor implements IvrNodeExecutor
{
    private static final Logger log = LoggerFactory.getLogger(SentimentNodeExecutor.class);

    public static final String NODE_TYPE = "sentiment";

    private final IvrTemplateRenderer templateRenderer;
    private final IAiModelConfigService modelConfigService;

    public SentimentNodeExecutor(IvrTemplateRenderer templateRenderer,
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
        String sentiment = detectSentiment(text);
        variables.put("sentiment", sentiment);
        step.setAction("SENTIMENT");
        step.setDetail("情绪分析：" + sentiment + "（文本：" + IvrNodeSupport.truncate(text, 100) + "）");
        ctx.getResult().getSteps().add(step);
        return IvrNodeSupport.nextNode(current, ctx.getNodeMap(), ctx.getEdges(), variables, null);
    }

    private String detectSentiment(String text)
    {
        if (StringUtils.isEmpty(text))
        {
            return "neutral";
        }
        try
        {
            String system = "你是情绪分析助手。请判断用户话语的情绪，仅返回JSON对象："
                    + "{\"sentiment\":\"positive|negative|neutral\",\"confidence\":0.0-1.0}";
            String user = "用户话语：" + text;
            String raw = modelConfigService.chatJson(system, user, AiModelCallLogRecorder.SCENE_EMOTION);
            JsonNode node = IvrNodeSupport.MAPPER.readTree(IvrNodeSupport.cleanJson(raw));
            String value = node.path("sentiment").asText(node.path("emotion").asText("neutral"));
            return normalizeSentiment(value);
        }
        catch (Exception e)
        {
            log.debug("情绪模型识别失败，使用关键词兜底: {}", e.getMessage());
            return keywordSentiment(text);
        }
    }

    private String keywordSentiment(String text)
    {
        String[] negative = { "投诉", "不满意", "很差", "恶劣", "威胁", "辱骂", "举报", "要命", "告你们",
                "太差", "垃圾", "混蛋", "气死", "崩溃" };
        String[] positive = { "谢谢", "感谢", "满意", "很好", "太好了", "点赞", "表扬" };
        for (String word : negative)
        {
            if (text.contains(word))
            {
                return "negative";
            }
        }
        for (String word : positive)
        {
            if (text.contains(word))
            {
                return "positive";
            }
        }
        return "neutral";
    }

    private String normalizeSentiment(String value)
    {
        if (StringUtils.isEmpty(value))
        {
            return "neutral";
        }
        String v = value.trim();
        if ("正面".equals(v) || "积极".equals(v) || "front".equalsIgnoreCase(v)
                || "positive".equalsIgnoreCase(v))
        {
            return "positive";
        }
        if ("负面".equals(v) || "消极".equals(v) || "negative".equalsIgnoreCase(v))
        {
            return "negative";
        }
        return "neutral";
    }
}
