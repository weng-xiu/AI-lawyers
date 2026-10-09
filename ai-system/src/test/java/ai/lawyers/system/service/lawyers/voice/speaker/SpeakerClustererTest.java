package ai.lawyers.system.service.lawyers.voice.speaker;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * P2-15：说话人聚类器单测。用合成的二维单位向量模拟“同一说话人/不同说话人”，
 * 校验阈值聚类、标签与成员归集。
 *
 * @author ai-lawyers
 */
class SpeakerClustererTest
{
    private final SpeakerClusterer clusterer = new SpeakerClusterer();

    @Test
    void cluster_similarVectors_mergedByThreshold()
    {
        // (1,0) 与近 (1,0)：余弦≈0.9999 ≥ 0.70 → 同一簇
        List<SpeakerClusterer.Sample<String>> samples = new ArrayList<>();
        samples.add(new SpeakerClusterer.Sample<>("a", unit(1f, 0.01f)));
        samples.add(new SpeakerClusterer.Sample<>("b", unit(1f, 0.02f)));

        List<SpeakerClusterer.Cluster<String>> clusters = clusterer.cluster(samples, 0.70f);

        assertEquals(1, clusters.size());
        assertEquals(2, clusters.get(0).size());
        assertTrue(clusters.get(0).getMembers().contains("a"));
        assertTrue(clusters.get(0).getMembers().contains("b"));
    }

    @Test
    void cluster_distinctVectors_splitIntoClusters()
    {
        // 两组方向正交（余弦≈0）→ 各成一簇
        List<SpeakerClusterer.Sample<String>> samples = new ArrayList<>();
        samples.add(new SpeakerClusterer.Sample<>("a", unit(1f, 0f)));
        samples.add(new SpeakerClusterer.Sample<>("b", unit(1f, 0f)));
        samples.add(new SpeakerClusterer.Sample<>("c", unit(0f, 1f)));
        samples.add(new SpeakerClusterer.Sample<>("d", unit(0f, 1f)));

        List<SpeakerClusterer.Cluster<String>> clusters = clusterer.cluster(samples, 0.70f);

        assertEquals(2, clusters.size());
        assertEquals(0, clusters.get(0).getLabel());
        assertEquals(1, clusters.get(1).getLabel());
        assertEquals(2, clusters.get(0).size());
        assertEquals(2, clusters.get(1).size());
    }

    @Test
    void cluster_thresholdControlsMerge()
    {
        // 夹角余弦 0.9：阈值 0.7 合并，阈值 0.95 拆分
        List<SpeakerClusterer.Sample<String>> samples = new ArrayList<>();
        samples.add(new SpeakerClusterer.Sample<>("a", unit(1f, 0f)));
        samples.add(new SpeakerClusterer.Sample<>("b", unit(1f, 0.484f))); // 余弦≈0.9

        assertEquals(1, clusterer.cluster(samples, 0.70f).size());
        assertEquals(2, clusterer.cluster(samples, 0.95f).size());
    }

    @Test
    void cluster_nullAndEmptySamples_ignored()
    {
        List<SpeakerClusterer.Sample<String>> samples = new ArrayList<>();
        samples.add(new SpeakerClusterer.Sample<>("a", null));
        samples.add(new SpeakerClusterer.Sample<>("b", new float[0]));
        samples.add(new SpeakerClusterer.Sample<>("c", unit(1f, 0f)));

        List<SpeakerClusterer.Cluster<String>> clusters = clusterer.cluster(samples);

        assertEquals(1, clusters.size());
        assertEquals(1, clusters.get(0).size());
        assertEquals("c", clusters.get(0).getMembers().get(0));
    }

    @Test
    void cluster_emptyInput_emptyResult()
    {
        assertTrue(clusterer.cluster(null).isEmpty());
        assertTrue(clusterer.cluster(new ArrayList<>()).isEmpty());
    }

    /** 归一化二维向量 */
    private static float[] unit(float x, float y)
    {
        double n = Math.sqrt((double) x * x + (double) y * y);
        return new float[] { (float) (x / n), (float) (y / n) };
    }
}
