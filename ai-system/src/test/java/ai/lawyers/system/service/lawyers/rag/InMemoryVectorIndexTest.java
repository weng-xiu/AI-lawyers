package ai.lawyers.system.service.lawyers.rag;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ai.lawyers.system.domain.lawyers.AiLegalKnowledgeChunk;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * T5-2 RAG 检索纯逻辑测试：内存向量索引近邻排序、知识范围过滤、topN 截断、RRF 融合。
 *
 * @author ai-lawyers
 */
class InMemoryVectorIndexTest
{
    private InMemoryVectorIndex index;

    /** 构造一个带向量的知识分块 */
    private AiLegalKnowledgeChunk chunk(long chunkId, long knowledgeId, float[] vec)
    {
        AiLegalKnowledgeChunk c = new AiLegalKnowledgeChunk();
        c.setChunkId(chunkId);
        c.setKnowledgeId(knowledgeId);
        c.setChunkContent("content-" + chunkId);
        c.setEmbeddingDim(vec.length);
        c.setEmbedding(VectorUtils.toBytes(vec));
        return c;
    }

    @BeforeEach
    void setUp()
    {
        index = new InMemoryVectorIndex();
        // 两个二维向量：A 沿 x 轴、B 沿 y 轴；query 沿 x 轴时 A 相似度更高
        List<AiLegalKnowledgeChunk> chunks = Arrays.asList(
                chunk(1L, 100L, new float[]{1f, 0f}),
                chunk(2L, 100L, new float[]{0f, 1f}),
                chunk(3L, 200L, new float[]{0.9f, 0.1f})  // 另一知识库
        );
        assertThat(index.rebuild(chunks)).isEqualTo(3);
        assertThat(index.size()).isEqualTo(3);
        assertThat(index.isReady()).isTrue();
    }

    @Test
    void searchNearest_ordersByCosineDesc()
    {
        List<RagChunk> hits = index.searchNearest(new float[]{1f, 0f}, null, 10);
        assertThat(hits).isNotEmpty();
        // 与 x 轴同向的 chunk 1 得分最高，应排第一
        assertThat(hits.get(0).getChunkId()).isEqualTo(1L);
        // 结果按 score 降序
        for (int i = 1; i < hits.size(); i++)
        {
            assertThat(hits.get(i - 1).getScore()).isGreaterThanOrEqualTo(hits.get(i).getScore());
        }
    }

    @Test
    void searchNearest_filtersByKnowledgeId()
    {
        List<RagChunk> hits = index.searchNearest(new float[]{1f, 0f}, Arrays.asList(200L), 10);
        assertThat(hits).isNotEmpty();
        assertThat(hits).allMatch(h -> Long.valueOf(200L).equals(h.getKnowledgeId()));
        assertThat(hits.get(0).getChunkId()).isEqualTo(3L);
    }

    @Test
    void searchNearest_respectsTopN()
    {
        List<RagChunk> hits = index.searchNearest(new float[]{1f, 0f}, null, 1);
        assertThat(hits).hasSize(1);
        assertThat(hits.get(0).getChunkId()).isEqualTo(1L);
    }

    @Test
    void searchNearest_emptyQuery_returnsEmpty()
    {
        assertThat(index.searchNearest(null, null, 10)).isEmpty();
        assertThat(index.searchNearest(new float[]{}, null, 10)).isEmpty();
    }

    @Test
    void rebuild_skipsUnvectorizedChunks()
    {
        List<AiLegalKnowledgeChunk> bad = new ArrayList<>();
        AiLegalKnowledgeChunk noVec = chunk(9L, 1L, new float[]{1f});
        noVec.setEmbedding(null);
        AiLegalKnowledgeChunk zeroDim = chunk(10L, 1L, new float[]{1f});
        zeroDim.setEmbeddingDim(0);
        bad.add(noVec);
        bad.add(zeroDim);
        InMemoryVectorIndex fresh = new InMemoryVectorIndex();
        assertThat(fresh.rebuild(bad)).isZero();
        assertThat(fresh.isReady()).isFalse();
    }

    @Test
    void rrfFuse_sumsScoresForChunkHitByBothRoutes() throws Exception
    {
        // chunk 1 关键词路 rank0（1/(60+1)）、向量路 rank0（1/61），双路命中应累加
        RagSearchService service = new RagSearchService();
        Method m = RagSearchService.class.getDeclaredMethod("rrfFuse", List.class, List.class);
        m.setAccessible(true);

        AiLegalKnowledgeChunk kw1 = chunk(1L, 1L, new float[]{1f});
        AiLegalKnowledgeChunk kw2 = chunk(2L, 1L, new float[]{1f});
        AiLegalKnowledgeChunk vecChunk3 = chunk(3L, 1L, new float[]{1f});

        List<AiLegalKnowledgeChunk> keywordHits = Arrays.asList(kw1, kw2);
        List<RagChunk> vectorHits = Arrays.asList(
                new RagChunk(kw1, 0d),           // chunk1 也在向量路（双路）
                new RagChunk(vecChunk3, 0d));    // chunk3 仅向量路

        @SuppressWarnings("unchecked")
        Map<Long, RagChunk> fused = (Map<Long, RagChunk>) m.invoke(service, keywordHits, vectorHits);

        assertThat(fused).containsKeys(1L, 2L, 3L);
        double expectedChunk1 = 1d / 61 + 1d / 61; // 双路累加
        assertThat(fused.get(1L).getScore()).isEqualTo(expectedChunk1, org.assertj.core.data.Offset.offset(1e-9));
        assertThat(fused.get(2L).getScore()).isEqualTo(1d / 62, org.assertj.core.data.Offset.offset(1e-9));
        assertThat(fused.get(3L).getScore()).isEqualTo(1d / 62, org.assertj.core.data.Offset.offset(1e-9));
    }
}
