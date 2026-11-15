package ai.lawyers.system.service.lawyers.voice;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

/**
 * 本地模拟语音引擎语义契约：ASR 永不返回 null（返回空串）、TTS 永远返回空字节数组，
 * 且对空入参与空配置容忍，保证无密钥联调链路不因 Mock 抛错中断。
 *
 * @author ai-lawyers
 */
class MockVoiceEngineTest
{
    private final MockVoiceEngine engine = new MockVoiceEngine();

    @Test
    void transcribe_withAudio_returnsEmptyStringNotNull()
    {
        String text = engine.transcribe(new byte[] { 1, 2, 3 }, "wav", 16000, new HashMap<>());

        assertThat(text).isNotNull().isEmpty();
    }

    @Test
    void transcribe_nullAudioAndOptions_tolerated()
    {
        assertThat(engine.transcribe(null, null, 0, null)).isEmpty();
    }

    @Test
    void synthesize_returnsEmptyArrayNotNull()
    {
        byte[] audio = engine.synthesize("您好", "wav", 16000, new HashMap<>());

        assertThat(audio).isNotNull().isEmpty();
    }

    @Test
    void synthesize_nullTextAndOptions_tolerated()
    {
        Map<String, Object> options = null;

        assertThat(engine.synthesize(null, "mp3", 8000, options)).isEmpty();
    }
}
