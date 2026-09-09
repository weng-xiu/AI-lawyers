package ai.lawyers.system.service.lawyers.rag;

import java.io.InputStream;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import ai.lawyers.system.domain.lawyers.AiLegalKnowledgeChunk;
import ai.lawyers.system.mapper.lawyers.AiLegalKnowledgeChunkMapper;
import ai.lawyers.system.service.lawyers.IAiModelConfigService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * W3 RAG 评测集回归测试：验证「混合检索（FULLTEXT + 向量 + RRF 融合）Top-K 命中率
 * 较 LIKE 基线提升 ≥ 20 个百分点」的 T3 建设目标。
 *
 * <p>评测集 {@code src/test/resources/rag-evaluation.json}：36 条法律知识语料 +
 * 32 个真实口径的咨询问题（8 条逐字标题问题 + 24 条口语化改述问题）。</p>
 *
 * <p>DB/LLM 环境按生产语义在测试内仿真：</p>
 * <ul>
 *   <li>LIKE 基线：复刻 SQL {@code chunk_content like '%q%' or title like '%q%'} 整句子串匹配；</li>
 *   <li>FULLTEXT：复刻 natural language mode 的词频相关性（字符 n-gram 重叠加权评分）；</li>
 *   <li>向量路：确定性字符 n-gram 哈希嵌入（256 维，语料与问句同分布），
 *       驱动真实的 {@link InMemoryVectorIndex} 余弦近邻。</li>
 * </ul>
 *
 * <p>被测对象 {@link RagSearchService} 为生产实现（含 RRF 融合、Top-K 截断、指标上报），
 * 仅 Mapper/Embedding 按上述语义打桩。</p>
 *
 * @author ai-lawyers
 */
class RagEvaluationTest
{
    private static final int EMBED_DIM = 256;
    private static final int TOP_K = 3;
    private static final int CANDIDATE_SIZE = 20;

    private final ObjectMapper mapper = new ObjectMapper();

    private RagSearchService service;
    private List<AiLegalKnowledgeChunk> corpus;
    private List<Case> cases;

    private final Map<Long, AiLegalKnowledgeChunk> corpusById = new LinkedHashMap<>();

    /** 评测用例结构 */
    private static final class Case
    {
        String question;
        List<Long> expected = new ArrayList<>();
    }

    @BeforeEach
    void setUp() throws Exception
    {
        loadEvaluationSet();

        AiLegalKnowledgeChunkMapper chunkMapper = mock(AiLegalKnowledgeChunkMapper.class);
        IAiModelConfigService modelConfigService = mock(IAiModelConfigService.class);
        InMemoryVectorIndex vectorIndex = new InMemoryVectorIndex();

        // ① 向量路：语料建索引（与生产一致：float32 小端字节存 embedding 字段）
        for (AiLegalKnowledgeChunk chunk : corpus)
        {
            corpusById.put(chunk.getKnowledgeId(), chunk);
            chunk.setEmbedding(VectorUtils.toBytes(embed(chunk.getTitle() + chunk.getChunkContent())));
            chunk.setEmbeddingDim(EMBED_DIM);
        }
        vectorIndex.rebuild(corpus);
        assertThat(vectorIndex.isReady()).as("向量索引应就绪").isTrue();

        // ② 查询向量
        when(modelConfigService.embedTexts(anyList())).thenAnswer(inv -> {
            List<String> texts = inv.getArgument(0);
            return texts.stream().map(this::embed).collect(Collectors.toList());
        });

        // ③ FULLTEXT 路：natural language mode 语义仿真（n-gram 重叠加权，按得分降序）
        when(chunkMapper.searchByFulltext(anyString(), any(), anyInt())).thenAnswer(inv -> {
            String keyword = inv.getArgument(0);
            int limit = inv.getArgument(2);
            Map<String, Double> qFeatures = features(keyword);
            List<AiLegalKnowledgeChunk> hits = new ArrayList<>();
            Map<Long, Double> scores = new HashMap<>();
            for (AiLegalKnowledgeChunk c : corpus)
            {
                double score = overlapScore(qFeatures, c.getTitle() + c.getChunkContent());
                if (score > 0)
                {
                    hits.add(c);
                    scores.put(c.getChunkId(), score);
                }
            }
            hits.sort((a, b) -> Double.compare(scores.get(b.getChunkId()), scores.get(a.getChunkId())));
            return hits.size() > limit ? new ArrayList<>(hits.subList(0, limit)) : hits;
        });

        // ④ LIKE 基线：整句子串匹配（生产 SQL 语义），按 chunk_id 排序
        when(chunkMapper.searchByLike(anyString(), any(), anyInt())).thenAnswer(inv -> {
            String keyword = inv.getArgument(0);
            int limit = inv.getArgument(2);
            return corpus.stream()
                    .filter(c -> (c.getChunkContent() != null && c.getChunkContent().contains(keyword))
                            || (c.getTitle() != null && c.getTitle().contains(keyword)))
                    .limit(limit)
                    .collect(Collectors.toList());
        });

        // ⑤ 装配生产 RagSearchService
        service = new RagSearchService();
        setField("chunkMapper", chunkMapper);
        setField("modelConfigService", modelConfigService);
        setField("vectorIndex", vectorIndex);
        setField("ragEnabled", true);
        setField("topK", TOP_K);
        setField("candidateSize", CANDIDATE_SIZE);
        setField("rerankEnabled", false);
        setField("chunkMaxLen", 500);
    }

