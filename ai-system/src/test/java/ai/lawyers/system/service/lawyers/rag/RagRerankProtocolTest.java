package ai.lawyers.system.service.lawyers.rag;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import ai.lawyers.system.domain.lawyers.AiLegalKnowledgeChunk;

/**
 * P3-E1：rerank 响应协议解析测试（多供应商兼容）。
 *
 * <p>核心回归点：results/data/顶层数组三种结果位置、relevance_score/score 两种分数字段、
 * 按分数降序、index 越界跳过、不可解析返回空（调用方据此退回 RRF 融合序）。</p>
 *
 * @author ai-lawyers
 */
class RagRerankProtocolTest
{
    private static final ObjectMapper M = new ObjectMapper();

    private List<RagChunk> candidates(int n)
    {
        List<RagChunk> list = new ArrayList<>();
        for (int i = 0; i < n; i++)
        {
            AiLegalKnowledgeChunk c = new AiLegalKnowledgeChunk();
            c.setChunkId((long) (i + 1));
            c.setChunkContent("doc-" + i);
            list.add(new RagChunk(c, 0.1d * (i + 1)));
        }
        return list;
    }

    private JsonNode json(String s) throws Exception
    {
        return M.readTree(s);
    }

    // ---------- 结果数组位置兼容 ----------

    @Test
    void resultsWrapper_relevanceScore_sortedDesc() throws Exception
    {
        // Cohere / SiliconFlow 风格
        JsonNode root = json("{\"results\":["
                + "{\"index\":0,\"relevance_score\":0.2},"
                + "{\"index\":1,\"relevance_score\":0.9},"
                + "{\"index\":2,\"relevance_score\":0.5}]}");

        List<RagChunk> out = RagSearchService.applyRerankResponse(candidates(3), root);

        assertThat(out).extracting(RagChunk::getChunkId).containsExactly(2L, 3L, 1L);
        assertThat(out.get(0).getScore()).isCloseTo(0.9d, org.assertj.core.data.Offset.offset(0.0001d));
    }

    @Test
    void resultsWrapper_scoreField_supported() throws Exception
    {
        // Jina 新版 / TEI 系风格：score 而非 relevance_score
        JsonNode root = json("{\"model\":\"jina-reranker\",\"results\":["
                + "{\"index\":0,\"score\":0.95},"
                + "{\"index\":1,\"score\":0.1}]}");

        List<RagChunk> out = RagSearchService.applyRerankResponse(candidates(2), root);

        assertThat(out).extracting(RagChunk::getChunkId).containsExactly(1L, 2L);
        assertThat(out.get(0).getScore()).isCloseTo(0.95d, org.assertj.core.data.Offset.offset(0.0001d));
    }

    @Test
    void dataWrapper_supported() throws Exception
    {
        // 部分国产网关：{data:[{index, score}]}
        JsonNode root = json("{\"data\":["
                + "{\"index\":1,\"relevance_score\":0.8},"
                + "{\"index\":0,\"relevance_score\":0.3}]}");

        List<RagChunk> out = RagSearchService.applyRerankResponse(candidates(2), root);

        assertThat(out).extracting(RagChunk::getChunkId).containsExactly(2L, 1L);
    }

    @Test
    void topLevelArray_supported() throws Exception
    {
        // 裸数组形态
        JsonNode root = json("[{\"index\":1,\"score\":0.7},{\"index\":0,\"score\":0.2}]");

        List<RagChunk> out = RagSearchService.applyRerankResponse(candidates(2), root);

        assertThat(out).extracting(RagChunk::getChunkId).containsExactly(2L, 1L);
    }

    // ---------- 边界 ----------

    @Test
    void outOfRangeIndex_skipped() throws Exception
    {
        // index=9 越界跳过，只保留合法条目
        JsonNode root = json("{\"results\":["
                + "{\"index\":9,\"relevance_score\":0.99},"
                + "{\"index\":0,\"relevance_score\":0.4}]}");

        List<RagChunk> out = RagSearchService.applyRerankResponse(candidates(2), root);

        assertThat(out).hasSize(1);
        assertThat(out.get(0).getChunkId()).isEqualTo(1L);
    }

    @Test
    void emptyResults_returnsEmpty() throws Exception
    {
        assertThat(RagSearchService.applyRerankResponse(candidates(2), json("{\"results\":[]}"))).isEmpty();
        assertThat(RagSearchService.applyRerankResponse(candidates(2), json("{\"unexpected\":true}"))).isEmpty();
        assertThat(RagSearchService.applyRerankResponse(candidates(2), json("{}"))).isEmpty();
    }

    @Test
    void missingScore_defaultsZero() throws Exception
    {
        // 无分数字段按 0 处理，不抛异常
        JsonNode root = json("{\"results\":[{\"index\":0}]}");
        List<RagChunk> out = RagSearchService.applyRerankResponse(candidates(1), root);
        assertThat(out).hasSize(1);
        assertThat(out.get(0).getScore()).isEqualTo(0d);
    }

    // ---------- 截断 ----------

    @Test
    void truncate_overLimit_cutAtLimit() throws Exception
    {
        RagSearchService svc = new RagSearchService();
        org.springframework.test.util.ReflectionTestUtils.setField(svc, "chunkMaxLen", 10);
        String in = "一二三四五六七八九十一二三四五";
        assertThat(svc.truncateForRerank(in)).hasSize(10);
        assertThat(svc.truncateForRerank("短文本")).isEqualTo("短文本");
        assertThat(svc.truncateForRerank(null)).isEqualTo("");
    }
}
