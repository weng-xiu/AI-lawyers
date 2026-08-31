package ai.lawyers.system.domain.lawyers;

import ai.lawyers.common.core.domain.BaseEntity;

/**
 * 法律知识分块对象 ai_legal_knowledge_chunk（T3 RAG）
 *
 * <p>将审核通过的知识按标题/法条 + 正文切分为语义分块；chunk_content 建 ngram
 * FULLTEXT 索引供关键词路召回，embedding 向量落 LONGBLOB 备份并在启动/重建后
 * 加载进应用内内存向量索引供向量路近邻召回。</p>
 *
 * @author ai-lawyers
 */
public class AiLegalKnowledgeChunk extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    /** 分块ID */
    private Long chunkId;

    /** 所属知识ID */
    private Long knowledgeId;

    /** 知识标题（冗余，溯源展示） */
    private String title;

    /** 分块序号（知识内从0开始） */
    private Integer chunkIndex;

    /** 分块文本（标题/法条 + 正文片段） */
    private String chunkContent;

    /** 法律条文（冗余溯源） */
    private String lawArticle;

    /** 出处来源（冗余溯源） */
    private String source;

    /** 分类ID（冗余，便于按类过滤） */
    private Long categoryId;

    /** 向量（float[] 小端字节，备份/重建内存索引用） */
    private byte[] embedding;

    /** 向量维度（0=未向量化） */
    private Integer embeddingDim;

    /** 状态（0正常 1停用） */
    private String status;

    public Long getChunkId() { return chunkId; }
    public void setChunkId(Long chunkId) { this.chunkId = chunkId; }

    public Long getKnowledgeId() { return knowledgeId; }
    public void setKnowledgeId(Long knowledgeId) { this.knowledgeId = knowledgeId; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public Integer getChunkIndex() { return chunkIndex; }
    public void setChunkIndex(Integer chunkIndex) { this.chunkIndex = chunkIndex; }

    public String getChunkContent() { return chunkContent; }
    public void setChunkContent(String chunkContent) { this.chunkContent = chunkContent; }

    public String getLawArticle() { return lawArticle; }
    public void setLawArticle(String lawArticle) { this.lawArticle = lawArticle; }

    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }

    public Long getCategoryId() { return categoryId; }
    public void setCategoryId(Long categoryId) { this.categoryId = categoryId; }

    public byte[] getEmbedding() { return embedding; }
    public void setEmbedding(byte[] embedding) { this.embedding = embedding; }

    public Integer getEmbeddingDim() { return embeddingDim; }
    public void setEmbeddingDim(Integer embeddingDim) { this.embeddingDim = embeddingDim; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
}
