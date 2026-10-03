package ai.lawyers.system.task;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import ai.lawyers.system.domain.lawyers.stat.AiStatMinute;
import ai.lawyers.system.mapper.lawyers.stat.AiStatMinuteMapper;
import ai.lawyers.system.service.lawyers.cluster.RedisLeaderLock;

/**
 * 分钟级物化统计任务（P3-F3）。
 *
 * <p>每分钟（第 30 秒）聚合<b>上一完整分钟</b>的指标写入 {@code ai_stat_minute}。
 * 指标分为 {@code ALL} 全局指标与多维度行（{@code agent:xx/category:xx/lang:xx/line:xx/channel:xx}）。</p>
 *
 * <p><b>ALL 全局指标（30 个）：</b></p>
 * <ul>
 *   <li>呼叫（ai_call_record）：call_total/call_answered/call_missed/call_transferred
 *       （status 1接通/2转接/3未接，口径与大屏一致）+ answered_duration_sum（接通通话时长合计，秒）；</li>
 *   <li>外呼（ai_call_dial_log，create_time）：outbound_total + queue_wait_ms_sum/queue_wait_count
 *       （仅统计 queue_wait_ms 非空）；</li>
 *   <li>排队 SLA（ai_call_queue，enqueue_time）：queue_total/queue_abandon（queue_status='3'）+
 *       queue_answered（='1'）/queue_answered_wait_sum（接听者 wait_duration 秒合计）/
 *       queue_within20（接听且 wait_duration&lt;=20 秒）；</li>
 *   <li>工单（ai_call_ticket，create_time）：ticket_total/ticket_closed（status in 2,3）+
 *       ticket_overdue（overtime_flag=1）/ticket_close_sec_sum（办结者建单→办结秒数合计）；</li>
 *   <li>质检（ai_quality_inspection，create_time）：quality_total + quality_score_sum/quality_score_count
 *       （total_score 非空才计入）+ quality_reviewed（review_status='1'）/quality_pending（'0'/null）/
 *       quality_risk（risk_warning_id 非空）；</li>
 *   <li>话单满意度（ai_call_ledger，del_flag='0'）：satisfaction_score_sum/satisfaction_count
 *       （分值映射 1→100/2→80/3→60/4→20）；</li>
 *   <li>公众端评价（ai_user_evaluation）：eval_total + eval_overall_sum/eval_professionalism_sum/
 *       eval_responsiveness_sum/eval_quality_sum。</li>
 * </ul>
 *
 * <p><b>维度行（仅输出区间内有活动的维度，避免无界膨胀）：</b></p>
 * <ul>
 *   <li>{@code agent:坐席id}：call_total/call_answered/answered_duration_sum；</li>
 *   <li>{@code category:分类id}（未分类=NONE）：call_total；</li>
 *   <li>{@code lang:语种代码}：call_total；</li>
 *   <li>{@code line:条线代码}（ai_ticket_transfer direction='OUT'）：transfer_total/closed_count/close_sec_sum；</li>
 *   <li>{@code channel:渠道代码}（ai_unified_session）：session_total。</li>
 * </ul>
 *
 * <p>幂等：唯一键 uk_stat(stat_time, dimension, metric_key) 冲突覆盖写，
 * 同一分钟重复聚合结果一致；多实例经 {@link RedisLeaderLock} 保证单实例执行。</p>
 *
 * <p>P0-2 起大屏与 D6 报表的可加指标统一读本表（call.dashboard.preagg-enabled 默认开启），
 * 依赖 count(distinct)/当前快照/状态流水切分的指标仍走实时；历史区间由
 * /lawyers/report/stat/backfill 手工回填。</p>
 *
 * @author ai-lawyers
 */
@Component
public class StatMinuteScheduleTask
{
    private static final Logger log = LoggerFactory.getLogger(StatMinuteScheduleTask.class);

    /** 集群单主锁名（TTL 2 分钟，大于单轮聚合最坏耗时） */
    private static final String LOCK_NAME = "job:stat-minute";
    private static final Duration LOCK_TTL = Duration.ofMinutes(2);

    private static final DateTimeFormatter MINUTE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:00");

    @Autowired
    private RedisLeaderLock leaderLock;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private AiStatMinuteMapper aiStatMinuteMapper;

