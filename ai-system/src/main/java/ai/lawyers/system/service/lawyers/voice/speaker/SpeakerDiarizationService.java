package ai.lawyers.system.service.lawyers.voice.speaker;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import ai.lawyers.system.service.impl.lawyers.quality.WavPcmDecoder;
import ai.lawyers.system.service.lawyers.rag.VectorUtils;
import ai.lawyers.system.service.lawyers.voice.vad.VoiceActivityDetector;

/**
 * P2-15 通话内说话人分离（diarization，纯内存、不跨通话建档）。
 *
 * <p>与 {@link SpeakerClusteringService} 的差异：</p>
 * <ul>
 *   <li>输入为双声道（FreeSWITCH RECORD_STEREO 单文件，或 uuid_record 双腿分轨两文件），
 *       声道下标天然对应通话两条腿；</li>
 *   <li>先按通道分别 VAD 切段、逐段提声纹向量，再对<b>本通话内</b>全部段向量做一次聚类；</li>
 *   <li>聚类标签按"簇内段时长主要落在哪个声道"多数表决映射为角色（坐席/客户），
 *       无法稳定归入任一声道（如背景第三人）标记为 OTHER；</li>
 *   <li>结果只在内存返回（段起止毫秒、通道、说话人标签、角色、段对簇中心置信度），
 *       <b>不落库、不与号码关联、不跨通话累积</b>——声纹属敏感个人信息，跨通话建档
 *       须先完成单独同意与 PIA，表设计见 sql/ai_system_p2_speaker_profile_20261010.sql
 *       （当前不写入）。</li>
 * </ul>
 *
 * <p><b>降级语义：</b>模型/运行时缺席（{@link SpeakerEmbeddingExtractor#isModelAvailable()}
 * 为 false）、单声道输入、无有效语音段或所有段提嵌入失败时，返回空段结果且不抛错；
 * 不伪造身份向量，避免错误的说话人归并。</p>
 *
 * @author ai-lawyers
 */
public class SpeakerDiarizationService
{
    /** VAD 帧长 20ms（与 VoiceActivityDetector 一致） */
    private static final int FRAME_MS = 20;

    /** 角色判定多数阈值：某声道段时长占比 ≥ 该值才归入该角色，否则视为第三人/不确定 */
    private static final float ROLE_DOMINANT_RATIO = 0.6f;

    /** 默认角色名：声道0=坐席（采集侧标定后可用重载入参覆盖） */
    public static final String DEFAULT_ROLE_A = "坐席";
    /** 默认角色名：声道1=客户 */
    public static final String DEFAULT_ROLE_B = "客户";
    /** 无法稳定映射到任一声道的簇角色名 */
    public static final String ROLE_OTHER = "其他";

    private final SpeakerEmbeddingExtractor extractor;
    private final SpeakerClusterer clusterer;

    public SpeakerDiarizationService()
    {
        this(new SpeakerEmbeddingExtractor(16000), new SpeakerClusterer());
    }

    public SpeakerDiarizationService(SpeakerEmbeddingExtractor extractor, SpeakerClusterer clusterer)
    {
        this.extractor = extractor;
        this.clusterer = clusterer;
    }

    /** 簇内说话人角色：由主要声道映射，OTHER 表示无法稳定归入通话两条腿（背景第三人等） */
    public enum SpeakerRole { ROLE_A, ROLE_B, OTHER }

    /** 单段分离结果：全局段序号 + 起止毫秒 + 物理声道 + 说话人标签/角色 + 置信度 */
    public static class Segment
    {
        private final int segmentIndex;
        private final int channel;
        private final long startMs;
        private final long endMs;
        private final int speakerLabel;
        private final SpeakerRole role;
        private final String roleName;
        private final float confidence;

        Segment(int segmentIndex, int channel, long startMs, long endMs, int speakerLabel,
                SpeakerRole role, String roleName, float confidence)
        {
            this.segmentIndex = segmentIndex;
            this.channel = channel;
            this.startMs = startMs;
            this.endMs = endMs;
            this.speakerLabel = speakerLabel;
            this.role = role;
            this.roleName = roleName;
            this.confidence = confidence;
        }

        public int getSegmentIndex() { return segmentIndex; }
        public int getChannel() { return channel; }
        public long getStartMs() { return startMs; }
        public long getEndMs() { return endMs; }
        public int getSpeakerLabel() { return speakerLabel; }
        public SpeakerRole getRole() { return role; }
        public String getRoleName() { return roleName; }
        /** 段向量与所属簇中心的余弦相似度（越接近 1 归属越可靠） */
        public float getConfidence() { return confidence; }
    }

    /** 说话人（簇）汇总：标签 + 角色 + 段数 + 总时长 + 平均置信度 */
    public static class Speaker
    {
        private final int label;
        private final SpeakerRole role;
        private final String roleName;
        private final int segmentCount;
        private final long durationMs;
        private final float avgConfidence;

