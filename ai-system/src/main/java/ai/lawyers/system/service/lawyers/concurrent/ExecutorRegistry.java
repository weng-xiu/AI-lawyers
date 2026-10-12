package ai.lawyers.system.service.lawyers.concurrent;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import jakarta.annotation.PreDestroy;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
// micrometer 1.12.x（Spring Boot 3.2 管理版本）中 ExecutorServiceMetrics 位于 binder.jvm 包；1.13+ 才迁移至 binder.concurrent
import io.micrometer.core.instrument.binder.jvm.ExecutorServiceMetrics;

/**
 * P2 线程池统一治理：ExecutorRegistry
 *
 * <p>目标（对应《代码结构盘点与优化规划》P2）：消除业务代码散建的
 * {@code Executors.newXxxThreadPool} 无界队列 OOM 风险，并让"哪个池打满"
 * 可定位。统一提供：</p>
 * <ul>
 *   <li><b>有界队列</b>：显式容量，杜绝无界 LinkedBlockingQueue；</li>
 *   <li><b>命名线程</b>：{@code name-%d} daemon 线程，线程 dump 可读；</li>
 *   <li><b>拒绝策略</b>：默认 {@code CallerRunsPolicy} 回压；特殊场景可显式传入
 *       （如录音上传池保持 {@code AbortPolicy}，入队失败走补扫兜底，不可回压呼叫主链路）；</li>
 *   <li><b>Micrometer 指标</b>：经 {@link ExecutorServiceMetrics} 暴露
 *       executor.queued / active / pool.size / completed 等，接入 Grafana；</li>
 *   <li><b>队列水位告警</b>：30s 巡检，占比 ≥ 80% 告警一次（去重），恢复后自动复位；</li>
 *   <li><b>生命周期</b>：统一优雅停机（shutdown → await 10s → shutdownNow）。</li>
 * </ul>
 *
 * <p>放置于 ai-system：首批纳管的外呼拨号池、录音上传池均在本模块；
 * micrometer-core 在本模块为 optional 编译依赖，运行期由 ai-admin 提供
 * MeterRegistry，缺省时自动跳过指标绑定（不影响单测环境）。</p>
 *
 * @author ai-lawyers
 */
@Component
public class ExecutorRegistry
{
    private static final Logger log = LoggerFactory.getLogger(ExecutorRegistry.class);

    /** 队列水位告警阈值（已用容量占比） */
    private static final double WATERMARK_RATIO = 0.8d;

    /** 水位巡检周期（秒） */
    private static final long WATERMARK_CHECK_SECONDS = 30L;

    /** 停机时单池等待时长（秒） */
    private static final long SHUTDOWN_WAIT_SECONDS = 10L;

    private final ObjectProvider<MeterRegistry> meterRegistryProvider;

    /** 已纳管线程池（name → pool） */
    private final Map<String, ThreadPoolExecutor> pools = new ConcurrentHashMap<>();

    /** 各池队列容量（水位计算用） */
    private final Map<String, Integer> queueCapacities = new ConcurrentHashMap<>();

    /** 水位告警去重：跨巡检周期只告警一次，恢复后复位 */
    private final Map<String, AtomicBoolean> watermarkAlerted = new ConcurrentHashMap<>();

    /** 水位巡检调度器（单线程 daemon） */
    private final ScheduledExecutorService watermarkChecker;

