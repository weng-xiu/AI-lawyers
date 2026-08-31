package ai.lawyers.system.service.lawyers.rag;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import javax.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import ai.lawyers.common.utils.StringUtils;
import ai.lawyers.system.domain.lawyers.AiLegalKnowledgeChunk;
import ai.lawyers.system.mapper.lawyers.AiLegalKnowledgeChunkMapper;
import ai.lawyers.system.service.lawyers.IAiModelConfigService;
import ai.lawyers.system.service.lawyers.metrics.HotlineMetrics;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;

/**
 * T3 RAG 混合检索服务。
 *
 * <p>检索链路：用户问句 → ①关键词路（MySQL FULLTEXT ngram，异常/无结果退回 LIKE）
 * → ②向量路（embedding + 应用内内存余弦近邻，embedding 不可用自动跳过）
 * → RRF(k=60) 倒数排名融合去重 →（可选）Cross-Encoder rerank 精排 → Top-K。</p>
 *
 * <p>可用性兜底：任一路异常都不影响另一路；两路均不可用时上层回退旧的整知识 LIKE 检索，
 * 保证问答功能始终可用。</p>
 *
 * @author ai-lawyers
 */
@Service
public class RagSearchService
{
    private static final Logger log = LoggerFactory.getLogger(RagSearchService.class);

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final MediaType JSON_MEDIA_TYPE = MediaType.parse("application/json; charset=utf-8");

    /** RRF 常数（经验值 60） */
    private static final double RRF_K = 60d;

    @Autowired
    private AiLegalKnowledgeChunkMapper chunkMapper;

    @Autowired
    private IAiModelConfigService modelConfigService;

    @Autowired
    private InMemoryVectorIndex vectorIndex;

    /** T5-1：RAG 召回指标（未引入 micrometer 时内部静默） */
    @Autowired(required = false)
    private HotlineMetrics metrics;

    @Value("${ai.rag.enabled:true}")
    private boolean ragEnabled;

    /** 最终拼 Prompt 的知识条数 */
    @Value("${ai.rag.top-k:3}")
    private int topK;

    /** 每路召回候选数（融合池大小） */
    @Value("${ai.rag.candidate-size:20}")
    private int candidateSize;

    /** 是否启用 Cross-Encoder rerank 精排（需配置 rerank-url） */
    @Value("${ai.rag.rerank-enabled:false}")
    private boolean rerankEnabled;

    /** rerank 服务地址（OpenAI 兼容 /rerank，如 bge-reranker 部署端点） */
    @Value("${ai.rag.rerank-url:}")
    private String rerankUrl;

    /** rerank 模型名 */
    @Value("${ai.rag.rerank-model:bge-reranker-base}")
    private String rerankModel;

    /** rerank 服务密钥（可空） */
    @Value("${ai.rag.rerank-api-key:}")
    private String rerankApiKey;

    /** 单块拼入 Prompt 的最大字符数 */
    @Value("${ai.rag.chunk-max-len:500}")
    private int chunkMaxLen;

    private OkHttpClient rerankClient;

    @PostConstruct
    public void init()
    {
        // rerank 为独立可选服务，单独建短连接池客户端（调用量小）
        rerankClient = new OkHttpClient.Builder()
                .connectTimeout(5, TimeUnit.SECONDS)
                .readTimeout(8, TimeUnit.SECONDS)
                .retryOnConnectionFailure(true)
                .build();
    }

    public boolean isEnabled()
    {
        return ragEnabled;
    }

