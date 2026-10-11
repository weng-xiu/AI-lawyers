package ai.lawyers.framework.websocket.voice;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * P3-B3：PBX 媒体接入（mod_audio_fork）配置。
 *
 * <p>PBX 为服务端受信组件，不发 JWT，采用共享密钥（query {@code key}）+
 * 网络层 IP 白名单（部署侧）双重防护。</p>
 *
 * @author ai-lawyers
 */
@Component
public class PbxMediaForkProperties
{
    /** 媒体接入总开关（关闭时拒连） */
    @Value("${call.pbx.media.enabled:true}")
    private boolean enabled;

    /** 共享密钥（PBX dialplan URL query key=；为空时不校验，仅限完全内网隔离场景） */
    @Value("${call.pbx.media.auth-key:}")
    private String authKey;

    /** fork start 未携带 sampleRate 时的默认采样率（mod_audio_fork 通常 16000） */
    @Value("${call.pbx.media.default-rate:16000}")
    private int defaultRate;

    /** #1 下行媒体预留开关（开启后 TTS 分片经防抖缓冲双路分发到 PBX 下行 sink；当前为空实现，真实下行播放模块待媒体环境） */
    @Value("${call.pbx.media.downlink-enabled:false}")
    private boolean downlinkEnabled;

    /** #1 下行防抖缓冲目标时长（毫秒，钳制 100~200，默认 150） */
    @Value("${call.pbx.media.downlink-buffer-ms:150}")
    private int downlinkBufferMs;

    public boolean isEnabled()
    {
        return enabled;
    }

    public String getAuthKey()
    {
        return authKey;
    }

    public int getDefaultRate()
    {
        return defaultRate;
    }

    public boolean isDownlinkEnabled()
    {
        return downlinkEnabled;
    }

    public int getDownlinkBufferMs()
    {
        return downlinkBufferMs;
    }
}
