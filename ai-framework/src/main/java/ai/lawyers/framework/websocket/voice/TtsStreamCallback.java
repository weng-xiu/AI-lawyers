package ai.lawyers.framework.websocket.voice;

/**
 * 流式 TTS 回调（P3-A1）。
 *
 * <p>分片顺序由 seq（0 起）保证；chunkCount 为总分片数，
 * 最后一片满足 {@code seq == chunkCount - 1}，其后 {@link #onEnd()} 恰好一次。</p>
 *
 * @author ai-lawyers
 */
public interface TtsStreamCallback
{
    /** 一片 S16LE 单声道 PCM（20ms） */
    void onAudio(int seq, int chunkCount, byte[] pcm);

    /** 正常合成结束（tts_end 哨兵帧） */
    void onEnd();

    /** 合成错误 */
    void onError(String code, String message);
}
