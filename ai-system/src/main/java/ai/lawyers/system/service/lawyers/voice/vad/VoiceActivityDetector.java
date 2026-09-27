package ai.lawyers.system.service.lawyers.voice.vad;

/**
 * 轻量语音活动检测（P3-A4）：能量（RMS）+ 过零率（ZCR）双判据。
 *
 * <p>判定规则（第十四部分 4.1 节指标）：</p>
 * <ul>
 *   <li>帧长 20ms（16kHz→320 样本，8kHz→160 样本），喂入字节按帧切分，残余字节跨包缓冲；</li>
 *   <li>有声帧判据：RMS ≥ energyThreshold 且 ZCR ∈ [zcrMin, zcrMax]
 *       （ZCR 下限排除直流偏置/低频嗡声，上限排除白噪嘶声）；</li>
 *   <li>语音起始：连续 {@value #DEFAULT_START_FRAMES} 个有声帧（60ms，满足 ≤80ms 指标）触发
 *       {@link Listener#onSpeechStart()}；</li>
 *   <li>语音结束：语音态下连续 {@value #DEFAULT_END_SILENCE_FRAMES} 个静默帧（400ms）触发
 *       {@link Listener#onSpeechEnd()}。</li>
 * </ul>
 *
 * <p>纯算法类，无 Spring 依赖；{@link #feed(byte[])} 与回调在同一个调用线程（WS I/O 线程）
 * 内同步执行，内部状态用 synchronized 保护以容忍多源喂入。</p>
 *
 * @author ai-lawyers
 */
public class VoiceActivityDetector
{
    /** 语音起始连续有声帧数：3 × 20ms = 60ms（指标 ≤80ms） */
    public static final int DEFAULT_START_FRAMES = 3;

    /** 尾音静默判结束帧数：20 × 20ms = 400ms */
    public static final int DEFAULT_END_SILENCE_FRAMES = 20;

    /** 默认 RMS 能量阈值（S16LE 量纲，正常口语数百~数千，环境底噪通常 <200） */
    public static final double DEFAULT_ENERGY_THRESHOLD = 500.0;

    /** 默认 ZCR 下限（排除直流偏置/纯低频，ZCR≈0） */
    public static final double DEFAULT_ZCR_MIN = 0.002;

    /** 默认 ZCR 上限（排除白噪/嘶声，ZCR≈0.5） */
    public static final double DEFAULT_ZCR_MAX = 0.40;

    /** 语音事件回调（在 feed 调用线程内同步触发，实现方不得阻塞） */
    public interface Listener
    {
        void onSpeechStart();

        void onSpeechEnd();
    }

    private final int frameBytes;

    private final int startFrames;

    private final int endSilenceFrames;

    private final double energyThreshold;

    private final double zcrMin;

    private final double zcrMax;

    private final Listener listener;

    /** 残余缓冲：喂入字节非整帧倍数时留存待下次拼帧 */
    private final byte[] residue;

    private int residueLen;

    private boolean speaking;

    private int consecutiveVoice;

    private int consecutiveSilence;

    public VoiceActivityDetector(int sampleRate, Listener listener)
    {
        this(sampleRate, DEFAULT_ENERGY_THRESHOLD, DEFAULT_ZCR_MIN, DEFAULT_ZCR_MAX,
                DEFAULT_START_FRAMES, DEFAULT_END_SILENCE_FRAMES, listener);
    }

    public VoiceActivityDetector(int sampleRate, double energyThreshold, double zcrMin, double zcrMax,
            int startFrames, int endSilenceFrames, Listener listener)
    {
        if (sampleRate != 8000 && sampleRate != 16000)
        {
            throw new IllegalArgumentException("VAD 采样率仅支持 8000/16000: " + sampleRate);
        }
        this.frameBytes = sampleRate * 20 / 1000 * 2;
        this.energyThreshold = energyThreshold;
        this.zcrMin = zcrMin;
        this.zcrMax = zcrMax;
        this.startFrames = Math.max(1, startFrames);
        this.endSilenceFrames = Math.max(1, endSilenceFrames);
        this.listener = listener;
        this.residue = new byte[frameBytes];
    }

    /** 喂入一段 S16LE PCM（可为任意长度，内部按 20ms 帧切分） */
    public synchronized void feed(byte[] pcm)
    {
        if (pcm == null || pcm.length == 0)
        {
            return;
        }
        int offset = 0;
        // 先补齐残余缓冲凑满一帧
        if (residueLen > 0)
        {
            int need = frameBytes - residueLen;
            int copy = Math.min(need, pcm.length);
            System.arraycopy(pcm, 0, residue, residueLen, copy);
            residueLen += copy;
            offset += copy;
            if (residueLen == frameBytes)
            {
                processFrame(residue, 0);
                residueLen = 0;
            }
        }
        // 整帧直处理
        while (offset + frameBytes <= pcm.length)
        {
            processFrame(pcm, offset);
            offset += frameBytes;
        }
        // 尾残余留存
        if (offset < pcm.length)
        {
            System.arraycopy(pcm, offset, residue, 0, pcm.length - offset);
            residueLen = pcm.length - offset;
        }
    }

    /** 重置到静默态（stop/重新开始时调用），不清残余缓冲之外的配置 */
    public synchronized void reset()
    {
        speaking = false;
        consecutiveVoice = 0;
        consecutiveSilence = 0;
        residueLen = 0;
    }

    public synchronized boolean isSpeaking()
    {
        return speaking;
    }

    /* ================= 内部 ================= */

    private void processFrame(byte[] buf, int off)
    {
        double sumSq = 0;
        int zeroCross = 0;
        int samples = frameBytes / 2;
        int prev = sampleAt(buf, off);
        for (int i = 1; i < samples; i++)
        {
            int s = sampleAt(buf, off + i * 2);
            sumSq += (double) s * (double) s;
            if ((prev >= 0 && s < 0) || (prev < 0 && s >= 0))
            {
                zeroCross++;
            }
            prev = s;
        }
        double rms = Math.sqrt(sumSq / samples);
        double zcr = (double) zeroCross / samples;
        boolean voiced = rms >= energyThreshold && zcr >= zcrMin && zcr <= zcrMax;
        onFrame(voiced);
    }

    private void onFrame(boolean voiced)
    {
        if (!speaking)
        {
            consecutiveVoice = voiced ? consecutiveVoice + 1 : 0;
            if (consecutiveVoice >= startFrames)
            {
                speaking = true;
                consecutiveVoice = 0;
                consecutiveSilence = 0;
                if (listener != null)
                {
                    listener.onSpeechStart();
                }
            }
        }
        else
        {
            consecutiveSilence = voiced ? 0 : consecutiveSilence + 1;
            if (consecutiveSilence >= endSilenceFrames)
            {
                speaking = false;
                consecutiveSilence = 0;
                consecutiveVoice = 0;
                if (listener != null)
                {
                    listener.onSpeechEnd();
                }
            }
        }
    }

    /** S16LE 小端取样本 */
    private static int sampleAt(byte[] buf, int off)
    {
        return (short) ((buf[off] & 0xFF) | (buf[off + 1] << 8));
    }
}
