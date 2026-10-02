package ai.lawyers.system.service.lawyers.voice.vad;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Silero VAD 影子服务（P2-12 方案 A）。
 *
 * <p>包装真实 VAD（能量+过零率）与 Silero VAD 影子判定，两者并行运行：
 * 主决策仍走旧 VAD，Silero 判定结果仅记录日志打标，用于后续对比分析。</p>
 *
 * <p>统计维度：</p>
 * <ul>
 *   <li>误打断率：Silero 判定为语音但旧 VAD 未触发（潜在误报）</li>
 *   <li>漏打断率：旧 VAD 触发语音但 Silero 未判定（潜在漏报）</li>
 *   <li>打断响应延迟差：两者判定起始时间的差值分布</li>
 * </ul>
 *
 * <p>每日统计报告：通过 {@link #dailyReport()} 输出结构化日志，可由上层调度器
 * 定时调用并落表（见 sql/ai_system_p2_vad_shadow_20261002.sql）。</p>
 *
 * @author ai-lawyers
 */
public class SileroVadShadowService
{
    private static final Logger log = LoggerFactory.getLogger(SileroVadShadowService.class);
    private static final Logger REPORT_LOG = LoggerFactory.getLogger("VAD_SHADOW_REPORT");

    /** 会话级影子状态（key=sessionId） */
    private final Map<String, SessionShadow> sessions = new ConcurrentHashMap<>();

    /** 全局累计统计 */
    private final LongAdder totalFrames = new LongAdder();
    private final LongAdder bothVoiceFrames = new LongAdder();
    private final LongAdder onlyLegacyVoiceFrames = new LongAdder();
    private final LongAdder onlySileroVoiceFrames = new LongAdder();
    private final LongAdder bothSilentFrames = new LongAdder();
    private final LongAdder legacySpeechStartCount = new LongAdder();
    private final LongAdder sileroSpeechStartCount = new LongAdder();
    private final LongAdder legacySpeechEndCount = new LongAdder();
    private final LongAdder sileroSpeechEndCount = new LongAdder();

    /** 延迟差值累计（ms，正数表示 Silero 比旧 VAD 慢） */
    private final LongAdder latencyDiffSumMs = new LongAdder();
    private final LongAdder latencyDiffCount = new LongAdder();

    /** 最近一次每日报告日期（yyyy-MM-dd） */
    private volatile String lastReportDate;

    /**
     * 为指定会话创建影子检测对。
     *
     * @param sessionId  会话ID
     * @param sampleRate 采样率
     * @param legacyListener 旧 VAD 事件回调（主链路）
     * @return 包装后的 VAD 对；null 表示影子模式未启用或创建失败
     */
    public VadPair createShadowPair(String sessionId, int sampleRate,
            VoiceActivityDetector.Listener legacyListener)
    {
        return createShadowPair(sessionId, sampleRate, legacyListener,
                SileroVadDetector.DEFAULT_MODEL_PATH, SileroVadDetector.DEFAULT_THRESHOLD);
    }

    /**
     * 为指定会话创建影子检测对（完整参数）。
     *
     * @param sessionId  会话ID
     * @param sampleRate 采样率
     * @param legacyListener 旧 VAD 事件回调（主链路）
     * @param modelPath  Silero 模型路径
     * @param threshold  Silero 概率阈值
     * @return 包装后的 VAD 对；null 表示影子模式未启用或创建失败
     */
    public VadPair createShadowPair(String sessionId, int sampleRate,
            VoiceActivityDetector.Listener legacyListener,
            String modelPath, float threshold)
    {
        SessionShadow shadow = new SessionShadow(sessionId);
        sessions.put(sessionId, shadow);

        // 旧 VAD：主链路，事件直接透传
        VoiceActivityDetector legacy = new VoiceActivityDetector(sampleRate, new VoiceActivityDetector.Listener()
        {
            @Override
            public void onSpeechStart()
            {
                shadow.legacySpeechStartNanos = System.nanoTime();
                legacySpeechStartCount.increment();
                if (legacyListener != null)
                {
                    legacyListener.onSpeechStart();
                }
            }

            @Override
            public void onSpeechEnd()
            {
                legacySpeechEndCount.increment();
                if (legacyListener != null)
                {
                    legacyListener.onSpeechEnd();
                }
            }
        });

        // Silero VAD：影子链路，仅记录日志和统计
        SileroVadDetector silero = new SileroVadDetector(sampleRate, modelPath, threshold,
                SileroVadDetector.DEFAULT_START_FRAMES, SileroVadDetector.DEFAULT_END_SILENCE_FRAMES,
                new SileroVadDetector.ShadowListener()
                {
                    @Override
                    public void onShadowSpeechStart(long detectMs)
                    {
                        shadow.sileroSpeechStartNanos = System.nanoTime();
                        shadow.sileroSpeechStartMs = detectMs;
                        sileroSpeechStartCount.increment();
                        log.debug("VAD影子[{}] Silero语音开始 detectMs={} legacySpeaking={} sileroSpeaking={}",
                                sessionId, detectMs, shadow.legacySpeaking, shadow.sileroSpeaking);
                    }

                    @Override
                    public void onShadowSpeechEnd()
                    {
                        sileroSpeechEndCount.increment();
                        log.debug("VAD影子[{}] Silero语音结束 legacySpeaking={} sileroSpeaking={}",
                                sessionId, shadow.legacySpeaking, shadow.sileroSpeaking);
                    }
                });

        return new VadPair(sessionId, legacy, silero, shadow, this);
    }

    /**
     * 帧级对比统计（由 VadPair.feed 内部调用）。
     *
     * @param sessionId 会话ID
     * @param legacyVoiced 旧 VAD 当前帧判定
     * @param sileroVoiced Silero 当前帧判定
     */
    void onFrameCompared(String sessionId, boolean legacyVoiced, boolean sileroVoiced)
    {
        totalFrames.increment();
        if (legacyVoiced && sileroVoiced)
        {
            bothVoiceFrames.increment();
        }
        else if (legacyVoiced && !sileroVoiced)
        {
            onlyLegacyVoiceFrames.increment();
        }
        else if (!legacyVoiced && sileroVoiced)
        {
            onlySileroVoiceFrames.increment();
        }
        else
        {
            bothSilentFrames.increment();
        }

        SessionShadow shadow = sessions.get(sessionId);
        if (shadow != null)
        {
            shadow.totalFrames.increment();
            if (legacyVoiced && sileroVoiced)
            {
                shadow.bothVoiceFrames.increment();
            }
            else if (legacyVoiced && !sileroVoiced)
            {
                shadow.onlyLegacyVoiceFrames.increment();
            }
            else if (!legacyVoiced && sileroVoiced)
            {
                shadow.onlySileroVoiceFrames.increment();
            }
            else
            {
                shadow.bothSilentFrames.increment();
            }
        }
    }

    /**
     * 语音起始事件对比（由 VadPair 内部回调触发）。
     *
     * @param sessionId 会话ID
     * @param legacyDetectMs 旧 VAD 起始判定耗时
     * @param sileroDetectMs Silero 起始判定耗时
     */
    void onSpeechStartCompared(String sessionId, long legacyDetectMs, long sileroDetectMs)
    {
        long diff = sileroDetectMs - legacyDetectMs;
        latencyDiffSumMs.add(diff);
        latencyDiffCount.increment();

        SessionShadow shadow = sessions.get(sessionId);
        if (shadow != null)
        {
            shadow.latencyDiffSumMs.add(diff);
            shadow.latencyDiffCount.increment();
        }

        log.debug("VAD影子[{}] 语音起始对比 legacyMs={} sileroMs={} diffMs={}",
                sessionId, legacyDetectMs, sileroDetectMs, diff);
    }

    /**
     * 会话结束，清理并输出会话级摘要。
     *
     * @param sessionId 会话ID
     */
    public void onSessionEnd(String sessionId)
    {
        SessionShadow shadow = sessions.remove(sessionId);
        if (shadow != null)
        {
            long total = shadow.totalFrames.sum();
            if (total > 0)
            {
                double falseAlarmRate = (double) shadow.onlySileroVoiceFrames.sum() / total;
                double missRate = (double) shadow.onlyLegacyVoiceFrames.sum() / total;
                double avgDiff = shadow.latencyDiffCount.sum() > 0
                        ? (double) shadow.latencyDiffSumMs.sum() / shadow.latencyDiffCount.sum() : 0;

                log.info("VAD影子会话摘要 sessionId={} totalFrames={} bothVoice={} onlyLegacy={} onlySilero={} "
                        + "falseAlarmRate={:.4f} missRate={:.4f} avgLatencyDiffMs={:.1f}",
                        sessionId, total, shadow.bothVoiceFrames.sum(),
                        shadow.onlyLegacyVoiceFrames.sum(), shadow.onlySileroVoiceFrames.sum(),
                        falseAlarmRate, missRate, avgDiff);
            }
        }
    }

    /**
     * 输出每日统计报告（结构化日志，可由调度器定时调用）。
     * 报告格式兼容落表解析：key=value 对，逗号分隔。
     */
    public void dailyReport()
    {
        String today = java.time.LocalDate.now().toString();
        if (today.equals(lastReportDate))
        {
            return;
        }
        lastReportDate = today;

        long total = totalFrames.sum();
        long bothVoice = bothVoiceFrames.sum();
        long onlyLegacy = onlyLegacyVoiceFrames.sum();
        long onlySilero = onlySileroVoiceFrames.sum();
        long bothSilent = bothSilentFrames.sum();
        long legacyStart = legacySpeechStartCount.sum();
        long sileroStart = sileroSpeechStartCount.sum();
        long legacyEnd = legacySpeechEndCount.sum();
        long sileroEnd = sileroSpeechEndCount.sum();

        double falseAlarmRate = total > 0 ? (double) onlySilero / total : 0;
        double missRate = total > 0 ? (double) onlyLegacy / total : 0;
        double avgLatencyDiff = latencyDiffCount.sum() > 0
                ? (double) latencyDiffSumMs.sum() / latencyDiffCount.sum() : 0;

        // 结构化报告日志（便于采集落表）
        REPORT_LOG.info("VAD_SHADOW_DAILY date={} totalFrames={} bothVoiceFrames={} "
                + "onlyLegacyVoiceFrames={} onlySileroVoiceFrames={} bothSilentFrames={} "
                + "legacySpeechStartCount={} sileroSpeechStartCount={} "
                + "legacySpeechEndCount={} sileroSpeechEndCount={} "
                + "falseAlarmRate={:.6f} missRate={:.6f} avgLatencyDiffMs={:.2f}",
                today, total, bothVoice, onlyLegacy, onlySilero, bothSilent,
                legacyStart, sileroStart, legacyEnd, sileroEnd,
                falseAlarmRate, missRate, avgLatencyDiff);

        // 重置日累计（可选：按日滚动统计）
        totalFrames.reset();
        bothVoiceFrames.reset();
        onlyLegacyVoiceFrames.reset();
        onlySileroVoiceFrames.reset();
        bothSilentFrames.reset();
        legacySpeechStartCount.reset();
        sileroSpeechStartCount.reset();
        legacySpeechEndCount.reset();
        sileroSpeechEndCount.reset();
        latencyDiffSumMs.reset();
        latencyDiffCount.reset();
    }

    /**
     * 获取当前全局统计快照（用于监控接口）。
     *
     * @return 统计 Map
     */
    public Map<String, Object> snapshot()
    {
        long total = totalFrames.sum();
        Map<String, Object> m = new ConcurrentHashMap<>();
        m.put("totalFrames", total);
        m.put("bothVoiceFrames", bothVoiceFrames.sum());
        m.put("onlyLegacyVoiceFrames", onlyLegacyVoiceFrames.sum());
        m.put("onlySileroVoiceFrames", onlySileroVoiceFrames.sum());
        m.put("bothSilentFrames", bothSilentFrames.sum());
        m.put("legacySpeechStartCount", legacySpeechStartCount.sum());
        m.put("sileroSpeechStartCount", sileroSpeechStartCount.sum());
        m.put("legacySpeechEndCount", legacySpeechEndCount.sum());
        m.put("sileroSpeechEndCount", sileroSpeechEndCount.sum());
        m.put("falseAlarmRate", total > 0 ? (double) onlySileroVoiceFrames.sum() / total : 0);
        m.put("missRate", total > 0 ? (double) onlyLegacyVoiceFrames.sum() / total : 0);
        m.put("avgLatencyDiffMs", latencyDiffCount.sum() > 0
                ? (double) latencyDiffSumMs.sum() / latencyDiffCount.sum() : 0);
        m.put("activeSessions", sessions.size());
        return m;
    }

    /* ================= 内部类 ================= */

    /** 会话级影子状态 */
    static class SessionShadow
    {
        final String sessionId;
        volatile boolean legacySpeaking;
        volatile boolean sileroSpeaking;
        volatile long legacySpeechStartNanos;
        volatile long sileroSpeechStartNanos;
        volatile long sileroSpeechStartMs;

        final LongAdder totalFrames = new LongAdder();
        final LongAdder bothVoiceFrames = new LongAdder();
        final LongAdder onlyLegacyVoiceFrames = new LongAdder();
        final LongAdder onlySileroVoiceFrames = new LongAdder();
        final LongAdder bothSilentFrames = new LongAdder();
        final LongAdder latencyDiffSumMs = new LongAdder();
        final LongAdder latencyDiffCount = new LongAdder();

        SessionShadow(String sessionId)
        {
            this.sessionId = sessionId;
        }
    }

    /**
     * VAD 对：包装旧 VAD（主链路）和 Silero VAD（影子链路），对外暴露统一 feed 入口。
     * 主链路事件直接透传，影子链路事件仅记录日志和统计。
     */
    public static class VadPair
    {
        private final String sessionId;
        private final VoiceActivityDetector legacy;
        private final SileroVadDetector silero;
        private final SessionShadow shadow;
        private final SileroVadShadowService service;

        VadPair(String sessionId, VoiceActivityDetector legacy, SileroVadDetector silero,
                SessionShadow shadow, SileroVadShadowService service)
        {
            this.sessionId = sessionId;
            this.legacy = legacy;
            this.silero = silero;
            this.shadow = shadow;
            this.service = service;
        }

        /**
         * 喂入音频帧：同时喂给旧 VAD（主决策）和 Silero VAD（影子判定）。
         * 旧 VAD 的 Listener 回调在 feed 线程内同步触发（主链路行为不变）。
         */
        public void feed(byte[] pcm)
        {
            // 旧 VAD：主链路，事件直接透传
            legacy.feed(pcm);
            // Silero VAD：影子链路，仅记录
            silero.feed(pcm);

            // 帧级对比：需要知道两者当前帧的判定结果
            // 由于 VoiceActivityDetector 未暴露 per-frame 判定，这里通过状态近似：
            // 用 speaking 状态作为"当前处于语音段"的代理指标
            boolean legacyState = legacy.isSpeaking();
            boolean sileroState = silero.isShadowSpeaking();
            service.onFrameCompared(sessionId, legacyState, sileroState);

            // 状态同步（用于日志）
            shadow.legacySpeaking = legacyState;
            shadow.sileroSpeaking = sileroState;
        }

        /** 获取旧 VAD（主链路） */
        public VoiceActivityDetector getLegacy()
        {
            return legacy;
        }

        /** 获取 Silero VAD（影子链路） */
        public SileroVadDetector getSilero()
        {
            return silero;
        }

        /** 重置两者状态 */
        public void reset()
        {
            legacy.reset();
            silero.reset();
        }

        /** 释放资源 */
        public void close()
        {
            silero.close();
            service.onSessionEnd(sessionId);
        }
    }
}
