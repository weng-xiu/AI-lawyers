package ai.lawyers.system.service.impl.lawyers.quality;

import java.math.BigDecimal;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import ai.lawyers.common.utils.StringUtils;
import ai.lawyers.system.domain.lawyers.AiCallRecord;
import ai.lawyers.system.service.lawyers.IAiModelConfigService;

/**
 * 质检双引擎评分协作类（P5 超大类拆分）。
 *
 * <p>从 {@link AiQualityInspectionServiceImpl} 抽出的"评分"叶子职责：
 * 规则引擎（确定性零成本）+ LLM 四维度评分融合，LLM 不可用时规则引擎降级；
 * 声学规则命中时对服务规范维度追加扣分并合并违规明细。
 * 不反向依赖门面，无循环依赖。</p>
 *
 * @author ai-lawyers
 */
@Component
public class QualityScoringEngine
{
    private static final Logger log = LoggerFactory.getLogger(QualityScoringEngine.class);

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Autowired
    private IAiModelConfigService modelConfigService;

    /** P1-7：规则引擎（违禁词/服务规范，零成本确定性评分） */
    @Autowired
    private QualityRuleEngine qualityRuleEngine;

    /** P1-7：质检模板（维度/权重/prompt 自定义） */
    @Autowired
    private QualityTemplateSupport qualityTemplateSupport;

    /** AI 评分中间结果 */
    public static class AiScore
    {
        public BigDecimal totalScore;
        public String dimensionJson;
        public String violationJson;
        public String remark;
        public JsonNode violationNodes;
    }

    /**
     * P1-7：AI + 规则双引擎融合评分。
     *
     * <p>规则引擎始终执行；LLM 可用时：compliance/serviceNorm 取两引擎更严值（min），
     * answerAccuracy/emotionAttitude 取 LLM，违规明细合并并标记来源，totalScore 按生效质检
     * 模板的维度权重加权（无模板则四维等权平均）。
     * LLM 不可用时：仅用规则引擎可评两维（模板中其余维度在 dimensions 中为 null，不计入加权），
     * 质检仍可完成，remark 注明降级——不再因模型抖动整单失败。</p>
     */
    public AiScore scoreByDualEngine(String transcript, AiCallRecord record,
                                     AcousticFeatureAnalyzer.AcousticResult acoustic)
    {
        QualityTemplateSupport.ActiveTemplate template = qualityTemplateSupport.loadActive();
        QualityRuleEngine.RuleResult rule = qualityRuleEngine.evaluate(transcript);
        AiScore score = new AiScore();
        try
        {
            AiScore ai = scoreByAi(transcript, record, template);
            JsonNode aiDims = MAPPER.readTree(ai.dimensionJson);

            double serviceNorm = Math.min(aiDims.path("serviceNorm").asDouble(100d), rule.serviceNormScore());
            double compliance = Math.min(aiDims.path("compliance").asDouble(100d), rule.getComplianceScore());
            double answerAccuracy = aiDims.path("answerAccuracy").asDouble(0d);
            double emotionAttitude = aiDims.path("emotionAttitude").asDouble(0d);
            // 声学规则对服务规范维度追加扣分（下限0）
            serviceNorm = applyAcousticPenalty(serviceNorm, acoustic);

            ObjectNode allFused = MAPPER.createObjectNode();
            allFused.put("serviceNorm", round1(serviceNorm));
            allFused.put("answerAccuracy", round1(answerAccuracy));
            allFused.put("emotionAttitude", round1(emotionAttitude));
            allFused.put("compliance", round1(compliance));

            // 输出维度按模板顺序裁剪（无模板则四维全量）
            ObjectNode fusedDims = MAPPER.createObjectNode();
            if (template != null)
            {
                for (QualityTemplateSupport.Dimension dim : template.dimensions)
                {
                    fusedDims.set(dim.key, allFused.get(dim.key));
                }
            }
            else
            {
                fusedDims = allFused;
            }
            score.dimensionJson = MAPPER.writeValueAsString(fusedDims);

            // 违规合并：AI 项（source=ai）+ 规则项（source=rule）+ 声学项（source=acoustic）
            ArrayNode merged = MAPPER.createArrayNode();
            if (ai.violationNodes != null && ai.violationNodes.isArray())
            {
                for (JsonNode item : ai.violationNodes)
                {
                    ObjectNode v = merged.addObject();
                    v.put("keyword", item.path("keyword").asText(""));
                    v.put("reason", item.path("reason").asText(""));
                    v.put("source", "ai");
                }
            }
            for (QualityRuleEngine.RuleViolation rv : rule.getViolations())
            {
                ObjectNode v = merged.addObject();
                v.put("keyword", rv.getKeyword());
                v.put("reason", rv.getReason());
                v.put("source", "rule");
            }
            appendAcousticViolations(merged, acoustic);
            score.violationNodes = merged;
            score.violationJson = MAPPER.writeValueAsString(merged);
            double total = template != null
                    ? qualityTemplateSupport.weightedTotal(template, fusedDims)
                    : (serviceNorm + compliance + answerAccuracy + emotionAttitude) / 4d;
            score.totalScore = BigDecimal.valueOf(round1(total));
            score.remark = "AI+规则双引擎融合评分"
                    + (template != null ? "（模板 " + template.templateId + "）。" : "。")
                    + (ai.remark == null ? "" : ai.remark)
                    + acousticRemark(acoustic);
            return score;
        }
        catch (RuntimeException e)
        {
            // scoreByAi 的模型调用/解析失败：规则引擎降级（不重投、不置失败）
            log.warn("AI 评分不可用，质检降级为规则引擎评分 recordId={}: {}",
                    record.getRecordId(), e.getMessage());
            return ruleOnlyScore(rule, template, e.getMessage(), acoustic);
        }
        catch (Exception e)
        {
            return ruleOnlyScore(rule, template, e.getMessage(), acoustic);
        }
    }

