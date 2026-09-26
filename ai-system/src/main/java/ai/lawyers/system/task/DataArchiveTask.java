package ai.lawyers.system.task;

import java.io.File;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import ai.lawyers.system.service.lawyers.cluster.RedisLeaderLock;

/**
 * 话单冷热分离归档任务（P3-F1）。
 *
 * <p>将热表 {@code ai_call_record} 中超过在线保留期（{@code data.archive.keep-months}，
 * 默认 12 个月）的话单分批迁移至按月分区的归档表 {@code ai_call_record_archive}
 * （DDL 见 sql/ai_system_f1_archive_partition_20260926.sql），归档表历史可查
 * （只读查询接口 /lawyers/trunk/callArchive），到期分区由 DBA 按合规保留期
 * DROP PARTITION 回收（脚本内提供操作模板）。</p>
 *
 * <p><b>与 N12 清理任务的分工：</b>归档开启后，超龄话单先于 N12 的 36 个月阈值
 * 移出热表，N12 对话单自然命中 0 行；录音文件随 F2 对象存储化管理。过渡期如需
 * 清理"已归档但仍在磁盘"的过期录音文件，可配置
 * {@code data.archive.delete-recording-file-months}（默认 0 关闭）：把归档表中
 * 超过该月数的话单录音文件删除并置空 record_file 字段（数据库行保留）。</p>
 *
 * <p><b>批量与崩溃恢复：</b>每批按 create_time 取 record_id → 查归档表已存在部分
 * （上次崩溃介于 INSERT 与 DELETE 之间的残留）→ 仅对缺失部分 INSERT → 校验
 * 插入行数一致后才按 id 删除热表行；任何一步不一致立即终止本轮并告警，宁可
 * 下轮重做也不丢数据。迁移期间热表业务照常（仅短事务按主键删行）。</p>
 *
 * <p><b>三重保险，默认安全：</b>{@code data.archive.enabled=false}（默认）不执行；
 * {@code data.archive.dry-run=true}（默认）只统计与打印；多实例经
 * {@link RedisLeaderLock} 单主执行，分批限量避免长事务。</p>
 *
 * @author ai-lawyers
 */
@Component
public class DataArchiveTask
{
    private static final Logger log = LoggerFactory.getLogger(DataArchiveTask.class);

    /** 集群单主锁名（TTL 30 分钟，须大于整轮归档最坏耗时） */
    private static final String LOCK_NAME = "job:data-archive";
    private static final Duration LOCK_TTL = Duration.ofMinutes(30);

    /** 热表（代码内白名单，禁止外部配置） */
    private static final String HOT_TABLE = "ai_call_record";
    /** 归档表（按月 RANGE 分区，结构与热表一致，主键含 create_time） */
    private static final String ARCHIVE_TABLE = "ai_call_record_archive";

    @Autowired
    private RedisLeaderLock leaderLock;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    /** 总开关：默认关闭，须先执行归档表 DDL 并经合规确认保留期后显式开启 */
    @Value("${data.archive.enabled:false}")
    private boolean enabled;

    /** 试运行：只统计与打印将归档的数据，不执行 INSERT/DELETE/文件清理 */
    @Value("${data.archive.dry-run:true}")
    private boolean dryRun;

    /** 单批迁移行数（小批量、短事务） */
    @Value("${data.archive.batch-size:500}")
    private int batchSize;

    /** 单轮最多迁移批次数（剩余次日继续，避免长时间占用 IO） */
    @Value("${data.archive.max-batches:200}")
    private int maxBatches;

    /** 话单在线保留月数（热表只保留近 N 个月，更早迁移归档；默认 12 个月） */
    @Value("${data.archive.keep-months:12}")
    private int keepMonths;

    /**
     * 已归档话单的录音文件到期清理月数（0=关闭，默认）。
     * 设为正数（如 36）时，删除归档表中超过该月数话单的磁盘录音文件并置空
     * record_file 字段，数据库行保留。开启归档后 N12 不再触达这些行，
     * 文件清理职责由此项接管；F2 对象存储化后建议关闭改用生命周期策略。
     */
    @Value("${data.archive.delete-recording-file-months:0}")
    private int deleteRecordingFileMonths;

    /** 录音文件基础目录（相对路径以此解析），复用 call.recording.base-path */
    @Value("${call.recording.base-path:}")
    private String recordingBasePath;

    /**
     * 每日凌晨低峰执行（默认 03:40，在 N12 清理 03:30 之后），可通过 data.archive.cron 调整。
     */
    @Scheduled(cron = "${data.archive.cron:0 40 3 * * ?}")
    public void runArchive()
    {
        if (!enabled)
        {
            return;
        }
        leaderLock.tryRun(LOCK_NAME, LOCK_TTL, this::doArchive);
    }

    /**
     * 执行一轮归档（包级可见便于单测）。
     */
    void doArchive()
    {
        long start = System.currentTimeMillis();
        try
        {
            long rows = archiveCallRecords();
            long files = cleanupArchivedRecordingFiles();
            log.info("[Archive] 归档结束，迁移 {} 行，录音文件清理 {} 个，耗时 {} ms，dryRun={}",
                    rows, files, System.currentTimeMillis() - start, dryRun);
        }
        catch (Exception e)
        {
            log.error("[Archive] 归档任务异常终止", e);
        }
    }

