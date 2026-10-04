package ai.lawyers.system.service.lawyers.voice.copilot;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
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
 * {@link CopilotSimulationManager} 单测：用同步执行器替换后台线程池，
 * Map 模拟 Redis，验证提交/执行/报告回写/互斥/失败释放锁。
 */
class CopilotSimulationManagerTest
{
    @Mock
    private RedisCache redisCache;

    @Mock
    private RedisLeaderLock leaderLock;

    @Mock
    private CopilotSimulationService simulationService;

    @Mock
    private ExecutorService executor;

    private CopilotSimulationManager manager;

    private final Map<String, CopilotSimulationManager.Task> store = new HashMap<>();

    @BeforeEach
    void setUp()
    {
        MockitoAnnotations.openMocks(this);
        manager = new CopilotSimulationManager();
        ReflectionTestUtils.setField(manager, "redisCache", redisCache);
        ReflectionTestUtils.setField(manager, "leaderLock", leaderLock);
        ReflectionTestUtils.setField(manager, "copilotSimulationService", simulationService);
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
        report.put("totalCases", 5);
        report.put("passedCases", 4);
        when(simulationService.run(any())).thenReturn(report);

        String taskId = manager.submit(null);

        CopilotSimulationManager.Task t = manager.getTask(taskId);
        assertEquals(CopilotSimulationManager.Task.SUCCESS, t.status);
        assertEquals(5, t.totalCases);
        assertEquals(4, t.passedCases);
        assertEquals(100, t.getPercent());
        assertEquals(4, t.report.path("passedCases").asInt());
        verify(leaderLock).releaseOnce(contains("copilot-simulation"),
                org.mockito.ArgumentMatchers.eq("token-1"));
    }

    @Test
    void submit_rejectsWhenLockHeld()
    {
        when(leaderLock.acquireOnce(any(String.class), any())).thenReturn(null);
        ServiceException ex = assertThrows(ServiceException.class, () -> manager.submit(null));
        assertEquals("已有 Copilot 评测任务正在执行，请等待其完成后再提交", ex.getMessage());
    }

    @Test
    void submit_marksFailedAndReleasesLockOnError()
    {
        when(leaderLock.acquireOnce(any(String.class), any())).thenReturn("token-2");
        when(simulationService.run(any())).thenThrow(new RuntimeException("model down"));

        String taskId = manager.submit(null);

        CopilotSimulationManager.Task t = manager.getTask(taskId);
        assertEquals(CopilotSimulationManager.Task.FAILED, t.status);
        assertEquals("model down", t.errorMsg);
        verify(leaderLock).releaseOnce(any(String.class),
                org.mockito.ArgumentMatchers.eq("token-2"));
    }

    @Test
    void getTask_missingThrows()
    {
        assertThrows(ServiceException.class, () -> manager.getTask("nope"));
        assertThrows(ServiceException.class, () -> manager.getTask(""));
    }
}
