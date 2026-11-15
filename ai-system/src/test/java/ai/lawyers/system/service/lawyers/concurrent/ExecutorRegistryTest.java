package ai.lawyers.system.service.lawyers.concurrent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import io.micrometer.core.instrument.MeterRegistry;

/**
 * ExecutorRegistry 单测：有界队列与命名 daemon 线程、重复/空名与参数兜底、
 * 自定义拒绝策略、无指标注册表兼容与优雅停机。
 *
 * @author ai-lawyers
 */
class ExecutorRegistryTest
{
    private ExecutorRegistry registry;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp()
    {
        ObjectProvider<MeterRegistry> provider = mock(ObjectProvider.class);
        registry = new ExecutorRegistry(provider);
    }

    @AfterEach
    void tearDown()
    {
        registry.shutdownAll();
    }

    @Test
    void createsBoundedPoolWithNamedDaemonThreads() throws Exception
    {
        ExecutorService pool = registry.create("biz", 1, 1, 2);

        ThreadPoolExecutor executor = (ThreadPoolExecutor) pool;
        assertThat(executor.getQueue().remainingCapacity()).isEqualTo(2);
        assertThat(executor.getRejectedExecutionHandler())
                .isInstanceOf(ThreadPoolExecutor.CallerRunsPolicy.class);

        AtomicReference<Thread> worker = new AtomicReference<>();
        Future<?> future = pool.submit(() -> worker.set(Thread.currentThread()));
        future.get(2, TimeUnit.SECONDS);

        assertThat(worker.get().getName()).startsWith("biz-");
        assertThat(worker.get().isDaemon()).isTrue();
    }

    @Test
    void duplicateNameRejected()
    {
        registry.create("dup", 1, 1, 1);

        assertThatThrownBy(() -> registry.create("dup", 1, 1, 1))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("重复注册");
    }

    @Test
    void blankNameRejected()
    {
        assertThatThrownBy(() -> registry.create("", 1, 1, 1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("name 不能为空");
        assertThatThrownBy(() -> registry.create(null, 1, 1, 1))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void nonPositiveParametersClampedToOne()
    {
        ExecutorService pool = registry.create("clamp", 0, -5, 0);

        ThreadPoolExecutor executor = (ThreadPoolExecutor) pool;
        assertThat(executor.getCorePoolSize()).isEqualTo(1);
        assertThat(executor.getMaximumPoolSize()).isEqualTo(1);
        assertThat(executor.getQueue().remainingCapacity()).isEqualTo(1);
    }

    @Test
    void customRejectedHandlerApplied()
    {
        ExecutorService pool = registry.create("abort", 1, 1, 1,
                new ThreadPoolExecutor.AbortPolicy());

        assertThat(((ThreadPoolExecutor) pool).getRejectedExecutionHandler())
                .isInstanceOf(ThreadPoolExecutor.AbortPolicy.class);
    }

    @Test
    void shutdownAllClosesPoolsAndIsIdempotent()
    {
        ExecutorService pool = registry.create("stop", 1, 1, 1);

        registry.shutdownAll();

        assertThat(pool.isShutdown()).isTrue();
        registry.shutdownAll();
    }
}
