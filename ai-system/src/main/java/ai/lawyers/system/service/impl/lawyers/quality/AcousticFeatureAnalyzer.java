package ai.lawyers.system.service.impl.lawyers.quality;

import java.util.ArrayList;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import ai.lawyers.system.service.impl.lawyers.quality.QualityRuleEngine.RuleViolation;
import ai.lawyers.system.service.lawyers.voice.vad.VoiceActivityDetector;

/**
 * P1-7/P2-7：质检声学特征分析器（静默时长 / 语速 / 双声道抢话 / 分角色语速）。
 *
 * <p>不依赖 ASR 时间戳：质检已能读到完整录音，本类把 WAV 解出的 S16LE PCM 逐帧喂给
 * 现成能量 VAD，依据 {@code isSpeaking()} 状态跳变还原语音段，进而计算：</p>
 * <ul>
 *   <li><b>段间静默</b>：相邻语音段之间的无声间隔（对 VAD 尾音 400ms、起始 60ms 做固定校正）；</li>
 *   <li><b>有声时长</b>：各语音段去头去尾后的实际发声时长之和；</li>
 *   <li><b>语速</b>：转写汉字数（中文约等于音节数）÷ 有声时长，单位 字/分钟。
 *       单声道链路算整体语速（{@link #analyze(int, byte[], String)}）；双声道链路按角色分别计算
 *       （{@link #analyzeTwoChannel}，转写需按 leg 分别提供）；</li>
 *   <li><b>抢话/重叠</b>（P2-7）：双通道各自 VAD 分段，校正后语音段时间区间发生重叠即判抢话，
 *       与声道身份解耦——输入是两路分轨 PCM，坐席/客户角色由采集侧标定后以 role 参数传入。</li>
 * </ul>
 *
 * <p>双声道数据来源：FreeSWITCH uuid_record 双腿分轨（{@link WavPcmDecoder#decodeTwoLegs}）或
 * RECORD_STEREO 立体声（{@link WavPcmDecoder#decodeChannels}），本类只消费已对齐的等长两路 PCM。
 * 纯算法、可离线对整段录音执行。</p>
 *
 * @author ai-lawyers
 */
@Component
public class AcousticFeatureAnalyzer
{
    /** 段间静默判定阈值（毫秒） */
    @Value("${ai.quality.acoustic.long-silence-ms:3000}")
    private long longSilenceMs = 3000;

    /** 每次长静默对服务规范维度的扣分 */
    @Value("${ai.quality.acoustic.long-silence-penalty:10}")
    private int longSilencePenalty = 10;

    /** 长静默累计扣分上限 */
    @Value("${ai.quality.acoustic.long-silence-max-penalty:30}")
    private int longSilenceMaxPenalty = 30;

    /** 语速下限（字/分钟），低于判语速过慢 */
    @Value("${ai.quality.acoustic.rate-min:80}")
    private int rateMin = 80;

    /** 语速上限（字/分钟），高于判语速过快 */
    @Value("${ai.quality.acoustic.rate-max:400}")
    private int rateMax = 400;

    /** 语速异常扣分 */
    @Value("${ai.quality.acoustic.rate-penalty:10}")
    private int ratePenalty = 10;

    /** P2-7：抢话/重叠判定的最短重叠时长（毫秒），短于此时长视为串音不判 */
    @Value("${ai.quality.acoustic.overlap-min-ms:300}")
    private long overlapMinMs = 300;

    /** P2-7：每次抢话/重叠对服务规范维度的扣分 */
    @Value("${ai.quality.acoustic.overlap-penalty:5}")
    private int overlapPenalty = 5;

    /** P2-7：抢话/重叠累计扣分上限 */
    @Value("${ai.quality.acoustic.overlap-max-penalty:20}")
    private int overlapMaxPenalty = 20;

    /** 计算语速所需的最短有声时长（毫秒），过短不出语速结论 */
    private static final long MIN_VOICED_MS_FOR_RATE = 1000;

    /** VAD 帧长 20ms；尾音 20 帧、起始 3 帧（与 VoiceActivityDetector 默认一致） */
    private static final int FRAME_MS = 20;
    private static final int END_SILENCE_FRAMES = VoiceActivityDetector.DEFAULT_END_SILENCE_FRAMES;
    private static final int START_FRAMES = VoiceActivityDetector.DEFAULT_START_FRAMES;

    /** 声学分析结果（违规/扣分 + 特征值，供融合与留痕） */
    public static class AcousticResult
    {
        private final List<RuleViolation> violations;
        private final int serviceNormPenalty;
        private final double speechRate;
        private final long maxSilenceMs;
        private final long voicedMs;
        private final int cjkChars;

