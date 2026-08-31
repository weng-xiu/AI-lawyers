package ai.lawyers.common.utils;

import java.util.Map;
import org.slf4j.MDC;

/**
 * T5-1 全链路 traceId 的 MDC 工具。
 *
 * <p>HTTP 入口由 TraceIdFilter 把 traceId 放入 MDC；对于<strong>非 Spring 管理</strong>
 * 的线程（Redis Stream 消费线程、自建 ExecutorService 等），{@link #wrap(Runnable)}
 * 在任务提交时快照当前 MDC、执行线程恢复，使异步日志与主链路同 traceId 串联。
 * Spring 的 @Async 线程池已由 ThreadPoolConfig 的 TaskDecorator 统一处理，无需调用本类。</p>
 *
 * @author ai-lawyers
 */
public class MdcUtils
{
    /** MDC 中的 traceId 键名（与 logback %X{traceId} 对应） */
    public static final String TRACE_ID = "traceId";

    private MdcUtils()
    {
    }

    /** 取当前线程 traceId（无则返回 null） */
    public static String getTraceId()
    {
        return MDC.get(TRACE_ID);
    }

    /** 设置当前线程 traceId */
    public static void setTraceId(String traceId)
    {
        if (traceId != null)
        {
            MDC.put(TRACE_ID, traceId);
        }
    }

    /** 包装任务：提交线程快照 MDC，执行线程恢复，执行完还原，避免线程池串号 */
    public static Runnable wrap(Runnable task)
    {
        if (task == null)
        {
            return null;
        }
        Map<String, String> context = MDC.getCopyOfContextMap();
        return () ->
        {
            Map<String, String> previous = MDC.getCopyOfContextMap();
            if (context != null)
            {
                MDC.setContextMap(context);
            }
            else
            {
                MDC.clear();
            }
            try
            {
                task.run();
            }
            finally
            {
                if (previous != null)
                {
                    MDC.setContextMap(previous);
                }
                else
                {
                    MDC.clear();
                }
            }
        };
    }
}
