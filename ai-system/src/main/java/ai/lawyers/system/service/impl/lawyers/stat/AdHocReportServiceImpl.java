package ai.lawyers.system.service.impl.lawyers.stat;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import ai.lawyers.common.exception.ServiceException;
import ai.lawyers.system.domain.lawyers.stat.AdHocStatQuery;
import ai.lawyers.system.mapper.lawyers.stat.AdHocStatMapper;
import ai.lawyers.system.mapper.lawyers.stat.AiStatMinuteMapper;
import ai.lawyers.system.service.lawyers.stat.AdHocStatCatalog;
import ai.lawyers.system.service.lawyers.stat.AdHocStatCatalog.DimDef;
import ai.lawyers.system.service.lawyers.stat.AdHocStatCatalog.MetricDef;
import ai.lawyers.system.service.lawyers.stat.IAdHocReportService;

/**
 * 报表自助分析 Service 实现（F6）
 *
 * <p>无维度/单维度走 ai_stat_minute 预聚合（SQL 端透视），跨维度组合（≥2 个，
 * 仅 agent/category/lang 同源维度）走 ai_call_record 实时聚合；两者均在 SQL 端
 * 先聚合后 limit，行数受 stat.adhoc.max-rows 上限保护（limit maxRows+1 探测截断）。</p>
 *
 * @author ai-lawyers
 */
@Service
public class AdHocReportServiceImpl implements IAdHocReportService
{
    private static final DateTimeFormatter TS_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /** 粒度 → SQL date_format 模式（白名单常量，非用户输入） */
    private static final Map<String, String> PERIOD_FMT = new LinkedHashMap<>();
    static
    {
        PERIOD_FMT.put("minute", "%Y-%m-%d %H:%i");
        PERIOD_FMT.put("hour", "%Y-%m-%d %H:00");
        PERIOD_FMT.put("day", "%Y-%m-%d");
    }

    private static final DateTimeFormatter MINUTE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    /** 各粒度允许的最大区间分钟数 */
    private static final long MINUTE_MAX_RANGE_MINUTES = 3 * 1440L;
    private static final long DEFAULT_MAX_RANGE_MINUTES = 31 * 1440L;

    @Autowired
    private AiStatMinuteMapper aiStatMinuteMapper;

    @Autowired
    private AdHocStatMapper adHocStatMapper;

    /** 结果行数上限（SQL limit，超限截断并返回 capped=true） */
    @Value("${stat.adhoc.max-rows:5000}")
    private int maxRows = 5000;

