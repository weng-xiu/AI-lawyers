package ai.lawyers.system.service.lawyers.voice;

import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * 本地模拟语音引擎：不产生真实音频/转写，仅记录调用日志，用于无密钥环境联调。
 */
@Service("mockVoiceEngine")
public class MockVoiceEngine implements AsrEngine, TtsEngine
{
    private static final Logger log = LoggerFactory.getLogger(MockVoiceEngine.class);

    @Override
    public String transcribe(byte[] audio, String format, int sampleRate, Map<String, Object> options)
    {
        int length = audio == null ? 0 : audio.length;
        log.info("[MOCK ASR] 收到音频 {} 字节，format={}, sampleRate={}，返回空转写", length, format, sampleRate);
        return "";
    }

    @Override
    public byte[] synthesize(String text, String format, int sampleRate, Map<String, Object> options)
    {
        log.info("[MOCK TTS] 合成文本={}, format={}, sampleRate={}，返回空音频", text, format, sampleRate);
        return new byte[0];
    }
}
