package ai.lawyers.system.service.impl.lawyers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.times;
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
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;
import com.fasterxml.jackson.databind.ObjectMapper;
import ai.lawyers.common.exception.ServiceException;
import ai.lawyers.system.domain.lawyers.stat.AiSatisfactionForecast;

/**
 * P1-11：预测性满意度 Service 单测。
 *
 * <p>覆盖时间区间校验、聚合口径（预测分布/均值/实际对照一致率与 ±1 容差）、
 * 行映射（质检调整分优先、dimension_json 容错、实际满意度非法值）、分页夹取。</p>
 *
 * @author ai-lawyers
 */
class AiSatisfactionForecastServiceImplTest
{
    private static final long DAY = 86400_000L;

    private AiSatisfactionForecastServiceImpl service;

    @Mock private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp()
    {
        MockitoAnnotations.openMocks(this);
        service = new AiSatisfactionForecastServiceImpl();
        ReflectionTestUtils.setField(service, "jdbcTemplate", jdbcTemplate);
        ReflectionTestUtils.setField(service, "objectMapper", new ObjectMapper());
    }

    // ---------------- 区间校验 ----------------

    @Test
    void summary_nullRange_throws()
    {
        assertThrows(ServiceException.class, () -> service.summary(null, new Date()));
        assertThrows(ServiceException.class, () -> service.summary(new Date(), null));
    }

    @Test
    void summary_endNotAfterBegin_throws()
    {
        Date begin = new Date();
        assertThrows(ServiceException.class, () -> service.summary(begin, begin));
        assertThrows(ServiceException.class, () -> service.summary(begin, new Date(begin.getTime() - 1)));
    }

    @Test
    void summary_spanOver31Days_throws()
    {
        Date begin = new Date(0);
        Date end = new Date(32 * DAY);
        assertThrows(ServiceException.class, () -> service.summary(begin, end));
    }

    @Test
    void list_invalidRange_throws()
    {
        Date begin = new Date();
        assertThrows(ServiceException.class, () -> service.list(begin, begin, 1, 10));
    }

    // ---------------- 聚合 ----------------

    @Test
    void summary_nullRows_returnsZeroMetrics()
    {
        when(jdbcTemplate.queryForList(anyString(), any(Object[].class))).thenReturn(null);

        Map<String, Object> result = service.summary(dayBegin(), dayEnd());

        assertEquals(0, result.get("total"));
        assertEquals(0.0, result.get("avgPredicted"));
        assertEquals(0, result.get("actualCount"));
        assertEquals(0.0, result.get("avgActual"));
        assertEquals(0.0, result.get("agreementRate"));
        assertEquals(0.0, result.get("agreementWithinOne"));
        @SuppressWarnings("unchecked")
        Map<String, Object> dist = (Map<String, Object>) result.get("predictedDist");
        assertEquals(0, dist.get("1"));
        assertEquals(0, dist.get("2"));
        assertEquals(0, dist.get("3"));
        assertEquals(0, dist.get("4"));
    }

    @Test
    void summary_aggregatesPredictionAndActualAgreement()
    {
        Map<String, Object> healthy = row(1L, 120, "1", null, 100, null, null, "1");
        // 负面情绪(0.8)+质检情绪低(0.8)+超短(0.4)+未接(1.0)=4.0 → 预测 4
        Map<String, Object> bad = row(2L, 10, "3", "我要投诉", 100, null,
                "{\"emotionAttitude\":50}", "2");
        // 申诉成立取调整分 50（若误用总分 90 则预测 1）→ 预测 2
        Map<String, Object> adjusted = row(3L, null, "1", null, 90, 50, null, null);
        when(jdbcTemplate.queryForList(anyString(), any(Object[].class)))
                .thenReturn(Arrays.asList(healthy, bad, adjusted));

        Map<String, Object> result = service.summary(dayBegin(), dayEnd());

        assertEquals(3, result.get("total"));
        assertEquals(2.33, result.get("avgPredicted"));
        assertEquals(2, result.get("actualCount"));
        assertEquals(1.5, result.get("avgActual"));
        assertEquals(50.0, result.get("agreementRate"));
        assertEquals(50.0, result.get("agreementWithinOne"));
        @SuppressWarnings("unchecked")
        Map<String, Object> dist = (Map<String, Object>) result.get("predictedDist");
        assertEquals(1, dist.get("1"));
        assertEquals(1, dist.get("2"));
        assertEquals(0, dist.get("3"));
        assertEquals(1, dist.get("4"));
    }