    /**
     * 混合检索。
     *
     * @param query        用户问句
     * @param knowledgeIds 限定知识ID范围（可空，表示全库）
     * @return 融合/精排后的命中分块（按相关性降序）；分块表不可用等异常时返回空列表（上层回退旧路）
     */
    public List<RagChunk> search(String query, List<Long> knowledgeIds)
    {
        if (!ragEnabled || StringUtils.isEmpty(query))
        {
            return new ArrayList<>();
        }
        int cand = candidateSize > 0 ? candidateSize : 20;

        // ① 关键词路：FULLTEXT 优先，异常/无结果退回 LIKE
        List<AiLegalKnowledgeChunk> keywordHits = keywordRoute(query, knowledgeIds, cand);

        // ② 向量路：embedding + 内存近邻；任何异常（未配置/服务不可用）都降级跳过
        List<RagChunk> vectorHits = vectorRoute(query, knowledgeIds, cand);

        // ③ RRF 融合
        Map<Long, RagChunk> fused = rrfFuse(keywordHits, vectorHits);
        // T5-1：RAG 命中路由统计（vector/keyword/fused/none）
        if (metrics != null)
        {
            boolean kwHit = keywordHits != null && !keywordHits.isEmpty();
            boolean vecHit = vectorHits != null && !vectorHits.isEmpty();
            String route = fused.isEmpty() ? "none"
                    : (kwHit && vecHit ? "fused" : (vecHit ? "vector" : "keyword"));
            metrics.incrementRagHit(route);
        }
        if (fused.isEmpty())
        {
            return new ArrayList<>();
        }
        List<RagChunk> candidates = new ArrayList<>(fused.values());
        candidates.sort(Comparator.comparingDouble(RagChunk::getScore).reversed());

        // ④ 可选 rerank 精排
        if (rerankEnabled && StringUtils.isNotEmpty(rerankUrl))
        {
            candidates = rerank(query, candidates);
        }

        int k = topK > 0 ? topK : 3;
        return candidates.size() > k ? new ArrayList<>(candidates.subList(0, k)) : candidates;
    }

    /** 关键词路：FULLTEXT ngram，异常或无结果退回 LIKE */
    private List<AiLegalKnowledgeChunk> keywordRoute(String query, List<Long> knowledgeIds, int limit)
    {
        try
        {
            List<AiLegalKnowledgeChunk> hits = chunkMapper.searchByFulltext(query, knowledgeIds, limit);
            if (hits != null && !hits.isEmpty())
            {
                return hits;
            }
        }
        catch (Exception e)
        {
            // FULLTEXT 索引/ngram 解析器不可用，降级 LIKE
            log.debug("RAG FULLTEXT 召回失败，降级 LIKE：{}", e.getMessage());
        }
        try
        {
            List<AiLegalKnowledgeChunk> hits = chunkMapper.searchByLike(query, knowledgeIds, limit);
            return hits == null ? new ArrayList<>() : hits;
        }
        catch (Exception e)
        {
            log.warn("RAG LIKE 关键词召回失败：{}", e.getMessage());
            return new ArrayList<>();
        }
    }

    /** 向量路：问句 embedding 后内存余弦近邻；未配置 embedding 或服务异常时返回空 */
    private List<RagChunk> vectorRoute(String query, List<Long> knowledgeIds, int limit)
    {
        if (!vectorIndex.isReady())
        {
            return new ArrayList<>();
        }
        try
        {
            List<float[]> qVec = modelConfigService.embedTexts(java.util.Collections.singletonList(query));
            if (qVec == null || qVec.isEmpty() || qVec.get(0).length == 0)
            {
                return new ArrayList<>();
            }
            return vectorIndex.searchNearest(qVec.get(0), knowledgeIds, limit);
        }
        catch (Exception e)
        {
            log.debug("RAG 向量召回跳过（embedding 不可用）：{}", e.getMessage());
            return new ArrayList<>();
        }
    }

    /**
     * RRF 倒数排名融合：score = Σ 1/(k + rank)，rank 从 1 起；同 chunk 跨路命中累加。
     */
    private Map<Long, RagChunk> rrfFuse(List<AiLegalKnowledgeChunk> keywordHits, List<RagChunk> vectorHits)
    {
        Map<Long, RagChunk> fused = new LinkedHashMap<>();
        if (keywordHits != null)
        {
            for (int rank = 0; rank < keywordHits.size(); rank++)
            {
                AiLegalKnowledgeChunk c = keywordHits.get(rank);
                fused.put(c.getChunkId(), new RagChunk(c, 1d / (RRF_K + rank + 1)));
            }
        }
        if (vectorHits != null)
        {
            for (int rank = 0; rank < vectorHits.size(); rank++)
            {
                RagChunk rc = vectorHits.get(rank);
                RagChunk existing = fused.get(rc.getChunkId());
                double add = 1d / (RRF_K + rank + 1);
                if (existing != null)
                {
                    existing.setScore(existing.getScore() + add);
                }
                else
                {
                    fused.put(rc.getChunkId(), new RagChunk(rc.getChunk(), add));
                }
            }
        }
        return fused;
    }

