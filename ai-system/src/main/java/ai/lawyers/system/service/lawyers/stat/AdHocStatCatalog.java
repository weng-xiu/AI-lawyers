package ai.lawyers.system.service.lawyers.stat;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * 报表自助分析指标/维度白名单目录（F6）
 *
 * <p>与 {@code StatMinuteScheduleTask} 写入 ai_stat_minute 的口径一一对应：
 * ALL 全局指标 30 个 + 5 类维度行专属指标。所有可自助查询的指标/维度必须登记在此，
 * Service 与 Mapper 据此做白名单校验，杜绝动态 SQL 注入面。</p>
 *
 * @author ai-lawyers
 */
public final class AdHocStatCatalog
{
    /** 指标定义 */
    public static class MetricDef
    {
        private final String key;
        private final String label;
        private final String group;

        public MetricDef(String key, String label, String group)
        {
            this.key = key;
            this.label = label;
            this.group = group;
        }

        public String getKey() { return key; }

        public String getLabel() { return label; }

        public String getGroup() { return group; }
    }

    /** 维度定义 */
    public static class DimDef
    {
        private final String key;
        private final String label;
        /** 该维度行可用的指标键 */
        private final Set<String> metrics;
        /** 是否支持跨维度实时组合（agent/category/lang 同源于 ai_call_record） */
        private final boolean crossDim;

        public DimDef(String key, String label, Set<String> metrics, boolean crossDim)
        {
            this.key = key;
            this.label = label;
            this.metrics = metrics;
            this.crossDim = crossDim;
        }

        public String getKey() { return key; }

        public String getLabel() { return label; }

        public Set<String> getMetrics() { return metrics; }

        public boolean isCrossDim() { return crossDim; }
    }

    /** ALL 全局指标（30 个） */
    public static final Map<String, MetricDef> ALL_METRICS;

    /** 维度行专属指标（4 个，仅对应维度选中时可用） */
    public static final Map<String, MetricDef> DIM_METRICS;

    /** 维度目录（5 类） */
    public static final Map<String, DimDef> DIMENSIONS;

    /** 跨维度实时组合可用指标（同源 ai_call_record，口径与分钟聚合一致） */
    public static final Set<String> CROSS_DIM_METRICS = new LinkedHashSet<>(java.util.Arrays.asList(
            "call_total", "call_answered", "call_missed", "call_transferred", "answered_duration_sum"));

    static
    {
        Map<String, MetricDef> all = new LinkedHashMap<>();
        // 呼叫
        all.put("call_total", new MetricDef("call_total", "呼叫总量", "呼叫"));
        all.put("call_answered", new MetricDef("call_answered", "接通量", "呼叫"));
        all.put("call_missed", new MetricDef("call_missed", "未接量", "呼叫"));
        all.put("call_transferred", new MetricDef("call_transferred", "转接量", "呼叫"));
        all.put("answered_duration_sum", new MetricDef("answered_duration_sum", "接通通话时长合计(秒)", "呼叫"));
        // 外呼
        all.put("outbound_total", new MetricDef("outbound_total", "外呼总量", "外呼"));
        all.put("queue_wait_ms_sum", new MetricDef("queue_wait_ms_sum", "排队等待时长合计(毫秒)", "外呼"));
        all.put("queue_wait_count", new MetricDef("queue_wait_count", "排队等待记录数", "外呼"));
        // 排队
        all.put("queue_total", new MetricDef("queue_total", "排队总量", "排队"));
        all.put("queue_abandon", new MetricDef("queue_abandon", "排队放弃量", "排队"));
        all.put("queue_answered", new MetricDef("queue_answered", "排队接听量", "排队"));
        all.put("queue_answered_wait_sum", new MetricDef("queue_answered_wait_sum", "接听等待时长合计(秒)", "排队"));
        all.put("queue_within20", new MetricDef("queue_within20", "20秒内接听量", "排队"));
        // 工单
        all.put("ticket_total", new MetricDef("ticket_total", "工单总量", "工单"));
        all.put("ticket_closed", new MetricDef("ticket_closed", "工单办结量", "工单"));
        all.put("ticket_overdue", new MetricDef("ticket_overdue", "工单超时量", "工单"));
        all.put("ticket_close_sec_sum", new MetricDef("ticket_close_sec_sum", "办结时长合计(秒)", "工单"));
        // 质检
        all.put("quality_total", new MetricDef("quality_total", "质检总量", "质检"));
        all.put("quality_score_sum", new MetricDef("quality_score_sum", "质检得分合计", "质检"));
        all.put("quality_score_count", new MetricDef("quality_score_count", "质检计分数量", "质检"));
        all.put("quality_reviewed", new MetricDef("quality_reviewed", "已复核量", "质检"));
        all.put("quality_pending", new MetricDef("quality_pending", "待复核量", "质检"));
        all.put("quality_risk", new MetricDef("quality_risk", "风险关联量", "质检"));
        // 满意度
        all.put("satisfaction_score_sum", new MetricDef("satisfaction_score_sum", "满意度得分合计", "满意度"));
        all.put("satisfaction_count", new MetricDef("satisfaction_count", "满意度评价数", "满意度"));
        // 公众端评价
        all.put("eval_total", new MetricDef("eval_total", "公众端评价总量", "公众端评价"));
        all.put("eval_overall_sum", new MetricDef("eval_overall_sum", "总体评分合计", "公众端评价"));
        all.put("eval_professionalism_sum", new MetricDef("eval_professionalism_sum", "专业性评分合计", "公众端评价"));
        all.put("eval_responsiveness_sum", new MetricDef("eval_responsiveness_sum", "响应速度评分合计", "公众端评价"));
        all.put("eval_quality_sum", new MetricDef("eval_quality_sum", "服务质量评分合计", "公众端评价"));
        ALL_METRICS = Collections.unmodifiableMap(all);

        Map<String, MetricDef> dim = new LinkedHashMap<>();
        dim.put("transfer_total", new MetricDef("transfer_total", "条线转办量", "维度专属"));
        dim.put("closed_count", new MetricDef("closed_count", "条线办结量", "维度专属"));
        dim.put("close_sec_sum", new MetricDef("close_sec_sum", "条线办结时长合计(秒)", "维度专属"));
        dim.put("session_total", new MetricDef("session_total", "渠道会话量", "维度专属"));
        DIM_METRICS = Collections.unmodifiableMap(dim);

        Map<String, DimDef> dims = new LinkedHashMap<>();
        dims.put("agent", new DimDef("agent", "坐席",
                new LinkedHashSet<>(java.util.Arrays.asList("call_total", "call_answered", "answered_duration_sum")), true));
        dims.put("category", new DimDef("category", "咨询分类",
                new LinkedHashSet<>(Collections.singletonList("call_total")), true));
        dims.put("lang", new DimDef("lang", "语种",
                new LinkedHashSet<>(Collections.singletonList("call_total")), true));
        dims.put("line", new DimDef("line", "条线转办",
                new LinkedHashSet<>(java.util.Arrays.asList("transfer_total", "closed_count", "close_sec_sum")), false));
        dims.put("channel", new DimDef("channel", "渠道会话",
                new LinkedHashSet<>(Collections.singletonList("session_total")), false));
        DIMENSIONS = Collections.unmodifiableMap(dims);
    }

    /** 全部已知指标键（全局 + 维度专属） */
    public static boolean isKnownMetric(String key)
    {
        return ALL_METRICS.containsKey(key) || DIM_METRICS.containsKey(key);
    }

    private AdHocStatCatalog()
    {
    }
}
