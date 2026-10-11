package ai.lawyers.framework.websocket.voice;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * #1：PBX 下行 Sink 空实现（PBX 侧先空实现）。
 *
 * <p>下行防抖合片照常运转（时序与真实实现一致），分片在此丢弃并低频记日志；
 * FreeSWITCH 下行播放模块（mod_socket/mod_external_media 或自定义模块）
 * 就绪后替换本实现即可，VoiceSession/分发链路零改动。</p>
 *
 * @author ai-lawyers
 */
public class NoopPbxDownlinkAudioSink implements PbxDownlinkAudioSink
{
    private static final Logger log = LoggerFactory.getLogger(NoopPbxDownlinkAudioSink.class);

    /** 低频日志：每 N 次写入记一条 debug */
    private static final int LOG_EVERY = 200;

    private long writes;

    @Override
    public boolean write(byte[] pcm, int sampleRate)
    {
        writes++;
        if (writes % LOG_EVERY == 1)
        {
            log.debug("[PbxDownlink] 空实现丢弃下行合片 writes={} bytes={} rate={}（真实下行播放模块待媒体环境）",
                    writes, pcm == null ? 0 : pcm.length, sampleRate);
        }
        return true;
    }
}
