package ai.lawyers.system.service.lawyers.voice;

import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import ai.lawyers.common.utils.StringUtils;
import ai.lawyers.system.service.lawyers.IAiModelConfigService;
import ai.lawyers.system.service.lawyers.stat.AiModelCallLogRecorder;

/**
 * P1-8：仿真检查点评分器——关键词字面匹配 + embedding 语义相似度。
 *
 * <p>判分从二值升级为连续相似度分（0~1）：先做零成本关键词字面匹配，未命中且语义
 * 开关开启时调用 {@link IAiModelConfigService#embedTexts} 算余弦相似度（场景 test
 * 计入成本看板）。embedding 服务未装配/异常时自然降级回纯关键词，任何路径不外抛。</p>
 *
 * <p>OR 组（如 Copilot 命中任一）取组内最大相似度；AND 组（如 Robot 期望关键词全部
 * 出现）取组内最小相似度。判定阈值默认 0.75。</p>
 *
 * @author ai-lawyers
 */
@Component
public class SimulationScorer
{
    private static final Logger log = LoggerFactory.getLogger(SimulationScorer.class);

    /** 一次 embedding 请求最多携带的文本条数，避免超长批量 */
    private static final int EMBED_BATCH = 32;

    @Value("${ai.simulation.semantic-enabled:true}")
    private boolean semanticEnabled;

    @Value("${ai.simulation.semantic-threshold:0.75}")
    private double semanticThreshold;

    @Autowired(required = false)
    private IAiModelConfigService modelConfigService;

    /**
     * OR 组评分：answer 与任一关键词的最大相似度；字面命中即 1.0。
     *
     * @param answer   被测文本
     * @param keywords 关键词数组
     * @return 0~1 相似度
     */
    public Score scoreAny(String answer, List<String> keywords)
    {
        return score(answer, keywords, true);
    }

    /**
     * AND 组评分：answer 与各关键词的最小相似度；全部字面命中即 1.0。
     */
    public Score scoreAll(String answer, List<String> keywords)
    {
        return score(answer, keywords, false);
    }

    /** 语义判定是否通过 */
    public boolean passed(Score score)
    {
        return score != null && score.similarity >= semanticThreshold;
    }

    private Score score(String answer, List<String> keywords, boolean anyMode)
    {
        List<String> kws = normalize(keywords);
        Score result = new Score();
        if (StringUtils.isEmpty(answer) || kws.isEmpty())
        {
            return result;
        }

        // ① 字面匹配：逐个记录命中
        List<Double> literalSims = new ArrayList<>();
        boolean[] literalHit = new boolean[kws.size()];
        for (int i = 0; i < kws.size(); i++)
        {
            boolean hit = answer.contains(kws.get(i));
            literalHit[i] = hit;
            literalSims.add(hit ? 1.0d : 0.0d);
        }
        double literalAgg = aggregate(literalSims, anyMode);
        result.matchedKeywords = pickMatched(kws, literalHit);

        // 字面已有结论：OR 至少命中一个 或 AND 全部命中（聚合分=1）→ 直接满分，省 embedding
        boolean literalDecided = literalAgg >= 1.0d;
        if (literalDecided || !semanticEnabled || modelConfigService == null)
        {
            result.similarity = literalAgg;
            result.mode = "keyword";
            return result;
        }

        // ② 语义：仅对未字面命中的关键词算 embedding 相似度
        try
        {
            List<String> pending = new ArrayList<>();
            for (int i = 0; i < kws.size(); i++)
            {
                if (!literalHit[i])
                {
                    pending.add(kws.get(i));
                }
            }
            double[] sims = semanticSimilarities(answer, pending);

            List<Double> merged = new ArrayList<>();
            int p = 0;
            for (int i = 0; i < kws.size(); i++)
            {
                if (literalHit[i])
                {
                    merged.add(1.0d);
                }
                else
                {
                    merged.add(sims[p++]);
                }
            }
            double agg = aggregate(merged, anyMode);
            result.similarity = agg;
            result.mode = "semantic";
            result.matchedKeywords = pickMatched(kws, merged);
            return result;
        }
        catch (Exception e)
        {
            log.warn("仿真语义评分降级为关键词: {}", e.getMessage());
            result.similarity = literalAgg;
            result.mode = "keyword";
            return result;
        }
    }