    /**
     * 可选 Cross-Encoder rerank：POST {rerank-url} {query, documents[]}，
     * 按返回 index/relevance_score 重排。任何异常退回融合序。
     */
    private List<RagChunk> rerank(String query, List<RagChunk> candidates)
    {
        try
        {
            ObjectNode body = MAPPER.createObjectNode();
            body.put("model", rerankModel);
            body.put("query", query);
            ArrayNode docs = body.putArray("documents");
            for (RagChunk rc : candidates)
            {
                docs.add(rc.getContent());
            }
            Request.Builder builder = new Request.Builder().url(rerankUrl)
                    .post(RequestBody.create(JSON_MEDIA_TYPE,
                            MAPPER.writeValueAsString(body).getBytes(StandardCharsets.UTF_8)));
            if (StringUtils.isNotEmpty(rerankApiKey))
            {
                builder.header("Authorization", "Bearer " + rerankApiKey);
            }
            try (Response response = rerankClient.newCall(builder.build()).execute())
            {
                ResponseBody rb = response.body();
                String text = rb == null ? "" : rb.string();
                if (!response.isSuccessful() || StringUtils.isEmpty(text))
                {
                    return candidates;
                }
                JsonNode root = MAPPER.readTree(text);
                JsonNode results = root.path("results");
                if (!results.isArray() || results.size() == 0)
                {
                    return candidates;
                }
                // results[].index 指向入参 documents 的下标，按下标取回候选并按分数降序
                List<RagChunk> reranked = new ArrayList<>();
                List<JsonNode> items = new ArrayList<>();
                for (JsonNode item : results)
                {
                    items.add(item);
                }
                items.sort(Comparator.comparingDouble((JsonNode it) ->
                        it.path("relevance_score").asDouble(0d)).reversed());
                for (JsonNode item : items)
                {
                    int idx = item.path("index").asInt(-1);
                    if (idx >= 0 && idx < candidates.size())
                    {
                        RagChunk rc = candidates.get(idx);
                        rc.setScore(item.path("relevance_score").asDouble(0d));
                        reranked.add(rc);
                    }
                }
                return reranked.isEmpty() ? candidates : reranked;
            }
        }
        catch (Exception e)
        {
            log.debug("RAG rerank 失败，使用 RRF 融合序：{}", e.getMessage());
            return candidates;
        }
    }

    /**
     * 将命中分块拼接为 Prompt 上下文文本（含法条/出处溯源）。
     */
    public String buildContext(List<RagChunk> hits)
    {
        if (hits == null || hits.isEmpty())
        {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        int idx = 1;
        for (RagChunk rc : hits)
        {
            String content = rc.getContent();
            if (content != null && content.length() > chunkMaxLen)
            {
                content = content.substring(0, chunkMaxLen) + "...";
            }
            sb.append(idx++).append(". ").append(safe(rc.getTitle()))
              .append("：").append(safe(content));
            if (StringUtils.isNotEmpty(rc.getLawArticle()))
            {
                sb.append("（法条：").append(rc.getLawArticle()).append("）");
            }
            if (StringUtils.isNotEmpty(rc.getSource()))
            {
                sb.append("（出处：").append(rc.getSource()).append("）");
            }
            sb.append("\n");
        }
        return sb.toString();
    }

    /** 命中分块对应的知识ID集合（用于 knowledgeRefs 留痕） */
    public List<Long> knowledgeIdsOf(List<RagChunk> hits)
    {
        List<Long> ids = new ArrayList<>();
        if (hits != null)
        {
            for (RagChunk rc : hits)
            {
                if (rc.getKnowledgeId() != null && !ids.contains(rc.getKnowledgeId()))
                {
                    ids.add(rc.getKnowledgeId());
                }
            }
        }
        return ids;
    }

    private String safe(String s)
    {
        return s == null ? "" : s;
    }
}
