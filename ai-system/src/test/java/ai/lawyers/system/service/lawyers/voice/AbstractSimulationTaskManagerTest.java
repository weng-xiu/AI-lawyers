package ai.lawyers.system.service.lawyers.voice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import ai.lawyers.common.core.redis.RedisCache;
import ai.lawyers.common.exception.ServiceException;
import ai.lawyers.system.service.lawyers.cluster.RedisLeaderLock;

/**
 * AbstractSimulationTaskManager 单测：提交/互斥占用、Redis 任务落库与轮询、
 * 成功/失败终态、锁释放、参数校验与关闭。
 *
 * @author ai-lawyers
 */
@ExtendWith(MockitoExtension.class)
class AbstractSimulationTaskManagerTest
{
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Mock
    private RedisCache redisCache;

    @Mock
    private RedisLeaderLock leaderLock;

    private TestManager manager;
    private final Map<String, Object> store = new HashMap<>();

    @BeforeEach
    void setUp()
    {
        manager = new TestManager(cases -> MAPPER.createObjectNode()
                .put("totalCases", 5).put("passedCases", 4));
        ReflectionTestUtils.setField(manager, "redisCache", redisCache);
        ReflectionTestUtils.setField(manager, "leaderLock", leaderLock);
        ReflectionTestUtils.setField(manager, "executor", new DirectExecutorService());

        lenient().doAnswer(invocation ->
        {
            store.put(invocation.getArgument(0), invocation.getArgument(1));
            return true;
        }).when(redisCache).setCacheObject(anyString(), any(), anyInt(), any());
        lenient().when(redisCache.getCacheObject(anyString()))
                .thenAnswer(invocation -> store.get(invocation.getArgument(0)));
    }

    @Test
    void submitRunsToSuccessAndReleasesLock()
    {
        when(leaderLock.acquireOnce("test-lock", Duration.ofSeconds(30))).thenReturn("token");

        String taskId = manager.submit("cases");

        assertThat(taskId).hasSize(32);
        AbstractSimulationTaskManager.Task task = manager.getTask(taskId);
        assertThat(task.status).isEqualTo(AbstractSimulationTaskManager.Task.SUCCESS);
        assertThat(task.totalCases).isEqualTo(5);
        assertThat(task.passedCases).isEqualTo(4);
        assertThat(task.report).isNotNull();
        assertThat(task.startTime).isNotNull();
        assertThat(task.finishTime).isNotNull();
        assertThat(task.getPercent()).isEqualTo(100);
        verify(leaderLock).acquireOnce("test-lock", Duration.ofSeconds(30));
        verify(leaderLock).releaseOnce("test-lock", "token");
    }

    @Test
    void busyLockRejectsSubmit()
    {
        when(leaderLock.acquireOnce(anyString(), any())).thenReturn(null);

        assertThatThrownBy(() -> manager.submit("cases"))
                .isInstanceOf(ServiceException.class)
                .hasMessage("评测进行中");
        verifyNoInteractions(redisCache);
    }

    @Test
    void failureMarksTaskFailedAndReleasesLock()
    {
        when(leaderLock.acquireOnce(anyString(), any())).thenReturn("token");
        manager = new TestManager(cases ->
        {
            throw new IllegalStateException("评测引擎异常");
        });
        ReflectionTestUtils.setField(manager, "redisCache", redisCache);
        ReflectionTestUtils.setField(manager, "leaderLock", leaderLock);
        ReflectionTestUtils.setField(manager, "executor", new DirectExecutorService());

        String taskId = manager.submit("cases");

        AbstractSimulationTaskManager.Task task = manager.getTask(taskId);
        assertThat(task.status).isEqualTo(AbstractSimulationTaskManager.Task.FAILED);
        assertThat(task.errorMsg).isEqualTo("评测引擎异常");
        assertThat(task.getPercent()).isZero();
        verify(leaderLock).releaseOnce("test-lock", "token");
    }

    @Test
    void blankTaskIdRejected()
    {
        assertThatThrownBy(() -> manager.getTask(""))
                .isInstanceOf(ServiceException.class)
                .hasMessage("任务ID不能为空");
        assertThatThrownBy(() -> manager.getTask(null))
                .isInstanceOf(ServiceException.class)
                .hasMessage("任务ID不能为空");
    }

    @Test
    void unknownTaskRejected()
    {
        assertThatThrownBy(() -> manager.getTask("missing"))
                .isInstanceOf(ServiceException.class)
                .hasMessage("评测任务不存在或已过期");
    }

    @Test
    void taskSavedWithTtl()
    {
        when(leaderLock.acquireOnce(anyString(), any())).thenReturn("token");

        manager.submit(null);

        verify(redisCache, atLeastOnce()).setCacheObject(anyString(),
                any(AbstractSimulationTaskManager.Task.class), eq(24), eq(TimeUnit.HOURS));
    }

    @Test
    void shutdownStopsExecutor()
    {
        DirectExecutorService executor = new DirectExecutorService();
        ReflectionTestUtils.setField(manager, "executor", executor);

        manager.shutdown();

        assertThat(executor.isShutdown()).isTrue();
    }

    @Test
    void taskPercentOnlySuccessIsHundred()
    {
        AbstractSimulationTaskManager.Task task = new AbstractSimulationTaskManager.Task();
        task.status = AbstractSimulationTaskManager.Task.RUNNING;
        assertThat(task.getPercent()).isZero();

        task.status = AbstractSimulationTaskManager.Task.FAILED;
        assertThat(task.getPercent()).isZero();

        task.status = AbstractSimulationTaskManager.Task.SUCCESS;
        assertThat(task.getPercent()).isEqualTo(100);
    }

    /** 测试用管理器 */
    private static class TestManager extends AbstractSimulationTaskManager
    {
        private final Function<String, ObjectNode> runner;

        TestManager(Function<String, ObjectNode> runner)
        {
            this.runner = runner;
        }

        @Override
        protected String taskKeyPrefix()
        {
            return "test:task:";
        }

        @Override
        protected String lockName()
        {
            return "test-lock";
        }

        @Override
        protected Duration lockTtl()
        {
            return Duration.ofSeconds(30);
        }

        @Override
        protected String busyMessage()
        {
            return "评测进行中";
        }

        @Override
        protected ObjectNode doRun(String casesJson)
        {
            return runner.apply(casesJson);
        }

        @Override
        protected String threadName()
        {
            return "test-sim";
        }
    }

    /** 同步执行器：提交即执行，便于断言异步任务的终态 */
    private static class DirectExecutorService extends AbstractExecutorService
    {
        private volatile boolean shutdown;

        @Override
        public void execute(Runnable command)
        {
            command.run();
        }

        @Override
        public void shutdown()
        {
            shutdown = true;
        }

        @Override
        public java.util.List<Runnable> shutdownNow()
        {
            shutdown = true;
            return java.util.Collections.emptyList();
        }

        @Override
        public boolean isShutdown()
        {
            return shutdown;
        }

        @Override
        public boolean isTerminated()
        {
            return shutdown;
        }

        @Override
        public boolean awaitTermination(long timeout, TimeUnit unit)
        {
            return shutdown;
        }
    }
}
