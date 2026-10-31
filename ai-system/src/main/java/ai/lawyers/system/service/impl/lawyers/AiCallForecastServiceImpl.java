package ai.lawyers.system.service.impl.lawyers;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
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
import ai.lawyers.system.domain.lawyers.forecast.AiForecastSlot;
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

    /** F9：15 分钟对比验收默认天数 */
    @Value("${forecast.compare-days:7}")
    private int compareDays;

    /** F9：15 分钟对比验收最大天数（防误传 90 天拉垮扫描） */
    @Value("${forecast.max-compare-days:14}")
    private int maxCompareDays;

    /** 每小时的 15 分钟时段数 */
    private static final int SLOTS_PER_HOUR = 4;

    /** 每天的 15 分钟时段数 */
    private static final int SLOTS_PER_DAY = 96;

    /** hour-of-week 剖面缓存：profile[weekday][hour] = 平均进线量 */
    private volatile double[][] profileCache;

    private volatile long profileCacheAt;

    /** F9：15 分钟剖面缓存：slotProfile[weekday][slot] = 平均 15 分钟进线量 */
    private volatile double[][] slotProfileCache;

    private volatile long slotProfileCacheAt;

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
    public List<AiForecastSlot> slotPreview(int days)
    {
        int horizon = normalizeDays(days);
        double[][] profile = slotProfile();
        Map<LocalDate, AiForecastCalendar> calendar = loadCalendar(horizon);

        List<AiForecastSlot> rows = new ArrayList<>();
        LocalDate today = LocalDate.now();
        for (int d = 1; d <= horizon; d++)
        {
            LocalDate date = today.plusDays(d);
            AiForecastCalendar cal = calendar.get(date);
            String dayType = cal == null ? null : cal.getDayType();
            BigDecimal eventCoef = cal == null ? null : cal.getCoefficient();
            BigDecimal factor = ForecastCalculator.dayFactor(dayType, eventCoef, holidayFactor, workdayFactor);
            double[] slots = profile[dayOfWeek(date)];
            for (int s = 0; s < SLOTS_PER_DAY; s++)
            {
                AiForecastSlot row = new AiForecastSlot();
                row.setForecastDate(date.format(DATE_FMT));
                row.setWeekday(dayOfWeek(date));
                row.setSlot(s);
                row.setBeginTime(slotTime(s));
                row.setEndTime(slotTime(s + 1));
                row.setTimeRange(slotTime(s) + "-" + slotTime(s + 1));
                int volume = ForecastCalculator.forecastVolume(slots[s], factor);
                row.setVolume(volume);
                row.setFactor(factor);
                row.setDayType(dayType);
                row.setDayName(cal == null ? null : cal.getDayName());
                row.setAgents(ForecastCalculator.agents(volume, ahtSeconds, utilization));
                rows.add(row);
            }
        }
        return rows;
    }

    @Override
    public Map<String, Object> slotAccuracy(int compareDays)
    {
        int days = normalizeCompareDays(compareDays);
        LocalDate today = LocalDate.now();
        LocalDate actualBegin = today.minusDays(days);
        // 剖面窗口 = 历史周内剔除最近 days 天，做不到一周时至少保留一周前段，避免自评
        int weeks = Math.max(1, (Math.max(1, historyWeeks) * 7 - days) / 7);
        LocalDate profileBegin = actualBegin.minusDays((long) weeks * 7);
        double[][] profile = computeSlotProfile(profileBegin, actualBegin, weeks);
        Map<String, double[]> actuals = loadActualSlots(actualBegin, today);

        List<Map<String, Object>> perDate = new ArrayList<>();
        double totalAbs = 0d;
        double totalActual = 0d;
        double totalForecast = 0d;
        double mapeSum = 0d;
        int mapeCount = 0;
        int slotCount = 0;
        for (Map.Entry<String, double[]> entry : actuals.entrySet())
        {
            LocalDate date;
            try
            {
                date = LocalDate.parse(entry.getKey());
            }
            catch (Exception ex)
            {
                continue;
            }
            double[] actual = entry.getValue();
            double[] forecast = new double[SLOTS_PER_DAY];
            double[] profileRow = profile[dayOfWeek(date)];
            double dateAbs = 0d;
            double dateActual = 0d;
            double dateForecast = 0d;
            double dateMapeSum = 0d;
            int dateMapeCount = 0;
            for (int s = 0; s < SLOTS_PER_DAY; s++)
            {
                forecast[s] = ForecastCalculator.forecastVolume(profileRow[s], BigDecimal.ONE);
                double diff = Math.abs(forecast[s] - actual[s]);
                dateAbs += diff;
                dateActual += actual[s];
                dateForecast += forecast[s];
                if (actual[s] > 0)
                {
                    dateMapeSum += diff / actual[s];
                    dateMapeCount++;
                }
            }
            totalAbs += dateAbs;
            totalActual += dateActual;
            totalForecast += dateForecast;
            mapeSum += dateMapeSum;
            mapeCount += dateMapeCount;
            slotCount += SLOTS_PER_DAY;

            Map<String, Object> row = new LinkedHashMap<>();
            row.put("date", entry.getKey());
            row.put("weekday", dayOfWeek(date));
            row.put("forecastTotal", round3(dateForecast));
            row.put("actualTotal", round3(dateActual));
            row.put("absError", round3(dateAbs));
            row.put("mae", round3(dateAbs / SLOTS_PER_DAY));
            row.put("mape", dateMapeCount > 0 ? round3(dateMapeSum / dateMapeCount) : null);
            row.put("accuracy", dateActual > 0 ? round3(1d - dateAbs / dateActual) : null);
            perDate.add(row);
        }

        Map<String, Object> result = new HashMap<>();
        result.put("available", !perDate.isEmpty());
        result.put("days", days);
        result.put("slotCount", slotCount);
        result.put("totalForecast", round3(totalForecast));
        result.put("totalActual", round3(totalActual));
        result.put("mae", slotCount > 0 ? round3(totalAbs / slotCount) : null);
        result.put("mape", mapeCount > 0 ? round3(mapeSum / mapeCount) : null);
        result.put("accuracy", totalActual > 0 ? round3(1d - totalAbs / totalActual) : null);
        result.put("perDate", perDate);
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("historyWeeks", historyWeeks);
        params.put("compareDays", days);
        params.put("slotMinutes", 60 / SLOTS_PER_HOUR);
        params.put("source", "ai_stat_minute/call_total");
        result.put("params", params);
        return result;
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
                        "select (dayofweek(stat_time) - 1) as wd, hour(stat_time) as hr, sum(metric_value) as total"
                      + " from ai_stat_minute"
                      + " where metric_key = 'call_total' and dimension = 'ALL' and stat_time >= ?"
                      + " group by dayofweek(stat_time), hour(stat_time)", begin);
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

    /**
     * F9：15 分钟粒度 hour-of-week 剖面（带 1 小时缓存）。
     *
     * <p>与小时剖面同源，按 weekday × (hour*4 + minute/15) 聚合近 historyWeeks 周
     * call_total，再除以周数得到「该 weekday 该 15 分钟」的平均进线量。</p>
     */
    private double[][] slotProfile()
    {
        double[][] cached = slotProfileCache;
        if (cached != null && System.currentTimeMillis() - slotProfileCacheAt < PROFILE_CACHE_TTL_MS)
        {
            return cached;
        }
        synchronized (this)
        {
            if (slotProfileCache != null && System.currentTimeMillis() - slotProfileCacheAt < PROFILE_CACHE_TTL_MS)
            {
                return slotProfileCache;
            }
            LocalDate today = LocalDate.now();
            int weeks = Math.max(1, historyWeeks);
            double[][] profile = computeSlotProfile(today.minusWeeks(weeks), today, weeks);
            slotProfileCache = profile;
            slotProfileCacheAt = System.currentTimeMillis();
            return profile;
        }
    }

    /**
     * 按 [begin, endExclusive) 聚合 15 分钟剖面；weeksDivisor 为平均化分母（周数）。
     */
    private double[][] computeSlotProfile(LocalDate begin, LocalDate endExclusive, int weeksDivisor)
    {
        double[][] profile = new double[7][SLOTS_PER_DAY];
        try
        {
            String beginText = begin.format(DATE_FMT) + " 00:00:00";
            String endText = endExclusive.format(DATE_FMT) + " 00:00:00";
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                    "select (dayofweek(stat_time) - 1) as wd,"
                  + " (hour(stat_time) * 4 + floor(minute(stat_time) / 15)) as slot,"
                  + " sum(metric_value) as total"
                  + " from ai_stat_minute"
                  + " where metric_key = 'call_total' and dimension = 'ALL'"
                  + " and stat_time >= ? and stat_time < ?"
                  + " group by dayofweek(stat_time), hour(stat_time), floor(minute(stat_time) / 15)",
                    beginText, endText);
            if (rows != null)
            {
                int divisor = Math.max(1, weeksDivisor);
                for (Map<String, Object> row : rows)
                {
                    int wd = intOf(row.get("wd"));
                    int slot = intOf(row.get("slot"));
                    if (wd >= 0 && wd < 7 && slot >= 0 && slot < SLOTS_PER_DAY)
                    {
                        profile[wd][slot] = doubleOf(row.get("total")) / divisor;
                    }
                }
            }
        }
        catch (Exception ex)
        {
            // 物化表缺失/分区未就绪等场景：返回全 0 剖面，接口不报错
            log.warn("[Forecast] 15 分钟剖面聚合失败（返回全 0 剖面）: {}", ex.getMessage());
        }
        return profile;
    }

    /** 最近 [begin, endExclusive) 的实际 15 分钟话务量：date(yyyy-MM-dd) → 96 时段 */
    private Map<String, double[]> loadActualSlots(LocalDate begin, LocalDate endExclusive)
    {
        Map<String, double[]> actuals = new TreeMap<>();
        try
        {
            String beginText = begin.format(DATE_FMT) + " 00:00:00";
            String endText = endExclusive.format(DATE_FMT) + " 00:00:00";
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                    "select date(stat_time) as d,"
                  + " (hour(stat_time) * 4 + floor(minute(stat_time) / 15)) as slot,"
                  + " sum(metric_value) as total"
                  + " from ai_stat_minute"
                  + " where metric_key = 'call_total' and dimension = 'ALL'"
                  + " and stat_time >= ? and stat_time < ?"
                  + " group by date(stat_time), hour(stat_time), floor(minute(stat_time) / 15)",
                    beginText, endText);
            if (rows != null)
            {
                for (Map<String, Object> row : rows)
                {
                    String date = dateText(row.get("d"));
                    int slot = intOf(row.get("slot"));
                    if (date == null || slot < 0 || slot >= SLOTS_PER_DAY)
                    {
                        continue;
                    }
                    actuals.computeIfAbsent(date, k -> new double[SLOTS_PER_DAY])[slot] += doubleOf(row.get("total"));
                }
            }
        }
        catch (Exception ex)
        {
            log.warn("[Forecast] 实际 15 分钟话务聚合失败（对比验收不可用）: {}", ex.getMessage());
        }
        return actuals;
    }

    /** 日期值归一为 yyyy-MM-dd（兼容 java.sql.Date / Timestamp / LocalDate / 字符串） */
    private static String dateText(Object value)
    {
        if (value == null)
        {
            return null;
        }
        if (value instanceof java.sql.Date)
        {
            return ((java.sql.Date) value).toLocalDate().format(DATE_FMT);
        }
        if (value instanceof LocalDate)
        {
            return ((LocalDate) value).format(DATE_FMT);
        }
        String text = String.valueOf(value).trim();
        if (text.length() >= 10)
        {
            return text.substring(0, 10).replace('/', '-');
        }
        return null;
    }

    /** 时段序号 → HH:mm（0 → 00:00；96 → 24:00） */
    private static String slotTime(int slotIndex)
    {
        int hour = slotIndex / SLOTS_PER_HOUR;
        int minute = (slotIndex % SLOTS_PER_HOUR) * (60 / SLOTS_PER_HOUR);
        return String.format("%02d:%02d", hour, minute);
    }

    /** 对比天数收敛：≤0 取配置默认，超过上限取上限 */
    private int normalizeCompareDays(int days)
    {
        int value = days <= 0 ? compareDays : days;
        if (value <= 0)
        {
            value = 7;
        }
        return Math.min(value, Math.max(1, maxCompareDays));
    }

    /** 指标保留 3 位小数，避免返回长浮点 */
    private static double round3(double value)
    {
        return Math.round(value * 1000d) / 1000d;
    }

    private static int dayOfWeek(LocalDate date)
    {
        // LocalDate DayOfWeek: MON=1..SUN=7 → 0=周日..6=周六。
        // 与聚合口径一致：MySQL dayofweek() 为 1=周日..7=周六，故 SQL 侧统一减 1；
        // 注意不能用 weekday()（其 0=周一，会整体错位一天）。
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
