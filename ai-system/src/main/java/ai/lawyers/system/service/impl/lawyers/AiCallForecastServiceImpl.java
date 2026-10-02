package ai.lawyers.system.service.impl.lawyers;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import com.fasterxml.jackson.databind.ObjectMapper;
import ai.lawyers.common.exception.ServiceException;
import ai.lawyers.common.utils.StringUtils;
import ai.lawyers.system.domain.lawyers.forecast.AiForecastCalendar;
import ai.lawyers.system.domain.lawyers.forecast.AiForecastHour;
import ai.lawyers.system.domain.lawyers.forecast.AiForecastPlan;
import ai.lawyers.system.mapper.lawyers.forecast.AiForecastCalendarMapper;
import ai.lawyers.system.mapper.lawyers.forecast.AiForecastPlanMapper;
import ai.lawyers.system.service.lawyers.IAiCallForecastService;

/**
 * 话务预测与智能排班 Service 实现（P1-9）。
 *
 * <p>基线剖面：近 history-weeks 周的 ai_stat_minute（P3-F3 物化表）
 * metric_key=call_total 按 weekday+hour 求平均（剖面在服务内缓存，
 * 避免每次请求重复聚合）；未来 N 天逐时预测量 = 剖面 × 日型系数。</p>
 *
 * @author ai-lawyers
 */
@Service
public class AiCallForecastServiceImpl implements IAiCallForecastService
{
    private static final Logger log = LoggerFactory.getLogger(AiCallForecastServiceImpl.class);

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    /** 剖面缓存 TTL（毫秒，1 小时） */
    private static final long PROFILE_CACHE_TTL_MS = 3600_000L;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private AiForecastCalendarMapper calendarMapper;

    @Autowired
    private AiForecastPlanMapper planMapper;

    @Autowired
    private ObjectMapper objectMapper;

    /** 基线剖面历史周数 */
    @Value("${forecast.history-weeks:8}")
    private int historyWeeks;

    /** 单次预测最大天数（防误传 365） */
    @Value("${forecast.max-days:14}")
    private int maxDays;

    /** 平均处理时长（秒，含通话+话后，排班推算用） */
    @Value("${forecast.aht-seconds:300}")
    private int ahtSeconds;

    /** 坐席利用率（0~1） */
    @Value("${forecast.utilization:0.85}")
    private double utilization;

    /** 假日话务系数 */
    @Value("${forecast.holiday-factor:0.4}")
    private BigDecimal holidayFactor;

    /** 调休补班话务系数 */
    @Value("${forecast.workday-factor:1.2}")
    private BigDecimal workdayFactor;

    /** hour-of-week 剖面缓存：profile[weekday][hour] = 平均进线量 */
    private volatile double[][] profileCache;

    private volatile long profileCacheAt;

    @Override
    public List<AiForecastHour> preview(int days)
    {
        int horizon = normalizeDays(days);
        double[][] profile = profile();
        Map<LocalDate, AiForecastCalendar> calendar = loadCalendar(horizon);

        List<AiForecastHour> rows = new ArrayList<>();
        LocalDate today = LocalDate.now();
        for (int d = 1; d <= horizon; d++)
        {
            LocalDate date = today.plusDays(d);
            AiForecastCalendar cal = calendar.get(date);
            String dayType = cal == null ? null : cal.getDayType();
            BigDecimal eventCoef = cal == null ? null : cal.getCoefficient();
            BigDecimal factor = ForecastCalculator.dayFactor(dayType, eventCoef, holidayFactor, workdayFactor);
            double[] hourly = profile[dayOfWeek(date)];
            for (int h = 0; h < 24; h++)
            {
                AiForecastHour row = new AiForecastHour();
                row.setForecastDate(date.format(DATE_FMT));
                row.setWeekday(dayOfWeek(date));
                row.setHour(h);
                row.setVolume(ForecastCalculator.forecastVolume(hourly[h], factor));
                row.setFactor(factor);
                row.setDayType(dayType);
                row.setDayName(cal == null ? null : cal.getDayName());
                row.setAgents(ForecastCalculator.agents(row.getVolume(), ahtSeconds, utilization));
                rows.add(row);
            }
        }
        return rows;
    }

