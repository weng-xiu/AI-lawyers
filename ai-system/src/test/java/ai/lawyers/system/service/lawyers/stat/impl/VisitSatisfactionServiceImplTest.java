package ai.lawyers.system.service.lawyers.stat.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.test.util.ReflectionTestUtils;
import ai.lawyers.system.mapper.lawyers.stat.VisitSatisfactionMapper;

/**
 * F5 回访满意度看板 Service 单测。
 *
 * <p>覆盖空数据兜底、负向文本多因归因与「其他」归类、电话/图文派生满意率、
 * 情绪极性合并与中性钳制、逾期三级分级，以及趋势/榜单透传。</p>
 *
 * @author ai-lawyers
 */
class VisitSatisfactionServiceImplTest
{
    private VisitSatisfactionServiceImpl service;

    @Mock private VisitSatisfactionMapper mapper;

    @BeforeEach
    void setUp()
    {
        MockitoAnnotations.openMocks(this);
        service = new VisitSatisfactionServiceImpl();
        ReflectionTestUtils.setField(service, "mapper", mapper);
    }

    @Test
    @SuppressWarnings("unchecked")
    void getBoard_allNull_returnsZeroStructure()
    {
        Map<String, Object> data = service.getBoard(begin(), end());

        assertEquals(0.0, ((Map<String, Object>) data.get("callbackOverview")).get("satisfiedRate"));
        assertEquals(0.0, ((Map<String, Object>) data.get("callbackOverview")).get("unsatisfiedRate"));
        assertEquals(0.0, ((Map<String, Object>) data.get("evaluation")).get("positiveRate"));
        assertTrue(((List<?>) data.get("callbackTrend")).isEmpty());
        assertTrue(((List<?>) data.get("visitorRanking")).isEmpty());
        assertTrue(((List<?>) data.get("reasonTop")).isEmpty());
        assertTrue(((List<?>) data.get("overdueTasks")).isEmpty());
        Map<String, Object> sentiment = (Map<String, Object>) data.get("sentiment");
        assertEquals(0L, sentiment.get("total"));
        assertEquals(0.0, sentiment.get("positiveRate"));
        Map<String, Object> overdue = (Map<String, Object>) data.get("overdueSummary");
        assertEquals(0, overdue.get("windowTotal"));
        assertEquals(1440L, overdue.get("l2ThresholdMinutes"));
        assertEquals(4320L, overdue.get("l3ThresholdMinutes"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void getBoard_classifiesReasonsAcrossCallbackAndEvaluation()
    {
        when(mapper.selectNegativeCallbackOpinions(any(), any(), anyInt())).thenReturn(Arrays.asList(
                row("opinion", "一直没人接，等了半天"),
                row("opinion", "态度很差，还踢皮球"),
                row("opinion", "系统打不开"),
                row("opinion", "说不上来")));
        when(mapper.selectNegativeEvaluationFeedback(any(), any(), anyInt())).thenReturn(
                Collections.singletonList(row("feedback", "流程太麻烦")));

        Map<String, Object> data = service.getBoard(begin(), end());

        List<Map<String, Object>> callbackReasons = (List<Map<String, Object>>) data.get("callbackReasons");
        assertEquals(5, callbackReasons.size());
        Map<String, Long> cbCounts = toCountMap(callbackReasons);
        assertEquals(1L, cbCounts.get("等待久/响应慢"));
        assertEquals(1L, cbCounts.get("解答未解决问题"));
        assertEquals(1L, cbCounts.get("服务态度问题"));
        assertEquals(1L, cbCounts.get("渠道/系统问题"));
        assertEquals(1L, cbCounts.get("其他"));

        List<Map<String, Object>> evaluationReasons = (List<Map<String, Object>>) data.get("evaluationReasons");
        assertEquals(1, evaluationReasons.size());
        assertEquals("流程繁琐/多次转接", evaluationReasons.get(0).get("reason"));

        List<Map<String, Object>> top = (List<Map<String, Object>>) data.get("reasonTop");
        assertEquals(6, top.size());
        assertEquals(1L, top.get(0).get("count"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void getBoard_reasonTextIsCaseInsensitive_andNullSkipped()
    {
        Map<String, Object> nullText = new HashMap<>();
        nullText.put("opinion", null);
        when(mapper.selectNegativeCallbackOpinions(any(), any(), anyInt())).thenReturn(Arrays.asList(
                row("opinion", "APP无法登录"), nullText));

        Map<String, Object> data = service.getBoard(begin(), end());

        Map<String, Long> counts = toCountMap((List<Map<String, Object>>) data.get("callbackReasons"));
        assertEquals(1L, counts.get("渠道/系统问题"));
        assertFalse(counts.containsKey("其他"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void getBoard_computesCallbackAndEvaluationRates()
    {
        Map<String, Object> callback = new HashMap<>();
        callback.put("ratedCount", 10);
        callback.put("verySatisfiedCount", 6);
        callback.put("satisfiedCount", 2);
        callback.put("unsatisfiedCount", 2);
        callback.put("normalCount", 1);
        when(mapper.selectCallbackOverview(any(), any())).thenReturn(callback);

        Map<String, Object> evaluation = new HashMap<>();
        evaluation.put("evalCount", 8);
        evaluation.put("positiveCount", 6);
        evaluation.put("negativeCount", 2);
        when(mapper.selectEvaluationOverview(any(), any())).thenReturn(evaluation);

        Map<String, Object> data = service.getBoard(begin(), end());

        Map<String, Object> cb = (Map<String, Object>) data.get("callbackOverview");
        assertEquals(80.0, cb.get("satisfiedRate"));
        assertEquals(20.0, cb.get("unsatisfiedRate"));
        Map<String, Object> ev = (Map<String, Object>) data.get("evaluation");
        assertEquals(75.0, ev.get("positiveRate"));
        assertEquals(25.0, ev.get("negativeRate"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void getBoard_mergesSentiment()
    {
        Map<String, Object> callback = new HashMap<>();
        callback.put("verySatisfiedCount", 3);
        callback.put("satisfiedCount", 2);
        callback.put("normalCount", 1);
        callback.put("unsatisfiedCount", 4);
        when(mapper.selectCallbackOverview(any(), any())).thenReturn(callback);
        Map<String, Object> evaluation = new HashMap<>();
        evaluation.put("evalCount", 10);
        evaluation.put("positiveCount", 6);
        evaluation.put("negativeCount", 2);
        when(mapper.selectEvaluationOverview(any(), any())).thenReturn(evaluation);

        Map<String, Object> data = service.getBoard(begin(), end());

        Map<String, Object> sentiment = (Map<String, Object>) data.get("sentiment");
        assertEquals(11L, sentiment.get("positive"));
        assertEquals(3L, sentiment.get("neutral"));
        assertEquals(6L, sentiment.get("negative"));
        assertEquals(20L, sentiment.get("total"));
        assertEquals(55.0, sentiment.get("positiveRate"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void getBoard_negativeEvaluationNeutralIsClampedToZero()
    {
        Map<String, Object> evaluation = new HashMap<>();
        evaluation.put("evalCount", 5);
        evaluation.put("positiveCount", 4);
        evaluation.put("negativeCount", 2);
        when(mapper.selectEvaluationOverview(any(), any())).thenReturn(evaluation);

        Map<String, Object> data = service.getBoard(begin(), end());

        Map<String, Object> sentiment = (Map<String, Object>) data.get("sentiment");
        assertEquals(0L, sentiment.get("neutral"));
        assertEquals(6L, sentiment.get("total"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void getBoard_gradesOverdueTasks()
    {
        when(mapper.selectOverdueTasks(any(), anyInt())).thenReturn(Arrays.asList(
                overdue(30), overdue(1439), overdue(1440), overdue(4319), overdue(4320)));

        Map<String, Object> data = service.getBoard(begin(), end());

        Map<String, Object> summary = (Map<String, Object>) data.get("overdueSummary");
        assertEquals(2L, summary.get("level1Count"));
        assertEquals(2L, summary.get("level2Count"));
        assertEquals(1L, summary.get("level3Count"));
        assertEquals(5, summary.get("windowTotal"));
        List<Map<String, Object>> tasks = (List<Map<String, Object>>) data.get("overdueTasks");
        assertEquals(1, tasks.get(0).get("level"));
        assertEquals(2, tasks.get(2).get("level"));
        assertEquals(3, tasks.get(4).get("level"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void getBoard_parsesStringMinutesAndSkipsInvalid()
    {
        Map<String, Object> numericString = new HashMap<>();
        numericString.put("overdueMinutes", "4320");
        Map<String, Object> invalid = new HashMap<>();
        invalid.put("overdueMinutes", "abc");
        when(mapper.selectOverdueTasks(any(), anyInt())).thenReturn(Arrays.asList(numericString, invalid));

        Map<String, Object> data = service.getBoard(begin(), end());

        List<Map<String, Object>> tasks = (List<Map<String, Object>>) data.get("overdueTasks");
        assertEquals(3, tasks.get(0).get("level"));
        assertEquals(1, tasks.get(1).get("level"));
    }

    @Test
    void getBoard_passesThroughTrendAndRanking()
    {
        List<Map<String, Object>> trend = Collections.singletonList(new HashMap<>());
        List<Map<String, Object>> ranking = Collections.singletonList(new HashMap<>());
        when(mapper.selectCallbackTrend(any(), any())).thenReturn(trend);
        when(mapper.selectByVisitor(any(), any())).thenReturn(ranking);

        Map<String, Object> data = service.getBoard(begin(), end());

        assertSame(trend, data.get("callbackTrend"));
        assertSame(ranking, data.get("visitorRanking"));
        verify(mapper).selectCallbackTrend(any(), any());
    }

    @Test
    @SuppressWarnings("unchecked")
    void getBoard_defaultsTaskOverviewToEmptyMap()
    {
        Map<String, Object> data = service.getBoard(begin(), end());

        Map<String, Object> taskOverview = (Map<String, Object>) data.get("taskOverview");
        assertNotNull(taskOverview);
        assertTrue(taskOverview.isEmpty());
    }

    // ---------------- 辅助 ----------------

    private Date begin()
    {
        return new Date(System.currentTimeMillis() - 86400_000L);
    }

    private Date end()
    {
        return new Date();
    }

    private Map<String, Object> row(String key, String text)
    {
        Map<String, Object> row = new HashMap<>();
        row.put(key, text);
        return row;
    }

    private Map<String, Object> overdue(long minutes)
    {
        Map<String, Object> row = new HashMap<>();
        row.put("overdueMinutes", minutes);
        return row;
    }

    private Map<String, Long> toCountMap(List<Map<String, Object>> reasons)
    {
        Map<String, Long> counts = new HashMap<>();
        for (Map<String, Object> item : reasons)
        {
            counts.put((String) item.get("reason"), (Long) item.get("count"));
        }
        return counts;
    }
}
