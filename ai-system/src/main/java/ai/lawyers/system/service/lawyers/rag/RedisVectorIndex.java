package ai.lawyers.system.service.lawyers.rag;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import ai.lawyers.system.domain.lawyers.AiLegalKnowledgeChunk;

/**
 * P3-C5：Redis Stack（RediSearch VSS）向量索引实现，多实例共享。
 *
 * <p>存储模型：每个分块一个 HASH（key = {@code rag:chunk:{chunkId}}），字段含
 * knowledge_id/title/chunk_index/category_id/law_article/source/chunk_content/embedding（FLOAT32 blob）；
 * 其上建 RediSearch 索引 {@code rag:chunk:idx}（FLAT 暴力 + COSINE 距离——千~万级分块下精确检索
 * 毫秒级，且无需调 HNSW 参数；规模上到十万级再改 HNSW 仅改 FT.CREATE 一处）。</p>
 *
 * <p>检索：FT.SEARCH KNN（{@code *=>[KNN n @embedding $BLOB AS dist]}），TAG 过滤
 * {@code @knowledge_id:{id1|id2}} 实现知识范围限定；相似度 = 1 - cosine_distance，
 * 与 {@link InMemoryVectorIndex} 的余弦口径一致（同样过滤 sim &lt;= 0）。</p>
 *
 * <p>spring-data-redis 2.5 无 RediSearch 支持，统一走 raw {@code connection.execute}
 * （与 XAUTOCLAIM 相同的既有模式）。索引 DIM 在重建时取首个有效向量维度并记录在
 * {@code rag:chunk:meta} HASH（dim 字段）；维度变化时 DROP INDEX 重建。</p>
 *
 * <p>本类不直接注册为 Spring Bean，由 {@link VectorIndexRouter} 持有并按开关路由；
 * 所有 Redis 异常向上抛出，由 Router 降级到内存快照。</p>
 *
 * @author ai-lawyers
 */
public class RedisVectorIndex implements VectorIndex
{
    private static final Logger log = LoggerFactory.getLogger(RedisVectorIndex.class);

    /** RediSearch 索引名 */
    static final String INDEX = "rag:chunk:idx";

    /** 分块 HASH key 前缀 */
    static final String KEY_PREFIX = "rag:chunk:";

    /** 元信息 key（记录当前索引向量维度） */
    static final String META_KEY = "rag:chunk:meta";

    private final StringRedisTemplate redis;

    /** 当前已载入条目数（rebuild 时更新） */
    private volatile int size = 0;

    /** 索引是否就绪（rebuild 成功后置 true） */
    private volatile boolean ready = false;

    public RedisVectorIndex(StringRedisTemplate redis)
    {
        this.redis = redis;
    }

    // ------------------------------------------------------------------ 写入

    @Override
    public int rebuild(List<AiLegalKnowledgeChunk> chunks)
    {
        // 收集有效分块
        List<AiLegalKnowledgeChunk> valid = new ArrayList<>();
        if (chunks != null)
        {
            for (AiLegalKnowledgeChunk c : chunks)
            {
                if (c.getChunkId() == null || c.getEmbeddingDim() == null || c.getEmbeddingDim() <= 0
                        || c.getEmbedding() == null || c.getEmbedding().length == 0)
                {
                    continue;
                }
                valid.add(c);
            }
        }
        // 维度取自首个有效向量；空库也要清理旧索引（知识可能全部删除）
        int dim = valid.isEmpty() ? readMetaDim() : valid.get(0).getEmbeddingDim();
        dropIndexAndKeys();
        if (valid.isEmpty() || dim <= 0)
        {
            size = 0;
            ready = false;
            log.info("RAG Redis 向量索引重建完成：空索引");
            return 0;
        }
        createIndex(dim);
        for (AiLegalKnowledgeChunk c : valid)
        {
            writeChunk(c);
        }
        writeMetaDim(dim);
        size = valid.size();
        ready = true;
        log.info("RAG Redis 向量索引重建完成：条目={} dim={}", valid.size(), dim);
        return valid.size();
    }

