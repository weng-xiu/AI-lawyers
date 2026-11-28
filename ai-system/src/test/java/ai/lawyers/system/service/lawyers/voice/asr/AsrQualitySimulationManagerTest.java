package ai.lawyers.system.service.lawyers.voice.asr;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.test.util.ReflectionTestUtils;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import ai.lawyers.common.core.redis.RedisCache;
import ai.lawyers.common.exception.ServiceException;
import ai.lawyers.system.service.lawyers.cluster.RedisLeaderLock;

/**
 * N2 S1：{@link AsrQualitySimulationManager} 单测——同步执行器替换后台线程池，Map 模拟
 * Redis，验证提交/执行/报告回写/计数别名/互斥/失败释放锁（与 Copilot、机器人平台锁与 key 独立）。
 */
class AsrQualitySimulationManagerTest
{
    @Mock
    private RedisCache redisCache;

    @Mock
    private RedisLeaderLock leaderLock;

    @Mock
    private AsrQualitySimulationService asrQualitySimulationService;

    @Mock
    private ExecutorService executor;

    private AsrQualitySimulationManager manager;

    private final Map<String, AsrQualitySimulationManager.Task> store = new HashMap<>();

    @BeforeEach
    void setUp()
    {
        MockitoAnnotations.openMocks(this);
        manager = new AsrQualitySimulationManager();
        ReflectionTestUtils.setField(manager, "redisCache", redisCache);
        ReflectionTestUtils.setField(manager, "leaderLock", leaderLock);
        ReflectionTestUtils.setField(manager, "asrQualitySimulationService", asrQualitySimulationService);
        ReflectionTestUtils.setField(manager, "executor", executor);

        when(executor.submit(any(Runnable.class))).thenAnswer(inv ->
        {
            ((Runnable) inv.getArgument(0)).run();
            return null;
        });
        org.mockito.Mockito.doAnswer(inv ->
        {
            store.put(inv.getArgument(0), inv.getArgument(1));
            return null;
        }).when(redisCache).setCacheObject(any(String.class), any(), any(Integer.class), any(TimeUnit.class));
        when(redisCache.getCacheObject(any(String.class)))
                .thenAnswer(inv -> store.get(inv.getArgument(0)));
    }

    @Test
    void submit_runsAndStoresReport()
    {
        when(leaderLock.acquireOnce(any(String.class), any())).thenReturn("token-1");
        ObjectNode report = new ObjectMapper().createObjectNode();
        report.put("totalSamples", 3);
        report.put("passedSamples", 2);
        when(asrQualitySimulationService.run(any())).thenReturn(report);

        String taskId = manager.submit(null);

        AsrQualitySimulationManager.Task t = manager.getTask(taskId);
        assertEquals(AsrQualitySimulationManager.Task.SUCCESS, t.status);
        // 别名适配：骨架便利计数应取到 totalSamples/passedSamples
        assertEquals(3, t.totalCases);
        assertEquals(2, t.passedCases);
        assertEquals(100, t.getPercent());
        // 报告内嵌原字段保持不变
        assertEquals(3, t.report.path("totalSamples").asInt());
        assertEquals(2, t.report.path("passedSamples").asInt());
        verify(leaderLock).releaseOnce(contains("asr-simulation"), eq("token-1"));
    }

    @Test
    void submit_rejectsWhenLockHeld()
    {
        when(leaderLock.acquireOnce(any(String.class), any())).thenReturn(null);
        ServiceException ex = assertThrows(ServiceException.class, () -> manager.submit(null));
        assertEquals("已有ASR转写质量评测任务正在执行，请等待其完成后再提交", ex.getMessage());
    }

    @Test
    void submit_marksFailedAndReleasesLockOnError()
    {
        when(leaderLock.acquireOnce(any(String.class), any())).thenReturn("token-2");
        when(asrQualitySimulationService.run(any())).thenThrow(new RuntimeException("asr down"));

        String taskId = manager.submit(null);

        AsrQualitySimulationManager.Task t = manager.getTask(taskId);
        assertEquals(AsrQualitySimulationManager.Task.FAILED, t.status);
        assertEquals("asr down", t.errorMsg);
        verify(leaderLock).releaseOnce(any(String.class), eq("token-2"));
    }

    @Test
    void getTask_missingThrows()
    {
        assertThrows(ServiceException.class, () -> manager.getTask("nope"));
        assertThrows(ServiceException.class, () -> manager.getTask(""));
    }
}