    /** 声学扣分：在服务规范基准分上减去扣分，下限 0；无声学结果原样返回 */
    private static double applyAcousticPenalty(double serviceNorm,
                                               AcousticFeatureAnalyzer.AcousticResult acoustic)
    {
        if (acoustic == null)
        {
            return serviceNorm;
        }
        return Math.max(0d, serviceNorm - acoustic.getServiceNormPenalty());
    }

    /** 追加声学违规明细（source=acoustic） */
    private static void appendAcousticViolations(ArrayNode target,
                                                 AcousticFeatureAnalyzer.AcousticResult acoustic)
    {
        if (acoustic == null)
        {
            return;
        }
        for (QualityRuleEngine.RuleViolation rv : acoustic.getViolations())
        {
            ObjectNode v = target.addObject();
            v.put("keyword", rv.getKeyword());
            v.put("reason", rv.getReason());
            v.put("source", "acoustic");
        }
    }

    /** 声学特征留痕：语速/最长静默/有声时长拼入 remark */
    private static String acousticRemark(AcousticFeatureAnalyzer.AcousticResult acoustic)
    {
        if (acoustic == null)
        {
            return "";
        }
        return " 声学：语速=" + acoustic.getSpeechRate() + " 字/分，最长静默="
                + acoustic.getMaxSilenceMs() + "ms，有声=" + acoustic.getVoicedMs() + "ms。";
    }

    /** 仅规则引擎评分：serviceNorm/compliance 计分，模板内其余维度置 null 不计加权 */
    private AiScore ruleOnlyScore(QualityRuleEngine.RuleResult rule,
                                  QualityTemplateSupport.ActiveTemplate template, String failReason,
                                  AcousticFeatureAnalyzer.AcousticResult acoustic)
    {
        AiScore score = new AiScore();
        try
        {
            ObjectNode all = MAPPER.createObjectNode();
            all.put("serviceNorm", round1(applyAcousticPenalty(rule.serviceNormScore(), acoustic)));
            all.putNull("answerAccuracy");
            all.putNull("emotionAttitude");
            all.put("compliance", round1(rule.getComplianceScore()));

            ObjectNode dims = MAPPER.createObjectNode();
            if (template != null)
            {
                for (QualityTemplateSupport.Dimension dim : template.dimensions)
                {
                    dims.set(dim.key, all.get(dim.key));
                }
            }
            else
            {
                dims = all;
            }
            score.dimensionJson = MAPPER.writeValueAsString(dims);

            ArrayNode violations = MAPPER.createArrayNode();
            for (QualityRuleEngine.RuleViolation rv : rule.getViolations())
            {
                ObjectNode v = violations.addObject();
                v.put("keyword", rv.getKeyword());
                v.put("reason", rv.getReason());
                v.put("source", "rule");
            }
            appendAcousticViolations(violations, acoustic);
            score.violationNodes = violations;
            score.violationJson = MAPPER.writeValueAsString(violations);
            double total = template != null
                    ? qualityTemplateSupport.weightedTotal(template, dims)
                    : (applyAcousticPenalty(rule.serviceNormScore(), acoustic)
                            + rule.getComplianceScore()) / 2d;
            score.totalScore = BigDecimal.valueOf(round1(total));
            score.remark = "AI 评分不可用，规则引擎降级评分（原因：" + failReason + "）"
                    + acousticRemark(acoustic);
        }
        catch (Exception ex)
        {
            throw new RuntimeException("规则引擎降级评分序列化失败：" + ex.getMessage(), ex);
        }
        return score;
    }

