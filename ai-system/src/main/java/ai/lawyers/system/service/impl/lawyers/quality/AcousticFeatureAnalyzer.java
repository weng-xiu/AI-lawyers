package ai.lawyers.system.service.impl.lawyers.quality;

import java.util.ArrayList;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import ai.lawyers.system.service.impl.lawyers.quality.QualityRuleEngine.RuleViolation;
import ai.lawyers.system.service.lawyers.voice.vad.VoiceActivityDetector;

/**
 * P1-7：质检声学特征分析器（静默时长 / 整体语速）。
 *
 * <p>不依赖 ASR 时间戳：质检已能读到完整录音，本类把 WAV 解出的 S16LE 单声道 PCM 逐帧喂给
 * 现成能量 VAD，依据 {@code isSpeaking()} 状态跳变还原语音段，进而计算：</p>
 * <ul>
 *   <li><b>段间静默</b>：相邻语音段之间的无声间隔（对 VAD 尾音 400ms、起始 60ms 做固定校正）；</li>
 *   <li><b>有声时长</b>：各语音段去头去尾后的实际发声时长之和；</li>
 *   <li><b>整体语速</b>：转写汉字数（中文约等于音节数）÷ 有声时长，单位 字/分钟。</li>
 * </ul>
 *
 * <p>硬阻塞项：抢话/重叠、分角色语速需双声道（双方分轨）录音，当前为单声道混音，
 * 须 FreeSWITCH 采集侧改造，不在本类范围。纯算法、可离线对整段录音执行。</p>
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

    /**
     * 对整段单声道 PCM 执行声学分析。
     *
     * @param sampleRate 采样率（仅支持 8000/16000）
     * @param pcm        S16LE 单声道 PCM
     * @param transcript 转写文本（用于字数与语速）
     */
    public AcousticResult analyze(int sampleRate, byte[] pcm, String transcript)
    {
        List<RuleViolation> violations = new ArrayList<>();
        int penalty = 0;
        long maxSilenceMs = 0;
        double speechRate = 0;

        if (pcm == null || pcm.length == 0)
        {
            return new AcousticResult(violations, penalty, speechRate, maxSilenceMs, 0, 0);
        }

        int frameBytes = sampleRate * FRAME_MS / 1000 * 2;
        int totalFrames = pcm.length / frameBytes;

        // 逐帧喂入，按 isSpeaking 跳变记录语音段（startFrame 含 VAD 起始3帧，endFrame 含尾音20帧）
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
            boolean lastOpen = k == segments.size() - 1 && open;
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
                    "检测到 " + longGaps + " 处超长静默（最长 " + maxSilenceMs + "ms，阈值 "
                            + longSilenceMs + "ms），服务规范扣 " + p + " 分"));
        }

        // 整体语速违规
        if (speechRate > 0)
        {
            if (speechRate > rateMax)
            {
                penalty += ratePenalty;
                violations.add(new RuleViolation("语速过快",
                        "整体语速约 " + speechRate + " 字/分钟（阈值 " + rateMax + "），服务规范扣 "
                                + ratePenalty + " 分"));
            }
            else if (speechRate < rateMin)
            {
                penalty += ratePenalty;
                violations.add(new RuleViolation("语速过慢",
                        "整体语速约 " + speechRate + " 字/分钟（阈值 " + rateMin + "），服务规范扣 "
                                + ratePenalty + " 分"));
            }
        }

        return new AcousticResult(violations, Math.min(100, penalty), speechRate,
                maxSilenceMs, voicedMs, cjk);
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
