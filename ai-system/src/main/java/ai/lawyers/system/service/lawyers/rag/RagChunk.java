package ai.lawyers.system.service.lawyers.rag;

import ai.lawyers.system.domain.lawyers.AiLegalKnowledgeChunk;

/**
 * T3 RAG 检索命中结果（携带融合得分与溯源信息）。
 *
 * @author ai-lawyers
 */
public class RagChunk
{
    /** 分块实体 */
    private final AiLegalKnowledgeChunk chunk;

    /** 融合/精排得分（越大越相关） */
    private double score;

    public RagChunk(AiLegalKnowledgeChunk chunk, double score)
    {
        this.chunk = chunk;
        this.score = score;
    }

    public AiLegalKnowledgeChunk getChunk() { return chunk; }

    public double getScore() { return score; }
    public void setScore(double score) { this.score = score; }

    public Long getChunkId() { return chunk.getChunkId(); }
    public Long getKnowledgeId() { return chunk.getKnowledgeId(); }
    public String getTitle() { return chunk.getTitle(); }
    public String getContent() { return chunk.getChunkContent(); }
    public String getLawArticle() { return chunk.getLawArticle(); }
    public String getSource() { return chunk.getSource(); }
}
