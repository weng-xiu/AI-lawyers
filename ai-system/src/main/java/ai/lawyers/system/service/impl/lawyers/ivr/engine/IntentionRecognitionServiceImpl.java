package ai.lawyers.system.service.impl.lawyers.ivr.engine;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.ObjectMapper;

import ai.lawyers.common.utils.StringUtils;
import ai.lawyers.system.domain.lawyers.AiConsultationCategory;
import ai.lawyers.system.domain.lawyers.AiModelConfig;
import ai.lawyers.system.domain.lawyers.ivr.AiIvrIntention;
import ai.lawyers.system.domain.lawyers.ivr.AiIvrIntentionLog;
import ai.lawyers.system.domain.lawyers.ivr.IntentionMatchResult;
import ai.lawyers.system.service.lawyers.IAiConsultationCategoryService;
import ai.lawyers.system.service.lawyers.IAiModelConfigService;
import ai.lawyers.system.service.lawyers.ivr.IAiIvrIntentionLogService;
import ai.lawyers.system.service.lawyers.ivr.IAiIvrIntentionService;
import ai.lawyers.system.service.lawyers.ivr.engine.IntentionRecognitionService;

/**
 * 意图识别服务实现：正则快速匹配 + AI 大模型深度识别。
 *
 * 融合点：
 *  - 复用 ai_ivr_intention 意图定义（正则/提示词/示例话术）；
 *  - AI 兜底复用 AiModelConfig 默认模型配置；
 *  - 识别结果自动映射到 ai_consultation_category 咨询分类；
 *  - 每次识别写入 ai_ivr_intention_log 并与通话记录关联。
 */
@Service
public class IntentionRecognitionServiceImpl implements IntentionRecognitionService
{
    private static final Logger log = LoggerFactory.getLogger(IntentionRecognitionServiceImpl.class);

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Autowired
    private IAiIvrIntentionService intentionService;

    @Autowired
    private IAiIvrIntentionLogService intentionLogService;

    @Autowired
    private IAiModelConfigService modelConfigService;

    @Autowired
    private IAiConsultationCategoryService categoryService;

    @Override
    public IntentionMatchResult recognize(String text, Long recordId, String sessionId, Long flowId, Long nodeId)
    {
        IntentionMatchResult match = doRecognize(text);

        AiIvrIntentionLog intentLog = new AiIvrIntentionLog();
        intentLog.setRecordId(recordId);
        intentLog.setSessionId(sessionId);
        intentLog.setFlowId(flowId);
        intentLog.setNodeId(nodeId);
        intentLog.setInputText(truncate(text, 1000));
        if (match.isMatched())
        {
            intentLog.setMatchedIntention(match.getIntentionCode());
            intentLog.setMatchedIntentionName(match.getIntentionName());
            intentLog.setConfidence(match.getConfidence());
            intentLog.setMatchMethod(match.getMatchMethod());
        }
        else
        {
            intentLog.setMatchMethod("0");
        }
        try
        {
            intentionLogService.insertAiIvrIntentionLog(intentLog);
        }
        catch (Exception e)
        {
            log.warn("意图识别日志写入失败: {}", e.getMessage());
        }
        return match;
    }

    @Override
    public IntentionMatchResult recognize(String text)
    {
        return doRecognize(text);
    }

    private IntentionMatchResult doRecognize(String text)
    {
        if (StringUtils.isEmpty(text))
        {
            return IntentionMatchResult.none("输入文本为空");
        }

        List<AiIvrIntention> intentions = new ArrayList<>(intentionService.selectActiveIntentions());
        intentions.sort(Comparator.comparing(AiIvrIntention::getPriority,
                Comparator.nullsFirst(Comparator.naturalOrder())).reversed());

        // 1. 正则快速匹配（收集全部命中，按优先级取最优）
        List<Map<String, Object>> allResults = new ArrayList<>();
        AiIvrIntention bestRegex = null;
        for (AiIvrIntention intention : intentions)
        {
            boolean hit = false;
            if (StringUtils.isNotEmpty(intention.getRegexPattern()))
            {
                try
                {
                    Pattern pattern = Pattern.compile(intention.getRegexPattern(), Pattern.CASE_INSENSITIVE);
                    hit = pattern.matcher(text).find();
                }
                catch (Exception e)
                {
                    log.debug("意图[{}]正则编译失败: {}", intention.getIntentionCode(), e.getMessage());
                }
            }
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("code", intention.getIntentionCode());
            item.put("name", intention.getIntentionName());
            item.put("priority", intention.getPriority());
            item.put("matched", hit);
            allResults.add(item);
            if (hit && bestRegex == null)
            {
                bestRegex = intention;
            }
        }

        if (bestRegex != null)
        {
            return buildMatch(bestRegex, new BigDecimal("1.0000"), "1", allResults, text);
        }

        // 2. AI 大模型兜底识别
        AiIvrIntention aiMatch = recognizeByModel(text, intentions);
        if (aiMatch != null)
        {
            return buildMatch(aiMatch, new BigDecimal("0.6000"), "2", allResults, text);
        }

        return IntentionMatchResult.none("未匹配到任何意图");
    }