        AcousticResult(List<RuleViolation> violations, int serviceNormPenalty, double speechRate,
                       long maxSilenceMs, long voicedMs, int cjkChars)
        {
            this.violations = violations;
            this.serviceNormPenalty = serviceNormPenalty;
            this.speechRate = speechRate;
            this.maxSilenceMs = maxSilenceMs;
            this.voicedMs = voicedMs;
            this.cjkChars = cjkChars;
        }

        public List<RuleViolation> getViolations() { return violations; }
        public int getServiceNormPenalty() { return serviceNormPenalty; }
        public double getSpeechRate() { return speechRate; }
        public long getMaxSilenceMs() { return maxSilenceMs; }
        public long getVoicedMs() { return voicedMs; }
        public int getCjkChars() { return cjkChars; }
    }

    /** 单声道 VAD 分段轨迹：段区间（VAD 原始帧）+ 总帧数 + 末段是否未闭合 */
    private static class VadTrack
    {
        final List<int[]> segments;
        final int totalFrames;
        final boolean openAtEnd;

        VadTrack(List<int[]> segments, int totalFrames, boolean openAtEnd)
        {
            this.segments = segments;
            this.totalFrames = totalFrames;
            this.openAtEnd = openAtEnd;
        }
    }

    /** P2-7：单角色声学特征（角色名 + 单路分析结果 + 校正后语音段毫秒区间） */
    public static class RoleFeatures
    {
        private final String role;
        private final AcousticResult result;
        private final List<long[]> speechIntervalsMs;

        RoleFeatures(String role, AcousticResult result, List<long[]> speechIntervalsMs)
        {
            this.role = role;
            this.result = result;
            this.speechIntervalsMs = speechIntervalsMs;
        }

        public String getRole() { return role; }
        public AcousticResult getResult() { return result; }
        /** 校正后语音段区间列表，每项 long[]{startMs, endMs}，按时间升序 */
        public List<long[]> getSpeechIntervalsMs() { return speechIntervalsMs; }
    }

    /** P2-7：一次抢话/重叠区间 */
    public static class Overlap
    {
        private final long startMs;
        private final long endMs;

        Overlap(long startMs, long endMs)
        {
            this.startMs = startMs;
            this.endMs = endMs;
        }

        public long getStartMs() { return startMs; }
        public long getEndMs() { return endMs; }
        public long getDurationMs() { return endMs - startMs; }
    }

    /** P2-7：双通道声学分析结果（分角色特征 + 抢话重叠区间/扣分） */
    public static class DualChannelResult
    {
        private final int sampleRate;
        private final RoleFeatures roleA;
        private final RoleFeatures roleB;
        private final List<Overlap> overlaps;
        private final long overlapTotalMs;
        private final List<RuleViolation> violations;
        private final int serviceNormPenalty;

        DualChannelResult(int sampleRate, RoleFeatures roleA, RoleFeatures roleB,
                          List<Overlap> overlaps, long overlapTotalMs,
                          List<RuleViolation> violations, int serviceNormPenalty)
        {
            this.sampleRate = sampleRate;
            this.roleA = roleA;
            this.roleB = roleB;
            this.overlaps = overlaps;
            this.overlapTotalMs = overlapTotalMs;
            this.violations = violations;
            this.serviceNormPenalty = serviceNormPenalty;
        }

        public int getSampleRate() { return sampleRate; }
        public RoleFeatures getRoleA() { return roleA; }
        public RoleFeatures getRoleB() { return roleB; }
        public List<Overlap> getOverlaps() { return overlaps; }
        public long getOverlapTotalMs() { return overlapTotalMs; }
        /** 跨角色汇总违规（当前仅"抢话"一条汇总）；各角色自身违规见 RoleFeatures#getResult() */
        public List<RuleViolation> getViolations() { return violations; }
        /** 两角色静默/语速扣分 + 抢话扣分合计（上限 100） */
        public int getServiceNormPenalty() { return serviceNormPenalty; }
    }

    /**
     * 对整段单声道 PCM 执行声学分析（整体语速，无角色归属）。
     *
     * @param sampleRate 采样率（仅支持 8000/16000）
     * @param pcm        S16LE 单声道 PCM
     * @param transcript 转写文本（用于字数与语速）
     */
    public AcousticResult analyze(int sampleRate, byte[] pcm, String transcript)
    {
        return analyze(sampleRate, pcm, transcript, null);
    }