    public ExecutorRegistry(ObjectProvider<MeterRegistry> meterRegistryProvider)
    {
        this.meterRegistryProvider = meterRegistryProvider;
        this.watermarkChecker = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "executor-registry-watermark");
            t.setDaemon(true);
            return t;
        });
        this.watermarkChecker.scheduleWithFixedDelay(this::checkWatermarks,
                WATERMARK_CHECK_SECONDS, WATERMARK_CHECK_SECONDS, TimeUnit.SECONDS);
    }

    /**
     * 创建有界线程池并纳管（默认 {@code CallerRunsPolicy} 回压策略）。
     *
     * @param name          线程池唯一名（同时用于线程名前缀与指标 tag）
     * @param corePoolSize  核心线程数
     * @param maxPoolSize   最大线程数（core=max 时为固定池）
     * @param queueCapacity 有界队列容量（必须 ≥ 1）
     */
    public ExecutorService create(String name, int corePoolSize, int maxPoolSize, int queueCapacity)
    {
        return create(name, corePoolSize, maxPoolSize, queueCapacity, new ThreadPoolExecutor.CallerRunsPolicy());
    }

    /**
     * 创建有界线程池并纳管（自定义拒绝策略）。
     */
    public ExecutorService create(String name, int corePoolSize, int maxPoolSize, int queueCapacity,
            RejectedExecutionHandler rejectedHandler)
    {
        if (name == null || name.isEmpty())
        {
            throw new IllegalArgumentException("ExecutorRegistry: name 不能为空");
        }
        if (pools.containsKey(name))
        {
            throw new IllegalStateException("ExecutorRegistry: 线程池重复注册 name=" + name);
        }
        final AtomicInteger threadSeq = new AtomicInteger(1);
        ThreadPoolExecutor pool = new ThreadPoolExecutor(
                Math.max(1, corePoolSize), Math.max(1, maxPoolSize),
                60L, TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(Math.max(1, queueCapacity)),
                r -> {
                    Thread t = new Thread(r, name + "-" + threadSeq.getAndIncrement());
                    t.setDaemon(true);
                    return t;
                },
                rejectedHandler);
        registerMetrics(name, pool);
        pools.put(name, pool);
        queueCapacities.put(name, Math.max(1, queueCapacity));
        watermarkAlerted.put(name, new AtomicBoolean(false));
        log.info("[ExecutorRegistry] 线程池注册 name={} core={} max={} queue={} reject={}",
                name, corePoolSize, maxPoolSize, queueCapacity, rejectedHandler.getClass().getSimpleName());
        return pool;
    }

    /** 绑定 Micrometer 指标（executor.queued/active/pool.size/completed 等）；无 MeterRegistry 时跳过 */
    private void registerMetrics(String name, ThreadPoolExecutor pool)
    {
        MeterRegistry registry = meterRegistryProvider.getIfAvailable();
        if (registry == null)
        {
            log.debug("[ExecutorRegistry] 无 MeterRegistry，跳过指标绑定 name={}", name);
            return;
        }
        try
        {
            new ExecutorServiceMetrics(pool, name, Tags.of("name", name)).bindTo(registry);
        }
        catch (Exception e)
        {
            log.warn("[ExecutorRegistry] 指标绑定失败 name={}: {}", name, e.getMessage());
        }
    }

    /** 队列水位巡检：占比 ≥ 80% 告警一次（去重），回落后复位允许再次告警 */
    private void checkWatermarks()
    {
        pools.forEach((name, pool) -> {
            int cap = queueCapacities.getOrDefault(name, 1);
            int used = pool.getQueue().size();
            double ratio = (double) used / cap;
            AtomicBoolean alerted = watermarkAlerted.get(name);
            if (ratio >= WATERMARK_RATIO)
            {
                if (alerted != null && alerted.compareAndSet(false, true))
                {
                    log.warn("[ExecutorRegistry] 线程池队列水位告警 name={} used={} capacity={} ratio={}%，请关注消费侧是否阻塞",
                            name, used, cap, String.format("%.0f", ratio * 100));
                }
            }
            else if (alerted != null)
            {
                alerted.set(false);
            }
        });
    }

    /** 统一优雅停机：shutdown → await 10s → shutdownNow（与业务侧 @PreDestroy 幂等共存） */
    @PreDestroy
    public void shutdownAll()
    {
        watermarkChecker.shutdownNow();
        pools.forEach((name, pool) -> {
            pool.shutdown();
            try
            {
                if (!pool.awaitTermination(SHUTDOWN_WAIT_SECONDS, TimeUnit.SECONDS))
                {
                    pool.shutdownNow();
                }
            }
            catch (InterruptedException e)
            {
                pool.shutdownNow();
                Thread.currentThread().interrupt();
            }
            log.info("[ExecutorRegistry] 线程池已关闭 name={}", name);
        });
    }
}
