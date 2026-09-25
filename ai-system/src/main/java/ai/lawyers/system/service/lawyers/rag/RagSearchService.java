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
import ai.lawyers.system.domain.lawyers.stat.AiModelCallLog;
import ai.lawyers.system.service.lawyers.IAiModelConfigService;
import ai.lawyers.system.service.lawyers.metrics.HotlineMetrics;
import ai.lawyers.system.service.lawyers.stat.AiModelCallLogRecorder;
import ai.lawyers.system.service.lawyers.stat.ModelUsage;
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
    private VectorIndex vectorIndex;

    /** T5-1：RAG 召回指标（未引入 micrometer 时内部静默） */
    @Autowired(required = false)
    private HotlineMetrics metrics;

    /** P3-E5：模型调用成本/质量埋点（未装配时静默，不影响检索） */
    @Autowired(required = false)
    private AiModelCallLogRecorder callLogRecorder;

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
     * P3-E1：rerank 配置状态（供 indexInfo/前端展示）。
     */
    public java.util.Map<String, Object> rerankStatus()
    {
        java.util.Map<String, Object> s = new LinkedHashMap<>();
        s.put("enabled", rerankEnabled);
        s.put("url", rerankUrl);
        s.put("model", rerankModel);
        s.put("configured", rerankEnabled && StringUtils.isNotEmpty(rerankUrl));
        return s;
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
            List<float[]> qVec = modelConfigService.embedTexts(
                    java.util.Collections.singletonList(query), AiModelCallLogRecorder.SCENE_RAG);
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
     * 可选 Cross-Encoder rerank：POST {rerank-url} {model, query, documents[], top_n}，
     * 按返回 index/relevance_score（兼容 score 字段、data/顶层数组响应）重排。
     * 任何异常退回融合序。
     *
     * <p>P3-E1 联调增强：① 传 top_n=topK 减少服务端计算；② 文档按 chunk-max-len 截断，
     * 避免超 bge-reranker 等模型的 max length；③ 响应协议多供应商兼容，解析逻辑抽到
     * {@link #applyRerankResponse} 便于单测；④ 联调验证见 {@link #testRerank()}。</p>
     */
    private List<RagChunk> rerank(String query, List<RagChunk> candidates)
    {
        // P3-E5：rerank 协议通常不回 token，usage 记 0，仅统计调用量/耗时/成败（费用按调用量另算）
        long start = System.currentTimeMillis();
        try
        {
            ObjectNode body = MAPPER.createObjectNode();
            body.put("model", rerankModel);
            body.put("query", query);
            // top_n：只需最终入 Prompt 的条数（topK），减少服务端打分开销
            body.put("top_n", topK > 0 ? topK : candidates.size());
            ArrayNode docs = body.putArray("documents");
            for (RagChunk rc : candidates)
            {
                docs.add(truncateForRerank(rc.getContent()));
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
                    log.debug("RAG rerank 返回非成功 HTTP {}，使用 RRF 融合序", response.code());
                    recordRerankLog(start, AiModelCallLog.RESULT_FAIL,
                            "rerank HTTP " + response.code());
                    if (metrics != null)
                    {
                        metrics.incrementAi("rerank", "fail");
                    }
                    return candidates;
                }
                JsonNode root = MAPPER.readTree(text);
                List<RagChunk> reranked = applyRerankResponse(candidates, root);
                if (reranked.isEmpty())
                {
                    // 协议解析为空属有效降级，但不计成功，避免成功率虚高
                    recordRerankLog(start, AiModelCallLog.RESULT_FAIL, "rerank 响应解析为空");
                    if (metrics != null)
                    {
                        metrics.incrementAi("rerank", "fail");
                    }
                    return candidates;
                }
                recordRerankLog(start, AiModelCallLog.RESULT_SUCCESS, null);
                if (metrics != null)
                {
                    metrics.incrementAi("rerank", "success");
                }
                return reranked;
            }
        }
        catch (Exception e)
        {
            log.debug("RAG rerank 失败，使用 RRF 融合序：{}", e.getMessage());
            recordRerankLog(start, AiModelCallLog.RESULT_FAIL, e.getMessage());
            if (metrics != null)
            {
                metrics.incrementAi("rerank", "fail");
            }
            return candidates;
        }
    }

    /** P3-E5：rerank 无独立模型配置行，走 recordWithoutConfig 快照埋点（best-effort） */
    private void recordRerankLog(long startMillis, String result, String failReason)
    {
        if (callLogRecorder == null)
        {
            return;
        }
        try
        {
            callLogRecorder.recordWithoutConfig(AiModelCallLogRecorder.KIND_RERANK,
                    AiModelCallLogRecorder.SCENE_RAG, "rerank",
                    StringUtils.isNotEmpty(rerankModel) ? rerankModel : "unknown",
                    ModelUsage.ZERO, System.currentTimeMillis() - startMillis, 1, result, failReason);
        }
        catch (Exception ignore)
        {
            // 埋点永不影响检索主链路
        }
    }

    /**
     * 文档截断：与拼 Prompt 的单块上限一致（chunk-max-len），避免超 bge-reranker 等
     * 模型的 max length 导致 400。包级可见供测试。
     */
    String truncateForRerank(String content)
    {
        if (content == null)
        {
            return "";
        }
        int max = chunkMaxLen > 0 ? chunkMaxLen : 500;
        return content.length() > max ? content.substring(0, max) : content;
    }

    /**
     * 解析 rerank 响应并按分数降序重排候选。多协议兼容（P3-E1 联调确认的实际供应商形态）：
     * <ul>
     *   <li>结果数组位置：{@code results}（Cohere/Jina/SiliconFlow）/ {@code data}（部分国产网关）/ 顶层数组；</li>
     *   <li>分数字段：{@code relevance_score}（主流）/ {@code score}（Jina 新版/TEI 系）；</li>
     *   <li>{@code index} 越界或缺失的条目跳过；全部不可解析时返回空列表（调用方退回融合序）。</li>
     * </ul>
     * 包级可见供测试。
     */
    static List<RagChunk> applyRerankResponse(List<RagChunk> candidates, JsonNode root)
    {
        JsonNode results = root.path("results");
        if (!results.isArray() || results.size() == 0)
        {
            results = root.path("data");
        }
        if (!results.isArray() || results.size() == 0)
        {
            // 顶层数组形态：[{index, score}, ...]
            results = root.isArray() ? root : null;
        }
        if (results == null || results.size() == 0)
        {
            return new ArrayList<>();
        }
        List<JsonNode> items = new ArrayList<>();
        for (JsonNode item : results)
        {
            items.add(item);
        }
        items.sort(Comparator.comparingDouble((JsonNode it) -> {
            JsonNode s = it.hasNonNull("relevance_score") ? it.get("relevance_score") : it.get("score");
            return s == null ? 0d : s.asDouble(0d);
        }).reversed());
        List<RagChunk> reranked = new ArrayList<>();
        for (JsonNode item : items)
        {
            int idx = item.path("index").asInt(-1);
            if (idx >= 0 && idx < candidates.size())
            {
                RagChunk rc = candidates.get(idx);
                JsonNode s = item.hasNonNull("relevance_score") ? item.get("relevance_score") : item.get("score");
                rc.setScore(s == null ? 0d : s.asDouble(0d));
                reranked.add(rc);
            }
        }
        return reranked;
    }

    /**
     * P3-E1：rerank 服务连通性联调（固定样例，不触知识库/embedding）。
     *
     * @return 结果描述：enabled/url/model、success、latencyMs、ranking（重排后下标序）、error
     */
    public java.util.Map<String, Object> testRerank()
    {
        java.util.Map<String, Object> out = new LinkedHashMap<>();
        out.put("enabled", rerankEnabled);
        out.put("url", rerankUrl);
        out.put("model", rerankModel);
        if (!rerankEnabled || StringUtils.isEmpty(rerankUrl))
        {
            out.put("success", false);
            out.put("error", "rerank 未启用或未配置 rerank-url（ai.rag.rerank-enabled / ai.rag.rerank-url）");
            return out;
        }
        List<RagChunk> samples = new ArrayList<>();
        samples.add(new RagChunk(stubChunk("劳动合同应当以书面形式订立"), 0.01d));
        samples.add(new RagChunk(stubChunk("今天天气晴朗适合户外运动"), 0.02d));
        long start = System.currentTimeMillis();
        try
        {
            ObjectNode body = MAPPER.createObjectNode();
            body.put("model", rerankModel);
            body.put("query", "劳动合同必须签书面合同吗");
            body.put("top_n", 2);
            ArrayNode docs = body.putArray("documents");
            for (RagChunk rc : samples)
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
                out.put("latencyMs", System.currentTimeMillis() - start);
                out.put("httpStatus", response.code());
                if (!response.isSuccessful())
                {
                    out.put("success", false);
                    out.put("error", "HTTP " + response.code() + "："
                            + (text.length() > 300 ? text.substring(0, 300) : text));
                    return out;
                }
                JsonNode root = MAPPER.readTree(text);
                List<RagChunk> reranked = applyRerankResponse(samples, root);
                if (reranked.isEmpty())
                {
                    out.put("success", false);
                    out.put("raw", text.length() > 500 ? text.substring(0, 500) : text);
                    out.put("error", "响应无法解析为 results/data 数组或 index 均越界，请核对服务协议");
                    return out;
                }
                List<String> ranking = new ArrayList<>();
                for (RagChunk rc : reranked)
                {
                    ranking.add(String.format("score=%.4f %s", rc.getScore(), rc.getContent()));
                }
                out.put("success", true);
                out.put("ranking", ranking);
                return out;
            }
        }
        catch (Exception e)
        {
            out.put("latencyMs", System.currentTimeMillis() - start);
            out.put("success", false);
            out.put("error", e.getClass().getSimpleName() + "：" + e.getMessage());
            return out;
        }
    }

    /** 联调样例用的轻量 chunk（无 DB 依赖） */
    private static AiLegalKnowledgeChunk stubChunk(String content)
    {
        AiLegalKnowledgeChunk c = new AiLegalKnowledgeChunk();
        c.setChunkId(-1L);
        c.setChunkContent(content);
        return c;
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