    /**
     * P2-7：带角色名的单路声学分析。违规原因前会标注角色（keyword 不变，不影响规则聚合）。
     *
     * @param role 角色名（如"坐席"/"客户"），null 时不标注
     */
    public AcousticResult analyze(int sampleRate, byte[] pcm, String transcript, String role)
    {
        List<RuleViolation> violations = new ArrayList<>();
        int penalty = 0;
        long maxSilenceMs = 0;
        double speechRate = 0;

        if (pcm == null || pcm.length == 0)
        {
            return new AcousticResult(violations, penalty, speechRate, maxSilenceMs, 0, 0);
        }

        VadTrack track = detectSegments(sampleRate, pcm);
        List<int[]> segments = track.segments;
        String prefix = role == null ? "" : "【" + role + "】";

        // 段间静默：false 帧 + 17 帧校正（尾音20 - 起始3 = 17 ×20ms = 340ms）
        for (int s = 1; s < segments.size(); s++)
        {
            int falseFrames = segments.get(s)[0] - segments.get(s - 1)[1];
            long gapMs = (falseFrames + END_SILENCE_FRAMES - START_FRAMES) * (long) FRAME_MS;
            maxSilenceMs = Math.max(maxSilenceMs, gapMs);
        }

        // 有声时长：闭合段去17帧头尾差，结尾开口段补起始3帧
        long voicedFrames = 0;
        for (int k = 0; k < segments.size(); k++)
        {
            int[] seg = segments.get(k);
            boolean lastOpen = k == segments.size() - 1 && track.openAtEnd;
            long f = seg[1] - seg[0] - (lastOpen ? (-START_FRAMES) : (END_SILENCE_FRAMES - START_FRAMES));
            voicedFrames += Math.max(0, f);
        }
        long voicedMs = voicedFrames * FRAME_MS;

        int cjk = countCjk(transcript);
        if (voicedMs >= MIN_VOICED_MS_FOR_RATE && cjk > 0)
        {
            speechRate = round1(cjk * 60000d / voicedMs);
        }

        // 长静默违规（汇总为一条，扣分按次数封顶）
        int longGaps = 0;
        for (int s = 1; s < segments.size(); s++)
        {
            int falseFrames = segments.get(s)[0] - segments.get(s - 1)[1];
            long gapMs = (falseFrames + END_SILENCE_FRAMES - START_FRAMES) * (long) FRAME_MS;
            if (gapMs >= longSilenceMs)
            {
                longGaps++;
            }
        }
        if (longGaps > 0)
        {
            int p = Math.min(longSilenceMaxPenalty, longGaps * longSilencePenalty);
            penalty += p;
            violations.add(new RuleViolation("长静默",
                    prefix + "检测到 " + longGaps + " 处超长静默（最长 " + maxSilenceMs + "ms，阈值 "
                            + longSilenceMs + "ms），服务规范扣 " + p + " 分"));
        }

        // 语速违规
        if (speechRate > 0)
        {
            if (speechRate > rateMax)
            {
                penalty += ratePenalty;
                violations.add(new RuleViolation("语速过快",
                        prefix + (role == null ? "整体" : role) + "语速约 " + speechRate + " 字/分钟（阈值 "
                                + rateMax + "），服务规范扣 " + ratePenalty + " 分"));
            }
            else if (speechRate < rateMin)
            {
                penalty += ratePenalty;
                violations.add(new RuleViolation("语速过慢",
                        prefix + (role == null ? "整体" : role) + "语速约 " + speechRate + " 字/分钟（阈值 "
                                + rateMin + "），服务规范扣 " + ratePenalty + " 分"));
            }
        }

        return new AcousticResult(violations, Math.min(100, penalty), speechRate,
                maxSilenceMs, voicedMs, cjk);
    }

    /**
     * P2-7：双通道分角色声学分析（默认角色名：legA=坐席，legB=客户）。
     * 两路 PCM 需由 {@link WavPcmDecoder#decodeChannels} 或 {@link WavPcmDecoder#decodeTwoLegs}
     * 对齐得到（等帧、同采样率）。
     *
     * @param pcmA        坐席侧 S16LE 单声道 PCM
     * @param transcriptA 坐席侧转写（可空，空则不出该角色语速结论）
     * @param pcmB        客户侧 S16LE 单声道 PCM
     * @param transcriptB 客户侧转写（可空）
     */
    public DualChannelResult analyzeTwoChannel(int sampleRate, byte[] pcmA, String transcriptA,
                                               byte[] pcmB, String transcriptB)
    {
        return analyzeTwoChannel(sampleRate, pcmA, transcriptA, pcmB, transcriptB, "坐席", "客户");
    }

