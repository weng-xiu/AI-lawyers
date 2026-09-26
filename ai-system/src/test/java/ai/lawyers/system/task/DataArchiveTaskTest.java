package ai.lawyers.system.task;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mockito;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import ai.lawyers.system.service.lawyers.cluster.RedisLeaderLock;

/**
 * P3-F1：{@link DataArchiveTask} 话单冷热分离归档测试。
 *
 * <p>覆盖：开关与试运行双保险、分批迁移收敛、插入行数不一致保守终止
 * （宁重做不丢数据）、崩溃恢复（归档表已存在的行跳过插入仍删除热表行）、
 * 归档录音文件到期清理与置空。JdbcTemplate 的 varargs 方法（update/queryForList）
 * 一律采用与实参个数一致的逐元素匹配器，规避 Mockito 对可变参数的整体匹配歧义。</p>
 *
 * @author ai-lawyers
 */
class DataArchiveTaskTest
{
    private DataArchiveTask task;
    private RedisLeaderLock leaderLock;
    private JdbcTemplate jdbc;

    @TempDir
    File tempDir;

    @BeforeEach
    void setUp()
    {
        task = new DataArchiveTask();
        leaderLock = mock(RedisLeaderLock.class);
        jdbc = mock(JdbcTemplate.class);
        ReflectionTestUtils.setField(task, "leaderLock", leaderLock);
        ReflectionTestUtils.setField(task, "jdbcTemplate", jdbc);
        ReflectionTestUtils.setField(task, "enabled", true);
        ReflectionTestUtils.setField(task, "dryRun", false);
        ReflectionTestUtils.setField(task, "batchSize", 2);
        ReflectionTestUtils.setField(task, "maxBatches", 5);
        ReflectionTestUtils.setField(task, "keepMonths", 12);
        ReflectionTestUtils.setField(task, "deleteRecordingFileMonths", 0);
        doAnswer(inv ->
        {
            ((Runnable) inv.getArgument(2)).run();
            return true;
        }).when(leaderLock).tryRun(anyString(), any(), any(Runnable.class));
    }

    @Test
    void runArchive_disabled_doesNothing()
    {
        ReflectionTestUtils.setField(task, "enabled", false);

        task.runArchive();

        verify(leaderLock, never()).tryRun(anyString(), any(), any(Runnable.class));
    }

    @Test
    void archiveCallRecords_batchesUntilShortBatch()
    {
        // 第一批满批 2 行，第二批 1 行（短批收敛），共迁移 3 行
        when(jdbc.queryForList(contains("select record_id from ai_call_record where"),
                eq(Long.class), any(Timestamp.class), eq(2)))
                .thenReturn(Arrays.asList(1L, 2L), Collections.singletonList(3L));
        // 归档表无历史残留：两批的 IN 查询都返回空
        stubAlreadyArchived("(?,?)", eq(1L), eq(2L));
        stubAlreadyArchived("(?)", eq(3L));
        // 两批插入与删除
        when(jdbc.update(contains(insertSql("(?,?)")), eq(1L), eq(2L))).thenReturn(2);
        when(jdbc.update(contains(insertSql("(?)")), eq(3L))).thenReturn(1);
        when(jdbc.update(contains(deleteSql("(?,?)")), eq(1L), eq(2L))).thenReturn(2);
        when(jdbc.update(contains(deleteSql("(?)")), eq(3L))).thenReturn(1);

        long rows = task.archiveCallRecords();

        assertThat(rows).isEqualTo(3L);
        verify(jdbc).update(contains(insertSql("(?,?)")), eq(1L), eq(2L));
        verify(jdbc).update(contains(insertSql("(?)")), eq(3L));
        verify(jdbc).update(contains(deleteSql("(?,?)")), eq(1L), eq(2L));
        verify(jdbc).update(contains(deleteSql("(?)")), eq(3L));
    }

    @Test
    void archiveCallRecords_insertCountMismatch_abortsBeforeDelete()
    {
        // 预期插入 2 行实际只落 1 行（并发变更）→ 终止本轮且绝不删热表行（宁重做不丢数据）
        when(jdbc.queryForList(contains("select record_id from ai_call_record where"),
                eq(Long.class), any(Timestamp.class), eq(2)))
                .thenReturn(Arrays.asList(1L, 2L));
        stubAlreadyArchived("(?,?)", eq(1L), eq(2L));
        when(jdbc.update(contains(insertSql("(?,?)")), eq(1L), eq(2L))).thenReturn(1);

        long rows = task.archiveCallRecords();

        assertThat(rows).isZero();
        verify(jdbc, never()).update(contains(deleteSql("(?,?)")), eq(1L), eq(2L));
        verify(jdbc, never()).update(contains(deleteSql("(?)")), eq(1L));
        verify(jdbc, never()).update(contains(deleteSql("(?)")), eq(2L));
    }

