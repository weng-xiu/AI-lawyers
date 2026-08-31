package ai.lawyers.system.mapper.lawyers;

import java.util.List;
import org.apache.ibatis.annotations.Param;
import ai.lawyers.system.domain.lawyers.AiLegalKnowledgeChunk;

/**
 * 法律知识分块 数据层（T3 RAG）
 *
 * @author ai-lawyers
 */
public interface AiLegalKnowledgeChunkMapper
{
    /**
     * 加载全部分块（含 embedding 字节），供启动/重建内存向量索引。
     *
     * @return 分块集合
     */
    public List<AiLegalKnowledgeChunk> selectAllChunksForIndex();

    /**
     * 关键词路（FULLTEXT ngram 自然语言模式）召回；FULLTEXT 不可用时由上层改调 like 兜底。
     *
     * @param keyword      原始问句/关键词
     * @param knowledgeIds 限定知识ID范围（可空）
     * @param limit        召回条数
     * @return 命中分块（不含 embedding 大字段）
     */
    public List<AiLegalKnowledgeChunk> searchByFulltext(@Param("keyword") String keyword,
                                                        @Param("knowledgeIds") List<Long> knowledgeIds,
                                                        @Param("limit") int limit);

    /**
     * 关键词路 LIKE 兜底（FULLTEXT 不可用或无结果时）。
     *
     * @param keyword      原始问句/关键词
     * @param knowledgeIds 限定知识ID范围（可空）
     * @param limit        召回条数
     * @return 命中分块
     */
    public List<AiLegalKnowledgeChunk> searchByLike(@Param("keyword") String keyword,
                                                    @Param("knowledgeIds") List<Long> knowledgeIds,
                                                    @Param("limit") int limit);

    /**
     * 新增分块
     *
     * @param chunk 分块
     * @return 结果
     */
    public int insertChunk(AiLegalKnowledgeChunk chunk);

    /**
     * 批量回填分块向量
     *
     * @param chunkId 分块ID
     * @param embedding 向量字节
     * @param dim 向量维度
     * @return 结果
     */
    public int updateChunkEmbedding(@Param("chunkId") Long chunkId,
                                    @Param("embedding") byte[] embedding,
                                    @Param("dim") int dim);

    /**
     * 删除指定知识的全部分块（重建前清理）
     *
     * @param knowledgeId 知识ID
     * @return 结果
     */
    public int deleteByKnowledgeId(@Param("knowledgeId") Long knowledgeId);

    /**
     * 批量删除多个知识的全部分块（知识被删除时清理）
     *
     * @param knowledgeIds 知识ID集合
     * @return 结果
     */
    public int deleteByKnowledgeIds(@Param("knowledgeIds") Long[] knowledgeIds);

    /**
     * 清空分块表（全量重建前清理）
     *
     * @return 结果
     */
    public int deleteAll();

    /**
     * 统计已向量化的分块数（embedding_dim &gt; 0）
     *
     * @return 数量
     */
    public int countVectorized();
}