    /**
     * P2-7：双通道分角色声学分析（显式角色名，声道映射由采集侧标定后传入）。
     * 抢话=两路校正后语音段时间区间重叠（≥ overlapMinMs）；分角色语速=两路分别按各自转写计算。
     */
    public DualChannelResult analyzeTwoChannel(int sampleRate, byte[] pcmA, String transcriptA,
                                               byte[] pcmB, String transcriptB,
                                               String roleA, String roleB)
    {
        AcousticResult resultA = analyze(sampleRate, pcmA, transcriptA, roleA);
        AcousticResult resultB = analyze(sampleRate, pcmB, transcriptB, roleB);
        List<long[]> intervalsA = pcmA == null || pcmA.length == 0
                ? new ArrayList<>() : correctedIntervals(sampleRate, pcmA);
        List<long[]> intervalsB = pcmB == null || pcmB.length == 0
                ? new ArrayList<>() : correctedIntervals(sampleRate, pcmB);

        // 双指针归并求重叠区间（两列表均按时间升序）
        List<Overlap> overlaps = new ArrayList<>();
        long overlapTotalMs = 0;
        long maxOverlapMs = 0;
        int i = 0;
        int j = 0;
        while (i < intervalsA.size() && j < intervalsB.size())
        {
            long[] a = intervalsA.get(i);
            long[] b = intervalsB.get(j);
            long start = Math.max(a[0], b[0]);
            long end = Math.min(a[1], b[1]);
            if (end > start)
            {
                long dur = end - start;
                if (dur >= overlapMinMs)
                {
                    overlaps.add(new Overlap(start, end));
                    overlapTotalMs += dur;
                    maxOverlapMs = Math.max(maxOverlapMs, dur);
                }
            }
            if (a[1] <= b[1])
            {
                i++;
            }
            else
            {
                j++;
            }
        }

        List<RuleViolation> violations = new ArrayList<>();
        int overlapP = 0;
        if (!overlaps.isEmpty())
        {
            overlapP = Math.min(overlapMaxPenalty, overlaps.size() * overlapPenalty);
            violations.add(new RuleViolation("抢话",
                    "检测到 " + overlaps.size() + " 处" + roleA + "/" + roleB + "抢话或重叠"
                            + "（累计 " + overlapTotalMs + "ms，最长 " + maxOverlapMs + "ms，阈值 "
                            + overlapMinMs + "ms），服务规范扣 " + overlapP + " 分"));
        }

        int totalPenalty = Math.min(100,
                resultA.getServiceNormPenalty() + resultB.getServiceNormPenalty() + overlapP);
        return new DualChannelResult(sampleRate,
                new RoleFeatures(roleA, resultA, intervalsA),
                new RoleFeatures(roleB, resultB, intervalsB),
                overlaps, overlapTotalMs, violations, totalPenalty);
    }

    /** 逐帧 VAD 分段，返回段区间 [起始帧, 结束帧)（原始帧，未做尾音/起始校正） */
    private static VadTrack detectSegments(int sampleRate, byte[] pcm)
    {
        int frameBytes = sampleRate * FRAME_MS / 1000 * 2;
        int totalFrames = pcm.length / frameBytes;

        VoiceActivityDetector vad = new VoiceActivityDetector(sampleRate, null);
        List<int[]> segments = new ArrayList<>();
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
                segments.add(new int[]{segStart, i});
                segStart = -1;
            }
        }
        boolean open = segStart >= 0;
        if (open)
        {
            segments.add(new int[]{segStart, totalFrames});
        }
        return new VadTrack(segments, totalFrames, open);
    }

    /**
     * 还原校正后的语音段毫秒区间：
     * 起点回溯起始 3 帧（触发时已连续 3 帧有声），闭合段终点回退尾音 20 帧，开口段截到录音末尾。
     */
    private List<long[]> correctedIntervals(int sampleRate, byte[] pcm)
    {
        VadTrack track = detectSegments(sampleRate, pcm);
        List<long[]> intervals = new ArrayList<>();
        for (int k = 0; k < track.segments.size(); k++)
        {
            int[] seg = track.segments.get(k);
            boolean lastOpen = k == track.segments.size() - 1 && track.openAtEnd;
            int startFrame = Math.max(0, seg[0] - START_FRAMES);
            int endFrame = lastOpen ? seg[1] : Math.min(track.totalFrames, seg[1] - END_SILENCE_FRAMES);
            if (endFrame > startFrame)
            {
                intervals.add(new long[]{startFrame * (long) FRAME_MS, endFrame * (long) FRAME_MS});
            }
        }
        return intervals;
    }

    /** 统计 CJK 表意文字数（汉字，中文约等于音节数） */
    private static int countCjk(String text)
    {
        if (text == null || text.isEmpty())
        {
            return 0;
        }
        int count = 0;
        for (int i = 0; i < text.length(); )
        {
            int cp = text.codePointAt(i);
            if (Character.getType(cp) == Character.OTHER_LETTER)
            {
                count++;
            }
            i += Character.charCount(cp);
        }
        return count;
    }

    private static double round1(double value)
    {
        return java.math.BigDecimal.valueOf(value).setScale(1, java.math.BigDecimal.ROUND_HALF_UP).doubleValue();
    }
}
