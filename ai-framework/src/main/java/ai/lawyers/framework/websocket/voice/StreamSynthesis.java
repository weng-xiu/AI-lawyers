package ai.lawyers.framework.websocket.voice;

/**
 * 流式 TTS 合成句柄（P3-A1），支持 barge-in 打断。
 *
 * @author ai-lawyers
 */
public interface StreamSynthesis
{
    /** 请求取消：停止产生后续音频分片；实现需尽快（≤300ms 目标）生效，幂等 */
    void cancel();

    /** 是否已请求取消（回调侧据此丢弃已合成但不应再下发的分片） */
    boolean isCancelled();
}
