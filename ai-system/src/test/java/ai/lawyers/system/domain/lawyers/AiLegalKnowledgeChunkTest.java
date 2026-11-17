package ai.lawyers.system.domain.lawyers;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

/**
 * 法律知识分块实体测试：属性读写与默认值。
 *
 * @author ai-lawyers
 */
class AiLegalKnowledgeChunkTest
{
    @Test
    void 属性读写()
    {
        byte[] embedding = new byte[] {1, 2, 3, 4};
        AiLegalKnowledgeChunk chunk = new AiLegalKnowledgeChunk();
        chunk.setChunkId(1L);
        chunk.setKnowledgeId(2L);
        chunk.setTitle("劳动合同解除情形");
        chunk.setChunkIndex(0);
        chunk.setChunkContent("劳动合同法第三十六条：协商一致可以解除");
        chunk.setLawArticle("劳动合同法第三十六条");
        chunk.setSource("国家法律法规数据库");
        chunk.setCategoryId(3L);
        chunk.setEmbedding(embedding);
        chunk.setEmbeddingDim(4);
        chunk.setStatus("0");

        assertEquals(1L, chunk.getChunkId());
        assertEquals(2L, chunk.getKnowledgeId());
        assertEquals("劳动合同解除情形", chunk.getTitle());
        assertEquals(0, chunk.getChunkIndex());
        assertEquals("劳动合同法第三十六条：协商一致可以解除", chunk.getChunkContent());
        assertEquals("劳动合同法第三十六条", chunk.getLawArticle());
        assertEquals("国家法律法规数据库", chunk.getSource());
        assertEquals(3L, chunk.getCategoryId());
        assertArrayEquals(embedding, chunk.getEmbedding());
        assertEquals(4, chunk.getEmbeddingDim());
        assertEquals("0", chunk.getStatus());
    }

    @Test
    void 默认值为空()
    {
        AiLegalKnowledgeChunk chunk = new AiLegalKnowledgeChunk();

        assertNull(chunk.getChunkId());
        assertNull(chunk.getKnowledgeId());
        assertNull(chunk.getTitle());
        assertNull(chunk.getChunkIndex());
        assertNull(chunk.getChunkContent());
        assertNull(chunk.getEmbedding());
        assertNull(chunk.getEmbeddingDim());
        assertNull(chunk.getStatus());
    }
}
