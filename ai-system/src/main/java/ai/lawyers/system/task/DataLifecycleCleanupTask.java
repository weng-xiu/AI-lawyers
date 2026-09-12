package ai.lawyers.system.task;

import java.io.File;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import ai.lawyers.system.service.lawyers.cluster.RedisLeaderLock;

/**
 * 数据生命周期定时清理（N12）。
 *
 * <p>政务热线长期运行后，拨号流水、IVR 执行日志、坐席状态流水、短信日志等热表持续膨胀，
 * 本任务按各表保留期（月）分批物理删除超期数据；话单表 {@code ai_call_record} 清理时
 * 同步删除磁盘录音文件。所有被清理表名/时间列均为代码内白名单常量，不接受外部拼入。</p>
 *
 * <p><b>三重保险，默认安全：</b></p>
 * <ol>
 *   <li>{@code data.retention.enabled=false}（默认）：任务直接跳过，任何环境升级后零行为变化；</li>
 *   <li>{@code data.retention.dry-run=true}（默认）：开启任务后先只统计与打印将删除的数据/文件，
 *       不执行任何 DELETE/文件删除，运维核对日志后再显式置 false；</li>
 *   <li>分批删除（{@code batch-size} / {@code max-batches}），避免大事务长时间锁表；
 *       多实例经 {@link RedisLeaderLock} 保证全组单次仅一个实例执行。</li>
 * </ol>
 *
 * <p>保留期应遵循当地 12345/12348 政务热线数据与录音归档规定后再调整；
 * 需要长期归档的场景优先使用 DBA 分区/归档方案（见 sql/ai_system_data_lifecycle_20260912.sql）。</p>
 *
 * @author ai-lawyers
 */
@Component
public class DataLifecycleCleanupTask
{
    private static final Logger log = LoggerFactory.getLogger(DataLifecycleCleanupTask.class);

    /** 集群单主锁名（TTL 30 分钟，须大于整轮清理最坏耗时） */
    private static final String LOCK_NAME = "job:data-lifecycle";
    private static final Duration LOCK_TTL = Duration.ofMinutes(30);

    // ---- 白名单表与时间列（代码内固定，禁止外部配置表名，杜绝 SQL 注入） ----
    private static final String T_CALL_DIAL_LOG = "ai_call_dial_log";
    private static final String T_AGENT_STATUS_LOG = "ai_agent_status_log";
    private static final String T_SMS_LOG = "ai_sms_log";
    private static final String T_VIDEO_CONSULT_LOG = "ai_video_consult_log";
    private static final String T_IVR_EXECUTION_LOG = "ai_ivr_execution_log";
    private static final String T_IVR_INTENTION_LOG = "ai_ivr_intention_log";
    private static final String T_CALL_RECORD = "ai_call_record";

    @Autowired
    private RedisLeaderLock leaderLock;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    /** 总开关：默认关闭，须经合规确认保留期后显式开启 */
    @Value("${data.retention.enabled:false}")
    private boolean enabled;

    /** 试运行：默认只打印将删除内容不真正删除，首次上线建议先观察至少一轮日志 */
    @Value("${data.retention.dry-run:true}")
    private boolean dryRun;

    /** 单批删除行数（小批量、短事务） */
    @Value("${data.retention.batch-size:1000}")
    private int batchSize;

    /** 单表单轮最多删除批次数（防止一次清理过多影响在线业务，剩余次日继续） */
    @Value("${data.retention.max-batches:100}")
    private int maxBatches;

    /** 拨号流水（含排队/SIP 挂断明细，增长最快）保留月数 */
    @Value("${data.retention.dial-log-months:3}")
    private int dialLogMonths;

    /** 坐席状态变更流水保留月数 */
    @Value("${data.retention.agent-status-log-months:6}")
    private int agentStatusLogMonths;

    /** 短信发送日志保留月数 */
    @Value("${data.retention.sms-log-months:6}")
    private int smsLogMonths;

    /** 视频咨询事件日志保留月数 */
    @Value("${data.retention.video-consult-log-months:6}")
    private int videoConsultLogMonths;