    private void writeChunk(AiLegalKnowledgeChunk c)
    {
        String key = KEY_PREFIX + c.getChunkId();
        redis.execute((RedisCallback<Object>) connection ->
        {
            List<byte[]> args = new ArrayList<>();
            args.add(utf8(key));
            putField(args, "knowledge_id", c.getKnowledgeId());
            putField(args, "title", c.getTitle());
            putField(args, "chunk_index", c.getChunkIndex());
            putField(args, "category_id", c.getCategoryId());
            putField(args, "law_article", c.getLawArticle());
            putField(args, "source", c.getSource());
            putField(args, "chunk_content", c.getChunkContent());
            args.add(utf8("embedding"));
            args.add(c.getEmbedding());
            connection.execute("HSET", args.toArray(new byte[0][]));
            return null;
        });
    }

    private void putField(List<byte[]> args, String field, Object value)
    {
        if (value == null)
        {
            return;
        }
        args.add(utf8(field));
        args.add(utf8(String.valueOf(value)));
    }

    // ------------------------------------------------------------------ 检索

    @Override
    public List<RagChunk> searchNearest(float[] queryVector, List<Long> knowledgeIds, int topN)
    {
        if (queryVector == null || queryVector.length == 0 || !ready)
        {
            return new ArrayList<>();
        }
        int n = topN > 0 ? topN : 10;
        StringBuilder query = new StringBuilder();
        if (knowledgeIds != null && !knowledgeIds.isEmpty())
        {
            query.append("(@knowledge_id:{");
            for (int i = 0; i < knowledgeIds.size(); i++)
            {
                if (i > 0)
                {
                    query.append('|');
                }
                query.append(knowledgeIds.get(i));
            }
            query.append("})");
        }
        else
        {
            query.append('*');
        }
        query.append("=>[KNN ").append(n).append(" @embedding $BLOB AS dist]");

        Object raw = redis.execute((RedisCallback<Object>) connection ->
                connection.execute("FT.SEARCH",
                        utf8(INDEX), utf8(query.toString()),
                        utf8("PARAMS"), utf8("2"), utf8("BLOB"), VectorUtils.toBytes(queryVector),
                        utf8("SORTBY"), utf8("dist"), utf8("ASC"),
                        utf8("LIMIT"), utf8("0"), utf8(String.valueOf(n)),
                        utf8("RETURN"), utf8("8"),
                        utf8("knowledge_id"), utf8("title"), utf8("chunk_index"), utf8("category_id"),
                        utf8("law_article"), utf8("source"), utf8("chunk_content"), utf8("dist"),
                        utf8("DIALECT"), utf8("2")));
        return parseSearchReply(raw);
    }

    /**
     * 解析 FT.SEARCH RESP 回复：[total, key1, [f1,v1,...], key2, [...], ...]。
     * 包级可见供测试。
     */
    @SuppressWarnings("unchecked")
    static List<RagChunk> parseSearchReply(Object raw)
    {
        List<RagChunk> hits = new ArrayList<>();
        if (!(raw instanceof List))
        {
            return hits;
        }
        List<Object> reply = (List<Object>) raw;
        // i=0 是 total；之后成对出现 docKey、字段数组
        for (int i = 1; i + 1 < reply.size(); i += 2)
        {
            String docKey = str(reply.get(i));
            Object fieldsObj = reply.get(i + 1);
            if (!(fieldsObj instanceof List))
            {
                continue;
            }
            List<Object> fields = (List<Object>) fieldsObj;
            AiLegalKnowledgeChunk chunk = new AiLegalKnowledgeChunk();
            Double dist = null;
            for (int j = 0; j + 1 < fields.size(); j += 2)
            {
                String field = str(fields.get(j));
                String value = str(fields.get(j + 1));
                if (field == null || value == null)
                {
                    continue;
                }
                switch (field)
                {
                    case "knowledge_id":
                        chunk.setKnowledgeId(parseLong(value));
                        break;
                    case "title":
                        chunk.setTitle(value);
                        break;
                    case "chunk_index":
                        chunk.setChunkIndex(parseInt(value));
                        break;
                    case "category_id":
                        chunk.setCategoryId(parseLong(value));
                        break;
                    case "law_article":
                        chunk.setLawArticle(value);
                        break;
                    case "source":
                        chunk.setSource(value);
                        break;
                    case "chunk_content":
                        chunk.setChunkContent(value);
                        break;
                    case "dist":
                        dist = parseDouble(value);
                        break;
                    default:
                        break;
                }
            }
            // key 形如 rag:chunk:{chunkId}
            if (docKey != null && docKey.startsWith(KEY_PREFIX))
            {
                chunk.setChunkId(parseLong(docKey.substring(KEY_PREFIX.length())));
            }
            if (chunk.getChunkId() == null || dist == null)
            {
                continue;
            }
            double similarity = 1.0d - dist;
            if (similarity > 0d)
            {
                hits.add(new RagChunk(chunk, similarity));
            }
        }
        // SORTBY dist ASC 已保证序；转换后仍按相似度降序稳态输出
        hits.sort((a, b) -> Double.compare(b.getScore(), a.getScore()));
        return hits;
    }

