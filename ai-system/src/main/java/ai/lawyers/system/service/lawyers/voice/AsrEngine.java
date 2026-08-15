package ai.lawyers.system.service.lawyers.voice;

import java.util.Map;

/**
 * 语音识别（ASR）引擎抽象。
 */
public interface AsrEngine
{
    /**
     * 将音频转换为文本。
     *
     * @param audio      音频字节（wav/pcm/mp3 等，由 format 指定）
     * @param format     音频格式，如 wav / pcm / mp3
     * @param sampleRate 采样率，如 8000 / 16000
     * @param options    引擎扩展选项（模型、语言、音色等）
     * @return 识别文本；识别失败返回空字符串
     */
    String transcribe(byte[] audio, String format, int sampleRate, Map<String, Object> options);
}
