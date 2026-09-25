package ai.lawyers.framework.websocket.voice;

/**
 * 流式 ASR 引擎抽象（P3-A1，扩展而非替换现有同步 AsrEngine）。
 *
 * <p>生命周期：{@link #open} → 多次 {@link #feed} → {@link #close}；
 * 实现必须保证 close 幂等且释放内部线程/句柄（{@code @OnClose} 防泄漏）。
 * A2/A3 将提供 DashScope/Whisper 流式实现，当前仅有 {@link MockStreamAsrEngine}。</p>
 *
 * @author ai-lawyers
 */
public interface StreamAsrEngine
{
    /** 引擎编码（mock/dashscope/...） */
    String engineCode();

    /** 打开识别流；重复打开由会话层拦截，引擎内部不做状态兼容 */
    void open(VoiceAsrContext context, AsrStreamCallback callback);

    /** 喂入一帧 PCM 音频（建议 20ms/帧）；close 后调用静默忽略 */
    void feed(byte[] frame);

    /** 结束识别流：产出 final、释放工作线程，幂等 */
    void close();
}