    /** IVR 流程执行日志保留月数（排障用，增长快，保留期短） */
    @Value("${data.retention.ivr-execution-log-months:1}")
    private int ivrExecutionLogMonths;

    /** IVR 意图识别日志保留月数 */
    @Value("${data.retention.ivr-intention-log-months:1}")
    private int ivrIntentionLogMonths;

    /**
     * 话单及录音保留月数。政务热线录音通常要求保存较长时间，默认 36 个月；
     * 调整前必须确认当地 12345/12348 档案与合规规定。
     */
    @Value("${data.retention.call-record-months:36}")
    private int callRecordMonths;

    /** 清理话单时是否同步删除磁盘录音文件（false 时仅删数据库行，文件另行归档） */
    @Value("${data.retention.delete-recording-file:true}")
    private boolean deleteRecordingFile;

    /** 录音文件基础目录（相对路径以此解析），复用 call.recording.base-path */
    @Value("${call.recording.base-path:}")
    private String recordingBasePath;

    /**
     * 每日凌晨低峰执行（默认 03:30），可通过 data.retention.cron 调整。
     */
    @Scheduled(cron = "${data.retention.cron:0 30 3 * * ?}")
    public void runCleanup()
    {
        if (!enabled)
        {
            return;
        }
        leaderLock.tryRun(LOCK_NAME, LOCK_TTL, this::doCleanup);
    }

    /**
     * 执行一轮全量表清理（包级可见便于单测）。
     */
    void doCleanup()
    {
        long start = System.currentTimeMillis();
        log.info("[Lifecycle] 数据清理开始{}", dryRun ? "（DRY-RUN 试运行，不实际删除）" : "");
        List<TableSpec> specs = buildSpecs();
        long totalRows = 0;
        for (TableSpec spec : specs)
        {
            try
            {
                Timestamp cutoff = Timestamp.valueOf(
                        LocalDate.now().minusMonths(spec.months).atStartOfDay());
                long rows = spec.deleteFiles
                        ? cleanupCallRecords(cutoff)
                        : cleanupGenericTable(spec, cutoff);
                totalRows += rows;
            }
            catch (Exception e)
            {
                // 单表失败不影响其余表清理
                log.error("[Lifecycle] 清理表失败 table={}", spec.table, e);
            }
        }
        log.info("[Lifecycle] 数据清理结束，累计处理 {} 行，耗时 {} ms，dryRun={}",
                totalRows, System.currentTimeMillis() - start, dryRun);
    }

    private List<TableSpec> buildSpecs()
    {
        List<TableSpec> specs = new ArrayList<>();
        specs.add(new TableSpec(T_IVR_EXECUTION_LOG, "create_time", ivrExecutionLogMonths, false));
        specs.add(new TableSpec(T_IVR_INTENTION_LOG, "create_time", ivrIntentionLogMonths, false));
        specs.add(new TableSpec(T_CALL_DIAL_LOG, "create_time", dialLogMonths, false));
        specs.add(new TableSpec(T_AGENT_STATUS_LOG, "create_time", agentStatusLogMonths, false));
        specs.add(new TableSpec(T_SMS_LOG, "create_time", smsLogMonths, false));
        specs.add(new TableSpec(T_VIDEO_CONSULT_LOG, "create_time", videoConsultLogMonths, false));
        // 话单最后清理（其它流水逻辑上引用 record_id，先删流水再删话单）
        specs.add(new TableSpec(T_CALL_RECORD, "create_time", callRecordMonths, true));
        return specs;
    }

    /**
     * 普通日志表：按时间列分批 DELETE ... LIMIT。
     *
     * @return 本轮处理（或试运行预计）行数
     */
    long cleanupGenericTable(TableSpec spec, Timestamp cutoff)
    {
        String sql = "delete from " + spec.table + " where " + spec.timeColumn + " < ? limit ?";
        long total = 0;
        for (int batch = 0; batch < maxBatches; batch++)
        {
            if (dryRun)
            {
                // 试运行用同条件 count 一次性估算，不分批
                Long cnt = jdbcTemplate.queryForObject(
                        "select count(1) from " + spec.table + " where " + spec.timeColumn + " < ?",
                        Long.class, cutoff);
                long estimate = cnt == null ? 0 : Math.min(cnt, (long) maxBatches * batchSize);
                log.info("[Lifecycle][DRY] {} 将清理约 {} 行（保留 {} 个月）", spec.table, estimate, spec.months);
                return estimate;
            }
            int affected = jdbcTemplate.update(sql, cutoff, batchSize);
            total += affected;
            if (affected < batchSize)
            {
                break;
            }
        }
        log.info("[Lifecycle] {} 清理完成，删除 {} 行（保留 {} 个月）", spec.table, total, spec.months);
        return total;
    }