    @Override
    public Map<String, Object> catalog()
    {
        List<Map<String, Object>> metrics = new ArrayList<>();
        for (MetricDef m : AdHocStatCatalog.ALL_METRICS.values())
        {
            metrics.add(metricMap(m));
        }
        for (MetricDef m : AdHocStatCatalog.DIM_METRICS.values())
        {
            metrics.add(metricMap(m));
        }

        List<Map<String, Object>> dimensions = new ArrayList<>();
        for (DimDef d : AdHocStatCatalog.DIMENSIONS.values())
        {
            Map<String, Object> dm = new LinkedHashMap<>();
            dm.put("key", d.getKey());
            dm.put("label", d.getLabel());
            dm.put("metrics", new ArrayList<>(d.getMetrics()));
            dm.put("crossDim", d.isCrossDim());
            dimensions.add(dm);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("metrics", metrics);
        result.put("dimensions", dimensions);
        result.put("crossMetrics", new ArrayList<>(AdHocStatCatalog.CROSS_DIM_METRICS));
        return result;
    }

    private Map<String, Object> metricMap(MetricDef m)
    {
        Map<String, Object> mm = new LinkedHashMap<>();
        mm.put("key", m.getKey());
        mm.put("label", m.getLabel());
        mm.put("group", m.getGroup());
        return mm;
    }

    @Override
    public Map<String, Object> query(AdHocStatQuery query)
    {
        if (query == null)
        {
            throw new ServiceException("查询条件不能为空");
        }
        String gran = normalizeGranularity(query.getGranularity());
        LocalDateTime begin = parseTime(query.getBeginTime(), "开始时间");
        LocalDateTime end = parseTime(query.getEndTime(), "结束时间");
        if (!begin.isBefore(end))
        {
            throw new ServiceException("开始时间必须早于结束时间");
        }
        long rangeMinutes = Duration.between(begin, end).toMinutes();
        long maxRangeMinutes = "minute".equals(gran) ? MINUTE_MAX_RANGE_MINUTES : DEFAULT_MAX_RANGE_MINUTES;
        if (rangeMinutes > maxRangeMinutes)
        {
            throw new ServiceException("统计区间过大：" + ("minute".equals(gran)
                    ? "分钟粒度最长 3 天" : "该粒度最长 31 天"));
        }

        List<String> metrics = dedupe(query.getMetrics());
        if (metrics.isEmpty())
        {
            throw new ServiceException("请至少选择一个指标");
        }
        if (metrics.size() > 15)
        {
            throw new ServiceException("指标数量不能超过 15 个");
        }
        for (String m : metrics)
        {
            if (!AdHocStatCatalog.isKnownMetric(m))
            {
                throw new ServiceException("未知指标：" + m);
            }
        }

        List<String> dims = dedupe(query.getDimensions());
        for (String d : dims)
        {
            if (!AdHocStatCatalog.DIMENSIONS.containsKey(d))
            {
                throw new ServiceException("未知维度：" + d);
            }
        }

        String beginStr = begin.format(TS_FMT);
        String endStr = end.format(TS_FMT);
        String periodFmt = PERIOD_FMT.get(gran);

        boolean realtime = dims.size() >= 2;
        List<Map<String, Object>> raw;
        List<String> eff;
        List<String> ignored = new ArrayList<>();
        if (realtime)
        {
            for (String d : dims)
            {
                if (!AdHocStatCatalog.DIMENSIONS.get(d).isCrossDim())
                {
                    throw new ServiceException("维度「" + AdHocStatCatalog.DIMENSIONS.get(d).getLabel()
                            + "」不支持跨维度组合实时查询（仅支持坐席/咨询分类/语种）");
                }
            }
            eff = intersect(metrics, AdHocStatCatalog.CROSS_DIM_METRICS, ignored);
            if (eff.isEmpty())
            {
                throw new ServiceException("所选维度组合仅支持呼叫类指标："
                        + AdHocStatCatalog.CROSS_DIM_METRICS);
            }
            raw = adHocStatMapper.selectCallCrossDim(beginStr, endStr, periodFmt, dims, maxRows + 1);
        }
        else
        {
            String prefix = null;
            Set<String> allowed;
            if (dims.isEmpty())
            {
                allowed = AdHocStatCatalog.ALL_METRICS.keySet();
            }
            else
            {
                DimDef d = AdHocStatCatalog.DIMENSIONS.get(dims.get(0));
                prefix = d.getKey() + ":";
                allowed = d.getMetrics();
            }
            eff = intersect(metrics, allowed, ignored);
            if (eff.isEmpty())
            {
                throw new ServiceException("所选指标在当前维度下不可用，可用指标：" + allowed);
            }
            raw = adHocStatMapper.selectAdHocAgg(beginStr, endStr, periodFmt, eff, prefix, maxRows + 1);
        }

        boolean capped = raw.size() > maxRows;
        if (capped)
        {
            raw = raw.subList(0, maxRows);
        }

        Map<String, Map<String, String>> labels = new LinkedHashMap<>();
        if (!realtime && dims.size() == 1)
        {
            resolvePreaggLabels(dims.get(0), raw, labels);
        }
        else if (realtime)
        {
            resolveRealtimeLabels(dims, raw, labels);
        }

        // 动态列定义
        List<Map<String, Object>> columns = new ArrayList<>();
        columns.add(column("period", "周期"));
        for (String d : dims)
        {
            columns.add(column(d, AdHocStatCatalog.DIMENSIONS.get(d).getLabel()));
        }
        for (String m : eff)
        {
            MetricDef def = AdHocStatCatalog.ALL_METRICS.containsKey(m)
                    ? AdHocStatCatalog.ALL_METRICS.get(m) : AdHocStatCatalog.DIM_METRICS.get(m);
            columns.add(column(m, def.getLabel()));
        }

        // 输出行（含 _drill 下钻参数）
        List<Map<String, Object>> rows = new ArrayList<>();
        for (Map<String, Object> r : raw)
        {
            String period = String.valueOf(r.get("period"));
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("period", period);
            for (String d : dims)
            {
                Object v = realtime ? r.get("dim_" + d) : r.get("dim_value");
                String s = v == null ? "" : String.valueOf(v);
                if ("category".equals(d) && "-1".equals(s))
                {
                    s = "NONE";
                }
                row.put(d, s);
            }
            for (int i = 0; i < eff.size(); i++)
            {
                row.put(eff.get(i), r.get("m" + i));
            }
            Map<String, Object> drill = new LinkedHashMap<>();
            drill.put("begin", periodBegin(period, gran).format(TS_FMT));
            drill.put("end", periodEnd(period, gran).format(TS_FMT));
            for (String d : dims)
            {
                drill.put(d, row.get(d));
            }
            row.put("_drill", drill);
            rows.add(row);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("columns", columns);
        result.put("rows", rows);
        result.put("labels", labels);
        result.put("capped", capped);
        result.put("rowCount", rows.size());
        result.put("ignoredMetrics", ignored);
        result.put("source", realtime ? "realtime" : "preagg");
        result.put("maxRows", maxRows);
        return result;
    }

    /** 预聚合单维度路径：批量解析坐席/分类名称作为维度值标签 */
    private void resolvePreaggLabels(String dim, List<Map<String, Object>> raw,
                                     Map<String, Map<String, String>> labels)
    {
        if ("agent".equals(dim))
        {
            List<Long> ids = new ArrayList<>();
            for (Map<String, Object> r : raw)
            {
                Long id = parseLongOrNull(r.get("dim_value"));
                if (id != null && !ids.contains(id))
                {
                    ids.add(id);
                }
            }
            if (!ids.isEmpty())
            {
                Map<String, String> agentLabels = new LinkedHashMap<>();
                for (Map<String, Object> r : adHocStatMapper.selectAgentNames(ids))
                {
                    agentLabels.put(String.valueOf(r.get("agent_id")), String.valueOf(r.get("agent_name")));
                }
                labels.put("agent", agentLabels);
            }
        }
        else if ("category".equals(dim))
        {
            List<Long> ids = new ArrayList<>();
            for (Map<String, Object> r : raw)
            {
                Long id = parseLongOrNull(r.get("dim_value"));
                if (id != null && !ids.contains(id))
                {
                    ids.add(id);
                }
            }
            Map<String, String> catLabels = new LinkedHashMap<>();
            catLabels.put("NONE", "未分类");
            if (!ids.isEmpty())
            {
                for (Map<String, Object> r : adHocStatMapper.selectCategoryNames(ids))
                {
                    catLabels.put(String.valueOf(r.get("category_id")), String.valueOf(r.get("category_name")));
                }
            }
            labels.put("category", catLabels);
        }
    }

    /** 跨维度实时路径：SQL 已带 *_name 列，直接入标签映射 */
    private void resolveRealtimeLabels(List<String> dims, List<Map<String, Object>> raw,
                                       Map<String, Map<String, String>> labels)
    {
        for (String d : dims)
        {
            if ("lang".equals(d))
            {
                continue;
            }
            Map<String, String> dimLabels = new LinkedHashMap<>();
            if ("category".equals(d))
            {
                dimLabels.put("NONE", "未分类");
            }
            for (Map<String, Object> r : raw)
            {
                Object v = r.get("dim_" + d);
                Object name = r.get("dim_" + d + "_name");
                if (v != null && name != null)
                {
                    String key = String.valueOf(v);
                    if ("category".equals(d) && "-1".equals(key))
                    {
                        key = "NONE";
                    }
                    dimLabels.putIfAbsent(key, String.valueOf(name));
                }
            }
            labels.put(d, dimLabels);
        }
    }

    private List<String> intersect(List<String> selected, java.util.Set<String> allowed, List<String> ignored)
    {
        List<String> eff = new ArrayList<>();
        for (String m : selected)
        {
            if (allowed.contains(m))
            {
                eff.add(m);
            }
            else
            {
                ignored.add(m);
            }
        }
        return eff;
    }

    private Map<String, Object> column(String key, String label)
    {
        Map<String, Object> c = new LinkedHashMap<>();
        c.put("key", key);
        c.put("label", label);
        return c;
    }

    private Long parseLongOrNull(Object v)
    {
        if (v == null)
        {
            return null;
        }
        try
        {
            return Long.parseLong(String.valueOf(v));
        }
        catch (NumberFormatException e)
        {
            return null;
        }
    }

    private LocalDateTime periodBegin(String period, String gran)
    {
        if ("day".equals(gran))
        {
            return LocalDate.parse(period).atStartOfDay();
        }
        return LocalDateTime.parse(period, MINUTE_FMT);
    }

    private LocalDateTime periodEnd(String period, String gran)
    {
        switch (gran)
        {
            case "minute":
                return periodBegin(period, gran).plusMinutes(1);
            case "hour":
                return periodBegin(period, gran).plusHours(1);
            default:
                return periodBegin(period, gran).plusDays(1);
        }
    }

    private String normalizeGranularity(String granularity)
    {
        return PERIOD_FMT.containsKey(granularity) ? granularity : "day";
    }

    private LocalDateTime parseTime(String value, String field)
    {
        if (value == null || value.trim().isEmpty())
        {
            throw new ServiceException(field + "不能为空");
        }
        String[] patterns = { "yyyy-MM-dd HH:mm:ss", "yyyy-MM-dd HH:mm", "yyyy-MM-dd" };
        for (String p : patterns)
        {
            try
            {
                return LocalDateTime.parse(value.trim(), DateTimeFormatter.ofPattern(p));
            }
            catch (DateTimeParseException ignored)
            {
            }
        }
        throw new ServiceException(field + "格式不合法：" + value);
    }

    private List<String> dedupe(List<String> input)
    {
        List<String> out = new ArrayList<>();
        if (input == null)
        {
            return out;
        }
        for (String s : input)
        {
            if (s != null && !s.trim().isEmpty() && !out.contains(s.trim()))
            {
                out.add(s.trim());
            }
        }
        return out;
    }
}
