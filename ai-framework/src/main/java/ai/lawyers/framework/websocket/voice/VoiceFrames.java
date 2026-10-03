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

    /** E4：实时情绪/意图命中（客户端展示预警条；warningId 关联风险预警） */
    static final String T_EMOTION = "emotion";

    /** F4：实时案情要素（纠纷类型/诉求/紧急度/关键事实） */
    static final String T_COPILOT_ELEMENT = "copilot_element";

    /** F4：Copilot 推荐法条（含 chunkId，点击溯源） */
    static final String T_COPILOT_LAWS = "copilot_laws";

    /** F4：Copilot 相似工单 */
    static final String T_COPILOT_TICKETS = "copilot_tickets";

    /** P1-8：Copilot 建议动作（createTicket/queryTicket 白名单草稿） */
    static final String T_COPILOT_ACTIONS = "copilot_actions";

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

    /** E4：情绪/意图命中帧（level ∈ urgent/negative；keywords 为命中情绪词） */
    static String emotion(String level, String intent, java.util.List<String> keywords, Long warningId)
    {
        Map<String, Object> m = base(T_EMOTION);
        m.put("level", level);
        if (intent != null)
        {
            m.put("intent", intent);
        }
        m.put("keywords", keywords == null ? java.util.Collections.emptyList() : keywords);
        if (warningId != null)
        {
            m.put("warningId", warningId);
        }
        return write(m);
    }

    /* ================= F4：Copilot 帧 ================= */

    /** F4：案情要素帧（seq 为 Copilot 回合号，客户端按新回合整体刷新） */
    static String copilotElement(long seq, String disputeType, java.util.List<String> claims,
                                 String urgency, java.util.List<String> keyFacts, boolean degraded)
    {
        Map<String, Object> m = base(T_COPILOT_ELEMENT);
        m.put("seq", seq);
        m.put("disputeType", disputeType == null ? "" : disputeType);
        m.put("claims", claims == null ? java.util.Collections.emptyList() : claims);
        m.put("urgency", urgency == null ? "normal" : urgency);
        m.put("keyFacts", keyFacts == null ? java.util.Collections.emptyList() : keyFacts);
        m.put("degraded", degraded);
        return write(m);
    }

    /** F4：推荐法条帧（laws 元素含 chunkId/title/lawArticle/source） */
    static String copilotLaws(long seq, java.util.List<Map<String, Object>> laws)
    {
        Map<String, Object> m = base(T_COPILOT_LAWS);
        m.put("seq", seq);
        m.put("laws", laws == null ? java.util.Collections.emptyList() : laws);
        return write(m);
    }

    /** F4：相似工单帧（tickets 元素含 ticketId/ticketNo/title/status/contentSnippet） */
    static String copilotTickets(long seq, java.util.List<Map<String, Object>> tickets)
    {
        Map<String, Object> m = base(T_COPILOT_TICKETS);
        m.put("seq", seq);
        m.put("tickets", tickets == null ? java.util.Collections.emptyList() : tickets);
        return write(m);
    }

    /** P1-8：建议动作帧（actions 元素含 action/label/payload） */
    static String copilotActions(long seq, java.util.List<Map<String, Object>> actions)
    {
        Map<String, Object> m = base(T_COPILOT_ACTIONS);
        m.put("seq", seq);
        m.put("actions", actions == null ? java.util.Collections.emptyList() : actions);
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
