package ai.lawyers.system.service.lawyers.voice;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.test.util.ReflectionTestUtils;
import ai.lawyers.system.service.lawyers.IAiModelConfigService;
import ai.lawyers.system.service.lawyers.stat.AiModelCallLogRecorder;

/**
 * P1-8：{@link SimulationScorer} 单测——关键词字面命中、embedding 语义相似度、
 * OR(max)/AND(min) 聚合、阈值判定，以及服务未装配/异常时降级回纯关键词（不外抛）。
 */
class SimulationScorerTest
{
    @Mock
    private IAiModelConfigService modelConfigService;

    private SimulationScorer scorer;

    @BeforeEach
    void setUp()
    {
        MockitoAnnotations.openMocks(this);
        scorer = new SimulationScorer();
        ReflectionTestUtils.setField(scorer, "semanticEnabled", true);
        ReflectionTestUtils.setField(scorer, "semanticThreshold", 0.75d);
        ReflectionTestUtils.setField(scorer, "modelConfigService", modelConfigService);
    }

    @Test
    void literalHit_or_returnsFullScoreWithoutEmbedding()
    {
        SimulationScorer.Score s = scorer.scoreAny("关于拖欠工资的答复", Arrays.asList("工资"));

        assertEquals(1.0d, s.getSimilarity(), 0.0001);
        assertEquals("keyword", s.getMode());
        assertEquals(Arrays.asList("工资"), s.getMatchedKeywords());
        assertTrue(scorer.passed(s));
        verify(modelConfigService, never()).embedTexts(anyList(),
                org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void literalMiss_semanticDisabled_returnsZeroKeyword()
    {
        ReflectionTestUtils.setField(scorer, "semanticEnabled", false);

        SimulationScorer.Score s = scorer.scoreAny("我要讨个说法", Arrays.asList("工资"));

        assertEquals(0d, s.getSimilarity(), 0.0001);
        assertEquals("keyword", s.getMode());
        assertFalse(scorer.passed(s));
        verify(modelConfigService, never()).embedTexts(anyList(),
                org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void literalMiss_serviceMissing_degradesKeyword()
    {
        ReflectionTestUtils.setField(scorer, "modelConfigService", null);

        SimulationScorer.Score s = scorer.scoreAny("我要讨个说法", Arrays.asList("工资"));

        assertEquals(0d, s.getSimilarity(), 0.0001);
        assertEquals("keyword", s.getMode());
    }

    @Test
    void semantic_or_highSimilarity_passes()
    {
        // answer 与“劳动报酬”方向一致（余弦 1），与“工资”正交（余弦 0）→ OR 取 max=1
        stubEmbedding(vectors(new float[] { 1, 0 }, new float[] { 1, 0 }, new float[] { 0, 1 }));

        SimulationScorer.Score s = scorer.scoreAny("我要讨个说法", Arrays.asList("劳动报酬", "工资"));

        assertEquals("semantic", s.getMode());
        assertEquals(1.0d, s.getSimilarity(), 0.0001);
        assertTrue(s.getMatchedKeywords().contains("劳动报酬"));
        assertTrue(scorer.passed(s));
    }

    @Test
    void semantic_and_lowSimilarity_fails()
    {
        // answer 字面含“工资”(1.0)，“劳动报酬”正交(0) → AND 取 min=0
        stubEmbedding(vectors(new float[] { 1, 0 }, new float[] { 0, 1 }));

        SimulationScorer.Score s = scorer.scoreAll("关于工资的说法", Arrays.asList("工资", "劳动报酬"));

        assertEquals("semantic", s.getMode());
        assertEquals(0d, s.getSimilarity(), 0.0001);
        assertFalse(scorer.passed(s));
    }

    @Test
    void semantic_and_bothHigh_passes()
    {
        // answer 字面含“工资”，“劳动报酬”语义同向 → min=1
        stubEmbedding(vectors(new float[] { 1, 0 }, new float[] { 1, 0 }));

        SimulationScorer.Score s = scorer.scoreAll("关于工资的说法", Arrays.asList("工资", "劳动报酬"));

        assertEquals(1.0d, s.getSimilarity(), 0.0001);
        assertTrue(scorer.passed(s));
    }

    @Test
    void embeddingThrows_degradesKeywordNoThrow()
    {
        when(modelConfigService.embedTexts(anyList(),
                org.mockito.ArgumentMatchers.eq(AiModelCallLogRecorder.SCENE_TEST)))
                .thenThrow(new RuntimeException("embed down"));

        SimulationScorer.Score s = scorer.scoreAny("我要讨个说法", Arrays.asList("工资"));

        assertEquals(0d, s.getSimilarity(), 0.0001);
        assertEquals("keyword", s.getMode());
        assertFalse(scorer.passed(s));
    }

    @Test
    void embeddingShortResult_degradesKeyword()
    {
        when(modelConfigService.embedTexts(anyList(),
                org.mockito.ArgumentMatchers.eq(AiModelCallLogRecorder.SCENE_TEST)))
                .thenReturn(Collections.singletonList(new float[] { 1, 0 }));

        SimulationScorer.Score s = scorer.scoreAny("我要讨个说法", Arrays.asList("工资"));

        assertEquals("keyword", s.getMode());
        assertEquals(0d, s.getSimilarity(), 0.0001);
    }

    @Test
    void emptyAnswerOrKeywords_returnsZero()
    {
        SimulationScorer.Score a = scorer.scoreAny("", Arrays.asList("工资"));
        SimulationScorer.Score b = scorer.scoreAny("一些答复", Collections.emptyList());

        assertEquals(0d, a.getSimilarity(), 0.0001);
        assertEquals(0d, b.getSimilarity(), 0.0001);
        assertFalse(scorer.passed(null));
    }

    private void stubEmbedding(List<float[]> vectors)
    {
        when(modelConfigService.embedTexts(anyList(),
                org.mockito.ArgumentMatchers.eq(AiModelCallLogRecorder.SCENE_TEST)))
                .thenReturn(vectors);
    }

    private List<float[]> vectors(float[]... v)
    {
        return Arrays.asList(v);
    }
}
