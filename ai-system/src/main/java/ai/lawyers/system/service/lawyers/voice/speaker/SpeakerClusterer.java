package ai.lawyers.system.service.lawyers.voice.speaker;

import java.util.ArrayList;
import java.util.List;
import ai.lawyers.system.service.lawyers.rag.VectorUtils;

/**
 * P2-15 说话人聚类器（纯算法骨架）。
 *
 * <p>对一批已提取的声纹向量做贪心单遍阈值聚类（近似单链/AHC 的在线版）：依次取每个向量，
 * 与现有各簇中心算余弦相似度，归入相似度最高且 ≥ 阈值的簇，否则新开一簇。簇中心为成员
 * 向量的均值（每加入一个成员即增量更新），不依赖聚类数 K，无需引入第三方 ML 库。</p>
 *
 * <p>输入向量建议已 L2 归一（{@link SpeakerEmbeddingExtractor#extract(byte[])} 已归一）；
 * 比较统一走余弦，对未归一向量同样适用。本类无状态、线程安全、可离线单测。</p>
 *
 * @author ai-lawyers
 */
public class SpeakerClusterer
{
    /** 默认合并阈值：余弦 ≥ 该值视为同一说话人（经验值，可由配置覆盖） */
    public static final float DEFAULT_THRESHOLD = 0.70f;

    /** 待聚类样本：业务键 + 声纹向量 */
    public static class Sample<K>
    {
        private final K key;
        private final float[] embedding;

        public Sample(K key, float[] embedding)
        {
            this.key = key;
            this.embedding = embedding;
        }

        public K getKey() { return key; }
        public float[] getEmbedding() { return embedding; }
    }

    /** 聚类结果簇：标签 + 成员键 + 簇中心（成员均值） */
    public static class Cluster<K>
    {
        private final int label;
        private final List<K> members = new ArrayList<>();
        private final float[] centroid;

        Cluster(int label, int dim)
        {
            this.label = label;
            this.centroid = new float[dim];
        }

        void add(K key, float[] v)
        {
            members.add(key);
            // 增量平均：centroid = centroid + (v - centroid) / n
            double n = members.size();
            for (int i = 0; i < centroid.length; i++)
            {
                centroid[i] = (float) (centroid[i] + (v[i] - centroid[i]) / n);
            }
        }

        public int getLabel() { return label; }
        public List<K> getMembers() { return members; }
        public float[] getCentroid() { return centroid; }
        public int size() { return members.size(); }
    }

    /**
     * 用默认阈值聚类。
     *
     * @param samples 带键声纹样本（向量为空的样本被忽略）
     * @return 簇列表（按创建顺序，标签从 0 起）；输入为空返回空表
     */
    public <K> List<Cluster<K>> cluster(List<Sample<K>> samples)
    {
        return cluster(samples, DEFAULT_THRESHOLD);
    }

    /**
     * 用指定阈值聚类。
     *
     * @param samples   带键声纹样本
     * @param threshold 合并阈值（余弦），建议 (0,1)
     */
    public <K> List<Cluster<K>> cluster(List<Sample<K>> samples, float threshold)
    {
        List<Cluster<K>> clusters = new ArrayList<>();
        if (samples == null || samples.isEmpty())
        {
            return clusters;
        }
        for (Sample<K> sample : samples)
        {
            float[] v = sample.getEmbedding();
            if (v == null || v.length == 0)
            {
                continue;
            }
            Cluster<K> best = null;
            float bestSim = threshold;
            for (Cluster<K> c : clusters)
            {
                float sim = VectorUtils.cosine(v, c.getCentroid());
                if (sim >= bestSim)
                {
                    bestSim = sim;
                    best = c;
                }
            }
            if (best == null)
            {
                best = new Cluster<>(clusters.size(), v.length);
                clusters.add(best);
            }
            best.add(sample.getKey(), v);
        }
        return clusters;
    }
}
