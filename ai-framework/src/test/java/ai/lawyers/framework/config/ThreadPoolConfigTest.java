package ai.lawyers.framework.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.core.task.TaskDecorator;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * 线程池配置测试：核心参数、拒绝策略、MDC 透传与调度线程池。
 *
 * @author ai-lawyers
 */
class ThreadPoolConfigTest
{
    private final ThreadPoolConfig config = new ThreadPoolConfig();

    @AfterEach
    void tearDown()
    {
        MDC.clear();
    }

    @Test
    void threadPoolTaskExecutor_核心参数与拒绝策略()
    {
        ThreadPoolTaskExecutor executor = config.threadPoolTaskExecutor();

        assertEquals(50, executor.getCorePoolSize());
        assertEquals(200, executor.getMaxPoolSize());
        assertEquals(1000, executor.getQueueCapacity());
        assertEquals(300, executor.getKeepAliveSeconds());

        executor.initialize();
        try
        {
            assertInstanceOf(ThreadPoolExecutor.CallerRunsPolicy.class,
                    executor.getThreadPoolExecutor().getRejectedExecutionHandler());
        }
        finally
        {
            executor.shutdown();
        }
    }

    @Test
    void taskDecorator_向执行线程透传MDC() throws Exception
    {
        AtomicReference<String> captured = new AtomicReference<>();
        MDC.put("traceId", "trace-123");

        TaskDecorator decorator = (TaskDecorator) ReflectionTestUtils
                .getField(config.threadPoolTaskExecutor(), "taskDecorator");
        Runnable decorated = decorator.decorate(() -> captured.set(MDC.get("traceId")));
        MDC.clear();

        Thread worker = new Thread(decorated);
        worker.start();
        worker.join();

        assertEquals("trace-123", captured.get());
    }

    @Test
    void taskDecorator_无上下文时清空执行线程MDC()
    {
        MDC.clear();
        TaskDecorator decorator = (TaskDecorator) ReflectionTestUtils
                .getField(config.threadPoolTaskExecutor(), "taskDecorator");

        AtomicReference<String> insideValue = new AtomicReference<>("init");
        Runnable decorated = decorator.decorate(() -> insideValue.set(MDC.get("traceId")));

        // 提交后才出现调用方上下文，执行线程不应继承
        MDC.put("traceId", "stale");
        decorated.run();

        assertNull(insideValue.get());
        // 执行结束后还原调用方上下文
        assertEquals("stale", MDC.get("traceId"));
    }

    @Test
    void scheduledExecutorService_守护调度线程池()
    {
        ScheduledExecutorService executor = config.scheduledExecutorService();

        assertNotNull(executor);
        assertInstanceOf(ScheduledThreadPoolExecutor.class, executor);
        assertEquals(50, ((ScheduledThreadPoolExecutor) executor).getCorePoolSize());
        assertTrue(((ScheduledThreadPoolExecutor) executor).getRejectedExecutionHandler()
                instanceof ThreadPoolExecutor.CallerRunsPolicy);

        executor.shutdownNow();
    }
}
