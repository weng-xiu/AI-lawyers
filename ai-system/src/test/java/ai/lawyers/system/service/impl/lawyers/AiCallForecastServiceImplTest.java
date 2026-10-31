package ai.lawyers.system.service.impl.lawyers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;
import ai.lawyers.system.domain.lawyers.forecast.AiForecastCalendar;
import ai.lawyers.system.domain.lawyers.forecast.AiForecastSlot;
import ai.lawyers.system.mapper.lawyers.forecast.AiForecastCalendarMapper;
import ai.lawyers.system.mapper.lawyers.forecast.AiForecastPlanMapper;

/**
 * F9 15 分钟粒度话务预测与对比验收单测：
 * <ol>
 *   <li>剖面 × 日型系数 → 每日 96 个时段，时段边界与坐席建议取整正确；</li>
 *   <li>物化表缺失/聚合异常时降级为全 0 剖面，接口不抛异常；</li>
 *   <li>对比验收用「剔除最近 compareDays 天」的剖面，MAE/MAPE/准确率口径与降级行为。</li>
 * </ol>
 */
@ExtendWith(MockitoExtension.class)
public class AiCallForecastServiceImplTest
{
    private static final int HISTORY_WEEKS = 8;

    @Mock
    private JdbcTemplate jdbcTemplate;

    @Mock
    private AiForecastCalendarMapper calendarMapper;

    @Mock
    private AiForecastPlanMapper planMapper;

    private AiCallForecastServiceImpl service;

    private final LocalDate tomorrow = LocalDate.now().plusDays(1);

    @BeforeEach
    void setUp()
    {
        service = new AiCallForecastServiceImpl();
        ReflectionTestUtils.setField(service, "jdbcTemplate", jdbcTemplate);
        ReflectionTestUtils.setField(service, "calendarMapper", calendarMapper);
        ReflectionTestUtils.setField(service, "planMapper", planMapper);
        ReflectionTestUtils.setField(service, "historyWeeks", HISTORY_WEEKS);
        ReflectionTestUtils.setField(service, "maxDays", 14);
        ReflectionTestUtils.setField(service, "ahtSeconds", 300);
        ReflectionTestUtils.setField(service, "utilization", 0.85d);
        ReflectionTestUtils.setField(service, "holidayFactor", new BigDecimal("0.4"));
        ReflectionTestUtils.setField(service, "workdayFactor", new BigDecimal("1.2"));
        ReflectionTestUtils.setField(service, "compareDays", 7);
        ReflectionTestUtils.setField(service, "maxCompareDays", 14);
    }

    // ---------------- 15 分钟预测 ----------------

    @Test
    void slotPreview_emits96SlotsPerDayWithCorrectBoundaries()
    {
        when(calendarMapper.selectActiveBetween(any(), any())).thenReturn(new ArrayList<>());
        stubQueries(Collections.singletonList(
                row("wd", weekday(tomorrow), "slot", 33, "total", 280)), Collections.emptyList());

        List<AiForecastSlot> rows = service.slotPreview(1);

        assertEquals(96, rows.size(), "1 天应有 96 个 15 分钟时段");
        assertEquals(tomorrow.toString(), rows.get(0).getForecastDate());
        assertEquals(Integer.valueOf(weekday(tomorrow)), rows.get(0).getWeekday());
        assertEquals("00:00", rows.get(0).getBeginTime());
        assertEquals("00:15", rows.get(0).getEndTime());
        assertEquals("00:00-00:15", rows.get(0).getTimeRange());
        // 末时段结束时间为 24:00，前端可直接按 15 分钟连续出图
        AiForecastSlot last = rows.get(95);
        assertEquals("23:45", last.getBeginTime());
        assertEquals("24:00", last.getEndTime());
        assertEquals("23:45-24:00", last.getTimeRange());

        // 剖面 280 / 8 周 = 35，常规日系数 1
        AiForecastSlot target = rows.get(33);
        assertEquals("08:15", target.getBeginTime());
        assertEquals("08:30", target.getEndTime());
        assertEquals(Integer.valueOf(35), target.getVolume());
        // ceil(35 * 300 / 3600 / 0.85) = ceil(3.43) = 4
        assertEquals(Integer.valueOf(4), target.getAgents());

        // 无数据的时段量为 0、坐席为 0，不因缺失而报错
        AiForecastSlot empty = rows.get(34);
        assertEquals(Integer.valueOf(0), empty.getVolume());
        assertEquals(Integer.valueOf(0), empty.getAgents());
    }

    @Test
    void slotPreview_appliesHolidayFactorFromCalendar()
    {
        stubQueries(Collections.singletonList(
                row("wd", weekday(tomorrow), "slot", 40, "total", 800)), Collections.emptyList());
        when(calendarMapper.selectActiveBetween(any(), any())).thenReturn(
                Collections.singletonList(holiday(tomorrow, "春节")));

        List<AiForecastSlot> rows = service.slotPreview(1);

        AiForecastSlot target = rows.get(40);
        // 剖面 800 / 8 周 = 100，假日系数 0.4 → 40
        assertEquals(Integer.valueOf(40), target.getVolume());
        assertEquals("HOLIDAY", target.getDayType());
        assertEquals("春节", target.getDayName());
        assertEquals(0, new BigDecimal("0.4").compareTo(target.getFactor()));
        assertEquals(Integer.valueOf(4), target.getAgents());
    }

