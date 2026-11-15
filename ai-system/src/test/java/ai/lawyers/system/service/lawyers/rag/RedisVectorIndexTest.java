package ai.lawyers.system.service.lawyers.rag;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import ai.lawyers.system.domain.lawyers.AiLegalKnowledgeChunk;

/**
 * P3-C5：{@link RedisVectorIndex} Redis Stack 向量索引测试。
 *
 * <p>覆盖重建时的分块过滤与状态、空库重建、检索前置条件、FT.SEARCH 回复解析
 * （字段映射、相似度换算、越界/非法条目跳过与降序输出）。</p>
 *
 * @author ai-lawyers
 */
class RedisVectorIndexTest
{
    private StringRedisTemplate redis;

    private RedisVectorIndex index;

    @BeforeEach
    void setUp()
    {
        redis = mock(StringRedisTemplate.class);
        index = new RedisVectorIndex(redis);
    }

    @Test
    void rebuildSkipsInvalidChunks()
    {
        List<AiLegalKnowledgeChunk> chunks = new ArrayList<>();
        chunks.add(chunk(1L, 3, VectorUtils.toBytes(new float[] { 1f, 0f, 0f })));
        chunks.add(chunk(null, 3, VectorUtils.toBytes(new float[] { 1f })));
        chunks.add(chunk(2L, null, VectorUtils.toBytes(new float[] { 1f })));
        chunks.add(chunk(3L, 0, VectorUtils.toBytes(new float[] { 1f })));
        chunks.add(chunk(4L, 3, null));
        chunks.add(chunk(5L, 3, new byte[0]));

        assertThat(index.rebuild(chunks)).isEqualTo(1);
        assertThat(index.size()).isEqualTo(1);
        assertThat(index.isReady()).isTrue();
    }

    @Test
    void rebuildNullClearsIndex()
    {
        index.rebuild(Arrays.asList(chunk(1L, 2, VectorUtils.toBytes(new float[] { 1f, 0f }))));

        assertThat(index.rebuild(null)).isZero();
        assertThat(index.size()).isZero();
        assertThat(index.isReady()).isFalse();
    }

    @Test
    void searchNearestRequiresVectorAndReadyState()
    {
        assertThat(index.searchNearest(new float[] { 1f }, null, 5)).isEmpty();

        index.rebuild(Arrays.asList(chunk(1L, 2, VectorUtils.toBytes(new float[] { 1f, 0f }))));
        assertThat(index.searchNearest(null, null, 5)).isEmpty();
        assertThat(index.searchNearest(new float[0], null, 5)).isEmpty();
    }

    @Test
    @SuppressWarnings("unchecked")
    void searchNearestParsesSearchReply()
    {
        index.rebuild(Arrays.asList(chunk(1L, 2, VectorUtils.toBytes(new float[] { 1f, 0f }))));
        List<Object> reply = Arrays.asList(2L,
                "rag:chunk:1", fields("11", "0.2"),
                "rag:chunk:2", fields("12", "0.8"));
        when(redis.execute(any(RedisCallback.class))).thenReturn(reply);

        List<RagChunk> hits = index.searchNearest(new float[] { 1f, 0f },
                Arrays.asList(11L, 12L), 5);

        assertThat(hits).extracting(RagChunk::getChunkId).containsExactly(1L, 2L);
        assertThat(hits.get(0).getScore()).isEqualTo(0.8d);
        assertThat(hits.get(0).getContent()).isEqualTo("内容11");
        assertThat(hits.get(1).getKnowledgeId()).isEqualTo(12L);
    }

    @Test
    @SuppressWarnings("unchecked")
    void searchNearestWithoutKnowledgeFilter()
    {
        index.rebuild(Arrays.asList(chunk(1L, 2, VectorUtils.toBytes(new float[] { 1f, 0f }))));
        when(redis.execute(any(RedisCallback.class))).thenReturn(null);

        assertThat(index.searchNearest(new float[] { 1f, 0f }, null, 0)).isEmpty();
    }

