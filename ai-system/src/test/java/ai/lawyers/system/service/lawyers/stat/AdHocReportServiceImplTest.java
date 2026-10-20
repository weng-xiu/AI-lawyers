package ai.lawyers.system.service.lawyers.stat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.test.util.ReflectionTestUtils;
import ai.lawyers.common.exception.ServiceException;
import ai.lawyers.system.domain.lawyers.stat.AdHocStatQuery;
import ai.lawyers.system.mapper.lawyers.stat.AdHocStatMapper;
import ai.lawyers.system.mapper.lawyers.stat.AiStatMinuteMapper;
import ai.lawyers.system.service.impl.lawyers.stat.AdHocReportServiceImpl;

/**
 * {@link AdHocReportServiceImpl} 单元测试（F6 报表自助分析）：
 * 白名单校验、预聚合/实时双路径行映射、维度标签解析、结果上限截断。
 */
class AdHocReportServiceImplTest
{
    @Mock
    private AiStatMinuteMapper aiStatMinuteMapper;

    @Mock
    private AdHocStatMapper adHocStatMapper;

    private AdHocReportServiceImpl service;

    @BeforeEach
    void setUp()
    {
        MockitoAnnotations.openMocks(this);
        service = new AdHocReportServiceImpl();
        ReflectionTestUtils.setField(service, "aiStatMinuteMapper", aiStatMinuteMapper);
        ReflectionTestUtils.setField(service, "adHocStatMapper", adHocStatMapper);
        ReflectionTestUtils.setField(service, "maxRows", 3);
    }

    // ---------------- 目录 ----------------

    @Test
    void catalogShouldExpose30AllMetricsAnd5Dimensions()
    {
        Map<String, Object> catalog = service.catalog();

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> metrics = (List<Map<String, Object>>) catalog.get("metrics");
        assertEquals(34, metrics.size(), "30 全局指标 + 4 维度专属指标");

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> dimensions = (List<Map<String, Object>>) catalog.get("dimensions");
        assertEquals(5, dimensions.size());

        @SuppressWarnings("unchecked")
        List<String> crossMetrics = (List<String>) catalog.get("crossMetrics");
        assertEquals(5, crossMetrics.size());
        assertTrue(crossMetrics.contains("call_total"));
    }

    // ---------------- 白名单与参数校验 ----------------

    private AdHocStatQuery query(String gran, List<String> metrics, List<String> dims)
    {
        AdHocStatQuery q = new AdHocStatQuery();
        q.setBeginTime("2026-10-01 00:00:00");
        q.setEndTime("2026-10-02 00:00:00");
        q.setGranularity(gran);
        q.setMetrics(metrics);
        q.setDimensions(dims);
        return q;
    }

    @Test
    void queryShouldRejectUnknownMetric()
    {
        ServiceException ex = assertThrows(ServiceException.class,
                () -> service.query(query("day", Arrays.asList("call_total", "evil_key"),
                        new ArrayList<>())));
        assertTrue(ex.getMessage().contains("未知指标"));
    }

    private ServiceException serviceQuery(String gran, List<String> metrics, List<String> dims)
    {
        return assertThrows(ServiceException.class, () -> service.query(query(gran, metrics, dims)));
    }

    @Test
    void queryShouldRejectUnknownDimensionKey()
    {
        ServiceException ex = serviceQuery("day", Arrays.asList("call_total"), Arrays.asList("hacker"));
        assertTrue(ex.getMessage().contains("未知维度"));
    }

    @Test
    void queryShouldRejectLineInCrossDim()
    {
        ServiceException ex = serviceQuery("day", Arrays.asList("call_total"),
                Arrays.asList("agent", "line"));
        assertTrue(ex.getMessage().contains("不支持跨维度"));
    }

    @Test
    void queryShouldRejectOversizedMinuteRange()
    {
        AdHocStatQuery q = query("minute", Arrays.asList("call_total"), new ArrayList<>());
        q.setEndTime("2026-10-15 00:00:00");
        ServiceException ex = assertThrows(ServiceException.class, () -> service.query(q));
        assertTrue(ex.getMessage().contains("统计区间过大"));
    }

