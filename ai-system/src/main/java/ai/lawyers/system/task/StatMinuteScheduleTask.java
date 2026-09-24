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
 * <p>每分钟（第 30 秒）聚合<b>上一完整分钟</b>的核心指标写入 {@code ai_stat_minute}：
 * 呼叫（total/answered/missed/transferred，口径与大屏一致 status 1接通/2转接/3未接）
 * 与工单（total/closed，办结口径 status in('2','3') 与 SLA 看板一致），维度固定 ALL。</p>
 *
 * <p>幂等：唯一键 uk_stat(stat_time, dimension, metric_key) 冲突覆盖写，
 * 同一分钟重复聚合结果一致；多实例经 {@link RedisLeaderLock} 保证单实例执行。</p>
 *
 * <p>大屏与 D6 报表当前仍读实时聚合，待本表数据积累后仅切 Mapper 数据源（契约已预留）。</p>
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

        Map<String, Object> call = jdbcTemplate.queryForMap(
                "select count(*) as total,"
              + " sum(case when status = '1' then 1 else 0 end) as answered,"
              + " sum(case when status = '3' then 1 else 0 end) as missed,"
              + " sum(case when status = '2' then 1 else 0 end) as transferred"
              + " from ai_call_record where call_time >= ? and call_time < ?", begin, end);
        rows.add(build(statTime, "call_total", call.get("total")));
        rows.add(build(statTime, "call_answered", call.get("answered")));
        rows.add(build(statTime, "call_missed", call.get("missed")));
        rows.add(build(statTime, "call_transferred", call.get("transferred")));

        Map<String, Object> ticket = jdbcTemplate.queryForMap(
                "select count(*) as total,"
              + " sum(case when status in ('2', '3') then 1 else 0 end) as closed"
              + " from ai_call_ticket where create_time >= ? and create_time < ?", begin, end);
        rows.add(build(statTime, "ticket_total", ticket.get("total")));
        rows.add(build(statTime, "ticket_closed", ticket.get("closed")));

        aiStatMinuteMapper.batchUpsert(rows);
        log.debug("分钟级物化聚合完成 statTime={} rows={}", begin, rows.size());
        return rows.size();
    }

    private AiStatMinute build(Date statTime, String metricKey, Object value)
    {
        AiStatMinute stat = new AiStatMinute();
        stat.setStatTime(statTime);
        stat.setDimension("ALL");
        stat.setMetricKey(metricKey);
        stat.setMetricValue(value == null ? BigDecimal.ZERO : new BigDecimal(String.valueOf(value)));
        return stat;
    }
}
