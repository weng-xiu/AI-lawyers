package ai.lawyers.framework.websocket.voice;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

/**
 * 流式 ASR 会话上下文测试：不可变字段读取。
 *
 * @author ai-lawyers
 */
class VoiceAsrContextTest
{
    @Test
    void 构造后_读取全部字段()
    {
        VoiceAsrContext context = new VoiceAsrContext("session-1", "caller", "pcm_s16le", 8000);

        assertEquals("session-1", context.getSessionId());
        assertEquals("caller", context.getRole());
        assertEquals("pcm_s16le", context.getFormat());
        assertEquals(8000, context.getSampleRate());
    }

    @Test
    void 坐席侧_支持十六千采样率()
    {
        VoiceAsrContext context = new VoiceAsrContext("session-2", "agent", "pcm_s16le", 16000);

        assertEquals("agent", context.getRole());
        assertEquals(16000, context.getSampleRate());
    }

    @Test
    void 允许空会话标识()
    {
        VoiceAsrContext context = new VoiceAsrContext(null, null, null, 0);

        assertNull(context.getSessionId());
        assertNull(context.getRole());
        assertNull(context.getFormat());
        assertEquals(0, context.getSampleRate());
    }
}
