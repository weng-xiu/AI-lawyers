package ai.lawyers.framework.websocket.voice;

/**
 * 流式 ASR 会话上下文（P3-A1）。
 *
 * <p>不可变值对象，描述本次流式识别会话的音频格式与归属；
 * 8kHz/16kHz 均支持（电话侧 8k、坐席/客户端侧 16k）。</p>
 *
 * @author ai-lawyers
 */
public class VoiceAsrContext
{
    /** 业务会话 ID（话单/会话标识） */
    private final String sessionId;

    /** 角色：agent（坐席）/ caller（来电者） */
    private final String role;

    /** 音频格式，A1 仅支持 PCM S16LE 单声道 */
    private final String format;

    /** 采样率：8000 / 16000 */
    private final int sampleRate;

    public VoiceAsrContext(String sessionId, String role, String format, int sampleRate)
    {
        this.sessionId = sessionId;
        this.role = role;
        this.format = format;
        this.sampleRate = sampleRate;
    }

    public String getSessionId()
    {
        return sessionId;
    }

    public String getRole()
    {
        return role;
    }

    public String getFormat()
    {
        return format;
    }

    public int getSampleRate()
    {
        return sampleRate;
    }
}