    /** 调 embedding 批量算 answer 与各关键词的余弦相似度（关键词分批） */
    private double[] semanticSimilarities(String answer, List<String> keywords)
    {
        double[] out = new double[keywords.size()];
        for (int from = 0; from < keywords.size(); from += EMBED_BATCH)
        {
            int to = Math.min(keywords.size(), from + EMBED_BATCH);
            List<String> batch = new ArrayList<>();
            batch.add(answer);
            for (int i = from; i < to; i++)
            {
                batch.add(keywords.get(i));
            }
            List<float[]> vectors = modelConfigService.embedTexts(batch,
                    AiModelCallLogRecorder.SCENE_TEST);
            if (vectors == null || vectors.size() < batch.size() || vectors.get(0) == null)
            {
                throw new IllegalStateException("embedding 返回向量不足");
            }
            float[] ansVec = vectors.get(0);
            for (int i = 1; i < vectors.size(); i++)
            {
                out[from + i - 1] = cosine(ansVec, vectors.get(i));
            }
        }
        return out;
    }

    private double aggregate(List<Double> sims, boolean anyMode)
    {
        if (sims.isEmpty())
        {
            return 0d;
        }
        double agg = anyMode ? -1d : 2d;
        for (double s : sims)
        {
            agg = anyMode ? Math.max(agg, s) : Math.min(agg, s);
        }
        return agg < 0 ? 0d : agg;
    }

    private List<String> pickMatched(List<String> keywords, boolean[] hits)
    {
        List<String> matched = new ArrayList<>();
        for (int i = 0; i < keywords.size(); i++)
        {
            if (hits[i])
            {
                matched.add(keywords.get(i));
            }
        }
        return matched;
    }

    /** 相似度达到阈值的关键词视为语义命中 */
    private List<String> pickMatched(List<String> keywords, List<Double> sims)
    {
        List<String> matched = new ArrayList<>();
        for (int i = 0; i < keywords.size(); i++)
        {
            if (sims.get(i) >= semanticThreshold)
            {
                matched.add(keywords.get(i));
            }
        }
        return matched;
    }

    private List<String> normalize(List<String> keywords)
    {
        List<String> list = new ArrayList<>();
        if (keywords != null)
        {
            for (String kw : keywords)
            {
                if (StringUtils.isNotEmpty(kw) && !kw.trim().isEmpty())
                {
                    list.add(kw.trim());
                }
            }
        }
        return list;
    }

    private double cosine(float[] a, float[] b)
    {
        if (a == null || b == null || a.length != b.length || a.length == 0)
        {
            return 0d;
        }
        double dot = 0, na = 0, nb = 0;
        for (int i = 0; i < a.length; i++)
        {
            dot += a[i] * b[i];
            na += a[i] * a[i];
            nb += b[i] * b[i];
        }
        if (na == 0 || nb == 0)
        {
            return 0d;
        }
        double v = dot / (Math.sqrt(na) * Math.sqrt(nb));
        return Math.max(0d, Math.min(1d, v));
    }

    /** 单检查点评分结果（0~1 相似度 + 命中关键词 + 判定模式） */
    public static class Score
    {
        /** 聚合相似度 0~1 */
        private double similarity;
        /** keyword / semantic */
        private String mode = "keyword";
        /** 命中（字面或语义达阈值）关键词 */
        private List<String> matchedKeywords = new ArrayList<>();

        public double getSimilarity() { return similarity; }
        public String getMode() { return mode; }
        public List<String> getMatchedKeywords() { return matchedKeywords; }
    }
}