    @Override
    public Map<String, Object> staffing(int days)
    {
        List<AiForecastHour> rows = preview(days);

        // 逐日汇总：总话量/峰值时段/峰值坐席
        Map<String, Map<String, Object>> perDay = new java.util.LinkedHashMap<>();
        for (AiForecastHour row : rows)
        {
            Map<String, Object> day = perDay.computeIfAbsent(row.getForecastDate(), k -> {
                Map<String, Object> m = new HashMap<>();
                m.put("date", k);
                m.put("volume", 0);
                m.put("peakHour", null);
                m.put("peakVolume", 0);
                m.put("peakAgents", 0);
                return m;
            });
            int volume = (int) day.get("volume") + row.getVolume();
            day.put("volume", volume);
            if (row.getVolume() > (int) day.get("peakVolume"))
            {
                day.put("peakVolume", row.getVolume());
                day.put("peakHour", String.format("%02d:00", row.getHour()));
                day.put("peakAgents", row.getAgents());
            }
            else if (row.getVolume() == (int) day.get("peakVolume") && row.getAgents() > (int) day.get("peakAgents"))
            {
                // 同话量取坐席需求高者（保守排班）
                day.put("peakAgents", row.getAgents());
            }
        }

        int totalVolume = 0;
        String peakHour = null;
        int peakAgents = 0;
        int peakVolume = 0;
        for (Map<String, Object> day : perDay.values())
        {
            totalVolume += (int) day.get("volume");
            if ((int) day.get("peakVolume") > peakVolume)
            {
                peakVolume = (int) day.get("peakVolume");
                peakHour = day.get("date") + " " + day.get("peakHour");
                peakAgents = (int) day.get("peakAgents");
            }
        }

        Map<String, Object> result = new HashMap<>();
        result.put("totalVolume", totalVolume);
        result.put("peakHour", peakHour);
        result.put("peakAgents", peakAgents);
        result.put("perDay", new ArrayList<>(perDay.values()));
        Map<String, Object> params = new HashMap<>();
        params.put("historyWeeks", historyWeeks);
        params.put("ahtSeconds", ahtSeconds);
        params.put("utilization", utilization);
        params.put("holidayFactor", holidayFactor);
        params.put("workdayFactor", workdayFactor);
        result.put("params", params);
        return result;
    }

    @Override
    public Long confirm(int days, String remark, String confirmedBy)
    {
        Map<String, Object> staffing = staffing(days);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> perDay = (List<Map<String, Object>>) staffing.get("perDay");
        if (perDay == null || perDay.isEmpty())
        {
            throw new ServiceException("无可确认的预测数据（基线剖面为空，请先回填 ai_stat_minute）");
        }
        AiForecastPlan plan = new AiForecastPlan();
        plan.setBeginDate(Date.from(LocalDate.now().plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant()));
        plan.setEndDate(Date.from(LocalDate.now().plusDays(normalizeDays(days))
                .atStartOfDay(ZoneId.systemDefault()).toInstant()));
        plan.setTotalVolume((Integer) staffing.get("totalVolume"));
        plan.setPeakHour((String) staffing.get("peakHour"));
        plan.setPeakAgents((Integer) staffing.get("peakAgents"));
        plan.setConfirmedBy(confirmedBy);
        plan.setRemark(remark);
        try
        {
            Map<String, Object> payload = new HashMap<>();
            payload.put("staffing", staffing);
            payload.put("preview", preview(days));
            payload.put("confirmedAt", new Date());
            plan.setPayloadJson(objectMapper.writeValueAsString(payload));
        }
        catch (Exception ex)
        {
            throw new ServiceException("预测快照序列化失败: " + ex.getMessage());
        }
        planMapper.insertPlan(plan);
        return plan.getPlanId();
    }

    @Override
    public List<AiForecastCalendar> selectCalendarList(AiForecastCalendar query)
    {
        return calendarMapper.selectCalendarList(query);
    }

    @Override
    public int insertCalendar(AiForecastCalendar calendar)
    {
        validateCalendar(calendar);
        List<AiForecastCalendar> exist = calendarMapper.selectActiveBetween(calendar.getCalendarDate(), calendar.getCalendarDate());
        if (exist != null && !exist.isEmpty())
        {
            throw new ServiceException("该日期已有日历条目，请直接修改");
        }
        return calendarMapper.insertCalendar(calendar);
    }

    @Override
    public int updateCalendar(AiForecastCalendar calendar)
    {
        if (calendar.getCalendarId() == null)
        {
            throw new ServiceException("日历ID不能为空");
        }
        validateCalendar(calendar);
        return calendarMapper.updateCalendar(calendar);
    }