        Speaker(int label, SpeakerRole role, String roleName, int segmentCount,
                long durationMs, float avgConfidence)
        {
            this.label = label;
            this.role = role;
            this.roleName = roleName;
            this.segmentCount = segmentCount;
            this.durationMs = durationMs;
            this.avgConfidence = avgConfidence;
        }

        public int getLabel() { return label; }
        public SpeakerRole getRole() { return role; }
        public String getRoleName() { return roleName; }
        public int getSegmentCount() { return segmentCount; }
        public long getDurationMs() { return durationMs; }
        public float getAvgConfidence() { return avgConfidence; }
    }

    /** 整通通话分离结果（仅内存，不落库） */
    public static class Result
    {
        private final int sampleRate;
        private final String roleAName;
        private final String roleBName;
        private final int speakerCount;
        private final List<Segment> segments;
        private final List<Speaker> speakers;

        Result(int sampleRate, String roleAName, String roleBName, int speakerCount,
               List<Segment> segments, List<Speaker> speakers)
        {
            this.sampleRate = sampleRate;
            this.roleAName = roleAName;
            this.roleBName = roleBName;
            this.speakerCount = speakerCount;
            this.segments = segments;
            this.speakers = speakers;
        }

        public int getSampleRate() { return sampleRate; }
        public String getRoleAName() { return roleAName; }
        public String getRoleBName() { return roleBName; }
        public int getSpeakerCount() { return speakerCount; }
        public List<Segment> getSegments() { return segments; }
        public List<Speaker> getSpeakers() { return speakers; }
    }

    /** 段键：全局段序号 + 物理声道 + 起止帧（聚类后回填角色/置信度用） */
    private static final class SegKey
    {
        final int index;
        final int channel;
        final int startFrame;
        final int endFrame;

        SegKey(int index, int channel, int startFrame, int endFrame)
        {
            this.index = index;
            this.channel = channel;
            this.startFrame = startFrame;
            this.endFrame = endFrame;
        }

        long durationFrames() { return (long) endFrame - startFrame; }
    }

    /**
     * 对单通双声道 WAV 做通话内说话人分离（默认角色名 坐席/客户，默认聚类阈值）。
     *
     * @param wav 双声道 WAV（PCM/16bit）；单声道直接返回空段结果
     */
    public Result diarizeStereo(byte[] wav)
    {
        return diarizeChannels(WavPcmDecoder.decodeChannels(wav),
                DEFAULT_ROLE_A, DEFAULT_ROLE_B, SpeakerClusterer.DEFAULT_THRESHOLD);
    }

    /**
     * 对 FreeSWITCH uuid_record 双腿分轨的两个单声道 WAV 做通话内说话人分离
     * （采样率须一致，短轨静默补齐；默认角色名 坐席/客户，默认聚类阈值）。
     */
    public Result diarizeTwoLegs(byte[] legAWav, byte[] legBWav)
    {
        return diarizeChannels(WavPcmDecoder.decodeTwoLegs(legAWav, legBWav),
                DEFAULT_ROLE_A, DEFAULT_ROLE_B, SpeakerClusterer.DEFAULT_THRESHOLD);
    }

