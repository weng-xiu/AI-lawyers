package ai.lawyers.framework.manager;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;
import java.util.concurrent.ScheduledExecutorService;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.test.util.ReflectionTestUtils;

import ai.lawyers.common.utils.spring.SpringUtils;

/**
 * 应用停止管理器测试：关闭调度线程池、异常不外抛。
 *
 * @author ai-lawyers
 */
class ShutdownManagerTest
{
    private ScheduledExecutorService executor;

    private Object originalExecutor;

    private Field beanFactoryField;

    @BeforeEach
    void setUp() throws Exception
    {
        executor = mock(ScheduledExecutorService.class);
        // AsyncManager 静态单例构造时从 SpringUtils 取调度线程池
        ConfigurableListableBeanFactory beanFactory = mock(ConfigurableListableBeanFactory.class);
        when(beanFactory.getBean("scheduledExecutorService")).thenReturn(executor);
        beanFactoryField = SpringUtils.class.getDeclaredField("beanFactory");
        beanFactoryField.setAccessible(true);
        beanFactoryField.set(null, beanFactory);

        originalExecutor = ReflectionTestUtils.getField(AsyncManager.me(), "executor");
        ReflectionTestUtils.setField(AsyncManager.me(), "executor", executor);
    }

    @AfterEach
    void tearDown() throws Exception
    {
        ReflectionTestUtils.setField(AsyncManager.me(), "executor", originalExecutor);
        beanFactoryField.set(null, null);
    }

    @Test
    void destroy关闭调度线程池()
    {
        new ShutdownManager().destroy();

        verify(executor).shutdown();
        // 模拟等待超时后强制关闭
        verify(executor).shutdownNow();
    }

    @Test
    void destroy关闭异常不向外抛出()
    {
        doThrow(new RuntimeException("关闭失败")).when(executor).shutdown();

        assertDoesNotThrow(() -> new ShutdownManager().destroy());
    }
}