    /**
     * 调用默认模型，按意图 Prompt 模板做深度识别。
     * 解析失败或模型不可用时返回 null（不影响主流程）。
     */
    private AiIvrIntention recognizeByModel(String text, List<AiIvrIntention> intentions)
    {
        boolean hasModelIntention = intentions.stream().anyMatch(i -> i.getModelId() != null);
        AiModelConfig defaultConfig = null;
        try
        {
            defaultConfig = modelConfigService.getDefaultAiModelConfig();
        }
        catch (Exception ignored)
        {
        }
        if (!hasModelIntention && defaultConfig == null)
        {
            return null;
        }

        StringBuilder candidate = new StringBuilder();
        for (AiIvrIntention intention : intentions)
        {
            candidate.append(intention.getIntentionCode()).append(":").append(intention.getIntentionName()).append(";");
        }
        String question = "请判断用户问题最匹配下列哪个意图编码，仅返回编码。用户问题：" + text;
        String context = "意图候选：" + candidate;
        try
        {
            String response = modelConfigService.callAiModel(question, context,
                    ai.lawyers.system.service.lawyers.stat.AiModelCallLogRecorder.SCENE_INTENTION);
            if (StringUtils.isEmpty(response))
            {
                return null;
            }
            for (AiIvrIntention intention : intentions)
            {
                if (response.contains(intention.getIntentionCode()))
                {
                    return intention;
                }
            }
        }
        catch (Exception e)
        {
            log.warn("AI意图识别调用失败: {}", e.getMessage());
        }
        return null;
    }

    private IntentionMatchResult buildMatch(AiIvrIntention intention, BigDecimal confidence, String method,
                                            List<Map<String, Object>> allResults, String text)
    {
        IntentionMatchResult result = new IntentionMatchResult();
        result.setMatched(true);
        result.setIntentionId(intention.getIntentionId());
        result.setIntentionCode(intention.getIntentionCode());
        result.setIntentionName(intention.getIntentionName());
        result.setCategory(intention.getCategory());
        result.setConfidence(confidence);
        result.setMatchMethod(method);
        result.setMessage("识别成功");

        // 意图 -> 咨询分类 映射（融合点：意图识别结果自动关联咨询分类）
        AiConsultationCategory category = resolveCategory(intention);
        if (category != null)
        {
            result.setCategoryId(category.getCategoryId());
            result.setCategoryName(category.getCategoryName());
        }
        return result;
    }

    private AiConsultationCategory resolveCategory(AiIvrIntention intention)
    {
        if (intention == null)
        {
            return null;
        }
        List<AiConsultationCategory> categories;
        try
        {
            categories = categoryService.selectAiConsultationCategoryList(new AiConsultationCategory());
        }
        catch (Exception e)
        {
            return null;
        }

        AiConsultationCategory best = null;
        int bestScore = 0;
        String[] names = { intention.getIntentionName(), intention.getCategory() };
        for (String raw : names)
        {
            if (StringUtils.isEmpty(raw))
            {
                continue;
            }
            String normalized = normalize(raw);
            for (AiConsultationCategory category : categories)
            {
                if (category == null || StringUtils.isEmpty(category.getCategoryName()))
                {
                    continue;
                }
                String catName = category.getCategoryName();
                int score = 0;
                if (catName.equals(raw))
                {
                    score = 3;
                }
                else if (catName.contains(raw) || raw.contains(catName))
                {
                    score = 2;
                }
                else
                {
                    String catNormalized = normalize(catName);
                    if (StringUtils.isNotEmpty(catNormalized) && catNormalized.equals(normalized))
                    {
                        score = 1;
                    }
                }
                if (score > bestScore)
                {
                    bestScore = score;
                    best = category;
                }
            }
        }
        return best;
    }

    /** 归一化分类名：去除常见后缀，便于"劳动争议/劳动纠纷"这类别名对齐 */
    private String normalize(String name)
    {
        String result = name == null ? "" : name.trim();
        String[] suffixes = { "纠纷", "争议", "咨询", "案件", "问题", "事项", "类", "罪" };
        boolean changed = true;
        while (changed)
        {
            changed = false;
            for (String suffix : suffixes)
            {
                if (result.endsWith(suffix))
                {
                    result = result.substring(0, result.length() - suffix.length());
                    changed = true;
                }
            }
        }
        return result;
    }

    private String truncate(String s, int max)
    {
        if (s == null)
        {
            return null;
        }
        return s.length() > max ? s.substring(0, max) : s;
    }
}