    /**
     * 话单热表 → 归档表分批迁移。
     *
     * @return 本轮迁移（或试运行预计）行数
     */
    long archiveCallRecords()
    {
        Timestamp cutoff = Timestamp.valueOf(
                LocalDate.now().minusMonths(keepMonths).atStartOfDay());
        if (dryRun)
        {
            Long cnt = jdbcTemplate.queryForObject(
                    "select count(1) from " + HOT_TABLE + " where create_time < ?", Long.class, cutoff);
            long estimate = cnt == null ? 0 : Math.min(cnt, (long) maxBatches * batchSize);
            log.info("[Archive][DRY] {} 将归档约 {} 行至 {}（在线保留 {} 个月）",
                    HOT_TABLE, estimate, ARCHIVE_TABLE, keepMonths);
            return estimate;
        }

        long total = 0;
        for (int batch = 0; batch < maxBatches; batch++)
        {
            List<Long> ids = jdbcTemplate.queryForList(
                    "select record_id from " + HOT_TABLE
                            + " where create_time < ? order by record_id limit ?",
                    Long.class, cutoff, batchSize);
            if (ids.isEmpty())
            {
                break;
            }
            // 崩溃恢复：上次 INSERT 后 DELETE 前中断的行，归档表已有，直接从热表删除即可
            Set<Long> alreadyArchived = new HashSet<>(jdbcTemplate.queryForList(
                    "select record_id from " + ARCHIVE_TABLE + " where record_id in ("
                            + placeholders(ids.size()) + ")",
                    Long.class, ids.toArray()));
            List<Long> toInsert = new ArrayList<>(ids.size());
            for (Long id : ids)
            {
                if (!alreadyArchived.contains(id))
                {
                    toInsert.add(id);
                }
            }
            if (!toInsert.isEmpty())
            {
                // 结构一致性由 INSERT ... SELECT * 的列数匹配强校验（归档表必须 LIKE 热表同构迁移）
                int inserted = jdbcTemplate.update(
                        "insert into " + ARCHIVE_TABLE + " select * from " + HOT_TABLE
                                + " where record_id in (" + placeholders(toInsert.size()) + ")",
                        toInsert.toArray());
                if (inserted != toInsert.size())
                {
                    // 行数不一致说明迁移期间有并发变更，保守终止本轮（已插入的不回滚，
                    // 下轮走 alreadyArchived 分支继续），避免误删未落归档的热表数据
                    log.error("[Archive] 插入行数 {} 与预期 {} 不一致，终止本轮归档（下轮自动续做）",
                            inserted, toInsert.size());
                    break;
                }
            }
            jdbcTemplate.update("delete from " + HOT_TABLE + " where record_id in ("
                    + placeholders(ids.size()) + ")", ids.toArray());
            total += ids.size();
            if (ids.size() < batchSize)
            {
                break;
            }
        }
        log.info("[Archive] {} → {} 迁移完成，{} 行（在线保留 {} 个月）",
                HOT_TABLE, ARCHIVE_TABLE, total, keepMonths);
        return total;
    }

    /**
     * 已归档话单的磁盘录音文件到期清理（delete-recording-file-months > 0 时生效）：
     * 删文件并把 record_file 置空（行保留），防止重复扫描。
     *
     * @return 本轮清理（或试运行预计）文件数
     */
    long cleanupArchivedRecordingFiles()
    {
        if (deleteRecordingFileMonths <= 0)
        {
            return 0;
        }
        Timestamp cutoff = Timestamp.valueOf(
                LocalDate.now().minusMonths(deleteRecordingFileMonths).atStartOfDay());
        long total = 0;
        for (int batch = 0; batch < maxBatches; batch++)
        {
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                    "select record_id, record_file from " + ARCHIVE_TABLE
                            + " where create_time < ? and record_file is not null"
                            + " order by record_id limit ?",
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
                if (fileVal != null && !dryRun)
                {
                    deleteRecordingFile(fileVal.toString());
                }
                else if (fileVal != null && dryRun)
                {
                    log.info("[Archive][DRY] {} 将删除归档录音：{}", ARCHIVE_TABLE, fileVal);
                }
            }
            if (!dryRun)
            {
                jdbcTemplate.update("update " + ARCHIVE_TABLE + " set record_file = null where record_id in ("
                        + placeholders(ids.size()) + ")", ids.toArray());
            }
            total += ids.size();
            if (ids.size() < batchSize)
            {
                break;
            }
        }
        log.info("[Archive] {} 归档录音到期清理完成（{} 个月），{} 个文件，dryRun={}",
                ARCHIVE_TABLE, deleteRecordingFileMonths, total, dryRun);
        return total;
    }

    private void deleteRecordingFile(String storedPath)
    {
        File file = DataLifecycleCleanupTask.resolveRecordingFile(recordingBasePath, storedPath);
        if (file == null || !file.exists() || !file.isFile())
        {
            log.warn("[Archive] 归档录音文件不存在或不可识别，跳过置空前先记录：{}", storedPath);
            // 仍置空字段避免死循环重扫；文件本身交给运维按日志处理
            return;
        }
        if (!file.delete())
        {
            log.warn("[Archive] 归档录音文件删除失败（权限/文件锁）：{}", file.getAbsolutePath());
        }
    }

    private static String placeholders(int count)
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
}
