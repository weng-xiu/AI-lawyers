package ai.lawyers.system.service.impl.lawyers.quality;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import ai.lawyers.system.domain.lawyers.AiCallRecord;
import ai.lawyers.system.service.lawyers.IAiModelConfigService;
import ai.lawyers.system.service.lawyers.stat.AiModelCallLogRecorder;

/**
 * QualityScoringEngine 单测：AI+规则双引擎融合、模板裁剪与加权、
 * 声学扣分/违规合并、AI 失败规则降级与脏 JSON 容错。
 *
 * @author ai-lawyers
 */
@ExtendWith(MockitoExtension.class)
class QualityScoringEngineTest
{
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Mock
    private IAiModelConfigService modelConfigService;

    @Mock
    private QualityRuleEngine qualityRuleEngine;

    private QualityTemplateSupport templateSupport;
    private QualityScoringEngine engine;

    @BeforeEach
    void setUp()
    {
        templateSupport = spy(new QualityTemplateSupport());
        engine = new QualityScoringEngine();
        ReflectionTestUtils.setField(engine, "modelConfigService", modelConfigService);
        ReflectionTestUtils.setField(engine, "qualityRuleEngine", qualityRuleEngine);
        ReflectionTestUtils.setField(engine, "qualityTemplateSupport", templateSupport);
    }

    @Test
    void fusesAiAndRuleByStricterValue() throws Exception
    {
        stubAi(aiJson());
        stubRule(85d, 95d, violation("违禁词", "含敏感表述"));

        QualityScoringEngine.AiScore score = engine.scoreByDualEngine("transcript", record(), null);

        assertThat(score.totalScore).isEqualByComparingTo("75.0");
        JsonNode dims = MAPPER.readTree(score.dimensionJson);
        assertThat(dims.path("serviceNorm").asDouble()).isEqualTo(90d);
        assertThat(dims.path("compliance").asDouble()).isEqualTo(60d);
        assertThat(dims.path("answerAccuracy").asDouble()).isEqualTo(80d);
        assertThat(dims.path("emotionAttitude").asDouble()).isEqualTo(70d);
        assertThat(score.remark).contains("AI+规则双引擎融合评分").contains("总体不错");
        assertThat(score.remark).doesNotContain("声学");
    }

    @Test
    void mergesAiAndRuleViolationsWithSource() throws Exception
    {
        stubAi(aiJson());
        stubRule(85d, 95d, violation("违禁词", "含敏感表述"));

        QualityScoringEngine.AiScore score = engine.scoreByDualEngine("transcript", record(), null);

        JsonNode violations = MAPPER.readTree(score.violationJson);
        assertThat(violations).hasSize(2);
        List<String> sources = new ArrayList<>();
        for (JsonNode item : violations)
        {
            sources.add(item.path("source").asText());
        }
        assertThat(sources).containsExactly("ai", "rule");
        assertThat(violations.get(0).path("keyword").asText()).isEqualTo("承诺胜诉");
    }

    @Test
    void templateTrimsDimensionsAndUsesWeightedTotal() throws Exception
    {
        doReturn(template(7L, null,
                dimension("serviceNorm", 2d), dimension("compliance", 1d)))
                .when(templateSupport).loadActive();
        stubAi(aiJson());
        stubRule(85d, 95d);

        QualityScoringEngine.AiScore score = engine.scoreByDualEngine("transcript", record(), null);

        JsonNode dims = MAPPER.readTree(score.dimensionJson);
        assertThat(dims.size()).isEqualTo(2);
        assertThat(dims.has("answerAccuracy")).isFalse();
        // (90*2 + 60*1) / 3 = 80.0
        assertThat(score.totalScore).isEqualByComparingTo("80.0");
        assertThat(score.remark).contains("模板 7");
    }

    @Test
    void templateCustomPromptUsedForAiCall() throws Exception
    {
        doReturn(template(8L, "自定义质检提示词", dimension("serviceNorm", 1d)))
                .when(templateSupport).loadActive();
        stubAi(aiJson());
        stubRule(100d, 100d);

        engine.scoreByDualEngine("transcript", record(), null);

        ArgumentCaptor<String> systemCaptor = ArgumentCaptor.forClass(String.class);
        verify(modelConfigService).chatJson(systemCaptor.capture(), anyString(),
                eq(AiModelCallLogRecorder.SCENE_QUALITY));
        assertThat(systemCaptor.getValue()).isEqualTo("自定义质检提示词");
    }

