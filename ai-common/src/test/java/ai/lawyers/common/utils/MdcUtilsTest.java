package ai.lawyers.common.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

/**
 * traceId 上下文工具测试：MDC 读写、异步任务上下文透传与还原、异常路径清理。
 *
 * @author ai-lawyers
 */
class MdcUtilsTest
{
    @AfterEach
    void 清理上下文()
    {
        MDC.clear();
    }

    @Test
    void traceId读写()
    {
        assertNull(MdcUtils.getTraceId());

        MdcUtils.setTraceId("trace-1");
        assertEquals("trace-1", MdcUtils.getTraceId());

        // 空值不覆盖已有 traceId
        MdcUtils.setTraceId(null);
        assertEquals("trace-1", MdcUtils.getTraceId());
    }

    @Test
    void wrap_空任务返回null()
    {
        assertNull(MdcUtils.wrap(null));
    }

    @Test
    void wrap_异步线程继承提交时traceId()
    {
        MdcUtils.setTraceId("trace-parent");
        AtomicReference<String> insideChild = new AtomicReference<>();
        AtomicReference<String> afterChild = new AtomicReference<>();

        Runnable wrapped = MdcUtils.wrap(() -> insideChild.set(MdcUtils.getTraceId()));
        Thread thread = new Thread(() ->
        {
            wrapped.run();
            afterChild.set(MdcUtils.getTraceId());
        });
        thread.start();
        join(thread);

        assertEquals("trace-parent", insideChild.get());
        // 执行线程原本无上下文，执行后清理
        assertNull(afterChild.get());
    }

    @Test
    void wrap_执行后还原执行线程原有traceId()
    {
        MdcUtils.setTraceId("trace-parent");
        AtomicReference<String> insideChild = new AtomicReference<>();
        AtomicReference<String> afterChild = new AtomicReference<>();

        Runnable wrapped = MdcUtils.wrap(() -> insideChild.set(MdcUtils.getTraceId()));
        Thread thread = new Thread(() ->
        {
            MdcUtils.setTraceId("trace-child");
            wrapped.run();
            afterChild.set(MdcUtils.getTraceId());
        });
        thread.start();
        join(thread);

        assertEquals("trace-parent", insideChild.get());
        assertEquals("trace-child", afterChild.get());
    }

    @Test
    void wrap_任务异常仍还原上下文并向外抛出()
    {
        MdcUtils.setTraceId("trace-submit");
        Runnable wrapped = MdcUtils.wrap(() ->
        {
            throw new IllegalStateException("任务失败");
        });
        MdcUtils.setTraceId("trace-run");

        assertThrows(IllegalStateException.class, wrapped::run);
        assertEquals("trace-run", MdcUtils.getTraceId());
    }

    /** 等待线程结束 */
    private void join(Thread thread)
    {
        try
        {
            thread.join(3000L);
        }
        catch (InterruptedException e)
        {
            Thread.currentThread().interrupt();
        }
    }
}
