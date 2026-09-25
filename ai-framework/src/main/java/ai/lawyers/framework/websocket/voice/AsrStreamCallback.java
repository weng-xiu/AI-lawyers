package ai.lawyers.framework.websocket.voice;

/**
 * 流式 ASR 回调（P3-A1）。
 *
 * <p>引擎在独立工作线程上回调；实现方（{@link VoiceSession}）只做帧入队，
 * 不直接操作 WebSocket，保证所有下发帧经单发送线程保序。</p>
 *
 * @author ai-lawyers
 */
public interface AsrStreamCallback
{
    /** 增量识别结果（可多次） */
    void onPartial(String text);

    /** 终态识别结果（每会话仅一次，通常在 {@link StreamAsrEngine#close()} 时产生） */
    void onFinal(String text);

    /** 引擎错误（不下发则会话无法继续时使用） */
    void onError(String code, String message);
}
