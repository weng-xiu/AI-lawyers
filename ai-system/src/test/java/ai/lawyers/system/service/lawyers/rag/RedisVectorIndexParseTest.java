package ai.lawyers.system.service.lawyers.rag;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * P3-C5：RedisVectorIndex FT.SEARCH RESP 回复解析测试。
 *
 * <p>核心回归点：docKey→chunkId 还原、字段映射、相似度=1-余弦距离、
 * sim&lt;=0 过滤（与内存索引 score&gt;0 口径一致）、按相似度降序输出。</p>
 *
 * @author ai-lawyers
 */
class RedisVectorIndexParseTest
{
    private static byte[] b(String s)
    {
        return s.getBytes(StandardCharsets.UTF_8);
    }

    /** 构造一条 FT.SEARCH 文档条目：[key, [f1,v1,f2,v2,...]] */
    private static List<Object> doc(String key, Object... fields)
    {
        return Arrays.asList(b(key), Arrays.asList(fields));
    }

    @Test
    void parse_fullFields_mapsToChunk()
    {
        List<Object> reply = Arrays.asList(
                b("1"),
                b("rag:chunk:101"),
                Arrays.asList(
                        b("knowledge_id"), b("7"),
                        b("title"), b("劳动合同法"),
                        b("chunk_index"), b("0"),
                        b("category_id"), b("3"),
                        b("law_article"), b("第十条"),
                        b("source"), b("法律法规库"),
                        b("chunk_content"), b("建立劳动关系应当订立书面劳动合同"),
                        b("dist"), b("0.15")));

        List<RagChunk> hits = RedisVectorIndex.parseSearchReply(reply);

        assertThat(hits).hasSize(1);
        RagChunk hit = hits.get(0);
        assertThat(hit.getChunkId()).isEqualTo(101L);
        assertThat(hit.getKnowledgeId()).isEqualTo(7L);
        assertThat(hit.getTitle()).isEqualTo("劳动合同法");
        assertThat(hit.getLawArticle()).isEqualTo("第十条");
        assertThat(hit.getContent()).isEqualTo("建立劳动关系应当订立书面劳动合同");
        // 相似度 = 1 - 0.15
        assertThat(hit.getScore()).isCloseTo(0.85d, org.assertj.core.data.Offset.offset(0.0001d));
    }

    @Test
    void parse_filtersNonPositiveSimilarity()
    {
        // dist=1.2 → sim=-0.2 应被过滤（与内存索引 score>0 口径一致）
        List<Object> reply = Arrays.asList(
                b("2"),
                b("rag:chunk:101"), Arrays.asList(b("dist"), b("0.4")),
                b("rag:chunk:102"), Arrays.asList(b("dist"), b("1.2")));

        List<RagChunk> hits = RedisVectorIndex.parseSearchReply(reply);

        assertThat(hits).hasSize(1);
        assertThat(hits.get(0).getChunkId()).isEqualTo(101L);
    }

    @Test
    void parse_sortsBySimilarityDesc()
    {
        List<Object> reply = Arrays.asList(
                b("2"),
                b("rag:chunk:101"), Arrays.asList(b("dist"), b("0.5")),
                b("rag:chunk:102"), Arrays.asList(b("dist"), b("0.1")));

        List<RagChunk> hits = RedisVectorIndex.parseSearchReply(reply);

        assertThat(hits).hasSize(2);
        assertThat(hits.get(0).getChunkId()).isEqualTo(102L);
        assertThat(hits.get(1).getChunkId()).isEqualTo(101L);
    }

    @Test
    void parse_skipsEntryWithoutChunkIdOrDist()
    {
        // key 前缀不符 → 无 chunkId；无 dist 字段 → 跳过
        List<Object> reply = Arrays.asList(
                b("2"),
                b("other:101"), Arrays.asList(b("dist"), b("0.1")),
                b("rag:chunk:102"), Arrays.asList(b("title"), b("无距离")));

        List<RagChunk> hits = RedisVectorIndex.parseSearchReply(reply);

        assertThat(hits).isEmpty();
    }

    @Test
    void parse_nonListReply_returnsEmpty()
    {
        assertThat(RedisVectorIndex.parseSearchReply(null)).isEmpty();
        assertThat(RedisVectorIndex.parseSearchReply("unexpected")).isEmpty();
        assertThat(RedisVectorIndex.parseSearchReply(Collections.emptyList())).isEmpty();
    }
}