    @Test
    void queryShouldRejectNonMonotonicRange()
    {
        AdHocStatQuery q = query("day", Arrays.asList("call_total"), new ArrayList<>());
        q.setBeginTime("2026-10-02 00:00:00");
        q.setEndTime("2026-10-01 00:00:00");
        ServiceException ex = assertThrows(ServiceException.class, () -> service.query(q));
        assertTrue(ex.getMessage().contains("开始时间必须早于结束时间"));
    }

    // ---------------- 预聚合路径 ----------------

    @Test
    void queryAllPathShouldMapAggRowsAndPassNullPrefix()
    {
        List<Map<String, Object>> raw = new ArrayList<>();
        Map<String, Object> r1 = new HashMap<>();
        r1.put("period", "2026-10-01");
        r1.put("dim_value", "ALL");
        r1.put("m0", new BigDecimal("10"));
        r1.put("m1", new BigDecimal("8"));
        raw.add(r1);
        when(adHocStatMapper.selectAdHocAgg(anyString(), anyString(), anyString(), anyList(),
                isNull(), anyInt())).thenReturn(raw);

        Map<String, Object> result = service.query(query("day",
                Arrays.asList("call_total", "call_answered"), new ArrayList<>()));

        assertEquals("preagg", result.get("source"));
        assertFalse((Boolean) result.get("capped"));

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> rows = (List<Map<String, Object>>) result.get("rows");
        assertEquals(1, rows.size());
        assertEquals("2026-10-01", rows.get(0).get("period"));
        assertEquals(new BigDecimal("10"), rows.get(0).get("call_total"));
        assertEquals(new BigDecimal("8"), rows.get(0).get("call_answered"));

        @SuppressWarnings("unchecked")
        Map<String, Object> drill = (Map<String, Object>) rows.get(0).get("_drill");
        assertEquals("2026-10-01 00:00:00", drill.get("begin"));
        assertEquals("2026-10-02 00:00:00", drill.get("end"));

        verify(adHocStatMapper).selectAdHocAgg(eq("2026-10-01 00:00:00"), eq("2026-10-02 00:00:00"),
                eq("%Y-%m-%d"), anyList(), isNull(), eq(4));
    }

    @Test
    void queryAgentDimShouldResolveLabelsAndPrefix()
    {
        List<Map<String, Object>> raw = new ArrayList<>();
        Map<String, Object> r1 = new HashMap<>();
        r1.put("period", "2026-10-01 10:00");
        r1.put("dim_value", "5");
        r1.put("m0", new BigDecimal("6"));
        raw.add(r1);
        when(adHocStatMapper.selectAdHocAgg(anyString(), anyString(), anyString(), anyList(),
                anyString(), anyInt())).thenReturn(raw);

        List<Map<String, Object>> agentNames = new ArrayList<>();
        Map<String, Object> n1 = new HashMap<>();
        n1.put("agent_id", 5L);
        n1.put("agent_name", "张三");
        agentNames.add(n1);
        when(adHocStatMapper.selectAgentNames(anyList())).thenReturn(agentNames);

        Map<String, Object> result = service.query(query("hour",
                Arrays.asList("call_total"), Arrays.asList("agent")));

        @SuppressWarnings("unchecked")
        Map<String, Map<String, String>> labels = (Map<String, Map<String, String>>) result.get("labels");
        assertEquals("张三", labels.get("agent").get("5"));

        verify(adHocStatMapper).selectAdHocAgg(anyString(), anyString(), eq("%Y-%m-%d %H:00"),
                anyList(), eq("agent:"), anyInt());
    }