    /**
     * 核心断言：混合检索 Top-3 命中率较 LIKE 基线提升 ≥ 20 个百分点，且绝对命中率 ≥ 60%。
     */
    @Test
    void fusedRetrieval_beatsLikeBaseline_byAtLeast20Points()
    {
        int likeHits = 0;
        int fusedHits = 0;
        List<String> misses = new ArrayList<>();

        for (Case c : cases)
        {
            boolean likeHit = likeBaselineTopK(c.question, TOP_K);
            boolean fusedHit = fusedTopK(c.question, TOP_K);
            if (likeHit)
            {
                likeHits++;
            }
            if (fusedHit)
            {
                fusedHits++;
            }
            else
            {
                misses.add(c.question);
            }
        }

        double likeRate = (double) likeHits / cases.size();
        double fusedRate = (double) fusedHits / cases.size();
        System.out.printf("[RAG评测] 语料=%d 问题=%d LIKE命中率=%.1f%% 混合检索命中率=%.1f%% 提升=%.1fpp 未命中=%s%n",
                corpus.size(), cases.size(), likeRate * 100, fusedRate * 100,
                (fusedRate - likeRate) * 100, misses);

        assertThat(fusedRate - likeRate)
                .as("混合检索命中率较 LIKE 基线应提升 ≥ 20 个百分点")
                .isGreaterThanOrEqualTo(0.20);
        assertThat(fusedRate).as("混合检索绝对命中率应 ≥ 60%%").isGreaterThanOrEqualTo(0.60);
    }

    /**
     * 溯源一致性：命中结果的 lawArticle/source 透传完整，Prompt 上下文可携带出处。
     */
    @Test
    void buildContext_carriesTraceability()
    {
        List<RagChunk> hits = service.search("网购的衣服不喜欢能七天无理由退货吗", null);
        assertThat(hits).isNotEmpty();
        RagChunk top = hits.get(0);
        assertThat(top.getKnowledgeId()).isNotNull();
        String context = service.buildContext(hits);
        assertThat(context).isNotBlank();
        if (top.getLawArticle() != null)
        {
            assertThat(context).contains("法条");
        }
        assertThat(service.knowledgeIdsOf(hits)).isNotEmpty();
    }

    /** LIKE 基线（生产 SQL 语义）取 Top-K 后判断命中 */
    private boolean likeBaselineTopK(String question, int k)
    {
        return corpus.stream()
                .filter(c -> (c.getChunkContent() != null && c.getChunkContent().contains(question))
                        || (c.getTitle() != null && c.getTitle().contains(question)))
                .limit(k)
                .map(AiLegalKnowledgeChunk::getKnowledgeId)
                .anyMatch(casesExpected(question)::contains);
    }

    /** 混合检索（被测生产实现）Top-K 命中判断 */
    private boolean fusedTopK(String question, int k)
    {
        List<RagChunk> hits = service.search(question, null);
        List<Long> topK = hits.stream().limit(k).map(RagChunk::getKnowledgeId).collect(Collectors.toList());
        List<Long> expected = casesExpected(question);
        return topK.stream().anyMatch(expected::contains);
    }

