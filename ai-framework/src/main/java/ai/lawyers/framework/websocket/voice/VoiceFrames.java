package ai.lawyers.framework.websocket.voice;

import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * /ws/voice JSON 帧构造（P3-A1）。
 *
 * <p>所有下发帧统一经此助手序列化（{@link LinkedHashMap} 保证字段顺序稳定，
 * 便于抓包/前端调试）；序列化失败不应发生在静态结构上，兜底为最小 error 帧。</p>
 *
 * <pre>
 * 服务端→客户端：
 *  connected / started / asr_partial / asr_final / tts_audio / tts_end / error
 * </pre>
 *
 * @author ai-lawyers
 */
final class VoiceFrames
{
    static final String T_CONNECTED = "connected";

    static final String T_STARTED = "started";

    static final String T_ASR_PARTIAL = "asr_partial";

    static final String T_ASR_FINAL = "asr_final";

    static final String T_TTS_AUDIO = "tts_audio";

    static final String T_TTS_END = "tts_end";

    static final String T_ERROR = "error";

    /** A4：VAD 语音起始（播报期间客户端应立即清空播放队列） */
    static final String T_VAD_SPEECH_START = "vad_speech_start";

    /** A4：VAD 语音结束 */
    static final String T_VAD_SPEECH_END = "vad_speech_end";

    /** E3：机器人应答文本增量（当前 LLM 非流式，单帧全量；seq 预留给流式） */
    static final String T_ANSWER_DELTA = "answer_delta";

    /** E3：机器人应答完成（含 RAG 命中数/溯源/降级标记） */
    static final String T_ANSWER_DONE = "answer_done";

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private VoiceFrames()
    {
    }

    static String connected(String sessionId, String role)
    {
        Map<String, Object> m = base(T_CONNECTED);
        m.put("sessionId", sessionId);
        m.put("role", role);
        return write(m);
    }

    static String started(String sessionId, String role, String engine, String format, int sampleRate)
    {
        Map<String, Object> m = base(T_STARTED);
        m.put("sessionId", sessionId);
        m.put("role", role);
        m.put("engine", engine);
        m.put("format", format);
        m.put("sampleRate", sampleRate);
        return write(m);
    }

    static String asr(String type, long seq, String text)
    {
        Map<String, Object> m = base(type);
        m.put("seq", seq);
        m.put("text", text);
        return write(m);
    }

    static String ttsAudio(long seq, int chunkCount, int sampleRate, byte[] pcm)
    {
        Map<String, Object> m = base(T_TTS_AUDIO);
        m.put("seq", seq);
        m.put("chunkCount", chunkCount);
        m.put("isEnd", seq == chunkCount - 1L);
        m.put("format", "pcm");
        m.put("sampleRate", sampleRate);
        m.put("data", Base64.getEncoder().encodeToString(pcm));
        return write(m);
    }

    /**
     * @param interrupted true=因 barge-in/stop/close 中断（无后续分片）；false=正常播完
     */
    static String ttsEnd(boolean interrupted, String reason)
    {
        Map<String, Object> m = base(T_TTS_END);
        m.put("interrupted", interrupted);
        if (reason != null)
        {
            m.put("reason", reason);
        }
        return write(m);
    }

    static String error(String code, String message)
    {
        Map<String, Object> m = base(T_ERROR);
        m.put("code", code);
        m.put("message", message);
        return write(m);
    }

    /** A4：VAD 事件帧（type 取 {@link #T_VAD_SPEECH_START}/{@link #T_VAD_SPEECH_END}） */
    static String vad(String type)
    {
        return write(base(type));
    }

    /** E3：机器人应答文本帧（turnId 供客户端丢弃已被新回合取代的迟到应答） */
    static String answerDelta(long turnId, long seq, String text)
    {
        Map<String, Object> m = base(T_ANSWER_DELTA);
        m.put("turnId", turnId);
        m.put("seq", seq);
        m.put("text", text);
        return write(m);
    }

    /** E3：机器人应答完成帧（degraded=true 表示兜底话术，sources 为法条溯源清单） */
    static String answerDone(long turnId, int ragHits, java.util.List<String> sources, boolean degraded)
    {
        Map<String, Object> m = base(T_ANSWER_DONE);
        m.put("turnId", turnId);
        m.put("ragHits", ragHits);
        m.put("sources", sources == null ? java.util.Collections.emptyList() : sources);
        m.put("degraded", degraded);
        return write(m);
    }

    private static Map<String, Object> base(String type)
    {
        Map<String, Object> m = new LinkedHashMap<>(4);
        m.put("type", type);
        return m;
    }

    private static String write(Map<String, Object> frame)
    {
        try
        {
            return MAPPER.writeValueAsString(frame);
        }
        catch (Exception e)
        {
            return "{\"type\":\"error\",\"code\":\"FRAME_ENCODE_FAIL\",\"message\":\"frame encode failed\"}";
        }
    }
}
