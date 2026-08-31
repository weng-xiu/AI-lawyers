package ai.lawyers.system.service.lawyers.rag;

/**
 * T3 RAG 知识分块与向量索引服务。
 *
 * <p>负责把审核通过的知识切分为语义分块、写入 ai_legal_knowledge_chunk、
 * 调用 embedding 生成向量并回填 LONGBLOB，最后刷新应用内内存向量索引。
 * 建索引为异步执行（单线程串行），不阻塞知识增删改主流程；embedding 不可用时
 * 分块与 FULLTEXT 关键词路仍可用。</p>
 *
 * @author ai-lawyers
 */
public interface IRagIndexService
{
    /**
     * 全量重建索引：清空分块表 → 对全部启用且审核通过的知识分块 → 批量向量化 → 刷新内存索引。
     * 异步执行，立即返回。
     */
    void rebuildAllAsync();

    /**
     * 重建单条知识的分块索引（新增/修改知识后触发）：删除该知识旧分块 → 重新分块 → 向量化 → 刷新内存索引。
     * 异步执行，立即返回。
     *
     * @param knowledgeId 知识ID
     */
    void rebuildKnowledgeAsync(Long knowledgeId);

    /**
     * 删除知识时清理其分块并刷新内存索引。
     *
     * @param knowledgeIds 被删除的知识ID集合
     */
    void onKnowledgeDeleted(Long[] knowledgeIds);

    /**
     * 启动时从分块表加载已有向量到内存索引（不重新调 embedding）。
     */
    void loadIndexOnStartup();

    /**
     * @return 内存向量索引当前条目数
     */
    int indexedVectorCount();
}
