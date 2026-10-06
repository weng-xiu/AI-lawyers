package ai.lawyers.system.service.lawyers.voice.robot;

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
 * P1-8：{@link RobotSimulationManager} 单测——同步执行器替换后台线程池，Map 模拟 Redis，
 * 验证提交/执行/报告回写/互斥/失败释放锁（与 Copilot 平台锁与 key 独立）。
 */
class RobotSimulationManagerTest
{
    @Mock
    private RedisCache redisCache;

    @Mock
    private RedisLeaderLock leaderLock;

    @Mock
    private RobotSimulationService robotSimulationService;

    @Mock
    private ExecutorService executor;

    private RobotSimulationManager manager;

    private final Map<String, RobotSimulationManager.Task> store = new HashMap<>();

    @BeforeEach
    void setUp()
    {
        MockitoAnnotations.openMocks(this);
        manager = new RobotSimulationManager();
        ReflectionTestUtils.setField(manager, "redisCache", redisCache);
        ReflectionTestUtils.setField(manager, "leaderLock", leaderLock);
        ReflectionTestUtils.setField(manager, "robotSimulationService", robotSimulationService);
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
        report.put("totalCases", 3);
        report.put("passedTurns", 6);
        when(robotSimulationService.run(any())).thenReturn(report);

        String taskId = manager.submit(null);

        RobotSimulationManager.Task t = manager.getTask(taskId);
        assertEquals(RobotSimulationManager.Task.SUCCESS, t.status);
        assertEquals(3, t.totalCases);
        assertEquals(100, t.getPercent());
        assertEquals(6, t.report.path("passedTurns").asInt());
        verify(leaderLock).releaseOnce(contains("robot-simulation"),
                org.mockito.ArgumentMatchers.eq("token-1"));
    }

    @Test
    void submit_rejectsWhenLockHeld()
    {
        when(leaderLock.acquireOnce(any(String.class), any())).thenReturn(null);
        ServiceException ex = assertThrows(ServiceException.class, () -> manager.submit(null));
        assertEquals("已有机器人评测任务正在执行，请等待其完成后再提交", ex.getMessage());
    }

    @Test
    void submit_marksFailedAndReleasesLockOnError()
    {
        when(leaderLock.acquireOnce(any(String.class), any())).thenReturn("token-2");
        when(robotSimulationService.run(any())).thenThrow(new RuntimeException("robot down"));

        String taskId = manager.submit(null);

        RobotSimulationManager.Task t = manager.getTask(taskId);
        assertEquals(RobotSimulationManager.Task.FAILED, t.status);
        assertEquals("robot down", t.errorMsg);
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