    @Test
    void slotPreview_degradesToZeroProfileWhenAggregationFails()
    {
        when(calendarMapper.selectActiveBetween(any(), any())).thenReturn(new ArrayList<>());
        when(jdbcTemplate.queryForList(anyString(), any(Object[].class)))
                .thenThrow(new RuntimeException("ai_stat_minute 未回填"));

        List<AiForecastSlot> rows = service.slotPreview(2);

        assertEquals(192, rows.size(), "2 天应有 192 个时段");
        assertTrue(rows.stream().allMatch(r -> r.getVolume() == 0 && r.getAgents() == 0));
    }

    // ---------------- 对比验收 ----------------

    @Test
    void slotAccuracy_computesMaeMapeAndAccuracy()
    {
        LocalDate first = LocalDate.now().minusDays(2);
        LocalDate second = LocalDate.now().minusDays(1);
        // 剖面窗口 7 周（8 周扣除最近 2 天做不到整周）：700 / 7 = 100
        List<Map<String, Object>> profileRows = new ArrayList<>();
        profileRows.add(row("wd", weekday(first), "slot", 0, "total", 700));
        profileRows.add(row("wd", weekday(second), "slot", 0, "total", 700));
        List<Map<String, Object>> actualRows = new ArrayList<>();
        actualRows.add(row("d", first.toString(), "slot", 0, "total", 90));
        actualRows.add(row("d", second.toString(), "slot", 0, "total", 110));
        stubQueries(profileRows, actualRows);

        Map<String, Object> result = service.slotAccuracy(2);

        assertTrue((Boolean) result.get("available"));
        assertEquals(2, result.get("days"));
        assertEquals(192, result.get("slotCount"));
        assertEquals(200d, asDouble(result.get("totalActual")), 1e-6);
        // 剖面仅 slot 0 有值：每个日期 100 通 × 2 天
        assertEquals(200d, asDouble(result.get("totalForecast")), 1e-6);
        // 2 个日期 × 各 10 通误差 / 192 时段 = 0.104
        assertEquals(0.104d, asDouble(result.get("mae")), 1e-6);
        // 1 - 20/200 = 0.9
        assertEquals(0.9d, asDouble(result.get("accuracy")), 1e-6);
        // (10/90 + 10/110) / 2 = 0.101
        assertEquals(0.101d, asDouble(result.get("mape")), 1e-6);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> perDate = (List<Map<String, Object>>) result.get("perDate");
        assertEquals(2, perDate.size());
        assertEquals(first.toString(), perDate.get(0).get("date"));
        assertEquals(0.889d, asDouble(perDate.get(0).get("accuracy")), 1e-6);
        assertEquals(0.111d, asDouble(perDate.get(0).get("mape")), 1e-6);
        assertEquals(0.909d, asDouble(perDate.get(1).get("accuracy")), 1e-6);

        @SuppressWarnings("unchecked")
        Map<String, Object> params = (Map<String, Object>) result.get("params");
        assertEquals(HISTORY_WEEKS, params.get("historyWeeks"));
        assertEquals(2, params.get("compareDays"));
        assertEquals(15, params.get("slotMinutes"));
        assertNotNull(params.get("source"));
    }

    @Test
    void slotAccuracy_withoutActualData_reportsUnavailable()
    {
        stubQueries(Collections.emptyList(), Collections.emptyList());

        Map<String, Object> result = service.slotAccuracy(7);

        assertEquals(Boolean.FALSE, result.get("available"));
        assertEquals(0, result.get("slotCount"));
        assertNull(result.get("mae"));
        assertNull(result.get("mape"));
        assertNull(result.get("accuracy"));
        assertTrue(((List<?>) result.get("perDate")).isEmpty());
    }

    @Test
    void slotAccuracy_clampsCompareDaysToConfiguredUpperBound()
    {
        stubQueries(Collections.emptyList(), Collections.emptyList());

        assertEquals(14, service.slotAccuracy(90).get("days"));
        // 非正数回退到配置默认 7 天
        assertEquals(7, service.slotAccuracy(0).get("days"));
    }

    // ---------------- helpers ----------------

    /**
     * 按 SQL 文本分流两道聚合：剖面（weekday/slot 聚合）与实际（按日期聚合）。
     * 物化表查询用可变参数，需按 sql 文本匹配而不能只按参数位置。
     */
    private void stubQueries(List<Map<String, Object>> profileRows, List<Map<String, Object>> actualRows)
    {
        when(jdbcTemplate.queryForList(anyString(), any(Object[].class))).thenAnswer(invocation -> {
            String sql = invocation.getArgument(0);
            if (sql.contains("date(stat_time) as d"))
            {
                return actualRows;
            }
            if (sql.contains("weekday(stat_time) as wd") || sql.contains("as wd"))
            {
                return profileRows;
            }
            return new ArrayList<>();
        });
    }

    private static Map<String, Object> row(Object... keyValues)
    {
        Map<String, Object> map = new HashMap<>();
        for (int i = 0; i + 1 < keyValues.length; i += 2)
        {
            map.put((String) keyValues[i], keyValues[i + 1]);
        }
        return map;
    }

    private static AiForecastCalendar holiday(LocalDate date, String name)
    {
        AiForecastCalendar calendar = new AiForecastCalendar();
        calendar.setCalendarDate(Date.from(date.atStartOfDay(ZoneId.systemDefault()).toInstant()));
        calendar.setDayType("HOLIDAY");
        calendar.setDayName(name);
        return calendar;
    }

    /** 与 MySQL dayofweek()-1 一致：0=周日 … 6=周六 */
    private static int weekday(LocalDate date)
    {
        return date.getDayOfWeek().getValue() % 7;
    }

    private static double asDouble(Object value)
    {
        return ((Number) value).doubleValue();
    }
}
