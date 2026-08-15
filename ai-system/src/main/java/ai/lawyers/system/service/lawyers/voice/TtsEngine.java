package ai.lawyers.system.service.lawyers.voice;

import java.util.Map;

/**
 * 语音合成（TTS）引擎抽象。
 */
public interface TtsEngine
{
    /**
     * 将文本合成为音频。
     *
     * @param text       待合成文本
     * @param format     目标音频格式，如 wav / mp3
     * @param sampleRate 采样率，如 8000 / 16000
     * @param options    引擎扩展选项（模型、音色、语速等）
     * @return 音频字节；合成失败返回空数组
     */
    byte[] synthesize(String text, String format, int sampleRate, Map<String, Object> options);
}
