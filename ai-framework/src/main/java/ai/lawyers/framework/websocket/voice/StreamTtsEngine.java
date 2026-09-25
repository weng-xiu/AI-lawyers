package ai.lawyers.framework.websocket.voice;

/**
 * 流式 TTS 引擎抽象（P3-A1，扩展而非替换现有同步 TtsEngine）。
 *
 * <p>合成在引擎内部异步执行，立即返回 {@link StreamSynthesis} 句柄；
 * 音频分片经回调按序返回。A2/A3 将提供真实 Provider。</p>
 *
 * @author ai-lawyers
 */
public interface StreamTtsEngine
{
    /** 引擎编码（mock/dashscope/...） */
    String engineCode();

    /**
     * 异步流式合成。
     *
     * @param text 待合成文本（非空，长度上限由会话层控制）
     * @param voice 音色标识（可空，引擎取默认）
     * @param sampleRate 输出采样率（8000/16000）
     * @param callback 分片回调
     * @return 可取消句柄（barge-in）
     */
    StreamSynthesis synthesizeStream(String text, String voice, int sampleRate, TtsStreamCallback callback);
}