    @Test
    void acousticPenaltyReducesServiceNormAndMergesViolations() throws Exception
    {
        stubAi(aiJson());
        stubRule(85d, 95d);
        AcousticFeatureAnalyzer.AcousticResult acoustic = acoustic(15, 120d, 500L, 1000L,
                violation("静默过长", "存在长静默"));

        QualityScoringEngine.AiScore score = engine.scoreByDualEngine("transcript", record(), acoustic);

        JsonNode dims = MAPPER.readTree(score.dimensionJson);
        assertThat(dims.path("serviceNorm").asDouble()).isEqualTo(75d);
        // (75 + 80 + 70 + 60) / 4 = 71.25 -> 71.3
        assertThat(score.totalScore).isEqualByComparingTo("71.3");
        JsonNode violations = MAPPER.readTree(score.violationJson);
        assertThat(violations).hasSize(2);
        assertThat(violations.get(1).path("source").asText()).isEqualTo("acoustic");
        assertThat(score.remark).contains("声学：语速=120.0 字/分")
                .contains("最长静默=500ms").contains("有声=1000ms");
    }

    @Test
    void acousticPenaltyFloorsAtZero() throws Exception
    {
        stubAi(aiJson());
        stubRule(85d, 95d);
        AcousticFeatureAnalyzer.AcousticResult acoustic = acoustic(500, 100d, 0L, 0L);

        QualityScoringEngine.AiScore score = engine.scoreByDualEngine("transcript", record(), acoustic);

        assertThat(MAPPER.readTree(score.dimensionJson).path("serviceNorm").asDouble()).isZero();
    }

    @Test
    void aiFailureFallsBackToRuleOnly() throws Exception
    {
        when(modelConfigService.chatJson(anyString(), anyString(), eq(AiModelCallLogRecorder.SCENE_QUALITY)))
                .thenThrow(new RuntimeException("模型超时"));
        stubRule(85d, 95d, violation("违禁词", "含敏感表述"));

        QualityScoringEngine.AiScore score = engine.scoreByDualEngine("transcript", record(), null);

        assertThat(score.totalScore).isEqualByComparingTo("90.0");
        JsonNode dims = MAPPER.readTree(score.dimensionJson);
        assertThat(dims.path("serviceNorm").asDouble()).isEqualTo(95d);
        assertThat(dims.path("compliance").asDouble()).isEqualTo(85d);
        assertThat(dims.path("answerAccuracy").isNull()).isTrue();
        assertThat(dims.path("emotionAttitude").isNull()).isTrue();
        assertThat(score.remark).contains("规则引擎降级评分（原因：模型超时）");
        assertThat(MAPPER.readTree(score.violationJson)).hasSize(1);
    }

    @Test
    void aiFailureWithTemplateUsesWeightedTotal() throws Exception
    {
        doReturn(template(9L, null,
                dimension("serviceNorm", 2d), dimension("compliance", 1d)))
                .when(templateSupport).loadActive();
        when(modelConfigService.chatJson(anyString(), anyString(), eq(AiModelCallLogRecorder.SCENE_QUALITY)))
                .thenThrow(new RuntimeException("超时"));
        stubRule(85d, 95d);

        QualityScoringEngine.AiScore score = engine.scoreByDualEngine("transcript", record(), null);

        // (95*2 + 85*1) / 3 = 91.666... -> 91.7
        assertThat(score.totalScore).isEqualByComparingTo("91.7");
        assertThat(MAPPER.readTree(score.dimensionJson).size()).isEqualTo(2);
    }

    @Test
    void invalidAiJsonFallsBackToRuleOnly() throws Exception
    {
        when(modelConfigService.chatJson(anyString(), anyString(), eq(AiModelCallLogRecorder.SCENE_QUALITY)))
                .thenReturn("not-json");
        stubRule(100d, 100d);

        QualityScoringEngine.AiScore score = engine.scoreByDualEngine("transcript", record(), null);

        assertThat(score.remark).contains("规则引擎降级评分");
        assertThat(score.totalScore).isEqualByComparingTo("100.0");
    }

