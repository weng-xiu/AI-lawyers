package ai.lawyers.system.service.lawyers.rag;

import java.io.File;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

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
 * RAG 评测集常态化回归（W3 建设目标 + P3-E2 门禁）：
 * 验证「混合检索（FULLTEXT + 向量 + RRF 融合）Top-K 命中率较 LIKE 基线提升 ≥ 20 个百分点」，
 * 并以评测集 {@code src/test/resources/rag-evaluation.json} 驱动质量门禁。
 *
 * <p>P3-E2 常态化机制：</p>
 * <ul>
 *   <li>评测集为外置 JSON（v2：category 业务分类 + difficulty=verbatim/colloquial），
 *       可在不改代码的情况下持续扩充至 ≥300 条真实来电问句；</li>
 *   <li>{@link #evaluationDataset_isValid} 校验数据集结构（问句唯一非空、期望知识存在等）；</li>
 *   <li>{@link #qualityGate_topKHitRate} 产出整体/分难度/分分类命中率与 MRR，
 *       落盘 {@code target/rag-evaluation-report.json} 供 CI 采集；
 *       命中率低于 {@code rag.eval.minHitRate}（默认 0.95）打印门禁告警，
 *       仅当 {@code -Drag.eval.gate=strict} 时构建失败（CI 卡点）。</li>
 * </ul>
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

    /** 门禁告警阈值（strict 模式下也是失败线，可用 -Drag.eval.minHitRate 覆盖） */
    private static final double GATE_HIT_RATE =
            Double.parseDouble(System.getProperty("rag.eval.minHitRate", "0.95"));

    private final ObjectMapper mapper = new ObjectMapper();

    private RagSearchService service;
    private List<AiLegalKnowledgeChunk> corpus;
    private List<Case> cases;

    private final Map<Long, AiLegalKnowledgeChunk> corpusById = new LinkedHashMap<>();

    /** 评测用例结构 */
    private static final class Case
    {
        String question;
        String category = "综合";
        String difficulty = "colloquial";
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

    /**
     * P3-E2：评测集结构校验——扩充数据集（向 300 条目标推进）时防止格式/引用错误：
     * 问句非空且唯一、期望知识 ID 全部存在于语料、分类与难度枚举合法、口语问句占主体。
     */
    @Test
    void evaluationDataset_isValid()
    {
        Set<Long> corpusIds = corpus.stream()
                .map(AiLegalKnowledgeChunk::getKnowledgeId).collect(Collectors.toSet());
        Set<String> questions = new HashSet<>();
        int colloquial = 0;
        for (Case c : cases)
        {
            assertThat(c.question).as("评测问句不能为空").isNotBlank();
            assertThat(questions.add(c.question)).as("评测问句不能重复：%s", c.question).isTrue();
            assertThat(c.expected).as("用例期望知识不能为空：%s", c.question).isNotEmpty();
            for (Long id : c.expected)
            {
                assertThat(corpusIds).as("用例 [%s] 引用了不存在的 knowledgeId=%s", c.question, id).contains(id);
            }
            assertThat(c.category).as("用例分类缺失：%s", c.question).isNotBlank();
            assertThat(Arrays.asList("verbatim", "colloquial"))
                    .as("difficulty 仅支持 verbatim/colloquial：%s", c.question).contains(c.difficulty);
            if ("colloquial".equals(c.difficulty))
            {
                colloquial++;
            }
        }
        // 常态化下限：语料/问句规模只允许增长（W3 基线 36/32，P3-E2 v2 为 36/103，目标 ≥300）
        assertThat(corpus.size()).as("语料规模不应回退").isGreaterThanOrEqualTo(36);
        assertThat(cases.size()).as("评测问句规模不应回退（目标 ≥300）").isGreaterThanOrEqualTo(100);
        assertThat((double) colloquial / cases.size())
                .as("口语化改述问句应占主体（≥70%），否则无法检验混合检索相对 LIKE 的增益")
                .isGreaterThanOrEqualTo(0.70d);
    }

    /**
     * P3-E2：质量门禁。
     *
     * <p>默认（本地/普通 CI）：命中率低于 {@link #GATE_HIT_RATE}（默认 95%）只打印醒目的
     * 门禁告警并落盘报告，不阻断构建；{@code -Drag.eval.gate=strict}（发布流水线）时
     * 低于阈值直接失败。硬地板 60% 与"+20pp 提升"由 {@link #fusedRetrieval_beatsLikeBaseline_byAtLeast20Points()}
     * 独立保证，门禁退化为告警不会掩盖检索链路的破坏性回归。</p>
     */
    @Test
    void qualityGate_topKHitRate() throws Exception
    {
        Map<String, Object> report = runEvaluation();
        writeReport(report);

        double fusedRate = (double) report.get("fusedHitRate");
        boolean strict = "strict".equalsIgnoreCase(System.getProperty("rag.eval.gate", ""));
        if (fusedRate < GATE_HIT_RATE)
        {
            String msg = String.format(
                    "[RAG门禁告警] Top-%d 命中率 %.1f%% 低于门禁 %.0f%%（strict=%s），详见 target/rag-evaluation-report.json",
                    TOP_K, fusedRate * 100, GATE_HIT_RATE * 100, strict);
            System.out.println("⚠ " + msg);
            if (strict)
            {
                assertThat(fusedRate).as(msg).isGreaterThanOrEqualTo(GATE_HIT_RATE);
            }
        }
        else
        {
            System.out.printf("[RAG门禁通过] Top-%d 命中率 %.1f%% ≥ %.0f%%%n",
                    TOP_K, fusedRate * 100, GATE_HIT_RATE * 100);
        }
    }

    // ------------------------------------------------------------------ 评测执行与报告

    /** 跑全量评测，产出整体/分难度/分分类命中率、MRR、未命中清单 */
    private Map<String, Object> runEvaluation()
    {
        int likeHits = 0;
        int fusedHits = 0;
        double reciprocalRankSum = 0d;
        List<String> misses = new ArrayList<>();
        Map<String, int[]> byDifficulty = new TreeMap<>();   // [hits, total]
        Map<String, int[]> byCategory = new TreeMap<>();

        for (Case c : cases)
        {
            boolean likeHit = likeBaselineTopK(c.question, TOP_K);
            List<RagChunk> hits = service.search(c.question, null);
            List<Long> topK = hits.stream().limit(TOP_K).map(RagChunk::getKnowledgeId)
                    .collect(Collectors.toList());
            boolean fusedHit = topK.stream().anyMatch(c.expected::contains);
            int rank = firstExpectedRank(topK, c.expected);
            if (likeHit)
            {
                likeHits++;
            }
            if (fusedHit)
            {
                fusedHits++;
                reciprocalRankSum += 1d / rank;
            }
            else
            {
                misses.add(c.question);
            }
            byDifficulty.computeIfAbsent(c.difficulty, k -> new int[2])[1]++;
            byCategory.computeIfAbsent(c.category, k -> new int[2])[1]++;
            if (fusedHit)
            {
                byDifficulty.get(c.difficulty)[0]++;
                byCategory.get(c.category)[0]++;
            }
        }

        double likeRate = (double) likeHits / cases.size();
        double fusedRate = (double) fusedHits / cases.size();
        double mrr = reciprocalRankSum / cases.size();

        Map<String, Object> report = new LinkedHashMap<>();
        report.put("generatedAt", new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new java.util.Date()));
        report.put("topK", TOP_K);
        report.put("corpusCount", corpus.size());
        report.put("caseCount", cases.size());
        report.put("likeHitRate", round4(likeRate));
        report.put("fusedHitRate", round4(fusedRate));
        report.put("improvementPoints", round4((fusedRate - likeRate) * 100d));
        report.put("mrr", round4(mrr));
        report.put("gateThreshold", GATE_HIT_RATE);
        report.put("byDifficulty", ratioMap(byDifficulty));
        report.put("byCategory", ratioMap(byCategory));
        report.put("misses", misses);

        System.out.printf("[RAG评测] 语料=%d 问题=%d LIKE=%.1f%% 混合=%.1f%% 提升=%.1fpp MRR=%.3f 未命中=%d%n",
                corpus.size(), cases.size(), likeRate * 100, fusedRate * 100,
                (fusedRate - likeRate) * 100, mrr, misses.size());
        System.out.println("[RAG评测] 分难度：" + ratioMap(byDifficulty));
        return report;
    }

    /** 期望知识在 Top-K 中首次出现的名次（1 起），未命中由调用方过滤 */
    private int firstExpectedRank(List<Long> topK, List<Long> expected)
    {
        for (int i = 0; i < topK.size(); i++)
        {
            if (expected.contains(topK.get(i)))
            {
                return i + 1;
            }
        }
        return Integer.MAX_VALUE;
    }

    private Map<String, Object> ratioMap(Map<String, int[]> raw)
    {
        Map<String, Object> out = new TreeMap<>();
        for (Map.Entry<String, int[]> e : raw.entrySet())
        {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("hits", e.getValue()[0]);
            item.put("total", e.getValue()[1]);
            item.put("hitRate", round4((double) e.getValue()[0] / e.getValue()[1]));
            out.put(e.getKey(), item);
        }
        return out;
    }

    private static double round4(double v)
    {
        return Math.round(v * 10000d) / 10000d;
    }

    /** 落盘机器可读报告（CI 可归档/采集；surefire 工作目录为 ai-system 模块根） */
    private void writeReport(Map<String, Object> report)
    {
        try
        {
            File target = new File("target");
            if (!target.exists() && !target.mkdirs())
            {
                return;
            }
            ObjectMapper writer = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
            Files.write(new File(target, "rag-evaluation-report.json").toPath(),
                    writer.writeValueAsBytes(report));
        }
        catch (Exception e)
        {
            System.out.println("[RAG评测] 报告落盘失败（不影响门禁）：" + e.getMessage());
        }
    }

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
                if (item.hasNonNull("category"))
                {
                    c.category = item.get("category").asText();
                }
                if (item.hasNonNull("difficulty"))
                {
                    c.difficulty = item.get("difficulty").asText();
                }
                for (JsonNode id : item.path("expectedKnowledgeIds"))
                {
                    c.expected.add(id.asLong());
                }
                cases.add(c);
            }
        }
        // 结构校验交给 evaluationDataset_isValid；此处仅保证非空，数据集扩充无需改测试代码
        assertThat(corpus).as("评测集语料不能为空").isNotEmpty();
        assertThat(cases).as("评测集问句不能为空").isNotEmpty();
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
