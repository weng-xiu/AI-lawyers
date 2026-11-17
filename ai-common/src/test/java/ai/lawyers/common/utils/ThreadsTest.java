package ai.lawyers.common.utils;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.FutureTask;

import org.junit.jupiter.api.Test;

/**
 * 线程工具测试：休眠、异常打印与线程池关闭。
 *
 * @author ai-lawyers
 */
class ThreadsTest
{
    @Test
    void sleep_等待指定毫秒()
    {
        long start = System.currentTimeMillis();

        Threads.sleep(20L);

        assertTrue(System.currentTimeMillis() - start >= 10L);
    }

    @Test
    void sleep_被中断时提前返回且不恢复中断标记()
    {
        Thread.currentThread().interrupt();
        try
        {
            Threads.sleep(1000L);
        }
        finally
        {
            // sleep 捕获中断后直接返回，不再恢复中断标记
            assertFalse(Thread.currentThread().isInterrupted());
        }
    }

    @Test
    void printException_普通任务与空异常不抛错()
    {
        Threads.printException(() -> { }, null);
        Threads.printException(() -> { }, new IllegalStateException("业务异常"));
    }

    @Test
    void printException_Future已正常完成不抛错()
    {
        FutureTask<String> task = new FutureTask<>(() -> "ok");
        task.run();

        Threads.printException(task, null);
    }

    @Test
    void printException_捕获Future执行异常与取消异常()
    {
        FutureTask<String> failed = new FutureTask<>(() ->
        {
            throw new IllegalStateException("执行失败");
        });
        failed.run();
        Threads.printException(failed, null);

        FutureTask<String> cancelled = new FutureTask<>(() -> "ok");
        cancelled.cancel(true);
        Threads.printException(cancelled, null);
    }

    @Test
    void shutdownAndAwaitTermination_关闭线程池()
    {
        ExecutorService pool = Executors.newSingleThreadExecutor();
        pool.submit(() -> { });

        Threads.shutdownAndAwaitTermination(pool);

        assertTrue(pool.isShutdown());
        assertTrue(pool.isTerminated());
    }

    @Test
    void shutdownAndAwaitTermination_空池与已关闭池直接跳过()
    {
        Threads.shutdownAndAwaitTermination(null);

        ExecutorService pool = Executors.newSingleThreadExecutor();
        pool.shutdown();
        Threads.shutdownAndAwaitTermination(pool);

        assertTrue(pool.isShutdown());
    }
}
