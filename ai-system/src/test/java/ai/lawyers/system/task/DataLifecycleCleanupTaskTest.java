package ai.lawyers.system.task;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
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
 * N12：{@link DataLifecycleCleanupTask} 分批删除、试运行、录音文件联动与开关测试。
 *
 * @author ai-lawyers
 */
class DataLifecycleCleanupTaskTest
{
    private DataLifecycleCleanupTask task;
    private RedisLeaderLock leaderLock;
    private JdbcTemplate jdbc;

    @TempDir
    File tempDir;

    @BeforeEach
    void setUp()
    {
        task = new DataLifecycleCleanupTask();
        leaderLock = mock(RedisLeaderLock.class);
        jdbc = mock(JdbcTemplate.class);
        ReflectionTestUtils.setField(task, "leaderLock", leaderLock);
        ReflectionTestUtils.setField(task, "jdbcTemplate", jdbc);
        ReflectionTestUtils.setField(task, "enabled", true);
        ReflectionTestUtils.setField(task, "dryRun", false);
        ReflectionTestUtils.setField(task, "batchSize", 1000);
        ReflectionTestUtils.setField(task, "maxBatches", 5);
        ReflectionTestUtils.setField(task, "dialLogMonths", 3);
        ReflectionTestUtils.setField(task, "agentStatusLogMonths", 6);
        ReflectionTestUtils.setField(task, "smsLogMonths", 6);
        ReflectionTestUtils.setField(task, "videoConsultLogMonths", 6);
        ReflectionTestUtils.setField(task, "ivrExecutionLogMonths", 1);
        ReflectionTestUtils.setField(task, "ivrIntentionLogMonths", 1);
        ReflectionTestUtils.setField(task, "callRecordMonths", 36);
        ReflectionTestUtils.setField(task, "deleteRecordingFile", true);
        // 单主锁透传执行 Runnable（模拟抢锁成功）
        doAnswer(inv ->
        {
            ((Runnable) inv.getArgument(2)).run();
            return true;
        }).when(leaderLock).tryRun(anyString(), any(), any(Runnable.class));
    }

    @Test
    void runCleanup_disabled_doesNothing()
    {
        ReflectionTestUtils.setField(task, "enabled", false);

        task.runCleanup();

        verify(leaderLock, never()).tryRun(anyString(), any(), any(Runnable.class));
    }

    @Test
    void cleanupGenericTable_batchesUntilShortBatch()
    {
        // 第一批满批 1000，第二批 0 → 共删除 1000 行，两轮收敛
        when(jdbc.update(anyString(), any(Timestamp.class), eq(1000))).thenReturn(1000, 0);
        DataLifecycleCleanupTask.TableSpec spec =
                new DataLifecycleCleanupTask.TableSpec("ai_call_dial_log", "create_time", 3, false);
        Timestamp cutoff = Timestamp.valueOf(LocalDate.now().minusMonths(3).atStartOfDay());

        long rows = task.cleanupGenericTable(spec, cutoff);

        assertThat(rows).isEqualTo(1000L);
        Mockito.verify(jdbc, Mockito.times(2)).update(anyString(), any(Timestamp.class), eq(1000));
    }

    @Test
    void cleanupGenericTable_dryRun_onlyCountsNeverDeletes()
    {
        ReflectionTestUtils.setField(task, "dryRun", true);
        when(jdbc.queryForObject(anyString(), eq(Long.class), any(Timestamp.class))).thenReturn(42L);
        DataLifecycleCleanupTask.TableSpec spec =
                new DataLifecycleCleanupTask.TableSpec("ai_sms_log", "create_time", 6, false);
        Timestamp cutoff = Timestamp.valueOf(LocalDate.now().minusMonths(6).atStartOfDay());

        long rows = task.cleanupGenericTable(spec, cutoff);

        assertThat(rows).isEqualTo(42L);
        verify(jdbc, never()).update(anyString(),
                org.mockito.ArgumentMatchers.<Object[]>any());
    }

    @Test
    void cleanupCallRecords_deletesRecordingFileThenRows() throws Exception
    {
        // 录音文件放在临时目录，DB 中存相对路径
        File rec = new File(tempDir, "2023/rec-1.wav");
        rec.getParentFile().mkdirs();
        Files.write(rec.toPath(), "fake-recording".getBytes(StandardCharsets.UTF_8));
        ReflectionTestUtils.setField(task, "recordingBasePath", tempDir.getAbsolutePath());

        List<Map<String, Object>> rows = new ArrayList<>();
        Map<String, Object> row = new HashMap<>();
        row.put("record_id", 1001L);
        row.put("record_file", "2023/rec-1.wav");
        rows.add(row);
        when(jdbc.queryForList(anyString(), any(Timestamp.class), eq(1000))).thenReturn(rows);
        Timestamp cutoff = Timestamp.valueOf(LocalDate.now().minusMonths(36).atStartOfDay());

        long deleted = task.cleanupCallRecords(cutoff);

        assertThat(deleted).isEqualTo(1L);
        assertThat(rec).doesNotExist();
        // 按主键删行，参数为 1001L（Mockito 将 varargs 展开为独立参数匹配）
        verify(jdbc).update(Mockito.contains("delete from ai_call_record where record_id in"),
                eq(1001L));
    }

    @Test
    void resolveRecordingFile_relativeJoinedWithBase()
    {
        File f = DataLifecycleCleanupTask.resolveRecordingFile("/data/rec", "2026/a.wav");
        assertThat(f.getAbsolutePath().replace('\\', '/'))
                .isEqualTo(new File("/data/rec/2026/a.wav").getAbsolutePath().replace('\\', '/'));
    }

    @Test
    void resolveRecordingFile_absoluteUsedAsIs()
    {
        String absolute = new File(tempDir, "b.wav").getAbsolutePath();
        File f = DataLifecycleCleanupTask.resolveRecordingFile("/data/rec", absolute);
        assertThat(f.isAbsolute()).isTrue();
        assertThat(f.getAbsolutePath()).isEqualTo(absolute);
    }

    @Test
    void resolveRecordingFile_blankReturnsNull()
    {
        assertThat(DataLifecycleCleanupTask.resolveRecordingFile("/data/rec", null)).isNull();
        assertThat(DataLifecycleCleanupTask.resolveRecordingFile("/data/rec", "  ")).isNull();
        assertThat(DataLifecycleCleanupTask.resolveRecordingFile("", "2026/a.wav")).isNull();
    }
}