    /**
     * 话单表：先取本批待删话单的录音文件路径，删文件后按主键删行。
     */
    long cleanupCallRecords(Timestamp cutoff)
    {
        long total = 0;
        long filesDeleted = 0;
        for (int batch = 0; batch < maxBatches; batch++)
        {
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                    "select record_id, record_file from " + T_CALL_RECORD
                            + " where create_time < ? order by record_id limit ?",
                    cutoff, batchSize);
            if (rows.isEmpty())
            {
                break;
            }
            List<Long> ids = new ArrayList<>(rows.size());
            for (Map<String, Object> row : rows)
            {
                Long id = ((Number) row.get("record_id")).longValue();
                ids.add(id);
                Object fileVal = row.get("record_file");
                if (deleteRecordingFile && fileVal != null && !dryRun)
                {
                    if (deleteRecordingFile(fileVal.toString()))
                    {
                        filesDeleted++;
                    }
                }
                else if (deleteRecordingFile && fileVal != null && dryRun)
                {
                    log.info("[Lifecycle][DRY] {} 将删除录音：{}", T_CALL_RECORD, fileVal);
                }
            }
            if (!dryRun)
            {
                String placeholders = buildPlaceholders(ids.size());
                jdbcTemplate.update("delete from " + T_CALL_RECORD + " where record_id in (" + placeholders + ")",
                        ids.toArray());
            }
            total += ids.size();
            if (ids.size() < batchSize)
            {
                break;
            }
        }
        log.info("[Lifecycle] {} 清理完成，{} {} 行，删除录音文件 {} 个（保留 {} 个月）",
                T_CALL_RECORD, dryRun ? "预计清理" : "删除", total, filesDeleted, callRecordMonths);
        return total;
    }

    private boolean deleteRecordingFile(String storedPath)
    {
        File file = resolveRecordingFile(recordingBasePath, storedPath);
        if (file == null || !file.exists())
        {
            log.warn("[Lifecycle] 录音文件不存在，跳过：{}", storedPath);
            return false;
        }
        if (!file.isFile())
        {
            log.warn("[Lifecycle] 录音路径不是普通文件，跳过：{}", file.getAbsolutePath());
            return false;
        }
        if (file.delete())
        {
            return true;
        }
        log.warn("[Lifecycle] 录音文件删除失败（权限/文件锁）：{}", file.getAbsolutePath());
        return false;
    }

    /**
     * 解析录音文件物理路径：存储为绝对路径时直接使用，相对路径拼接录音基础目录。
     * 包级静态，便于单测。
     */
    static File resolveRecordingFile(String basePath, String storedPath)
    {
        if (storedPath == null || storedPath.trim().isEmpty())
        {
            return null;
        }
        File stored = new File(storedPath);
        if (stored.isAbsolute())
        {
            return stored;
        }
        if (basePath == null || basePath.trim().isEmpty())
        {
            return null;
        }
        return new File(basePath, storedPath);
    }

    private static String buildPlaceholders(int count)
    {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < count; i++)
        {
            if (i > 0)
            {
                sb.append(',');
            }
            sb.append('?');
        }
        return sb.toString();
    }

    /** 清理表描述（白名单结构） */
    static class TableSpec
    {
        final String table;
        final String timeColumn;
        final int months;
        final boolean deleteFiles;

        TableSpec(String table, String timeColumn, int months, boolean deleteFiles)
        {
            this.table = table;
            this.timeColumn = timeColumn;
            this.months = months;
            this.deleteFiles = deleteFiles;
        }
    }
}
