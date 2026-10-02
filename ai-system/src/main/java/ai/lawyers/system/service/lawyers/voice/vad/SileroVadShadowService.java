package ai.lawyers.system.service.lawyers.voice.vad;

import java.time.LocalDate;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.LongAdder;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import ai.lawyers.system.service.lawyers.voice.VoiceProperties;

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
 * <p>每日统计报告：{@link #dailyReport()} 输出结构化日志（logger=VAD_SHADOW_REPORT）
 * 并 UPSERT 落表 ai_vad_shadow_daily（见 sql/ai_system_p2_vad_shadow_20261002.sql），
 * 由 {@code VadShadowScheduleTask} 每日触发；多实例各自累加同一日期行
 * （统计为本机内存态，不走单主竞选）。会话级明细按
 * {@code voice.vad-shadow-session-sample-rate} 抽样写 ai_vad_shadow_session。</p>
 *
 * <p>无 ONNX Runtime / 模型文件时 Silero 链路降级 Mock（与旧 VAD 同判据），
 * Mock 模式仅验证链路与落表，误报/漏报必然趋零，真实对比结论须以 ONNX 模式为准。</p>
 *
 * @author ai-lawyers
 */
@Component
public class SileroVadShadowService
{
    private static final Logger log = LoggerFactory.getLogger(SileroVadShadowService.class);
    private static final Logger REPORT_LOG = LoggerFactory.getLogger("VAD_SHADOW_REPORT");

    /** 日统计 UPSERT：多实例按同一 stat_date 原子累加，率值由累加后的计数重算 */
    private static final String UPSERT_DAILY_SQL =
            "INSERT INTO ai_vad_shadow_daily "
            + "(stat_date,total_frames,both_voice_frames,only_legacy_frames,only_silero_frames,"
            + "both_silent_frames,legacy_start_count,silero_start_count,legacy_end_count,silero_end_count,"
            + "false_alarm_rate,miss_rate,avg_latency_diff_ms,active_sessions,latency_diff_count,"
            + "create_time,update_time) "
            + "VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,NOW(),NOW()) "
            + "ON DUPLICATE KEY UPDATE "
            + "false_alarm_rate=(only_silero_frames+VALUES(only_silero_frames))"
            + "/GREATEST(total_frames+VALUES(total_frames),1), "
            + "miss_rate=(only_legacy_frames+VALUES(only_legacy_frames))"
            + "/GREATEST(total_frames+VALUES(total_frames),1), "
            + "avg_latency_diff_ms=CASE WHEN (latency_diff_count+VALUES(latency_diff_count))=0 THEN 0 "
            + "ELSE (avg_latency_diff_ms*latency_diff_count"
            + "+VALUES(avg_latency_diff_ms)*VALUES(latency_diff_count))"
            + "/(latency_diff_count+VALUES(latency_diff_count)) END, "
            + "total_frames=total_frames+VALUES(total_frames), "
            + "both_voice_frames=both_voice_frames+VALUES(both_voice_frames), "
            + "only_legacy_frames=only_legacy_frames+VALUES(only_legacy_frames), "
            + "only_silero_frames=only_silero_frames+VALUES(only_silero_frames), "
            + "both_silent_frames=both_silent_frames+VALUES(both_silent_frames), "
            + "legacy_start_count=legacy_start_count+VALUES(legacy_start_count), "
            + "silero_start_count=silero_start_count+VALUES(silero_start_count), "
            + "legacy_end_count=legacy_end_count+VALUES(legacy_end_count), "
            + "silero_end_count=silero_end_count+VALUES(silero_end_count), "
            + "active_sessions=active_sessions+VALUES(active_sessions), "
            + "latency_diff_count=latency_diff_count+VALUES(latency_diff_count), "
            + "update_time=NOW()";

    /** 会话明细抽样落表（审计用） */
    private static final String INSERT_SESSION_SQL =
            "INSERT INTO ai_vad_shadow_session "
            + "(session_id,sample_rate,mock_mode,total_frames,both_voice_frames,"
            + "only_legacy_frames,only_silero_frames,false_alarm_rate,miss_rate,"
            + "avg_latency_diff_ms,legacy_start_count,silero_start_count,create_time,end_time) "
            + "VALUES (?,?,?,?,?,?,?,?,?,?,?,?,NOW(),NOW())";

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

    /** 当日已结束的有效会话数（有音频帧的会话） */
    private final LongAdder endedSessionCount = new LongAdder();

    /** 影子配置（模型路径/阈值/抽样率/开关） */
    private final VoiceProperties voiceProperties;

    /** 落表模板（未装配时仅日志统计，不阻断影子链路与单测） */
    @Autowired(required = false)
    private JdbcTemplate jdbcTemplate;

    /** 最近一次每日报告日期（yyyy-MM-dd） */
    private volatile String lastReportDate;

    public SileroVadShadowService(VoiceProperties voiceProperties)
    {
        this.voiceProperties = voiceProperties != null ? voiceProperties : new VoiceProperties();
    }

    /**
     * 为指定会话创建影子检测对；模型路径/概率阈值取 voice.vad-shadow-* 配置。
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
                voiceProperties.getVadShadowModelPath(), voiceProperties.getVadShadowThreshold());
    }

    /**
     * 为指定会话创建影子检测对（完整参数）。
     *
     * @param sessionId  会话ID
     * @param sampleRate 采样率
     * @param legacyListener 旧 VAD 事件回调（主链路）
     * @param modelPath  Silero 模型路径
     * @param threshold  Silero 概率阈值
     * @return 包装后的 VAD 对
     */
    public VadPair createShadowPair(String sessionId, int sampleRate,
            VoiceActivityDetector.Listener legacyListener,
            String modelPath, float threshold)
    {
        SessionShadow shadow = new SessionShadow(sessionId, sampleRate);
        sessions.put(sessionId, shadow);

        // 旧 VAD：主链路，事件直接透传
        VoiceActivityDetector legacy = new VoiceActivityDetector(sampleRate, new VoiceActivityDetector.Listener()
        {
            @Override
            public void onSpeechStart()
            {
                shadow.legacySpeechStartNanos = System.nanoTime();
                shadow.legacyStartTicks++;
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
                        shadow.sileroStartTicks++;
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
        shadow.mockMode = silero.isMockMode();

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
        accumulateFrame(totalFrames, bothVoiceFrames, onlyLegacyVoiceFrames,
                onlySileroVoiceFrames, bothSilentFrames, legacyVoiced, sileroVoiced);

        SessionShadow shadow = sessions.get(sessionId);
        if (shadow != null)
        {
            accumulateFrame(shadow.totalFrames, shadow.bothVoiceFrames, shadow.onlyLegacyVoiceFrames,
                    shadow.onlySileroVoiceFrames, shadow.bothSilentFrames, legacyVoiced, sileroVoiced);
        }
    }

    private static void accumulateFrame(LongAdder total, LongAdder bothVoice, LongAdder onlyLegacy,
            LongAdder onlySilero, LongAdder bothSilent, boolean legacyVoiced, boolean sileroVoiced)
    {
        total.increment();
        if (legacyVoiced && sileroVoiced)
        {
            bothVoice.increment();
        }
        else if (legacyVoiced)
        {
            onlyLegacy.increment();
        }
        else if (sileroVoiced)
        {
            onlySilero.increment();
        }
        else
        {
            bothSilent.increment();
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
     * 会话结束，清理并输出会话级摘要；按抽样率写会话明细。
     *
     * @param sessionId 会话ID
     */
    public void onSessionEnd(String sessionId)
    {
        SessionShadow shadow = sessions.remove(sessionId);
        if (shadow == null)
        {
            return;
        }
        long total = shadow.totalFrames.sum();
        if (total <= 0)
        {
            return;
        }
        endedSessionCount.increment();
        double falseAlarmRate = (double) shadow.onlySileroVoiceFrames.sum() / total;
        double missRate = (double) shadow.onlyLegacyVoiceFrames.sum() / total;
        long latCount = shadow.latencyDiffCount.sum();
        double avgDiff = latCount > 0 ? (double) shadow.latencyDiffSumMs.sum() / latCount : 0;

        log.info("VAD影子会话摘要 sessionId={} totalFrames={} bothVoice={} onlyLegacy={} onlySilero={} "
                        + "falseAlarmRate={} missRate={} avgLatencyDiffMs={}",
                sessionId, total, shadow.bothVoiceFrames.sum(),
                shadow.onlyLegacyVoiceFrames.sum(), shadow.onlySileroVoiceFrames.sum(),
                String.format(Locale.ROOT, "%.4f", falseAlarmRate),
                String.format(Locale.ROOT, "%.4f", missRate),
                String.format(Locale.ROOT, "%.1f", avgDiff));

        maybePersistSession(shadow, total, falseAlarmRate, missRate, avgDiff, latCount);
    }

    /** 按 voice.vad-shadow-session-sample-rate 抽样写会话明细；任何异常不外抛 */
    private void maybePersistSession(SessionShadow shadow, long total, double falseAlarmRate,
            double missRate, double avgLatencyDiffMs, long latencyCount)
    {
        double rate = voiceProperties.getVadShadowSessionSampleRate();
        if (jdbcTemplate == null || rate <= 0d)
        {
            return;
        }
        if (rate < 1d && ThreadLocalRandom.current().nextDouble() >= rate)
        {
            return;
        }
        try
        {
            jdbcTemplate.update(INSERT_SESSION_SQL,
                    shadow.sessionId, shadow.sampleRate, shadow.mockMode ? "1" : "0",
                    total, shadow.bothVoiceFrames.sum(), shadow.onlyLegacyVoiceFrames.sum(),
                    shadow.onlySileroVoiceFrames.sum(), falseAlarmRate, missRate,
                    avgLatencyDiffMs, shadow.legacyStartTicks, shadow.sileroStartTicks);
        }
        catch (Exception e)
        {
            log.warn("VAD影子会话明细落表失败 sessionId={}: {}", shadow.sessionId, e.getMessage());
        }
    }

    /**
     * 输出每日统计报告（结构化日志 + ai_vad_shadow_daily UPSERT）并重置日累计。
     * 影子开关关闭、当日无数据时跳过；同一天重复调用幂等。
     */
    public void dailyReport()
    {
        if (!voiceProperties.isVadShadowEnabled())
        {
            return;
        }
        String today = LocalDate.now().toString();
        if (today.equals(lastReportDate))
        {
            return;
        }
        lastReportDate = today;

        DailyStats stats = snapshotDaily();
        if (stats.totalFrames == 0 && stats.endedSessions == 0)
        {
            return;
        }

        REPORT_LOG.info("VAD_SHADOW_DAILY date={} totalFrames={} bothVoiceFrames={} "
                        + "onlyLegacyVoiceFrames={} onlySileroVoiceFrames={} bothSilentFrames={} "
                        + "legacySpeechStartCount={} sileroSpeechStartCount={} "
                        + "legacySpeechEndCount={} sileroSpeechEndCount={} "
                        + "falseAlarmRate={} missRate={} avgLatencyDiffMs={} activeSessions={}",
                today, stats.totalFrames, stats.bothVoiceFrames,
                stats.onlyLegacyFrames, stats.onlySileroFrames, stats.bothSilentFrames,
                stats.legacyStartCount, stats.sileroStartCount,
                stats.legacyEndCount, stats.sileroEndCount,
                String.format(Locale.ROOT, "%.6f", stats.falseAlarmRate),
                String.format(Locale.ROOT, "%.6f", stats.missRate),
                String.format(Locale.ROOT, "%.2f", stats.avgLatencyDiffMs),
                stats.endedSessions);

        persistDaily(today, stats);
        resetDaily();
    }

    /** 日统计 UPSERT 落表；失败仅告警（影子链路绝不影响语音主链路） */
    private void persistDaily(String date, DailyStats s)
    {
        if (jdbcTemplate == null)
        {
            return;
        }
        try
        {
            java.sql.Date statDate = java.sql.Date.valueOf(date);
            jdbcTemplate.update(UPSERT_DAILY_SQL,
                    statDate, s.totalFrames, s.bothVoiceFrames, s.onlyLegacyFrames, s.onlySileroFrames,
                    s.bothSilentFrames, s.legacyStartCount, s.sileroStartCount,
                    s.legacyEndCount, s.sileroEndCount,
                    s.falseAlarmRate, s.missRate, s.avgLatencyDiffMs, s.endedSessions, s.latencyDiffCount);
        }
        catch (Exception e)
        {
            log.warn("VAD影子日统计落表失败 date={}: {}", date, e.getMessage());
        }
    }

    private void resetDaily()
    {
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
        endedSessionCount.reset();
    }

    /**
     * 获取当前全局统计快照（值对象，供日报落表与监控使用）。
     *
     * @return 日累计统计快照
     */
    public DailyStats snapshotDaily()
    {
        long total = totalFrames.sum();
        long onlySilero = onlySileroVoiceFrames.sum();
        long onlyLegacy = onlyLegacyVoiceFrames.sum();
        long latCount = latencyDiffCount.sum();
        return new DailyStats(
                total,
                bothVoiceFrames.sum(),
                onlyLegacy,
                onlySilero,
                bothSilentFrames.sum(),
                legacySpeechStartCount.sum(),
                sileroSpeechStartCount.sum(),
                legacySpeechEndCount.sum(),
                sileroSpeechEndCount.sum(),
                total > 0 ? (double) onlySilero / total : 0d,
                total > 0 ? (double) onlyLegacy / total : 0d,
                latCount > 0 ? (double) latencyDiffSumMs.sum() / latCount : 0d,
                endedSessionCount.sum(),
                latCount,
                sessions.size());
    }

    /**
     * 获取当前全局统计快照（用于监控接口）。
     *
     * @return 统计 Map
     */
    public Map<String, Object> snapshot()
    {
        DailyStats s = snapshotDaily();
        Map<String, Object> m = new ConcurrentHashMap<>();
        m.put("totalFrames", s.totalFrames);
        m.put("bothVoiceFrames", s.bothVoiceFrames);
        m.put("onlyLegacyVoiceFrames", s.onlyLegacyFrames);
        m.put("onlySileroVoiceFrames", s.onlySileroFrames);
        m.put("bothSilentFrames", s.bothSilentFrames);
        m.put("legacySpeechStartCount", s.legacyStartCount);
        m.put("sileroSpeechStartCount", s.sileroStartCount);
        m.put("legacySpeechEndCount", s.legacyEndCount);
        m.put("sileroSpeechEndCount", s.sileroEndCount);
        m.put("falseAlarmRate", s.falseAlarmRate);
        m.put("missRate", s.missRate);
        m.put("avgLatencyDiffMs", s.avgLatencyDiffMs);
        m.put("latencyDiffCount", s.latencyDiffCount);
        m.put("endedSessions", s.endedSessions);
        m.put("activeSessions", s.activeSessions);
        return m;
    }

    /** 日累计统计快照（不可变值对象） */
    public static class DailyStats
    {
        public final long totalFrames;
        public final long bothVoiceFrames;
        public final long onlyLegacyFrames;
        public final long onlySileroFrames;
        public final long bothSilentFrames;
        public final long legacyStartCount;
        public final long sileroStartCount;
        public final long legacyEndCount;
        public final long sileroEndCount;
        public final double falseAlarmRate;
        public final double missRate;
        public final double avgLatencyDiffMs;
        public final long endedSessions;
        public final long latencyDiffCount;
        /** 当前仍在活动的会话数（瞬时值，不累加） */
        public final int activeSessions;

        public DailyStats(long totalFrames, long bothVoiceFrames, long onlyLegacyFrames,
                long onlySileroFrames, long bothSilentFrames, long legacyStartCount,
                long sileroStartCount, long legacyEndCount, long sileroEndCount,
                double falseAlarmRate, double missRate, double avgLatencyDiffMs,
                long endedSessions, long latencyDiffCount, int activeSessions)
        {
            this.totalFrames = totalFrames;
            this.bothVoiceFrames = bothVoiceFrames;
            this.onlyLegacyFrames = onlyLegacyFrames;
            this.onlySileroFrames = onlySileroFrames;
            this.bothSilentFrames = bothSilentFrames;
            this.legacyStartCount = legacyStartCount;
            this.sileroStartCount = sileroStartCount;
            this.legacyEndCount = legacyEndCount;
            this.sileroEndCount = sileroEndCount;
            this.falseAlarmRate = falseAlarmRate;
            this.missRate = missRate;
            this.avgLatencyDiffMs = avgLatencyDiffMs;
            this.endedSessions = endedSessions;
            this.latencyDiffCount = latencyDiffCount;
            this.activeSessions = activeSessions;
        }
    }

    /* ================= 内部类 ================= */

    /** 会话级影子状态 */
    static class SessionShadow
    {
        final String sessionId;
        final int sampleRate;
        volatile boolean mockMode;
        volatile boolean legacySpeaking;
        volatile boolean sileroSpeaking;
        volatile long legacySpeechStartNanos;
        volatile long sileroSpeechStartNanos;
        volatile long sileroSpeechStartMs;
        /** 会话内旧 VAD/Silero 起始触发次数（明细落表用） */
        volatile int legacyStartTicks;
        volatile int sileroStartTicks;

        final LongAdder totalFrames = new LongAdder();
        final LongAdder bothVoiceFrames = new LongAdder();
        final LongAdder onlyLegacyVoiceFrames = new LongAdder();
        final LongAdder onlySileroVoiceFrames = new LongAdder();
        final LongAdder bothSilentFrames = new LongAdder();
        final LongAdder latencyDiffSumMs = new LongAdder();
        final LongAdder latencyDiffCount = new LongAdder();

        SessionShadow(String sessionId, int sampleRate)
        {
            this.sessionId = sessionId;
            this.sampleRate = sampleRate;
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

        /** 本语音段是否已做过起始延迟对比（双方都触发起始时记一次，段结束后复位） */
        private boolean startCompared;

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

            // 帧级对比：VoiceActivityDetector 未暴露 per-frame 判定，
            // 用 speaking 状态作为"当前处于语音段"的代理指标
            boolean legacyState = legacy.isSpeaking();
            boolean sileroState = silero.isShadowSpeaking();
            service.onFrameCompared(sessionId, legacyState, sileroState);

            // 双方都判定语音起始时记一次检测延迟差；单边起始（漏/误报）不计延迟，
            // 由帧分歧计数与起始次数差体现
            if (!startCompared && legacyState && sileroState)
            {
                startCompared = true;
                service.onSpeechStartCompared(sessionId,
                        legacy.getLastDetectionMs(), silero.getLastDetectionMs());
            }
            if (startCompared && !legacyState && !sileroState)
            {
                startCompared = false;
            }

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
            startCompared = false;
        }

        /** 释放资源 */
        public void close()
        {
            silero.close();
            service.onSessionEnd(sessionId);
        }
    }
}
