package ai.lawyers.system.service.lawyers.stat;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Date;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import ai.lawyers.common.utils.MdcUtils;
import ai.lawyers.common.utils.StringUtils;
import ai.lawyers.system.domain.lawyers.AiModelConfig;
import ai.lawyers.system.domain.lawyers.stat.AiModelCallLog;
import ai.lawyers.system.mapper.lawyers.stat.AiModelCallLogMapper;

/**
 * 大模型调用日志记录器（P3-E5）。
 *
 * <p>模型调用结束后把 Token 用量、耗时、结果与费用快照写入 ai_model_call_log。
 * <strong>三条工程红线</strong>：</p>
 * <ol>
 *   <li>永不影响业务：独立守护线程异步落库，提交/落库任何异常仅记日志不外抛；</li>
 *   <li>永不拖垮模型线程：有界队列（2000）满后丢弃最旧任务并计数，限频 WARN，不阻塞调用方；</li>
 *   <li>表/SQL 未就位时静默：插入异常限频告警，应用与模型调用正常运行。</li>
 * </ol>
 *
 * <p>费用按调用时刻模型配置上的单价（元/千Token）快照折算，调价不改变历史费用口径。</p>
 *
 * @author ai-lawyers
 * @date 2026-09-25
 */
@Component
public class AiModelCallLogRecorder
{
    private static final Logger log = LoggerFactory.getLogger(AiModelCallLogRecorder.class);

    /** 调用类型 */
    public static final String KIND_CHAT = "chat";
    public static final String KIND_EMBED = "embed";
    public static final String KIND_RERANK = "rerank";

    /** 业务场景 */
    public static final String SCENE_INTENTION = "intention";
    public static final String SCENE_EMOTION = "emotion";
    public static final String SCENE_EXTRACT = "extract";
    public static final String SCENE_QUALITY = "quality";
    public static final String SCENE_SUMMARY = "summary";
    public static final String SCENE_AGENT = "agent";
    public static final String SCENE_CONSULTATION = "consultation";
    public static final String SCENE_RAG = "rag";
    public static final String SCENE_RAG_INDEX = "rag_index";
    public static final String SCENE_TEST = "test";
    public static final String SCENE_OTHER = "other";

    private static final int QUEUE_CAPACITY = 2000;
    private static final int FAIL_REASON_MAX = 500;
    /** 落库/丢弃告警限频间隔（毫秒），防止异常期日志风暴 */
    private static final long WARN_INTERVAL_MS = 60_000L;

    /** 总开关（SQL 未执行/故障排查时可关），默认开启 */
    @Value("${ai.model.call-log.enabled:true}")
    private boolean enabled;

    @Autowired
    private AiModelCallLogMapper mapper;

    private ExecutorService executor;
    private final AtomicLong droppedCount = new AtomicLong(0);
    private volatile long lastInsertWarn = 0L;
    private volatile long lastDropWarn = 0L;

    @PostConstruct
    public void init()
    {
        // 队列满不阻塞业务线程：拒绝时丢弃当前任务，由记录器计数并限频 WARN 暴露
        this.executor = new ThreadPoolExecutor(1, 1, 0L, TimeUnit.MILLISECONDS,
                new LinkedBlockingQueue<>(QUEUE_CAPACITY),
                new ThreadFactory()
                {
                    private final AtomicInteger n = new AtomicInteger(1);

                    @Override
                    public Thread newThread(Runnable r)
                    {
                        Thread t = new Thread(r, "ai-model-call-log-" + n.getAndIncrement());
                        t.setDaemon(true);
                        return t;
                    }
                },
                (r, e) ->
                {
                    long d = droppedCount.incrementAndGet();
                    long now = System.currentTimeMillis();
                    if (now - lastDropWarn > WARN_INTERVAL_MS)
                    {
                        lastDropWarn = now;
                        log.warn("大模型调用日志队列已满（{}），累计丢弃 {} 条，请检查 ai_model_call_log 落库性能或扩容",
                                QUEUE_CAPACITY, d);
                    }
                });
        log.info("大模型调用日志记录器初始化 enabled={} queueCapacity={}", enabled, QUEUE_CAPACITY);
    }

