package ai.lawyers.framework.manager;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;
import java.util.TimerTask;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.test.util.ReflectionTestUtils;

import ai.lawyers.common.utils.spring.SpringUtils;

/**
 * 异步任务管理器测试：单例获取、延迟调度与线程池关闭。
 *
 * <p>单例在类加载时从 Spring 容器获取线程池，因此需先注入桩 BeanFactory。</p>
 *
 * @author ai-lawyers
 */
class AsyncManagerTest
{
    private static Object originalBeanFactory;

    private static ScheduledExecutorService bootstrapExecutor;

    private ScheduledExecutorService originalExecutor;

    private ScheduledExecutorService executor;

    @BeforeAll
    static void bootstrap() throws Exception
    {
        bootstrapExecutor = Executors.newSingleThreadScheduledExecutor();
        ConfigurableListableBeanFactory beanFactory = mock(ConfigurableListableBeanFactory.class);
        when(beanFactory.getBean("scheduledExecutorService")).thenReturn(bootstrapExecutor);

        Field field = SpringUtils.class.getDeclaredField("beanFactory");
        field.setAccessible(true);
        originalBeanFactory = field.get(null);
        field.set(null, beanFactory);

        // 触发单例类初始化
        AsyncManager.me();
    }

    @AfterAll
    static void tearDownClass() throws Exception
    {
        bootstrapExecutor.shutdownNow();
        Field field = SpringUtils.class.getDeclaredField("beanFactory");
        field.setAccessible(true);
        field.set(null, originalBeanFactory);
    }

    @BeforeEach
    void setUp()
    {
        originalExecutor = (ScheduledExecutorService) ReflectionTestUtils.getField(AsyncManager.me(), "executor");
        executor = mock(ScheduledExecutorService.class);
        ReflectionTestUtils.setField(AsyncManager.me(), "executor", executor);
    }

    @AfterEach
    void tearDown()
    {
        ReflectionTestUtils.setField(AsyncManager.me(), "executor", originalExecutor);
    }

    @Test
    void me_始终返回同一实例()
    {
        assertSame(AsyncManager.me(), AsyncManager.me());
    }

    @Test
    void execute_按10毫秒延迟调度任务()
    {
        TimerTask task = mock(TimerTask.class);

        AsyncManager.me().execute(task);

        verify(executor).schedule(task, 10, TimeUnit.MILLISECONDS);
    }

    @Test
    void shutdown_关闭线程池并等待终止() throws Exception
    {
        when(executor.isShutdown()).thenReturn(false);
        when(executor.awaitTermination(120, TimeUnit.SECONDS)).thenReturn(true);

        AsyncManager.me().shutdown();

        verify(executor).shutdown();
        verify(executor).awaitTermination(120, TimeUnit.SECONDS);
    }
}
