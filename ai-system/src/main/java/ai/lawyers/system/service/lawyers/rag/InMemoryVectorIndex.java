package ai.lawyers.system.service.lawyers.rag;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.springframework.stereotype.Component;
import ai.lawyers.system.domain.lawyers.AiLegalKnowledgeChunk;

/**
 * T3 RAG 应用内内存向量索引（零新中间件方案 A）。
 *
 * <p>启动/重建时从 ai_legal_knowledge_chunk 加载已向量化分块（向量存 LONGBLOB 备份），
 * 驻留 JVM 内存；查询时对 query 向量做暴力余弦近邻。万级分块、512~768 维下，
 * 单次近邻为毫秒级，完全满足热线知识库规模；数据量更大时可平滑替换为 Milvus 等向量库
 * （仅需替换本类实现，RagSearchService 不感知）。</p>
 *
 * <p>线程模型：用 volatile 快照 + 原子引用整体替换，重建期间读请求仍走旧快照，无锁读。</p>
 *
 * @author ai-lawyers
 */
@Component
public class InMemoryVectorIndex
{
    /** 索引条目：分块元信息 + 向量 */
    public static class Entry
    {
        final AiLegalKnowledgeChunk chunk;
        final float[] vector;

        Entry(AiLegalKnowledgeChunk chunk, float[] vector)
        {
            this.chunk = chunk;
            this.vector = vector;
        }

        public AiLegalKnowledgeChunk getChunk() { return chunk; }
    }

    /** 当前索引快照（整体替换，读不加锁） */
    private final AtomicReference<List<Entry>> snapshot = new AtomicReference<>(new ArrayList<>());

    /**
     * 用全量分块重建内存索引（仅收录 embedding_dim &gt; 0 且向量可解析的分块）。
     *
     * @param chunks 全部分块（含 embedding 字节）
     * @return 成功载入的向量条目数
     */
    public int rebuild(List<AiLegalKnowledgeChunk> chunks)
    {
        List<Entry> entries = new ArrayList<>();
        if (chunks != null)
        {
            for (AiLegalKnowledgeChunk c : chunks)
            {
                if (c.getEmbeddingDim() == null || c.getEmbeddingDim() <= 0 || c.getEmbedding() == null)
                {
                    continue;
                }
                float[] vec = VectorUtils.fromBytes(c.getEmbedding());
                if (vec.length > 0)
                {
                    entries.add(new Entry(c, vec));
                }
            }
        }
        snapshot.set(entries);
        return entries.size();
    }

    /**
     * 余弦近邻检索。
     *
     * @param queryVector  查询向量
     * @param knowledgeIds 限定知识ID范围（可空表示不限）
     * @param topN         返回条数
     * @return 按相似度降序的命中条目（相似度得分为余弦值）
     */
    public List<RagChunk> searchNearest(float[] queryVector, List<Long> knowledgeIds, int topN)
    {
        List<Entry> entries = snapshot.get();
        List<RagChunk> hits = new ArrayList<>();
        if (queryVector == null || queryVector.length == 0 || entries.isEmpty())
        {
            return hits;
        }
        for (Entry e : entries)
        {
            if (knowledgeIds != null && !knowledgeIds.isEmpty()
                    && (e.chunk.getKnowledgeId() == null
                        || !knowledgeIds.contains(e.chunk.getKnowledgeId())))
            {
                continue;
            }
            float score = VectorUtils.cosine(queryVector, e.vector);
            if (score > 0f)
            {
                hits.add(new RagChunk(e.chunk, score));
            }
        }
        hits.sort(Comparator.comparingDouble(RagChunk::getScore).reversed());
        if (hits.size() > topN)
        {
            return new ArrayList<>(hits.subList(0, topN));
        }
        return hits;
    }

    /** 当前索引向量条目数 */
    public int size()
    {
        return snapshot.get().size();
    }

    /** 是否已就绪（有可用向量） */
    public boolean isReady()
    {
        return !snapshot.get().isEmpty();
    }
}