    @PreDestroy
    public void shutdown()
    {
        if (executor == null)
        {
            return;
        }
        executor.shutdown();
        try
        {
            // 停机尽量把队列里的费用日志写完（最多 10s）
            if (!executor.awaitTermination(10, TimeUnit.SECONDS))
            {
                executor.shutdownNow();
            }
        }
        catch (InterruptedException e)
        {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    /** 带模型配置快照的记录入口（chat/embed 走模型配置） */
    public void record(String kind, String scene, AiModelConfig config, ModelUsage usage,
                       long elapsedMs, int attempts, String result, String failReason)
    {
        if (!enabled)
        {
            return;
        }
        Long configId = config == null ? null : config.getConfigId();
        String configName = config == null ? null : config.getConfigName();
        String modelType = config == null ? null : config.getModelType();
        String modelName = config == null ? null : config.getModelName();
        BigDecimal inputPrice = config == null ? null : config.getInputPrice();
        BigDecimal outputPrice = config == null ? null : config.getOutputPrice();
        submit(build(kind, scene, configId, configName, modelType, modelName,
                inputPrice, outputPrice, usage, elapsedMs, attempts, result, failReason));
    }

    /** 无模型配置的记录入口（rerank 独立服务，仅有模型名） */
    public void recordWithoutConfig(String kind, String scene, String modelType, String modelName,
                                    ModelUsage usage, long elapsedMs, int attempts,
                                    String result, String failReason)
    {
        if (!enabled)
        {
            return;
        }
        submit(build(kind, scene, null, null, modelType, modelName,
                null, null, usage, elapsedMs, attempts, result, failReason));
    }

    private AiModelCallLog build(String kind, String scene, Long configId, String configName,
                                 String modelType, String modelName, BigDecimal inputPrice,
                                 BigDecimal outputPrice, ModelUsage usage, long elapsedMs,
                                 int attempts, String result, String failReason)
    {
        ModelUsage u = usage == null ? ModelUsage.ZERO : usage;
        AiModelCallLog entity = new AiModelCallLog();
        entity.setCallTime(new Date());
        entity.setKind(StringUtils.isEmpty(kind) ? KIND_CHAT : kind);
        entity.setScene(StringUtils.isEmpty(scene) ? SCENE_OTHER : scene);
        entity.setConfigId(configId);
        entity.setConfigName(truncate(configName, 100));
        entity.setModelType(truncate(modelType, 32));
        entity.setModelName(truncate(modelName, 100));
        entity.setPromptTokens(u.getPromptTokens());
        entity.setCompletionTokens(u.getCompletionTokens());
        entity.setTotalTokens(u.getTotalTokens());
        entity.setInputPrice(inputPrice);
        entity.setOutputPrice(outputPrice);
        entity.setCostAmount(calcCost(u.getPromptTokens(), u.getCompletionTokens(), inputPrice, outputPrice));
        entity.setElapsedMs((int) Math.min(Math.max(elapsedMs, 0L), Integer.MAX_VALUE));
        entity.setAttempts(Math.max(attempts, 1));
        entity.setResult(StringUtils.isEmpty(result) ? AiModelCallLog.RESULT_FAIL : result);
        entity.setFailReason(truncate(failReason, FAIL_REASON_MAX));
        // traceId 在调用线程快照（异步线程 MDC 已通过 MdcUtils.wrap 透传，双保险显式落字段）
        entity.setTraceId(truncate(MdcUtils.getTraceId(), 64));
        return entity;
    }

    private void submit(AiModelCallLog entity)
    {
        try
        {
            executor.execute(MdcUtils.wrap(() ->
            {
                try
                {
                    mapper.insertAiModelCallLog(entity);
                }
                catch (Exception e)
                {
                    long now = System.currentTimeMillis();
                    if (now - lastInsertWarn > WARN_INTERVAL_MS)
                    {
                        lastInsertWarn = now;
                        log.warn("大模型调用日志落库失败（限频告警，不影响业务），请确认 SQL ai_system_model_cost_20260925.sql 已执行：{}",
                                e.getMessage());
                    }
                }
            }));
        }
        catch (Exception e)
        {
            // Executor 已关闭等极端情况：静默，绝不影响模型调用
            log.debug("大模型调用日志提交失败：{}", e.getMessage());
        }
    }

    /**
     * 费用折算：输入/输出 Token 数（个）÷1000 × 对应单价（元/千Token），保留 6 位小数。
     * 单价为空（未配置计费价格）时费用记 0，不影响调用量/耗时统计。
     */
    public static BigDecimal calcCost(int promptTokens, int completionTokens,
                                      BigDecimal inputPrice, BigDecimal outputPrice)
    {
        BigDecimal cost = BigDecimal.ZERO;
        if (inputPrice != null && promptTokens > 0)
        {
            cost = cost.add(inputPrice.multiply(BigDecimal.valueOf(promptTokens))
                    .divide(BigDecimal.valueOf(1000), 6, RoundingMode.HALF_UP));
        }
        if (outputPrice != null && completionTokens > 0)
        {
            cost = cost.add(outputPrice.multiply(BigDecimal.valueOf(completionTokens))
                    .divide(BigDecimal.valueOf(1000), 6, RoundingMode.HALF_UP));
        }
        return cost.setScale(6, RoundingMode.HALF_UP);
    }

    /** 累计丢弃条数（供运维指标/排查） */
    public long getDroppedCount()
    {
        return droppedCount.get();
    }

    private static String truncate(String s, int max)
    {
        if (s == null)
        {
            return null;
        }
        String v = s.trim();
        return v.length() > max ? v.substring(0, max) : v;
    }
}