    @Test
    void archiveCallRecords_alreadyArchived_skipsInsertStillDeletesHotRows()
    {
        // 崩溃恢复：上次 INSERT 后 DELETE 前中断，归档表已有 record_id=1 →
        // 仅插入缺失的 2，但 1、2 都要从热表删除
        when(jdbc.queryForList(contains("select record_id from ai_call_record where"),
                eq(Long.class), any(Timestamp.class), eq(2)))
                .thenReturn(Arrays.asList(1L, 2L), Collections.emptyList());
        when(jdbc.queryForList(contains(archiveInSql("(?,?)")),
                eq(Long.class), eq(1L), eq(2L))).thenReturn(Collections.singletonList(1L));
        when(jdbc.update(contains(insertSql("(?)")), eq(2L))).thenReturn(1);
        when(jdbc.update(contains(deleteSql("(?,?)")), eq(1L), eq(2L))).thenReturn(2);

        long rows = task.archiveCallRecords();

        assertThat(rows).isEqualTo(2L);
        // 插入仅缺失部分（不含 record_id=1）
        verify(jdbc).update(contains(insertSql("(?)")), eq(2L));
        verify(jdbc, never()).update(contains(insertSql("(?,?)")), eq(1L), eq(2L));
        // 热表删除覆盖全部已归档 id
        verify(jdbc).update(contains(deleteSql("(?,?)")), eq(1L), eq(2L));
    }

    @Test
    void archiveCallRecords_dryRun_onlyCountsNeverWrites()
    {
        ReflectionTestUtils.setField(task, "dryRun", true);
        when(jdbc.queryForObject(contains("select count(1) from ai_call_record"),
                eq(Long.class), any(Timestamp.class))).thenReturn(7L);

        long rows = task.archiveCallRecords();

        // 7 行 < maxBatches*batchSize=10 → 全量预估
        assertThat(rows).isEqualTo(7L);
        verify(jdbc, never()).update(anyString(), Mockito.<Object>any(), Mockito.<Object>any());
        verify(jdbc, never()).update(anyString(), Mockito.<Object>any());
    }

    @Test
    void cleanupArchivedRecordingFiles_disabledByDefault()
    {
        assertThat(task.cleanupArchivedRecordingFiles()).isZero();
        verify(jdbc, never()).queryForList(anyString(), Mockito.<Object>any(), Mockito.<Object>any());
    }

    @Test
    void cleanupArchivedRecordingFiles_deletesFileAndNullsColumn() throws Exception
    {
        File rec = new File(tempDir, "2023/old-rec.wav");
        rec.getParentFile().mkdirs();
        Files.write(rec.toPath(), "old-recording".getBytes(StandardCharsets.UTF_8));
        ReflectionTestUtils.setField(task, "deleteRecordingFileMonths", 36);
        ReflectionTestUtils.setField(task, "recordingBasePath", tempDir.getAbsolutePath());

        List<Map<String, Object>> rows = new ArrayList<>();
        Map<String, Object> row = new HashMap<>();
        row.put("record_id", 501L);
        row.put("record_file", "2023/old-rec.wav");
        rows.add(row);
        when(jdbc.queryForList(contains("record_file is not null"), any(Timestamp.class), eq(2)))
                .thenReturn(rows);
        when(jdbc.update(contains("set record_file = null where record_id in (?)"),
                eq(501L))).thenReturn(1);

        long files = task.cleanupArchivedRecordingFiles();

        assertThat(files).isEqualTo(1L);
        assertThat(rec).doesNotExist();
        verify(jdbc).update(contains("update ai_call_record_archive set record_file = null"),
                eq(501L));
    }

    @Test
    void cleanupArchivedRecordingFiles_dryRun_keepsFile() throws Exception
    {
        File rec = new File(tempDir, "keep.wav");
        Files.write(rec.toPath(), "keep".getBytes(StandardCharsets.UTF_8));
        ReflectionTestUtils.setField(task, "dryRun", true);
        ReflectionTestUtils.setField(task, "deleteRecordingFileMonths", 36);
        ReflectionTestUtils.setField(task, "recordingBasePath", tempDir.getAbsolutePath());

        List<Map<String, Object>> rows = new ArrayList<>();
        Map<String, Object> row = new HashMap<>();
        row.put("record_id", 502L);
        row.put("record_file", "keep.wav");
        rows.add(row);
        when(jdbc.queryForList(contains("record_file is not null"), any(Timestamp.class), eq(2)))
                .thenReturn(rows);

        long files = task.cleanupArchivedRecordingFiles();

        assertThat(files).isEqualTo(1L);
        assertThat(rec).exists();
        verify(jdbc, never()).update(anyString(), Mockito.<Object>any(), Mockito.<Object>any());
        verify(jdbc, never()).update(anyString(), Mockito.<Object>any());
    }

    // ===================== helpers =====================

    private void stubAlreadyArchived(String inClause, Object... idMatchers)
    {
        when(jdbc.queryForList(contains(archiveInSql(inClause)),
                eq(Long.class), idMatchers)).thenReturn(Collections.emptyList());
    }

    private String archiveInSql(String inClause)
    {
        return "select record_id from ai_call_record_archive where record_id in " + inClause;
    }

    private String insertSql(String inClause)
    {
        return "insert into ai_call_record_archive select * from ai_call_record where record_id in " + inClause;
    }

    private String deleteSql(String inClause)
    {
        return "delete from ai_call_record where record_id in " + inClause;
    }
}
