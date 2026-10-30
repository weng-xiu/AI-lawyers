package ai.lawyers.system.service.lawyers.rag;

import java.util.Date;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import ai.lawyers.common.utils.MdcUtils;
import ai.lawyers.common.utils.StringUtils;
import ai.lawyers.system.domain.lawyers.AiKnowledgeQueryLog;
import ai.lawyers.system.mapper.lawyers.AiKnowledgeQueryLogMapper;
import ai.lawyers.system.service.lawyers.concurrent.ExecutorRegistry;

/**
 * F8 知识检索查询日志记录器。
 *
 * <p>在 RAG 检索返回后异步落一条 {@code ai_knowledge_query_log}，用于运营统计
 * 命中率与未命中问句排行。<strong>工程红线</strong>：</p>
 * <ol>
 *   <li>永不影响检索：独立守护线程异步落库，提交/落库异常仅记日志；</li>
 *   <li>队列有界：满后丢弃并限频 WARN，不阻塞问答主链路；</li>
 *   <li>表未创建时静默：插入失败限频告警，检索照常返回。</li>
 * </ol>
 *
 * <p>问句先经 {@link KnowledgeQueryTextUtils#maskForStore(String)} 脱敏截断，
 * 分组使用归一化哈希；开关 {@code ai.knowledge.query-log.enabled} 默认开启。</p>
 *
 * @author ai-lawyers
 */
@Component
public class KnowledgeQueryLogRecorder
{
    private static final Logger log = LoggerFactory.getLogger(KnowledgeQueryLogRecorder.class);

    /** 检索场景 */
    public static final String SCENE_AGENT = "agent";
    public static final String SCENE_VOICE_ROBOT = "voice_robot";
    public static final String SCENE_COPILOT = "copilot";
    public static final String SCENE_OTHER = "other";

    private static final int QUEUE_CAPACITY = 2000;
    private static final long WARN_INTERVAL_MS = 60_000L;

    /** 总开关（SQL 未执行/故障排查时可关），默认开启 */
    @Value("${ai.knowledge.query-log.enabled:true}")
    private boolean enabled;

    @Autowired
    private AiKnowledgeQueryLogMapper queryLogMapper;

    /** 线程池统一注册（可选注入；缺省时回退自建同规格池，单测环境不受影响） */
    @Autowired(required = false)
    private ExecutorRegistry registry;

    private ExecutorService executor;
    private final AtomicLong droppedCount = new AtomicLong(0);
    private volatile long lastInsertWarn = 0L;
    private volatile long lastDropWarn = 0L;

    @PostConstruct
    public void init()
    {
        RejectedExecutionHandler dropHandler = (r, e) ->
        {
            long dropped = droppedCount.incrementAndGet();
            long now = System.currentTimeMillis();
            if (now - lastDropWarn > WARN_INTERVAL_MS)
            {
                lastDropWarn = now;
                log.warn("知识检索日志队列已满（{}），累计丢弃 {} 条，请检查 ai_knowledge_query_log 落库性能或扩容",
                        QUEUE_CAPACITY, dropped);
            }
        };
        if (registry != null)
        {
            this.executor = registry.create("ai-knowledge-query-log", 1, 1, QUEUE_CAPACITY, dropHandler);
        }
        else
        {
            this.executor = new ThreadPoolExecutor(1, 1, 0L, TimeUnit.MILLISECONDS,
                    new LinkedBlockingQueue<>(QUEUE_CAPACITY),
                    new ThreadFactory()
                    {
                        private final AtomicInteger seq = new AtomicInteger(1);

                        @Override
                        public Thread newThread(Runnable r)
                        {
                            Thread t = new Thread(r, "ai-knowledge-query-log-" + seq.getAndIncrement());
                            t.setDaemon(true);
                            return t;
                        }
                    },
                    dropHandler);
        }
        log.info("知识检索查询日志记录器初始化 enabled={} queueCapacity={} managedByRegistry={}",
                enabled, QUEUE_CAPACITY, registry != null);
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

    /**
     * 异步记录一次检索（best-effort，永不抛异常）。
     *
     * @param query     原始问句（内部脱敏截断）
     * @param scene     检索场景，见 {@code SCENE_*} 常量
     * @param hits      命中结果（可为 null/空）
     * @param route     命中路由 fused/vector/keyword/none
     * @param latencyMs 检索耗时（毫秒）
     */
    public void record(String query, String scene, List<RagChunk> hits, String route, long latencyMs)
    {
        if (!enabled || KnowledgeQueryTextUtils.isBlank(query))
        {
            return;
        }
        try
        {
            String masked = KnowledgeQueryTextUtils.maskForStore(query);
            String hash = KnowledgeQueryTextUtils.hash(query);
            if (hash.isEmpty() || masked.isEmpty())
            {
                return;
            }
            AiKnowledgeQueryLog entity = new AiKnowledgeQueryLog();
            entity.setQueryText(masked);
            entity.setQueryHash(hash);
            entity.setScene(StringUtils.isEmpty(scene) ? SCENE_OTHER
                    : (scene.length() > 32 ? scene.substring(0, 32) : scene));
            int hitCount = hits == null ? 0 : hits.size();
            entity.setHitCount(hitCount);
            entity.setTopKnowledgeId(hitCount > 0 ? hits.get(0).getKnowledgeId() : null);
            entity.setRoute(StringUtils.isEmpty(route) ? "none"
                    : (route.length() > 16 ? route.substring(0, 16) : route));
            entity.setLatencyMs((int) Math.min(Math.max(latencyMs, 0L), Integer.MAX_VALUE));
            entity.setConverted(0);
            entity.setCreateTime(new Date());
            submit(entity);
        }
        catch (Exception e)
        {
            log.debug("知识检索日志组装失败：{}", e.getMessage());
        }
    }

    /**
     * 未命中问句转草稿成功后标记已转化（同步小更新，best-effort）。
     */
    public void markConverted(String queryHash)
    {
        if (!enabled || StringUtils.isEmpty(queryHash))
        {
            return;
        }
        try
        {
            queryLogMapper.markConverted(queryHash);
        }
        catch (Exception e)
        {
            warnInsertFailure(e);
        }
    }

    /** 累计丢弃条数（供运维排查） */
    public long getDroppedCount()
    {
        return droppedCount.get();
    }

    private void submit(AiKnowledgeQueryLog entity)
    {
        try
        {
            executor.execute(MdcUtils.wrap(() ->
            {
                try
                {
                    queryLogMapper.insertKnowledgeQueryLog(entity);
                }
                catch (Exception e)
                {
                    warnInsertFailure(e);
                }
            }));
        }
        catch (Exception e)
        {
            // Executor 已关闭等极端情况：静默，绝不影响检索
            log.debug("知识检索日志提交失败：{}", e.getMessage());
        }
    }

    private void warnInsertFailure(Exception e)
    {
        long now = System.currentTimeMillis();
        if (now - lastInsertWarn > WARN_INTERVAL_MS)
        {
            lastInsertWarn = now;
            log.warn("知识检索日志落库失败（限频告警，不影响检索），请确认 SQL ai_system_knowledge_query_log_20261030.sql 已执行：{}",
                    e.getMessage());
        }
    }
}
