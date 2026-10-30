package ai.lawyers.system.service.lawyers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.test.util.ReflectionTestUtils;
import ai.lawyers.system.domain.lawyers.KnowledgeQueryOverview;
import ai.lawyers.system.mapper.lawyers.AiKnowledgeQueryLogMapper;
import ai.lawyers.system.service.lawyers.rag.KnowledgeQueryLogRecorder;
import ai.lawyers.system.service.lawyers.rag.KnowledgeQueryTextUtils;

/**
 * F8 知识运营分析单测：命中率计算、区间默认/上限、表缺失降级、转草稿标记。
 *
 * @author ai-lawyers
 */
class KnowledgeOpsServiceTest
{
    @Mock
    private AiKnowledgeQueryLogMapper queryLogMapper;

    @Mock
    private KnowledgeDraftService knowledgeDraftService;

    @Mock
    private KnowledgeQueryLogRecorder queryLogRecorder;

    private KnowledgeOpsService service;

    @BeforeEach
    void setUp()
    {
        MockitoAnnotations.openMocks(this);
        service = new KnowledgeOpsService();
        ReflectionTestUtils.setField(service, "queryLogMapper", queryLogMapper);
        ReflectionTestUtils.setField(service, "knowledgeDraftService", knowledgeDraftService);
        ReflectionTestUtils.setField(service, "queryLogRecorder", queryLogRecorder);
    }

    @Test
    void overviewComputesHitRateAndAverageLatency()
    {
        KnowledgeQueryOverview row = new KnowledgeQueryOverview();
        row.setTotalCount(10L);
        row.setHitCount(6L);
        row.setMissCount(4L);
        row.setPendingCount(3L);
        row.setAvgLatencyMs(120.5d);
        when(queryLogMapper.selectQueryOverview(any(), any())).thenReturn(row);

        Map<String, Object> out = service.overview(null, null);

        assertEquals(true, out.get("available"));
        assertEquals(10L, out.get("total"));
        assertEquals(6L, out.get("hitCount"));
        assertEquals(4L, out.get("missCount"));
        assertEquals(3L, out.get("pendingCount"));
        assertEquals(60.0d, (Double) out.get("hitRate"), 0.001d);
        assertEquals(121L, out.get("avgLatencyMs"));
    }

    @Test
    void overviewDegradesWhenQueryLogTableIsMissing()
    {
        when(queryLogMapper.selectQueryOverview(any(), any()))
                .thenThrow(new IllegalStateException("Table 'ai_knowledge_query_log' doesn't exist"));

        Map<String, Object> out = service.overview(null, null);

        assertEquals(false, out.get("available"));
        assertEquals(0L, out.get("total"));
        assertEquals(0d, (Double) out.get("hitRate"), 0.001d);
        assertEquals(0L, out.get("avgLatencyMs"));
    }

    @Test
    void unmatchedListDegradesToEmptyWhenReadFails()
    {
        when(queryLogMapper.selectUnmatchedQuestions(any(), any()))
                .thenThrow(new IllegalStateException("table missing"));
        assertTrue(service.listUnmatchedQuestions(null, null).isEmpty());
    }

    @Test
    void defaultRangeIsSevenDaysAndLongRangeIsClampedToNinety()
    {
        KnowledgeOpsService.DateRange defaults = service.resolveRange(null, null);
        assertEquals(7L, Duration.between(defaults.begin.toInstant(), defaults.end.toInstant()).toDays());

        KnowledgeOpsService.DateRange clamped =
                service.resolveRange("2020-01-01 00:00:00", "2026-10-30 00:00:00");
        assertEquals(90L, Duration.between(clamped.begin.toInstant(), clamped.end.toInstant()).toDays());
    }

    @Test
    void reversedRangeIsSwapped()
    {
        KnowledgeOpsService.DateRange reversed =
                service.resolveRange("2026-10-30 00:00:00", "2026-10-01 00:00:00");
        assertTrue(!reversed.begin.after(reversed.end));
    }

    @Test
    void topKnowledgeLimitIsClamped()
    {
        when(queryLogMapper.selectTopKnowledge(any(), any(), eq(10))).thenReturn(new ArrayList<>());

        service.topKnowledge(null, null, 999);

        verify(queryLogMapper).selectTopKnowledge(any(), any(), eq(10));
    }

    @Test
    void draftFromQuestionMarksHashConverted()
    {
        when(knowledgeDraftService.draftFromQuery("劳动合同怎么签", "hash-1")).thenReturn(9L);

        assertEquals(9L, service.draftFromQuestion("劳动合同怎么签", "hash-1"));
        verify(queryLogRecorder).markConverted("hash-1");
    }

    @Test
    void draftFromQuestionFallsBackToComputedHash()
    {
        String question = "劳动 合同？怎么办";
        String hash = KnowledgeQueryTextUtils.hash(question);
        when(knowledgeDraftService.draftFromQuery(question, hash)).thenReturn(8L);

        assertEquals(8L, service.draftFromQuestion(question, null));
        verify(queryLogRecorder).markConverted(hash);
    }
}
