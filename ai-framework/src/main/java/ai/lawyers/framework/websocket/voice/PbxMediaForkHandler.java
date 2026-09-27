package ai.lawyers.framework.websocket.voice;

import java.util.Base64;
import javax.websocket.Session;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ai.lawyers.system.service.lawyers.metrics.HotlineMetrics;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * P3-B3：PBX 媒体 fork 协议处理器（每连接一个实例）。
 *
 * <p>把 PBX 上行的 fork 帧翻译为 {@link VoiceSession}（role=caller）操作，
 * 兼容两种主流模块协议：</p>
 * <ul>
 *   <li><b>mod_audio_fork</b>（drachtio 风格）：
 *     {@code {"type":"start","data":{"sampleRate":16000,"channels":1}}}，
 *     随后<b>二进制裸 PCM</b> 帧，结束 {@code {"type":"stop"}}；</li>
 *   <li><b>mod_audio_stream</b>：
 *     {@code {"type":"start","sampleRate":16000}}，
 *     {@code {"type":"audio","data":"<base64 PCM>"}}，结束 {@code {"type":"end"}}。</li>
 * </ul>
 *
 * <p>start 受理后经 {@link VoiceSessionManager} 以 caller 角色注册会话，
 * 并向其注入合成 start 帧；之后 ASR/E4 情绪/Copilot 全部复用既有链路。</p>
 *
 * @author ai-lawyers
 */
public class PbxMediaForkHandler
{
    private static final Logger log = LoggerFactory.getLogger(PbxMediaForkHandler.class);

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final String recordId;

    private final Session wsSession;

    private final VoiceSessionManager manager;

    private final PbxMediaForkProperties properties;

    /** M3-3：指标门面（可空，未装配时静默跳过） */
    private final HotlineMetrics metrics;

    /** start 受理后桥接的 caller 语音会话 */
    private VoiceSession voiceSession;

    /** start 是否已受理（拒绝重复 start、start 前音频丢弃） */
    private boolean started;

    /** 连接受理时间（会话生命周期 Timer 起点） */
    private final long openNanos = System.nanoTime();

    /** 最后一个音频帧到达时间（静默年龄 gauge 用，0=尚未收到任何音频） */
    private long lastAudioNanos;

    /** PBX 是否已发 stop/end（区分关闭原因 stop/abnormal） */
    private boolean stopReceived;

    public PbxMediaForkHandler(String recordId, Session wsSession,
                               VoiceSessionManager manager, PbxMediaForkProperties properties)
    {
        this(recordId, wsSession, manager, properties, null);
    }

    public PbxMediaForkHandler(String recordId, Session wsSession,
                               VoiceSessionManager manager, PbxMediaForkProperties properties,
                               HotlineMetrics metrics)
    {
        this.recordId = recordId;
        this.wsSession = wsSession;
        this.manager = manager;
        this.properties = properties;
        this.metrics = metrics;
    }

    /**
     * 处理 PBX 文本帧。
     *
     * @return true=已正常处理；false=协议非法（端点应以 1008 关闭）
     */
    public boolean handleText(String raw)
    {
        JsonNode node;
        try
        {
            node = MAPPER.readTree(raw);
        }
        catch (Exception e)
        {
            log.warn("[PbxMedia] 非法 JSON 帧 recordId={}: {}", recordId, e.getMessage());
            if (metrics != null)
            {
                metrics.incrementPbxBadFrame("bad_json");
            }
            return false;
        }
        String type = node.path("type").asText("");
        try
        {
            switch (type)
            {
                case "start":
                    handleStart(node);
                    break;
                case "stop":
                case "end":
                    handleStop();
                    break;
                case "audio":
                    handleAudioJson(node);
                    break;
                default:
                    // 心跳/未知帧：记录不关闭，避免 PBX 扩展字段导致断流
                    log.debug("[PbxMedia] 忽略未知帧 type={} recordId={}", type, recordId);
            }
        }
        catch (Exception e)
        {
            log.warn("[PbxMedia] 帧处理异常 recordId={} type={}: {}", recordId, type, e.getMessage());
        }
        return true;
    }

    /** 二进制裸 PCM（mod_audio_fork 主通道）直喂 caller 会话 */
    public void handleBinary(byte[] pcm)
    {
        if (!started || voiceSession == null)
        {
            log.debug("[PbxMedia] start 前二进制帧丢弃 recordId={}", recordId);
            return;
        }
        voiceSession.handleBinary(pcm);
        markAudio(pcm.length, "binary");
    }

    /** PBX/连接关闭时的兜底：若尚未 stop，先停识别（unregister 由端点统一执行） */
    public void finish()
    {
        if (started && voiceSession != null)
        {
            try
            {
                voiceSession.handleText("{\"type\":\"stop\"}");
            }
            catch (Exception e)
            {
                log.debug("[PbxMedia] finish stop 异常 recordId={}: {}", recordId, e.getMessage());
            }
        }
    }