    private List<Long> casesExpected(String question)
    {
        return cases.stream().filter(c -> c.question.equals(question)).findFirst()
                .map(c -> c.expected).orElse(new ArrayList<>());
    }

    // ------------------------------------------------------------------ 评测集加载与打桩工具

    private void loadEvaluationSet() throws Exception
    {
        try (InputStream in = getClass().getResourceAsStream("/rag-evaluation.json"))
        {
            assertThat(in).as("缺少评测集资源 rag-evaluation.json").isNotNull();
            JsonNode root = mapper.readTree(in);
            corpus = new ArrayList<>();
            long chunkId = 1;
            for (JsonNode item : root.path("corpus"))
            {
                AiLegalKnowledgeChunk chunk = new AiLegalKnowledgeChunk();
                chunk.setChunkId(chunkId++);
                chunk.setKnowledgeId(item.path("knowledgeId").asLong());
                chunk.setTitle(item.path("title").asText());
                chunk.setChunkContent(item.path("content").asText());
                chunk.setLawArticle(item.path("lawArticle").asText());
                chunk.setStatus("0");
                corpus.add(chunk);
            }
            cases = new ArrayList<>();
            for (JsonNode item : root.path("cases"))
            {
                Case c = new Case();
                c.question = item.path("question").asText();
                for (JsonNode id : item.path("expectedKnowledgeIds"))
                {
                    c.expected.add(id.asLong());
                }
                cases.add(c);
            }
        }
        assertThat(corpus).hasSize(36);
        assertThat(cases).hasSize(32);
    }

    /** 确定性字符 n-gram 哈希嵌入：unigram 权重 1.0、bigram 权重 1.5，L2 归一化 */
    private float[] embed(String text)
    {
        float[] vec = new float[EMBED_DIM];
        String t = text == null ? "" : text.replaceAll("\\s+", "");
        for (int i = 0; i < t.length(); i++)
        {
            addFeature(vec, String.valueOf(t.charAt(i)), 1.0f);
            if (i + 1 < t.length())
            {
                addFeature(vec, t.substring(i, i + 2), 1.5f);
            }
        }
        float norm = 0f;
        for (float v : vec)
        {
            norm += v * v;
        }
        if (norm > 0f)
        {
            float inv = (float) (1.0 / Math.sqrt(norm));
            for (int i = 0; i < vec.length; i++)
            {
                vec[i] *= inv;
            }
        }
        return vec;
    }

    private void addFeature(float[] vec, String feature, float weight)
    {
        int h = 7;
        for (int i = 0; i < feature.length(); i++)
        {
            h = h * 131 + feature.charAt(i);
        }
        int idx = Math.abs(h) % EMBED_DIM;
        vec[idx] += weight;
    }

    /** n-gram 特征表（bigram 权重 2、unigram 权重 1），用于 FULLTEXT 语义仿真评分 */
    private Map<String, Double> features(String text)
    {
        Map<String, Double> map = new HashMap<>();
        String t = text == null ? "" : text.replaceAll("\\s+", "");
        for (int i = 0; i < t.length(); i++)
        {
            map.merge(String.valueOf(t.charAt(i)), 1.0, Double::sum);
            if (i + 1 < t.length())
            {
                map.merge(t.substring(i, i + 2), 2.0, Double::sum);
            }
        }
        return map;
    }

    private double overlapScore(Map<String, Double> queryFeatures, String docText)
    {
        Map<String, Double> docFeatures = features(docText);
        double score = 0;
        for (Map.Entry<String, Double> e : queryFeatures.entrySet())
        {
            Double docWeight = docFeatures.get(e.getKey());
            if (docWeight != null)
            {
                score += e.getValue() * docWeight;
            }
        }
        return score;
    }

    private void setField(String name, Object value)
    {
        try
        {
            Field f = RagSearchService.class.getDeclaredField(name);
            f.setAccessible(true);
            f.set(service, value);
        }
        catch (Exception e)
        {
            throw new IllegalStateException("注入字段失败: " + name, e);
        }
    }
}