    /**
     * 完整参数入口：已解码的等长双通道 + 显式角色名（声道标定后映射）+ 聚类阈值。
     */
    public Result diarizeChannels(WavPcmDecoder.ChannelPcm channels, String roleAName,
                                  String roleBName, float threshold)
    {
        if (channels == null || extractor == null || !extractor.isModelAvailable()
                || channels.getChannelCount() < 2)
        {
            return empty(0, roleAName, roleBName);
        }
        int sampleRate = channels.getSampleRate();
        int frameBytes = sampleRate * FRAME_MS / 1000 * 2;

        // 1. 分通道 VAD 切段，全局段序号连续
        List<SegKey> keys = new ArrayList<>();
        List<SpeakerClusterer.Sample<SegKey>> samples = new ArrayList<>();
        int globalIndex = 0;
        for (int ch = 0; ch < channels.getChannelCount() && ch < 2; ch++)
        {
            byte[] pcm = channels.getChannel(ch);
            List<int[]> ranges = detectRanges(sampleRate, pcm);
            for (int[] r : ranges)
            {
                int from = r[0] * frameBytes;
                int len = (r[1] - r[0]) * frameBytes;
                if (len <= 0 || from + len > pcm.length)
                {
                    continue;
                }
                byte[] seg = new byte[len];
                System.arraycopy(pcm, from, seg, 0, len);
                float[] emb = extractor.extract(seg);
                SegKey key = new SegKey(globalIndex++, ch, r[0], r[1]);
                keys.add(key);
                if (emb != null && emb.length > 0)
                {
                    samples.add(new SpeakerClusterer.Sample<>(key, emb));
                }
            }
        }
        if (samples.isEmpty())
        {
            return empty(sampleRate, roleAName, roleBName);
        }

        // 2. 本通话内一次性聚类（不与历史通话任何中心比较）
        List<SpeakerClusterer.Cluster<SegKey>> clusters = clusterer.cluster(samples, threshold);

        // 3. 段键 → 标签、标签 → 簇中心
        Map<SegKey, Integer> labelByKey = new HashMap<>();
        Map<Integer, float[]> centerByLabel = new HashMap<>();
        for (SpeakerClusterer.Cluster<SegKey> c : clusters)
        {
            centerByLabel.put(c.getLabel(), c.getCentroid());
            for (SegKey k : c.getMembers())
            {
                labelByKey.put(k, c.getLabel());
            }
        }
        for (SpeakerClusterer.Cluster<SegKey> c : clusters)
        {
            centerByLabel.put(c.getLabel(), c.getCentroid());
        }

        // 4. 簇 → 角色（按簇内段时长多数表决）与汇总指标
        Map<Integer, SpeakerRole> roleByLabel = new HashMap<>();
        Map<Integer, String> roleNameByLabel = new HashMap<>();
        for (SpeakerClusterer.Cluster<SegKey> c : clusters)
        {
            long durA = 0L;
            long durB = 0L;
            for (SegKey k : c.getMembers())
            {
                if (k.channel == 0)
                {
                    durA += k.durationFrames();
                }
                else if (k.channel == 1)
                {
                    durB += k.durationFrames();
                }
            }
            long total = durA + durB;
            SpeakerRole role;
            String roleName;
            if (total > 0 && (double) durA / total >= ROLE_DOMINANT_RATIO)
            {
                role = SpeakerRole.ROLE_A;
                roleName = roleAName;
            }
            else if (total > 0 && (double) durB / total >= ROLE_DOMINANT_RATIO)
            {
                role = SpeakerRole.ROLE_B;
                roleName = roleBName;
            }
            else
            {
                role = SpeakerRole.OTHER;
                roleName = ROLE_OTHER;
            }
            roleByLabel.put(c.getLabel(), role);
            roleNameByLabel.put(c.getLabel(), roleName);
        }

        // 5. 组装段结果（仅保留成功提嵌入的段），按起始时间升序、同刻按通道
        List<Segment> segments = new ArrayList<>();
        Map<Integer, Double> confSumByLabel = new HashMap<>();
        Map<Integer, Integer> confCntByLabel = new HashMap<>();
        for (SpeakerClusterer.Sample<SegKey> sample : samples)
        {
            SegKey k = sample.getKey();
            int label = labelByKey.get(k);
            float conf = VectorUtils.cosine(sample.getEmbedding(), centerByLabel.get(label));
            conf = Math.max(0f, Math.min(1f, conf));
            confSumByLabel.merge(label, (double) conf, Double::sum);
            confCntByLabel.merge(label, 1, Integer::sum);
            segments.add(new Segment(k.index, k.channel,
                    k.startFrame * (long) FRAME_MS, k.endFrame * (long) FRAME_MS,
                    label, roleByLabel.get(label), roleNameByLabel.get(label), conf));
        }
        segments.sort((a, b) ->
        {
            int byStart = Long.compare(a.getStartMs(), b.getStartMs());
            return byStart != 0 ? byStart : Integer.compare(a.getChannel(), b.getChannel());
        });

        // 6. 说话人汇总（按标签序）
        List<Speaker> speakers = new ArrayList<>();
        for (SpeakerClusterer.Cluster<SegKey> c : clusters)
        {
            int label = c.getLabel();
            long durFrames = c.getMembers().stream().mapToLong(SegKey::durationFrames).sum();
            int cnt = confCntByLabel.getOrDefault(label, 0);
            float avg = cnt > 0 ? (float) (confSumByLabel.getOrDefault(label, 0d) / cnt) : 0f;
            speakers.add(new Speaker(label, roleByLabel.get(label), roleNameByLabel.get(label),
                    c.size(), durFrames * FRAME_MS, avg));
        }

        return new Result(sampleRate, roleAName, roleBName, clusters.size(), segments, speakers);
    }

    /**
     * 单通道 VAD 分段（与 SpeakerClusteringService 同口径）：
     * 逐帧 feed + isSpeaking 跳变，返回 [起始帧, 结束帧)；结尾仍开口则截到末帧。
     */
    private static List<int[]> detectRanges(int sampleRate, byte[] pcm)
    {
        int frameBytes = sampleRate * FRAME_MS / 1000 * 2;
        int totalFrames = pcm.length / frameBytes;
        VoiceActivityDetector vad = new VoiceActivityDetector(sampleRate, null);
        List<int[]> ranges = new ArrayList<>();
        int segStart = -1;
        for (int i = 0; i < totalFrames; i++)
        {
            boolean before = vad.isSpeaking();
            byte[] frame = new byte[frameBytes];
            System.arraycopy(pcm, i * frameBytes, frame, 0, frameBytes);
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
        return ranges;
    }

    private static Result empty(int sampleRate, String roleAName, String roleBName)
    {
        return new Result(sampleRate, roleAName, roleBName, 0, new ArrayList<>(), new ArrayList<>());
    }
}