    /** 总开关：仅写物化表不影响在线业务，默认开启 */
    @Value("${stat.minute.enabled:true}")
    private boolean enabled;

    /** 每分钟第 30 秒聚合上一完整分钟 */
    @Scheduled(cron = "30 * * * * ?")
    public void aggregateLastMinute()
    {
        if (!enabled)
        {
            return;
        }
        LocalDateTime minuteStart = LocalDateTime.now().minusMinutes(1).withSecond(0).withNano(0);
        leaderLock.tryRun(LOCK_NAME, LOCK_TTL, () -> doAggregate(minuteStart));
    }

    /**
     * 聚合指定分钟（分钟内的秒/纳秒会被归零）。供定时任务与手工回填共用。
     *
     * @return 写入指标条数
     */
    public int aggregateMinute(LocalDateTime minute)
    {
        LocalDateTime minuteStart = minute.withSecond(0).withNano(0);
        return doAggregate(minuteStart);
    }

    private int doAggregate(LocalDateTime minuteStart)
    {
        String begin = minuteStart.format(MINUTE_FMT);
        String end = minuteStart.plusMinutes(1).format(MINUTE_FMT);
        Date statTime = Date.from(minuteStart.atZone(ZoneId.systemDefault()).toInstant());

        List<AiStatMinute> rows = new ArrayList<>();

        // ---------------- ALL：呼叫 ----------------
        Map<String, Object> call = jdbcTemplate.queryForMap(
                "select count(*) as total,"
              + " sum(case when status = '1' then 1 else 0 end) as answered,"
              + " sum(case when status = '3' then 1 else 0 end) as missed,"
              + " sum(case when status = '2' then 1 else 0 end) as transferred,"
              + " sum(case when status = '1' then ifnull(duration, 0) else 0 end) as durationSum"
              + " from ai_call_record where call_time >= ? and call_time < ?", begin, end);
        rows.add(build(statTime, "ALL", "call_total", call.get("total")));
        rows.add(build(statTime, "ALL", "call_answered", call.get("answered")));
        rows.add(build(statTime, "ALL", "call_missed", call.get("missed")));
        rows.add(build(statTime, "ALL", "call_transferred", call.get("transferred")));
        rows.add(build(statTime, "ALL", "answered_duration_sum", call.get("durationSum")));

        // ---------------- ALL：外呼 + 排队等待毫秒（dial_log，create_time） ----------------
        Map<String, Object> dial = jdbcTemplate.queryForMap(
                "select count(*) as total,"
              + " sum(case when queue_wait_ms is not null then queue_wait_ms else 0 end) as waitSum,"
              + " sum(case when queue_wait_ms is not null then 1 else 0 end) as waitCount"
              + " from ai_call_dial_log where create_time >= ? and create_time < ?", begin, end);
        rows.add(build(statTime, "ALL", "outbound_total", dial.get("total")));
        rows.add(build(statTime, "ALL", "queue_wait_ms_sum", dial.get("waitSum")));
        rows.add(build(statTime, "ALL", "queue_wait_count", dial.get("waitCount")));

        // ---------------- ALL：排队 SLA（queue，enqueue_time，wait_duration 单位秒） ----------------
        Map<String, Object> queue = jdbcTemplate.queryForMap(
                "select count(*) as total,"
              + " sum(case when queue_status = '3' then 1 else 0 end) as abandon,"
              + " sum(case when queue_status = '1' then 1 else 0 end) as answered,"
              + " sum(case when queue_status = '1' then ifnull(wait_duration, 0) else 0 end) as waitSum,"
              + " sum(case when queue_status = '1' and wait_duration is not null"
              + "          and wait_duration <= 20 then 1 else 0 end) as within20"
              + " from ai_call_queue where enqueue_time >= ? and enqueue_time < ?", begin, end);
        rows.add(build(statTime, "ALL", "queue_total", queue.get("total")));
        rows.add(build(statTime, "ALL", "queue_abandon", queue.get("abandon")));
        rows.add(build(statTime, "ALL", "queue_answered", queue.get("answered")));
        rows.add(build(statTime, "ALL", "queue_answered_wait_sum", queue.get("waitSum")));
        rows.add(build(statTime, "ALL", "queue_within20", queue.get("within20")));

        // ---------------- ALL：工单（含超时、办结时长秒合计） ----------------
        Map<String, Object> ticket = jdbcTemplate.queryForMap(
                "select count(*) as total,"
              + " sum(case when status in ('2', '3') then 1 else 0 end) as closed,"
              + " sum(case when overtime_flag = 1 then 1 else 0 end) as overdue,"
              + " sum(case when status in ('2', '3') and close_time is not null"
              + "          then timestampdiff(SECOND, create_time, close_time) else 0 end) as closeSecSum"
              + " from ai_call_ticket where create_time >= ? and create_time < ?", begin, end);
        rows.add(build(statTime, "ALL", "ticket_total", ticket.get("total")));
        rows.add(build(statTime, "ALL", "ticket_closed", ticket.get("closed")));
        rows.add(build(statTime, "ALL", "ticket_overdue", ticket.get("overdue")));
        rows.add(build(statTime, "ALL", "ticket_close_sec_sum", ticket.get("closeSecSum")));

        // ---------------- ALL：质检 ----------------
        Map<String, Object> quality = jdbcTemplate.queryForMap(
                "select count(*) as total,"
              + " sum(case when total_score is not null then total_score else 0 end) as scoreSum,"
              + " sum(case when total_score is not null then 1 else 0 end) as scoreCount,"
              + " sum(case when review_status = '1' then 1 else 0 end) as reviewed,"
              + " sum(case when review_status = '0' or review_status is null then 1 else 0 end) as pending,"
              + " sum(case when risk_warning_id is not null then 1 else 0 end) as risk"
              + " from ai_quality_inspection where create_time >= ? and create_time < ?", begin, end);
        rows.add(build(statTime, "ALL", "quality_total", quality.get("total")));
        rows.add(build(statTime, "ALL", "quality_score_sum", quality.get("scoreSum")));
        rows.add(build(statTime, "ALL", "quality_score_count", quality.get("scoreCount")));
        rows.add(build(statTime, "ALL", "quality_reviewed", quality.get("reviewed")));
        rows.add(build(statTime, "ALL", "quality_pending", quality.get("pending")));
        rows.add(build(statTime, "ALL", "quality_risk", quality.get("risk")));

        // ---------------- ALL：话单满意度（分值映射后求和/计数） ----------------
        Map<String, Object> sat = jdbcTemplate.queryForMap(
                "select sum(case when satisfaction = '1' then 100"
              + "            when satisfaction = '2' then 80"
              + "            when satisfaction = '3' then 60"
              + "            when satisfaction = '4' then 20 end) as scoreSum,"
              + "       sum(case when satisfaction in ('1','2','3','4') then 1 else 0 end) as cnt"
              + " from ai_call_ledger"
              + " where del_flag = '0' and create_time >= ? and create_time < ?", begin, end);
        rows.add(build(statTime, "ALL", "satisfaction_score_sum", sat.get("scoreSum")));
        rows.add(build(statTime, "ALL", "satisfaction_count", sat.get("cnt")));

        // ---------------- ALL：公众端图文评价 ----------------
        Map<String, Object> eval = jdbcTemplate.queryForMap(
                "select count(*) as total,"
              + " sum(ifnull(overall_rating, 0)) as overallSum,"
              + " sum(ifnull(professionalism_rating, 0)) as professionalismSum,"
              + " sum(ifnull(responsiveness_rating, 0)) as responsivenessSum,"
              + " sum(ifnull(quality_rating, 0)) as qualitySum"
              + " from ai_user_evaluation where create_time >= ? and create_time < ?", begin, end);
        rows.add(build(statTime, "ALL", "eval_total", eval.get("total")));
        rows.add(build(statTime, "ALL", "eval_overall_sum", eval.get("overallSum")));
        rows.add(build(statTime, "ALL", "eval_professionalism_sum", eval.get("professionalismSum")));
        rows.add(build(statTime, "ALL", "eval_responsiveness_sum", eval.get("responsivenessSum")));
        rows.add(build(statTime, "ALL", "eval_quality_sum", eval.get("qualitySum")));

        // ---------------- 维度：坐席（call_record.agent_id 非空） ----------------
        List<Map<String, Object>> agents = jdbcTemplate.queryForList(
                "select agent_id as dim,"
              + " count(*) as total,"
              + " sum(case when status = '1' then 1 else 0 end) as answered,"
              + " sum(case when status = '1' then ifnull(duration, 0) else 0 end) as durationSum"
              + " from ai_call_record where call_time >= ? and call_time < ? and agent_id is not null"
              + " group by agent_id", begin, end);
        for (Map<String, Object> r : agents)
        {
            String dim = "agent:" + r.get("dim");
            rows.add(build(statTime, dim, "call_total", r.get("total")));
            rows.add(build(statTime, dim, "call_answered", r.get("answered")));
            rows.add(build(statTime, dim, "answered_duration_sum", r.get("durationSum")));
        }

        // ---------------- 维度：咨询分类（未分类记 NONE） ----------------
        List<Map<String, Object>> categories = jdbcTemplate.queryForList(
                "select ifnull(category_id, -1) as dim, count(*) as total"
              + " from ai_call_record where call_time >= ? and call_time < ?"
              + " group by category_id", begin, end);
        for (Map<String, Object> r : categories)
        {
            Object id = r.get("dim");
            String dim = "category:" + (id == null || "-1".equals(String.valueOf(id)) ? "NONE" : id);
            rows.add(build(statTime, dim, "call_total", r.get("total")));
        }

        // ---------------- 维度：语种（无档案记 zh-CN） ----------------
        List<Map<String, Object>> langs = jdbcTemplate.queryForList(
                "select ifnull(p.language_preference, 'zh-CN') as dim, count(*) as total"
              + " from ai_call_record cr"
              + " left join ai_caller_profile p on p.caller_number = cr.caller_number"
              + " where cr.call_time >= ? and cr.call_time < ?"
              + " group by ifnull(p.language_preference, 'zh-CN')", begin, end);
        for (Map<String, Object> r : langs)
        {
            rows.add(build(statTime, "lang:" + r.get("dim"), "call_total", r.get("total")));
        }

        // ---------------- 维度：条线转办（OUT） ----------------
        List<Map<String, Object>> lines = jdbcTemplate.queryForList(
                "select t.external_type as dim,"
              + " count(*) as total,"
              + " sum(case when tk.status in ('2','3') then 1 else 0 end) as closed,"
              + " sum(case when tk.status in ('2','3') and tk.close_time is not null"
              + "          then timestampdiff(SECOND, t.create_time, tk.close_time) else 0 end) as closeSecSum"
              + " from ai_ticket_transfer t"
              + " left join ai_call_ticket tk on tk.ticket_id = t.ticket_id"
              + " where t.direction = 'OUT' and t.create_time >= ? and t.create_time < ?"
              + " group by t.external_type", begin, end);
        for (Map<String, Object> r : lines)
        {
            String dim = "line:" + r.get("dim");
            rows.add(build(statTime, dim, "transfer_total", r.get("total")));
            rows.add(build(statTime, dim, "closed_count", r.get("closed")));
            rows.add(build(statTime, dim, "close_sec_sum", r.get("closeSecSum")));
        }

        // ---------------- 维度：公众端渠道会话 ----------------
        List<Map<String, Object>> channels = jdbcTemplate.queryForList(
                "select channel_type as dim, count(*) as total"
              + " from ai_unified_session where start_time >= ? and start_time < ?"
              + " group by channel_type", begin, end);
        for (Map<String, Object> r : channels)
        {
            rows.add(build(statTime, "channel:" + r.get("dim"), "session_total", r.get("total")));
        }

        aiStatMinuteMapper.batchUpsert(rows);
        log.debug("分钟级物化聚合完成 statTime={} rows={}", begin, rows.size());
        return rows.size();
    }

    private AiStatMinute build(Date statTime, String dimension, String metricKey, Object value)
    {
        AiStatMinute stat = new AiStatMinute();
        stat.setStatTime(statTime);
        stat.setDimension(dimension);
        stat.setMetricKey(metricKey);
        stat.setMetricValue(value == null ? BigDecimal.ZERO : new BigDecimal(String.valueOf(value)));
        return stat;
    }
}