    // ------------------------------------------------------------------ 索引管理

    /** FT.CREATE：knowledge_id TAG + embedding VECTOR FLAT（COSINE） */
    private void createIndex(int dim)
    {
        redis.execute((RedisCallback<Object>) connection ->
                connection.execute("FT.CREATE",
                        utf8(INDEX),
                        utf8("ON"), utf8("HASH"),
                        utf8("PREFIX"), utf8("1"), utf8(KEY_PREFIX),
                        utf8("SCHEMA"),
                        utf8("knowledge_id"), utf8("TAG"),
                        utf8("embedding"), utf8("VECTOR"), utf8("FLAT"), utf8("6"),
                        utf8("TYPE"), utf8("FLOAT32"),
                        utf8("DIM"), utf8(String.valueOf(dim)),
                        utf8("DISTANCE_METRIC"), utf8("COSINE")));
    }

    /** DROP INDEX（保留文档，key 由下方 SCAN 清理）+ 删除全部 rag:chunk:* 数据 key */
    private void dropIndexAndKeys()
    {
        try
        {
            redis.execute((RedisCallback<Object>) connection ->
                    connection.execute("FT.DROPINDEX", utf8(INDEX)));
        }
        catch (Exception e)
        {
            // 索引不存在等情况忽略
            log.debug("FT.DROPINDEX 忽略异常：{}", e.getMessage());
        }
        // SCAN 清理旧 key（避免 KEYS 阻塞；count 500 分批）
        redis.execute((RedisCallback<Object>) connection ->
        {
            try (org.springframework.data.redis.core.Cursor<byte[]> cursor = connection.scan(
                    org.springframework.data.redis.core.ScanOptions.scanOptions()
                            .match(KEY_PREFIX + "*").count(500).build()))
            {
                List<byte[]> batch = new ArrayList<>();
                while (cursor.hasNext())
                {
                    byte[] key = cursor.next();
                    if (META_KEY.equals(new String(key, StandardCharsets.UTF_8)))
                    {
                        continue;
                    }
                    batch.add(key);
                    if (batch.size() >= 500)
                    {
                        connection.execute("DEL", batch.toArray(new byte[0][]));
                        batch.clear();
                    }
                }
                if (!batch.isEmpty())
                {
                    connection.execute("DEL", batch.toArray(new byte[0][]));
                }
            }
            return null;
        });
    }

    private int readMetaDim()
    {
        try
        {
            Object v = redis.opsForHash().get(META_KEY, "dim");
            return v == null ? 0 : Integer.parseInt(String.valueOf(v));
        }
        catch (Exception e)
        {
            return 0;
        }
    }

    private void writeMetaDim(int dim)
    {
        try
        {
            redis.opsForHash().put(META_KEY, "dim", String.valueOf(dim));
        }
        catch (Exception e)
        {
            log.debug("写入向量维度元信息失败：{}", e.getMessage());
        }
    }

    // ------------------------------------------------------------------ 状态

    @Override
    public int size()
    {
        return size;
    }

    @Override
    public boolean isReady()
    {
        return ready;
    }

    // ------------------------------------------------------------------ 工具

    private static byte[] utf8(String s)
    {
        return s.getBytes(StandardCharsets.UTF_8);
    }

    private static String str(Object o)
    {
        if (o == null)
        {
            return null;
        }
        if (o instanceof byte[])
        {
            return new String((byte[]) o, StandardCharsets.UTF_8);
        }
        return String.valueOf(o);
    }

    private static Long parseLong(String s)
    {
        try
        {
            return Long.valueOf(s);
        }
        catch (Exception e)
        {
            return null;
        }
    }

    private static Integer parseInt(String s)
    {
        try
        {
            return Integer.valueOf(s);
        }
        catch (Exception e)
        {
            return null;
        }
    }

    private static Double parseDouble(String s)
    {
        try
        {
            return Double.valueOf(s);
        }
        catch (Exception e)
        {
            return null;
        }
    }
}