    /** 保留 1 位小数 */
    private static double round1(double value)
    {
        return BigDecimal.valueOf(value).setScale(1, BigDecimal.ROUND_HALF_UP).doubleValue();
    }

    private AiScore scoreByAi(String transcript, AiCallRecord record,
                              QualityTemplateSupport.ActiveTemplate template)
    {
        AiScore score = new AiScore();
        String defaultSystem = "你是12348公共法律服务热线的质检员。请根据坐席与群众的通话转写文本，从四个维度评分（0-100）："
                + "serviceNorm（服务规范：问候/自报身份/礼貌用语/结束语）、answerAccuracy（答复准确：法律解答正确、无误导）、"
                + "emotionAttitude（情绪态度：耐心、中立、无争执）、compliance（合规话术：无违禁/敏感/承诺胜诉/私自收费等）。"
                + "同时给出 violations 数组，列出命中的违禁/敏感/情绪问题（每项含 keyword 与 reason，无则空数组），"
                + "以及 overall 总体评语。仅返回JSON："
                + "{\"totalScore\":数字,\"dimensions\":{\"serviceNorm\":数字,\"answerAccuracy\":数字,"
                + "\"emotionAttitude\":数字,\"compliance\":数字},\"violations\":[{\"keyword\":\"\",\"reason\":\"\"}],"
                + "\"overall\":\"\"}";
        // P1-7：模板配置了自定义 prompt 则优先使用（须自行约定同样的 JSON 返回契约）
        String system = template != null && StringUtils.isNotEmpty(template.scorePrompt)
                ? template.scorePrompt.trim() : defaultSystem;
        String user = "通话记录ID：" + record.getRecordId()
                + (StringUtils.isNotEmpty(record.getContent()) ? "；咨询内容：" + record.getContent() : "")
                + "\n通话转写：\n" + transcript;
        String raw = modelConfigService.chatJson(system, user,
                ai.lawyers.system.service.lawyers.stat.AiModelCallLogRecorder.SCENE_QUALITY);
        try
        {
            JsonNode node = MAPPER.readTree(cleanJson(raw));
            score.totalScore = BigDecimal.valueOf(node.path("totalScore").asDouble(0));
            ObjectNode dims = MAPPER.createObjectNode();
            JsonNode d = node.path("dimensions");
            dims.put("serviceNorm", d.path("serviceNorm").asDouble(0));
            dims.put("answerAccuracy", d.path("answerAccuracy").asDouble(0));
            dims.put("emotionAttitude", d.path("emotionAttitude").asDouble(0));
            dims.put("compliance", d.path("compliance").asDouble(0));
            score.dimensionJson = MAPPER.writeValueAsString(dims);

            ArrayNode violations = MAPPER.createArrayNode();
            JsonNode v = node.path("violations");
            if (v.isArray())
            {
                for (JsonNode item : v)
                {
                    violations.add(item);
                }
            }
            score.violationJson = MAPPER.writeValueAsString(violations);
            score.remark = node.path("overall").asText("");
            score.violationNodes = v;
        }
        catch (Exception e)
        {
            throw new RuntimeException("解析质检评分结果失败：" + e.getMessage(), e);
        }
        return score;
    }

    private String cleanJson(String raw)
    {
        if (raw == null)
        {
            return "{}";
        }
        int start = raw.indexOf('{');
        int end = raw.lastIndexOf('}');
        if (start >= 0 && end > start)
        {
            return raw.substring(start, end + 1);
        }
        return raw;
    }
}