    /* ================= 内部 ================= */

    private void handleStart(JsonNode node)
    {
        if (started)
        {
            log.warn("[PbxMedia] 重复 start，忽略 recordId={}", recordId);
            return;
        }
        int rate = resolveSampleRate(node);

        // 以 caller 角色注册：会话ID=recordId（与话单一致；presence 含 role 不与坐席侧冲突）
        VoiceSession session = manager.register(wsSession, recordId, "caller");
        if (session == null)
        {
            log.warn("[PbxMedia] caller 会话注册失败（开关/容量）recordId={}", recordId);
            return;
        }

        // 注入合成 start 帧（engine 可配，默认 mock 与 VoiceCaption 一致；实机配 dashscope）
        ObjectNode synthStart = MAPPER.createObjectNode();
        synthStart.put("type", "start");
        synthStart.put("engine", resolveEngine());
        synthStart.put("format", "pcm");
        synthStart.put("sampleRate", rate);
        session.handleText(synthStart.toString());

        this.voiceSession = session;
        this.started = true;
        PbxMediaWebSocketServer.onForkStarted(this);
        log.info("[PbxMedia] caller 媒体链路已接通 recordId={} rate={} connId={}",
                recordId, rate, wsSession.getId());
    }

    private void handleStop()
    {
        if (!started || voiceSession == null)
        {
            return;
        }
        voiceSession.handleText("{\"type\":\"stop\"}");
        started = false;
        stopReceived = true;
        log.info("[PbxMedia] PBX 通知媒体结束 recordId={}", recordId);
    }

    /** mod_audio_stream 的 base64 音频帧：解码后走二进制通道 */
    private void handleAudioJson(JsonNode node)
    {
        if (!started || voiceSession == null)
        {
            log.debug("[PbxMedia] start 前音频帧丢弃 recordId={}", recordId);
            return;
        }
        String base64 = firstText(node, "data", "audio", "payload");
        if (base64 == null || base64.isEmpty())
        {
            log.debug("[PbxMedia] audio 帧无内容，忽略 recordId={}", recordId);
            return;
        }
        try
        {
            byte[] pcm = Base64.getDecoder().decode(base64);
            voiceSession.handleBinary(pcm);
            markAudio(pcm.length, "json");
        }
        catch (IllegalArgumentException e)
        {
            log.warn("[PbxMedia] base64 音频解码失败 recordId={}: {}", recordId, e.getMessage());
            if (metrics != null)
            {
                metrics.incrementPbxBadFrame("bad_base64");
            }
        }
    }

    /** 采样率：data.sampleRate（mod_audio_fork）→ sampleRate（mod_audio_stream）→ 默认值 */
    private int resolveSampleRate(JsonNode node)
    {
        int rate = node.path("data").path("sampleRate").asInt(0);
        if (rate <= 0)
        {
            rate = node.path("sampleRate").asInt(0);
        }
        if (rate <= 0)
        {
            rate = properties.getDefaultRate();
        }
        return rate;
    }

    private String resolveEngine()
    {
        // 复用 yml 配置的默认引擎；通过系统属性/环境变量覆盖与 VoiceCaption 一致
        return System.getProperty("PBX_MEDIA_ENGINE",
                System.getenv().getOrDefault("PBX_MEDIA_ENGINE", "mock"));
    }

    private void markAudio(int bytes, String kind)
    {
        lastAudioNanos = System.nanoTime();
        if (metrics != null)
        {
            metrics.incrementPbxBytes(bytes);
            metrics.incrementPbxAudioFrame(kind);
        }
    }

    /* ---- M3-3：端点/静态 gauge 读取的包级访问器 ---- */

    HotlineMetrics metrics()
    {
        return metrics;
    }

    long openNanos()
    {
        return openNanos;
    }

    boolean isStopReceived()
    {
        return stopReceived;
    }

    /**
     * 静默年龄（秒）：距最后音频帧；start 后从未收到音频则从受理时刻起算；
     * 已 stop 的 fork 返回 0（不计入异常静默）。
     */
    double silenceAgeSeconds(long nowNanos)
    {
        if (stopReceived)
        {
            return 0;
        }
        long base = lastAudioNanos > 0 ? lastAudioNanos : openNanos;
        long ns = nowNanos - base;
        return ns > 0 ? ns / 1_000_000_000.0 : 0;
    }

    private static String firstText(JsonNode node, String... keys)
    {
        for (String key : keys)
        {
            JsonNode v = node.get(key);
            if (v != null && v.isTextual() && !v.asText().isEmpty())
            {
                return v.asText();
            }
        }
        return null;
    }
}