    @Override
    public int deleteCalendarByIds(Long[] calendarIds)
    {
        return calendarMapper.deleteCalendarByIds(calendarIds);
    }

    private void validateCalendar(AiForecastCalendar calendar)
    {
        if (calendar.getCalendarDate() == null)
        {
            throw new ServiceException("日期不能为空");
        }
        if (StringUtils.isEmpty(calendar.getDayType()))
        {
            throw new ServiceException("日型不能为空");
        }
        if (!"HOLIDAY".equals(calendar.getDayType()) && !"EVENT".equals(calendar.getDayType())
                && !"WORKDAY".equals(calendar.getDayType()))
        {
            throw new ServiceException("日型非法（HOLIDAY/EVENT/WORKDAY）");
        }
    }

    private int normalizeDays(int days)
    {
        if (days <= 0)
        {
            days = 7;
        }
        return Math.min(days, Math.max(1, maxDays));
    }

    /** 未来 N 天的日历条目（日期 → 条目） */
    private Map<LocalDate, AiForecastCalendar> loadCalendar(int horizon)
    {
        LocalDate begin = LocalDate.now().plusDays(1);
        LocalDate end = begin.plusDays(horizon - 1);
        Date b = Date.from(begin.atStartOfDay(ZoneId.systemDefault()).toInstant());
        Date e = Date.from(end.atStartOfDay(ZoneId.systemDefault()).toInstant());
        Map<LocalDate, AiForecastCalendar> map = new HashMap<>();
        List<AiForecastCalendar> list = calendarMapper.selectActiveBetween(b, e);
        if (list != null)
        {
            for (AiForecastCalendar cal : list)
            {
                if (cal.getCalendarDate() != null)
                {
                    map.put(cal.getCalendarDate().toInstant().atZone(ZoneId.systemDefault()).toLocalDate(), cal);
                }
            }
        }
        return map;
    }

    /**
     * hour-of-week 基线剖面（带 1 小时缓存）：近 historyWeeks 周 call_total
     * 按 weekday+hour 平均。无数据时返回全 0 剖面（预测量 0，不报错）。
     */
    private double[][] profile()
    {
        double[][] cached = profileCache;
        if (cached != null && System.currentTimeMillis() - profileCacheAt < PROFILE_CACHE_TTL_MS)
        {
            return cached;
        }
        synchronized (this)
        {
            if (profileCache != null && System.currentTimeMillis() - profileCacheAt < PROFILE_CACHE_TTL_MS)
            {
                return profileCache;
            }
            double[][] profile = new double[7][24];
            try
            {
                int weeks = Math.max(1, historyWeeks);
                String begin = LocalDate.now().minusWeeks(weeks).format(DATE_FMT) + " 00:00:00";
                List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                        "select weekday(stat_time) as wd, hour(stat_time) as hr, sum(metric_value) as total"
                      + " from ai_stat_minute"
                      + " where metric_key = 'call_total' and dimension = 'ALL' and stat_time >= ?"
                      + " group by weekday(stat_time), hour(stat_time)", begin);
                if (rows != null)
                {
                    for (Map<String, Object> row : rows)
                    {
                        int wd = intOf(row.get("wd"));
                        int hr = intOf(row.get("hr"));
                        if (wd >= 0 && wd < 7 && hr >= 0 && hr < 24)
                        {
                            profile[wd][hr] = doubleOf(row.get("total")) / weeks;
                        }
                    }
                }
            }
            catch (Exception ex)
            {
                // 物化表未回填等场景：基线全 0，接口返回 0 量预测而不是报错
                log.warn("[Forecast] 基线剖面聚合失败（返回全 0 剖面）: {}", ex.getMessage());
            }
            profileCache = profile;
            profileCacheAt = System.currentTimeMillis();
            return profile;
        }
    }

    private static int dayOfWeek(LocalDate date)
    {
        // LocalDate DayOfWeek: MON=1..SUN=7 → weekday() 口径 0=周日..6=周六（MySQL weekday 与 Date 域一致）
        return date.getDayOfWeek().getValue() % 7;
    }

    private int intOf(Object value)
    {
        return value instanceof Number ? ((Number) value).intValue() : 0;
    }

    private double doubleOf(Object value)
    {
        return value instanceof Number ? ((Number) value).doubleValue() : 0d;
    }
}
