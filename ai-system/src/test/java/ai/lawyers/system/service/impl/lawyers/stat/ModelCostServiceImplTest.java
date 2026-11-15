package ai.lawyers.system.service.impl.lawyers.stat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.test.util.ReflectionTestUtils;
import ai.lawyers.system.domain.lawyers.stat.AiModelCallLog;
import ai.lawyers.system.mapper.lawyers.stat.AiModelCallLogMapper;

/**
 * 大模型调用成本看板服务单测。
 *
 * <p>覆盖默认近 7 天区间、非法日期兜底、起止倒置互换、超长区间截断，
 * 以及总览成功率与单位通话成本计算、趋势/模型/场景委托与明细默认区间。</p>
 *
 * @author ai-lawyers
 */
class ModelCostServiceImplTest
{
    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private ModelCostServiceImpl service;

    @Mock private AiModelCallLogMapper modelCallLogMapper;

    @BeforeEach
    void setUp()
    {
        MockitoAnnotations.openMocks(this);
        service = new ModelCostServiceImpl();
        ReflectionTestUtils.setField(service, "modelCallLogMapper", modelCallLogMapper);
    }

    private String[] captureRange()
    {
        ArgumentCaptor<String> begin = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> end = ArgumentCaptor.forClass(String.class);
        verify(modelCallLogMapper).selectTrend(begin.capture(), end.capture());
        return new String[] { begin.getValue(), end.getValue() };
    }

    @Test
    void overview_computesSuccessRateAndCostPerCall()
    {
        Map<String, Object> data = new HashMap<>();
        data.put("totalCalls", 4L);
        data.put("successCalls", 3L);
        data.put("costAmount", new BigDecimal("1.5"));
        when(modelCallLogMapper.selectOverview(anyString(), anyString())).thenReturn(data);
        when(modelCallLogMapper.selectCallCount(anyString(), anyString())).thenReturn(3L);

        Map<String, Object> result = service.overview("2026-01-01", "2026-01-07");

        assertEquals("2026-01-01", result.get("beginTime"));
        assertEquals("2026-01-07", result.get("endTime"));
        assertEquals(new BigDecimal("75.00"), result.get("successRate"));
        assertEquals(new BigDecimal("0.500000"), result.get("costPerCall"));
        assertEquals(3L, result.get("callCount"));
    }

    @Test
    void overview_nullDataAndZeroCalls_returnsZeros()
    {
        when(modelCallLogMapper.selectOverview(anyString(), anyString())).thenReturn(null);
        when(modelCallLogMapper.selectCallCount(anyString(), anyString())).thenReturn(0L);

        Map<String, Object> result = service.overview(null, null);

        assertEquals(BigDecimal.ZERO, result.get("successRate"));
        assertEquals(BigDecimal.ZERO, result.get("costPerCall"));
    }

    @Test
    void trend_defaultRange_lastSevenDays()
    {
        when(modelCallLogMapper.selectTrend(anyString(), anyString())).thenReturn(Collections.emptyList());

        service.trend(null, null);

        String[] range = captureRange();
        LocalDate today = LocalDate.now();
        assertEquals(today.minusDays(6).format(FMT), range[0]);
        assertEquals(today.format(FMT), range[1]);
    }

    @Test
    void trend_invalidDate_fallsBackToLastSevenDays()
    {
        when(modelCallLogMapper.selectTrend(anyString(), anyString())).thenReturn(Collections.emptyList());

        service.trend("bad-date", "2026-01-07");

        String[] range = captureRange();
        LocalDate today = LocalDate.now();
        assertEquals(today.minusDays(6).format(FMT), range[0]);
        assertEquals(today.format(FMT), range[1]);
    }

    @Test
    void trend_beginAfterEnd_swapped()
    {
        when(modelCallLogMapper.selectTrend(anyString(), anyString())).thenReturn(Collections.emptyList());

        service.trend("2026-02-10", "2026-02-01");

        String[] range = captureRange();
        assertEquals("2026-02-01", range[0]);
        assertEquals("2026-02-10", range[1]);
    }

    @Test
    void trend_rangeTooLong_truncated()
    {
        when(modelCallLogMapper.selectTrend(anyString(), anyString())).thenReturn(Collections.emptyList());

        service.trend("2025-01-01", "2026-02-01");

        String[] range = captureRange();
        assertEquals("2025-11-02", range[0]);
        assertEquals("2026-02-01", range[1]);
    }

    @Test
    void byModel_delegates()
    {
        List<Map<String, Object>> list = Collections.singletonList(new HashMap<>());
        when(modelCallLogMapper.selectByModel("2026-01-01", "2026-01-07")).thenReturn(list);

        assertSame(list, service.byModel("2026-01-01", "2026-01-07"));
    }

    @Test
    void byScene_delegates()
    {
        List<Map<String, Object>> list = Collections.singletonList(new HashMap<>());
        when(modelCallLogMapper.selectByScene("2026-01-01", "2026-01-07")).thenReturn(list);

        assertSame(list, service.byScene("2026-01-01", "2026-01-07"));
    }

    @Test
    void logList_nullQuery_usesDefaultRange()
    {
        when(modelCallLogMapper.selectLogList(any(AiModelCallLog.class))).thenReturn(Collections.emptyList());

        service.logList(null);

        ArgumentCaptor<AiModelCallLog> captor = ArgumentCaptor.forClass(AiModelCallLog.class);
        verify(modelCallLogMapper).selectLogList(captor.capture());
        String begin = (String) captor.getValue().getParams().get("beginTime");
        String end = (String) captor.getValue().getParams().get("endTime");
        LocalDate today = LocalDate.now();
        assertEquals(today.minusDays(6).format(FMT), begin);
        assertEquals(today.format(FMT) + " 23:59:59", end);
    }

    @Test
    void logList_explicitRange_keepsParams()
    {
        AiModelCallLog query = new AiModelCallLog();
        query.getParams().put("beginTime", "2026-01-01");
        query.getParams().put("endTime", "2026-01-31");
        when(modelCallLogMapper.selectLogList(query)).thenReturn(Collections.emptyList());

        service.logList(query);

        assertEquals("2026-01-01", query.getParams().get("beginTime"));
        assertEquals("2026-01-31", query.getParams().get("endTime"));
    }

    @Test
    void overview_totalCallsZero_butHasData_keepsZeroRate()
    {
        Map<String, Object> data = new HashMap<>();
        data.put("totalCalls", 0);
        data.put("successCalls", 0);
        data.put("costAmount", "2");
        when(modelCallLogMapper.selectOverview(anyString(), anyString())).thenReturn(data);
        when(modelCallLogMapper.selectCallCount(anyString(), anyString())).thenReturn(2L);

        Map<String, Object> result = service.overview("2026-01-01", "2026-01-07");

        assertEquals(BigDecimal.ZERO, result.get("successRate"));
        assertEquals(new BigDecimal("1.000000"), result.get("costPerCall"));
        assertTrue(result.containsKey("totalCalls"));
    }
}