    // ---------------- 明细 ----------------

    @Test
    void list_mapsRowFieldsAndParsesDimensionJson()
    {
        Map<String, Object> full = row(11L, 20, "2", "我要投诉", 70, null,
                "{\"emotionAttitude\":72.5}", " 3 ");
        Map<String, Object> weird = new HashMap<>();
        weird.put("recordId", 12L);
        weird.put("callTime", "not-a-date");
        weird.put("duration", "x");
        weird.put("status", null);
        weird.put("transcript", null);
        weird.put("totalScore", null);
        weird.put("adjustedScore", null);
        weird.put("dimensionJson", "{bad json");
        weird.put("actual", "5");
        when(jdbcTemplate.queryForList(anyString(), any(Object[].class)))
                .thenReturn(Arrays.asList(full, weird));

        List<AiSatisfactionForecast> list = service.list(dayBegin(), dayEnd(), 1, 10);

        assertEquals(2, list.size());
        AiSatisfactionForecast item = list.get(0);
        assertEquals(11L, item.getRecordId());
        assertNotNull(item.getCallTime());
        assertEquals(20, item.getDuration());
        assertEquals("2", item.getStatus());
        assertEquals("negative", item.getEmotion());
        assertEquals(70.0, item.getQcTotal());
        assertEquals(72.5, item.getQcEmotion());
        assertEquals(3, item.getPredicted());
        assertEquals("负面情绪+质检情绪低(72)+质检总分低(70)+超短通话+转接", item.getScoreDetail());
        assertEquals(3, item.getActual());

        AiSatisfactionForecast fallback = list.get(1);
        assertEquals(12L, fallback.getRecordId());
        assertNull(fallback.getCallTime());
        assertNull(fallback.getDuration());
        assertNull(fallback.getStatus());
        assertEquals("neutral", fallback.getEmotion());
        assertNull(fallback.getQcTotal());
        assertNull(fallback.getQcEmotion());
        assertEquals(1, fallback.getPredicted());
        assertEquals("无显著负向特征", fallback.getScoreDetail());
        assertNull(fallback.getActual());
    }

    @Test
    void list_clampsPageAndSize()
    {
        when(jdbcTemplate.queryForList(anyString(), any(Object[].class)))
                .thenReturn(Collections.emptyList());

        service.list(dayBegin(), dayEnd(), 0, 1000);
        service.list(dayBegin(), dayEnd(), 3, 0);

        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        verify(jdbcTemplate, times(2)).queryForList(sqlCaptor.capture(), any(Object[].class));
        assertTrue(sqlCaptor.getAllValues().get(0).endsWith("limit 0, 200"));
        assertTrue(sqlCaptor.getAllValues().get(1).endsWith("limit 2, 1"));
    }

    // ---------------- 辅助 ----------------

    private Date dayBegin()
    {
        return new Date(System.currentTimeMillis() - DAY);
    }

    private Date dayEnd()
    {
        return new Date();
    }

    private Map<String, Object> row(Long recordId, Integer duration, String status, String transcript,
                                    Integer totalScore, Integer adjustedScore, String dimensionJson,
                                    String actual)
    {
        Map<String, Object> row = new HashMap<>();
        row.put("recordId", recordId);
        row.put("callTime", new Date());
        row.put("duration", duration);
        row.put("status", status);
        row.put("transcript", transcript);
        row.put("totalScore", totalScore);
        row.put("adjustedScore", adjustedScore);
        row.put("dimensionJson", dimensionJson);
        row.put("actual", actual);
        return row;
    }
}