    @Test
    void codeFencedAiJsonCleaned() throws Exception
    {
        when(modelConfigService.chatJson(anyString(), anyString(), eq(AiModelCallLogRecorder.SCENE_QUALITY)))
                .thenReturn("```json\n" + aiJson() + "\n```");
        stubRule(85d, 95d);

        QualityScoringEngine.AiScore score = engine.scoreByDualEngine("transcript", record(), null);

        assertThat(score.totalScore).isEqualByComparingTo("75.0");
        assertThat(score.remark).contains("总体不错");
    }

    @Test
    void ruleOnlyKeepsRuleViolations() throws Exception
    {
        when(modelConfigService.chatJson(anyString(), anyString(), eq(AiModelCallLogRecorder.SCENE_QUALITY)))
                .thenThrow(new RuntimeException("模型超时"));
        stubRule(85d, 95d, violation("违禁词", "含敏感表述"));

        QualityScoringEngine.AiScore score = engine.scoreByDualEngine("transcript", record(), null);

        JsonNode violations = MAPPER.readTree(score.violationJson);
        assertThat(violations).hasSize(1);
        assertThat(violations.get(0).path("source").asText()).isEqualTo("rule");
        assertThat(violations.get(0).path("keyword").asText()).isEqualTo("违禁词");
    }

    @Test
    void ruleOnlyWithTemplateSerializesNullDimensions() throws Exception
    {
        doReturn(template(10L, null,
                dimension("serviceNorm", 1d), dimension("answerAccuracy", 1d)))
                .when(templateSupport).loadActive();
        when(modelConfigService.chatJson(anyString(), anyString(), eq(AiModelCallLogRecorder.SCENE_QUALITY)))
                .thenThrow(new RuntimeException("超时"));
        stubRule(85d, 95d);

        QualityScoringEngine.AiScore score = engine.scoreByDualEngine("transcript", record(), null);

        JsonNode dims = MAPPER.readTree(score.dimensionJson);
        assertThat(dims.path("serviceNorm").asDouble()).isEqualTo(95d);
        assertThat(dims.path("answerAccuracy").isNull()).isTrue();
        // 仅 serviceNorm 参与加权
        assertThat(score.totalScore).isEqualByComparingTo("95.0");
    }

    private void stubAi(String json) throws Exception
    {
        when(modelConfigService.chatJson(anyString(), anyString(),
                eq(AiModelCallLogRecorder.SCENE_QUALITY))).thenReturn(json);
    }

    private void stubRule(double compliance, double serviceNorm,
                          QualityRuleEngine.RuleViolation... violations)
    {
        when(qualityRuleEngine.evaluate("transcript")).thenReturn(
                new QualityRuleEngine.RuleResult(Collections.emptyList(), Arrays.asList(violations),
                        compliance, serviceNorm));
    }

    private QualityRuleEngine.RuleViolation violation(String keyword, String reason)
    {
        return new QualityRuleEngine.RuleViolation(keyword, reason);
    }

    private AcousticFeatureAnalyzer.AcousticResult acoustic(int penalty, double speechRate,
                                                            long maxSilenceMs, long voicedMs,
                                                            QualityRuleEngine.RuleViolation... violations)
    {
        return new AcousticFeatureAnalyzer.AcousticResult(Arrays.asList(violations), penalty,
                speechRate, maxSilenceMs, voicedMs, 10);
    }

    private QualityTemplateSupport.ActiveTemplate template(Long id, String prompt,
                                                           QualityTemplateSupport.Dimension... dims)
    {
        return new QualityTemplateSupport.ActiveTemplate(id, Arrays.asList(dims), prompt);
    }

    private QualityTemplateSupport.Dimension dimension(String key, double weight)
    {
        return new QualityTemplateSupport.Dimension(key, key, weight);
    }

    private AiCallRecord record()
    {
        AiCallRecord record = new AiCallRecord();
        record.setRecordId(100L);
        record.setContent("咨询内容");
        return record;
    }

    private String aiJson()
    {
        return "{\"totalScore\":80,\"dimensions\":{\"serviceNorm\":90,\"answerAccuracy\":80,"
                + "\"emotionAttitude\":70,\"compliance\":60},"
                + "\"violations\":[{\"keyword\":\"承诺胜诉\",\"reason\":\"不当承诺\"}],"
                + "\"overall\":\"总体不错\"}";
    }
}