    @Test
    void queryCategoryDimShouldNormalizeNone()
    {
        List<Map<String, Object>> raw = new ArrayList<>();
        Map<String, Object> r1 = new HashMap<>();
        r1.put("period", "2026-10-01");
        r1.put("dim_value", "NONE");
        r1.put("m0", new BigDecimal("2"));
        raw.add(r1);
        when(adHocStatMapper.selectAdHocAgg(anyString(), anyString(), anyString(), anyList(),
                anyString(), anyInt())).thenReturn(raw);

        Map<String, Object> result = service.query(query("day",
                Arrays.asList("call_total"), Arrays.asList("category")));

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> rows = (List<Map<String, Object>>) result.get("rows");
        assertEquals("NONE", rows.get(0).get("category"));

        @SuppressWarnings("unchecked")
        Map<String, Map<String, String>> labels = (Map<String, Map<String, String>>) result.get("labels");
        assertEquals("未分类", labels.get("category").get("NONE"));
        verify(adHocStatMapper, never()).selectCategoryNames(anyList());
    }

    @Test
    void queryAllPathShouldRejectDimOnlyMetric()
    {
        // 维度专属指标在无维度（全局）路径不可用
        ServiceException ex = serviceQuery("day", Arrays.asList("session_total"), new ArrayList<>());
        assertTrue(ex.getMessage().contains("不可用"));
    }

    // ---------------- 跨维度实时路径 ----------------

    @Test
    void queryCrossDimShouldMapRealtimeRowsAndLabels()
    {
        List<Map<String, Object>> raw = new ArrayList<>();
        Map<String, Object> r1 = new HashMap<>();
        r1.put("period", "2026-10-01");
        r1.put("dim_agent", 5L);
        r1.put("dim_agent_name", "张三");
        r1.put("dim_category", -1);
        r1.put("dim_category_name", null);
        r1.put("m0", new BigDecimal("7"));
        raw.add(r1);
        when(adHocStatMapper.selectCallCrossDim(anyString(), anyString(), anyString(), anyList(),
                anyInt())).thenReturn(raw);

        AdHocStatQuery q = query("day", Arrays.asList("call_total"), Arrays.asList("agent", "category"));
        Map<String, Object> result = service.query(q);

        assertEquals("realtime", result.get("source"));
        verify(adHocStatMapper).selectCallCrossDim(anyString(), anyString(), anyString(),
                eq(Arrays.asList("agent", "category")), anyInt());

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> rows = (List<Map<String, Object>>) result.get("rows");
        assertEquals("5", rows.get(0).get("agent"));
        assertEquals("NONE", rows.get(0).get("category"));

        @SuppressWarnings("unchecked")
        Map<String, Map<String, String>> labels = (Map<String, Map<String, String>>) result.get("labels");
        assertEquals("张三", labels.get("agent").get("5"));
        assertEquals("未分类", labels.get("category").get("NONE"));
    }

    @Test
    void queryCrossDimShouldRejectTicketMetric()
    {
        ServiceException ex = serviceQuery("day", Arrays.asList("ticket_total"),
                Arrays.asList("agent", "lang"));
        assertTrue(ex.getMessage().contains("仅支持呼叫类指标"));
    }

    // ---------------- 结果上限 ----------------

    @Test
    void queryShouldCapRowsAtMaxRows()
    {
        List<Map<String, Object>> raw = new ArrayList<>();
        for (int i = 0; i < 4; i++)
        {
            Map<String, Object> r = new HashMap<>();
            r.put("period", "2026-10-0" + (i + 1));
            r.put("dim_value", "ALL");
            r.put("m0", new BigDecimal(i));
            raw.add(r);
        }
        // maxRows=3 → limit 4 行 → 探测到截断
        when(adHocStatMapper.selectAdHocAgg(anyString(), anyString(), anyString(), anyList(),
                isNull(), anyInt())).thenReturn(raw);

        Map<String, Object> result = service.query(query("day",
                Arrays.asList("call_total"), new ArrayList<>()));

        assertTrue((Boolean) result.get("capped"));
        assertEquals(3, result.get("rowCount"));

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> rows = (List<Map<String, Object>>) result.get("rows");
        assertEquals(3, rows.size());
        assertEquals(3, result.get("maxRows"));
    }
}
