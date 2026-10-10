package ai.lawyers.system.service.lawyers.voice.speaker;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import ai.lawyers.system.service.impl.lawyers.quality.WavPcmDecoder;
import ai.lawyers.system.service.lawyers.voice.vad.VoiceActivityDetector;

/**
 * P2-15 说话人聚类编排层（骨架，纯内存）。
 *
 * <p>把现成组件串成一条可直接调用的离线链路：</p>
 * <ol>
 *   <li>{@link WavPcmDecoder}：WAV → S16LE 单声道 PCM + 采样率；</li>
 *   <li>{@link VoiceActivityDetector}：逐帧喂入，按 {@code isSpeaking()} 跳变还原语音段；</li>
 *   <li>{@link SpeakerEmbeddingExtractor}：逐段提取 L2 归一的说话人向量；</li>
 *   <li>{@link SpeakerClusterer}：对全部段向量做贪心单遍阈值聚类。</li>
 * </ol>
 *
 * <p>结果只在内存中返回（每段的起止毫秒与说话人标签、说话人数），不落库、不接定时任务、
 * 不碰主链路。放入声纹 ONNX 模型并具备 ONNX Runtime 后即可端到端工作。</p>
 *
 * <p><b>降级语义：</b>模型/运行时缺席（{@link SpeakerEmbeddingExtractor#isModelAvailable()}
 * 为 false）、WAV 无有效语音段或所有段提嵌入失败时，返回空段结果且不抛错，由调用方跳过；
 * 不伪造身份向量，避免错误的说话人归并。</p>
 *
 * @author ai-lawyers
 */
public class SpeakerClusteringService
{
    /** VAD 帧长 20ms（与 VoiceActivityDetector 一致） */
    private static final int FRAME_MS = 20;

    private final SpeakerEmbeddingExtractor extractor;

    private final SpeakerClusterer clusterer;

    public SpeakerClusteringService()
    {
        this(new SpeakerEmbeddingExtractor(16000), new SpeakerClusterer());
    }

    public SpeakerClusteringService(SpeakerEmbeddingExtractor extractor, SpeakerClusterer clusterer)
    {
        this.extractor = extractor;
        this.clusterer = clusterer;
    }

    /** 单个语音段的聚类结果：段序号 + 起止毫秒 + 说话人标签 */
    public static class SegmentLabel
    {
        private final int segmentIndex;
        private final long startMs;
        private final long endMs;
        private final int speakerLabel;

        SegmentLabel(int segmentIndex, long startMs, long endMs, int speakerLabel)
        {
            this.segmentIndex = segmentIndex;
            this.startMs = startMs;
            this.endMs = endMs;
            this.speakerLabel = speakerLabel;
        }

        public int getSegmentIndex() { return segmentIndex; }
        public long getStartMs() { return startMs; }
        public long getEndMs() { return endMs; }
        public int getSpeakerLabel() { return speakerLabel; }
    }

    /** 整段录音的聚类结果 */
    public static class Result
    {
        private final int sampleRate;
        private final int speakerCount;
        private final List<SegmentLabel> segments;

        Result(int sampleRate, int speakerCount, List<SegmentLabel> segments)
        {
            this.sampleRate = sampleRate;
            this.speakerCount = speakerCount;
            this.segments = segments;
        }

        public int getSampleRate() { return sampleRate; }
        public int getSpeakerCount() { return speakerCount; }
        public List<SegmentLabel> getSegments() { return segments; }
    }

    /**
     * 对整段 WAV 录音做说话人聚类（默认阈值）。
     *
     * @param wav WAV 字节（PCM/16bit/单双声道）
     * @return 聚类结果；模型不可用或无有效语音段时返回空段结果
     */
    public Result cluster(byte[] wav)
    {
        return cluster(wav, SpeakerClusterer.DEFAULT_THRESHOLD);
    }

    /**
     * 对整段 WAV 录音做说话人聚类（指定阈值）。
     *
     * @param wav       WAV 字节
     * @param threshold 合并阈值（余弦）
     */
    public Result cluster(byte[] wav, float threshold)
    {
        if (wav == null || extractor == null || !extractor.isModelAvailable())
        {
            return empty();
        }

        WavPcmDecoder.Pcm pcm = WavPcmDecoder.decode(wav);
        int sampleRate = pcm.getSampleRate();
        byte[] data = pcm.getData();
        int frameBytes = sampleRate * FRAME_MS / 1000 * 2;
        int totalFrames = data.length / frameBytes;

        // 逐帧 feed + isSpeaking 跳变，记录 [起始帧, 结束帧)
        VoiceActivityDetector vad = new VoiceActivityDetector(sampleRate, null);
        List<int[]> ranges = new ArrayList<>();
        int segStart = -1;
        for (int i = 0; i < totalFrames; i++)
        {
            boolean before = vad.isSpeaking();
            byte[] frame = new byte[frameBytes];
            System.arraycopy(data, i * frameBytes, frame, 0, frameBytes);
            vad.feed(frame);
            boolean after = vad.isSpeaking();
            if (!before && after)
            {
                segStart = i;
            }
            else if (before && !after)
            {
                ranges.add(new int[] { segStart, i });
                segStart = -1;
            }
        }
        if (segStart >= 0)
        {
            ranges.add(new int[] { segStart, totalFrames });
        }

        // 逐段提嵌入（失败段跳过），以段序号为键
        List<SpeakerClusterer.Sample<Integer>> samples = new ArrayList<>();
        for (int s = 0; s < ranges.size(); s++)
        {
            int[] r = ranges.get(s);
            int from = r[0] * frameBytes;
            int len = (r[1] - r[0]) * frameBytes;
            byte[] seg = new byte[len];
            System.arraycopy(data, from, seg, 0, len);
            float[] emb = extractor.extract(seg);
            if (emb != null && emb.length > 0)
            {
                samples.add(new SpeakerClusterer.Sample<>(s, emb));
            }
        }
        if (samples.isEmpty())
        {
            return new Result(sampleRate, 0, new ArrayList<>());
        }

        List<SpeakerClusterer.Cluster<Integer>> clusters = clusterer.cluster(samples, threshold);

        // 段序号 → 说话人标签
        Map<Integer, Integer> labelByIndex = new HashMap<>();
        for (SpeakerClusterer.Cluster<Integer> c : clusters)
        {
            for (Integer idx : c.getMembers())
            {
                labelByIndex.put(idx, c.getLabel());
            }
        }

        List<SegmentLabel> labels = new ArrayList<>();
        for (SpeakerClusterer.Sample<Integer> sample : samples)
        {
            int idx = sample.getKey();
            int[] r = ranges.get(idx);
            labels.add(new SegmentLabel(idx, r[0] * (long) FRAME_MS, r[1] * (long) FRAME_MS,
                    labelByIndex.get(idx)));
        }
        labels.sort((a, b) -> Integer.compare(a.getSegmentIndex(), b.getSegmentIndex()));

        return new Result(sampleRate, clusters.size(), labels);
    }

    private static Result empty()
    {
        return new Result(0, 0, new ArrayList<>());
    }
}
