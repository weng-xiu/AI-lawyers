package ai.lawyers.system.service.lawyers.rag;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import ai.lawyers.system.domain.lawyers.AiLegalKnowledgeChunk;

/**
 * RAG 命中结果测试：得分可更新、溯源字段透传自分块实体。
 *
 * @author ai-lawyers
 */
class RagChunkTest
{
    @Test
    void getters_透传分块元数据()
    {
        AiLegalKnowledgeChunk chunk = chunk();
        RagChunk ragChunk = new RagChunk(chunk, 0.86d);

        assertEquals(Long.valueOf(11L), ragChunk.getChunkId());
        assertEquals(Long.valueOf(22L), ragChunk.getKnowledgeId());
        assertEquals("劳动合同解除", ragChunk.getTitle());
        assertEquals("用人单位应当出具解除证明。", ragChunk.getContent());
        assertEquals("劳动合同法第五十条", ragChunk.getLawArticle());
        assertEquals("法律法规库", ragChunk.getSource());
        assertEquals(0.86d, ragChunk.getScore());
    }

    @Test
    void setScore_支持精排后更新得分()
    {
        RagChunk ragChunk = new RagChunk(chunk(), 0.5d);

        ragChunk.setScore(0.95d);

        assertEquals(0.95d, ragChunk.getScore());
    }

    /** 构造示例知识分块 */
    private AiLegalKnowledgeChunk chunk()
    {
        AiLegalKnowledgeChunk chunk = new AiLegalKnowledgeChunk();
        chunk.setChunkId(11L);
        chunk.setKnowledgeId(22L);
        chunk.setTitle("劳动合同解除");
        chunk.setChunkContent("用人单位应当出具解除证明。");
        chunk.setLawArticle("劳动合同法第五十条");
        chunk.setSource("法律法规库");
        return chunk;
    }
}