    @Test
    void parseSearchReplyHandlesMalformedReplies()
    {
        assertThat(RedisVectorIndex.parseSearchReply(null)).isEmpty();
        assertThat(RedisVectorIndex.parseSearchReply("raw")).isEmpty();
        assertThat(RedisVectorIndex.parseSearchReply(new ArrayList<>())).isEmpty();
        assertThat(RedisVectorIndex.parseSearchReply(Arrays.asList(0L))).isEmpty();
        assertThat(RedisVectorIndex.parseSearchReply(Arrays.asList(1L, "rag:chunk:1", "not-a-list"))).isEmpty();
        assertThat(RedisVectorIndex.parseSearchReply(Arrays.asList(1L, "other:1", fields("11", "0.2")))).isEmpty();
        assertThat(RedisVectorIndex.parseSearchReply(Arrays.asList(1L, "rag:chunk:1", fields("11", "abc")))).isEmpty();
    }

    @Test
    void parseSearchReplyFiltersNonPositiveSimilarityAndBadFields()
    {
        List<Object> malformedFields = Arrays.asList("knowledge_id", null, "title", "合同", "dist", "0.3");

        List<RagChunk> hits = RedisVectorIndex.parseSearchReply(Arrays.asList(2L,
                "rag:chunk:9", fields("9", "1.0"),
                "rag:chunk:8", fields("8", "1.5"),
                "rag:chunk:7", malformedFields));

        assertThat(hits).extracting(RagChunk::getChunkId).containsExactly(7L);
        assertThat(hits.get(0).getScore()).isEqualTo(0.7d);
        assertThat(hits.get(0).getTitle()).isEqualTo("合同");
    }

    @Test
    void parseSearchReplyToleratesInvalidNumericFields()
    {
        List<Object> fields = Arrays.asList(
                "knowledge_id", "abc",
                "chunk_index", "x",
                "category_id", "y",
                "dist", "0.1");

        List<RagChunk> hits = RedisVectorIndex.parseSearchReply(Arrays.asList(1L, "rag:chunk:6", fields));

        assertThat(hits).hasSize(1);
        assertThat(hits.get(0).getKnowledgeId()).isNull();
        assertThat(hits.get(0).getChunk().getChunkIndex()).isNull();
        assertThat(hits.get(0).getChunk().getCategoryId()).isNull();
    }

    @Test
    @SuppressWarnings("unchecked")
    void parseSearchReplyAcceptsByteFields()
    {
        List<Object> fields = Arrays.asList(
                utf8("knowledge_id"), utf8("42"),
                utf8("title"), utf8("标题"),
                utf8("chunk_content"), utf8("正文"),
                utf8("dist"), utf8("0.25"));
        List<Object> reply = Arrays.asList(1L, utf8("rag:chunk:3"), fields);

        List<RagChunk> hits = RedisVectorIndex.parseSearchReply(reply);

        assertThat(hits).hasSize(1);
        assertThat(hits.get(0).getChunkId()).isEqualTo(3L);
        assertThat(hits.get(0).getKnowledgeId()).isEqualTo(42L);
        assertThat(hits.get(0).getTitle()).isEqualTo("标题");
        assertThat(hits.get(0).getScore()).isEqualTo(0.75d);
    }

    private List<Object> fields(String knowledgeId, String dist)
    {
        return Arrays.asList("knowledge_id", knowledgeId, "title", "标题",
                "chunk_index", "0", "category_id", "5", "law_article", "第十条",
                "source", "民法典", "chunk_content", "内容" + knowledgeId, "dist", dist);
    }

    private byte[] utf8(String s)
    {
        return s.getBytes(StandardCharsets.UTF_8);
    }

    private AiLegalKnowledgeChunk chunk(Long chunkId, Integer dim, byte[] embedding)
    {
        AiLegalKnowledgeChunk c = new AiLegalKnowledgeChunk();
        c.setChunkId(chunkId);
        c.setEmbeddingDim(dim);
        c.setEmbedding(embedding);
        return c;
    }
}
