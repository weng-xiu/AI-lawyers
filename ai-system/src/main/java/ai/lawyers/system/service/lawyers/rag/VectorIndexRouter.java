package ai.lawyers.system.service.lawyers.rag;

import java.util.List;
import javax.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import ai.lawyers.system.domain.lawyers.AiLegalKnowledgeChunk;

/**
 * P3-C5：向量索引路由门面（调用方唯一注入点，{@code @Primary}）。
 *
 * <p>{@code ai.rag.vector-store} 取值：
 * <ul>
 *   <li>{@code memory}（默认）：纯 JVM 内存索引（单实例场景，行为与 P3-C5 前完全一致）；</li>
 *   <li>{@code redis}：写入侧双写 Redis Stack + 内存快照，读取侧优先 Redis，
 *       任何 Redis 异常（未装 RediSearch 模块/网络抖动/索引不存在）当次降级内存快照，
 *       保证检索链路永不断路。</li>
 * </ul>
 * </p>
 *
 * <p>内存快照在 redis 模式下也始终维护，作为降级底账；千~万级分块下内存冗余可接受，
 * 十万级后需改为"内存仅存知识ID→Redis 反查"或纯 Redis 读（本类 readFallback 开关预留）。</p>
 *
 * @author ai-lawyers
 */
@Primary
@Component
public class VectorIndexRouter implements VectorIndex
{
    private static final Logger log = LoggerFactory.getLogger(VectorIndexRouter.class);

    /** 存储后端：memory（默认）| redis */
    @Value("${ai.rag.vector-store:memory}")
    private String vectorStore;

    @Autowired
    private InMemoryVectorIndex memoryIndex;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    /** Redis 实现（非 Spring Bean，由本门面持有） */
    private RedisVectorIndex redisIndex;

    @PostConstruct
    public void init()
    {
        if (isRedisMode())
        {
            redisIndex = new RedisVectorIndex(stringRedisTemplate);
            log.info("RAG 向量索引后端：redis（Redis Stack VSS），内存快照作为降级底账");
        }
        else
        {
            log.info("RAG 向量索引后端：memory（JVM 内存，单实例模式）");
        }
    }

    public boolean isRedisMode()
    {
        return "redis".equalsIgnoreCase(vectorStore);
    }

    /**
     * 当前生效后端描述（运维确认用）：redis 模式返回 redis(ready=true/false)，否则 memory。
     */
    public String backend()
    {
        if (isRedisMode())
        {
            return "redis(ready=" + (redisIndex != null && redisIndex.isReady()) + ")";
        }
        return "memory";
    }

    @Override
    public int rebuild(List<AiLegalKnowledgeChunk> chunks)
    {
        // 内存快照始终维护（降级底账 + memory 模式主索引）
        int memoryLoaded = memoryIndex.rebuild(chunks);
        if (isRedisMode())
        {
            try
            {
                int redisLoaded = redisIndex.rebuild(chunks);
                log.info("RAG 向量索引双写完成：redis={} memory={}", redisLoaded, memoryLoaded);
                return redisLoaded;
            }
            catch (Exception e)
            {
                // Redis 写失败：内存快照仍在，检索降级可用；下次重建自动重试
                log.warn("RAG Redis 向量索引重建失败，降级为纯内存模式（内存已载入{}条）：{}",
                        memoryLoaded, e.getMessage());
            }
        }
        return memoryLoaded;
    }

    @Override
    public List<RagChunk> searchNearest(float[] queryVector, List<Long> knowledgeIds, int topN)
    {
        if (isRedisMode() && redisIndex.isReady())
        {
            try
            {
                return redisIndex.searchNearest(queryVector, knowledgeIds, topN);
            }
            catch (Exception e)
            {
                log.warn("RAG Redis 向量检索失败，当次降级内存快照：{}", e.getMessage());
            }
        }
        return memoryIndex.searchNearest(queryVector, knowledgeIds, topN);
    }

    @Override
    public int size()
    {
        if (isRedisMode() && redisIndex.isReady())
        {
            return redisIndex.size();
        }
        return memoryIndex.size();
    }

    @Override
    public boolean isReady()
    {
        if (isRedisMode() && redisIndex.isReady())
        {
            return true;
        }
        return memoryIndex.isReady();
    }
}
