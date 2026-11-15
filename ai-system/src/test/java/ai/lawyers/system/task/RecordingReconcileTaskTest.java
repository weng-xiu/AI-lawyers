package ai.lawyers.system.task;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Duration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import ai.lawyers.system.service.impl.lawyers.storage.RecordingUploadService;
import ai.lawyers.system.service.lawyers.cluster.RedisLeaderLock;

/**
 * 录音补传定时任务测试：开关、集群单主锁、扫描窗口参数与异常不外抛。
 */
class RecordingReconcileTaskTest
{
    private RecordingReconcileTask task;
    private RedisLeaderLock leaderLock;
    private RecordingUploadService uploadService;

    @BeforeEach
    void setUp()
    {
        task = new RecordingReconcileTask();
        leaderLock = mock(RedisLeaderLock.class);
        uploadService = mock(RecordingUploadService.class);
        ReflectionTestUtils.setField(task, "leaderLock", leaderLock);
        ReflectionTestUtils.setField(task, "recordingUploadService", uploadService);
        ReflectionTestUtils.setField(task, "enabled", true);
        ReflectionTestUtils.setField(task, "minAgeMinutes", 5);
        ReflectionTestUtils.setField(task, "maxAgeHours", 24);
        ReflectionTestUtils.setField(task, "batchLimit", 200);
    }

    @Test
    void scan_disabled_skipsLockAndUpload()
    {
        ReflectionTestUtils.setField(task, "enabled", false);

        task.scan();

        verifyNoInteractions(leaderLock, uploadService);
    }

    @Test
    void scan_enabled_runsUnderClusterLock()
    {
        task.scan();

        verify(leaderLock).tryRun(eq("job:recording-reconcile"), any(Duration.class), any(Runnable.class));
        // mock 锁不执行任务体，本轮不应触达补扫服务
        verifyNoInteractions(uploadService);
    }

    @Test
    void scan_lockAcquired_scansWithConfiguredWindow()
    {
        runTaskInsideLock();
        when(uploadService.reconcilePendingUploads(5, 24, 200)).thenReturn(3);

        task.scan();

        verify(uploadService).reconcilePendingUploads(5, 24, 200);
    }

    @Test
    void scan_nothingRequeued_noError()
    {
        runTaskInsideLock();
        when(uploadService.reconcilePendingUploads(anyInt(), anyInt(), anyInt())).thenReturn(0);

        task.scan();

        verify(uploadService).reconcilePendingUploads(5, 24, 200);
    }

    @Test
    void scan_serviceThrows_swallowedForNextRound()
    {
        runTaskInsideLock();
        when(uploadService.reconcilePendingUploads(anyInt(), anyInt(), anyInt()))
                .thenThrow(new IllegalStateException("db down"));

        task.scan();

        verify(uploadService).reconcilePendingUploads(5, 24, 200);
    }

    @Test
    void scan_lockNotAcquired_skipsUpload()
    {
        when(leaderLock.tryRun(any(), any(), any())).thenReturn(false);

        task.scan();

        verify(uploadService, never()).reconcilePendingUploads(anyInt(), anyInt(), anyInt());
    }

    /** 模拟抢到锁：执行 tryRun 传入的任务体 */
    private void runTaskInsideLock()
    {
        doAnswer(inv -> {
            inv.getArgument(2, Runnable.class).run();
            return true;
        }).when(leaderLock).tryRun(any(), any(), any());
    }
}
