package ai.lawyers.framework.config;

import ai.lawyers.common.utils.Threads;
import org.apache.commons.lang3.concurrent.BasicThreadFactory;
import org.slf4j.MDC;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskDecorator;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import java.util.Map;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * 线程池配置
 *
 * @author ruoyi
 **/
@Configuration
public class ThreadPoolConfig
{
    // 核心线程池大小
    private int corePoolSize = 50;

    // 最大可创建的线程数
    private int maxPoolSize = 200;

    // 队列最大长度
    private int queueCapacity = 1000;

    // 线程池维护线程所允许的空闲时间
    private int keepAliveSeconds = 300;

    @Bean(name = "threadPoolTaskExecutor")
    public ThreadPoolTaskExecutor threadPoolTaskExecutor()
    {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setMaxPoolSize(maxPoolSize);
        executor.setCorePoolSize(corePoolSize);
        executor.setQueueCapacity(queueCapacity);
        executor.setKeepAliveSeconds(keepAliveSeconds);
        // T5-1：@Async 任务透传调用方 MDC（含 traceId），异步日志与主链路同 traceId 可串联
        executor.setTaskDecorator(mdcTaskDecorator());
        // 线程池对拒绝任务(无线程可用)的处理策略
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        return executor;
    }

    /**
     * T5-1：MDC 任务装饰器。提交任务时快照当前线程 MDC，执行线程恢复该上下文，
     * 执行完还原/清理，保证 traceId 跨 @Async 线程传递且无线程池串号。
     */
    private TaskDecorator mdcTaskDecorator()
    {
        return runnable ->
        {
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
                    runnable.run();
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
        };
    }

    /**
     * 执行周期性或定时任务
     */
    @Bean(name = "scheduledExecutorService")
    protected ScheduledExecutorService scheduledExecutorService()
    {
        return new ScheduledThreadPoolExecutor(corePoolSize,
                new BasicThreadFactory.Builder().namingPattern("schedule-pool-%d").daemon(true).build(),
                new ThreadPoolExecutor.CallerRunsPolicy())
        {
            @Override
            protected void afterExecute(Runnable r, Throwable t)
            {
                super.afterExecute(r, t);
                Threads.printException(r, t);
            }
        };
    }
}
