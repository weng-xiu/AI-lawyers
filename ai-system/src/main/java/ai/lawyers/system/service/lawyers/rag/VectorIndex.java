package ai.lawyers.system.service.lawyers.rag;

import java.util.List;
import ai.lawyers.system.domain.lawyers.AiLegalKnowledgeChunk;

/**
 * RAG 向量索引抽象（P3-C5）。
 *
 * <p>实现：{@link InMemoryVectorIndex}（JVM 内存，默认/降级）、
 * {@link RedisVectorIndex}（Redis Stack VSS，多实例共享）；
 * 由 {@link VectorIndexRouter} 按 {@code ai.rag.vector-store} 路由并负责故障降级。</p>
 *
 * @author ai-lawyers
 */
public interface VectorIndex
{
    /**
     * 用全量分块重建索引（仅收录 embedding_dim &gt; 0 且向量可解析的分块）。
     *
     * @param chunks 全部分块（含 embedding 字节）
     * @return 成功载入的向量条目数
     */
    int rebuild(List<AiLegalKnowledgeChunk> chunks);

    /**
     * 余弦近邻检索。
     *
     * @param queryVector  查询向量
     * @param knowledgeIds 限定知识ID范围（可空表示不限）
     * @param topN         返回条数
     * @return 按相似度降序的命中条目（得分为余弦相似度）
     */
    List<RagChunk> searchNearest(float[] queryVector, List<Long> knowledgeIds, int topN);

    /** 当前索引向量条目数 */
    int size();

    /** 是否已就绪（有可用向量） */
    boolean isReady();
}
