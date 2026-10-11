package ai.lawyers.framework.websocket.voice;

/**
 * #1（调研报告 6.3-A）：PBX 下行音频 Sink——机器人播报回送 PBX 的预留出口。
 *
 * <p>现状：{@code PbxMediaForkHandler} 仅为单向媒体旁路（PBX→后端收流），
 * 机器人 TTS 只经浏览器 WS 播放，caller 听不到。本接口把"下行播放"抽象为
 * 可插拔出口：VoiceSession TTS 分片在发浏览器的同时经防抖缓冲
 * {@link DownlinkDebouncer} 双路分发到此（媒体桥接就绪后由真实实现承接，
 * 如 mod_socket/mod_external_media 边收边播）。</p>
 *
 * <p>PCM 约定：16bit 小端、单声道、{@code sampleRate} 为会话采样率。
 * 实现必须非阻塞快速返回（由 {@link DownlinkDebouncer} 承担 100~200ms
 * 防抖合片，避免 TTS 合成线程被下行 IO 阻塞）。</p>
 *
 * @author ai-lawyers
 */
public interface PbxDownlinkAudioSink
{
    /**
     * 写入一段下行 PCM 分片（已按防抖窗口合片）。
     *
     * @param pcm        16bit LE 单声道 PCM
     * @param sampleRate 采样率
     * @return true=受理；false=拒绝（调用方丢弃即可，不重试）
     */
    boolean write(byte[] pcm, int sampleRate);

    /** 会话释放时回调（幂等） */
    default void close()
    {
    }
}
