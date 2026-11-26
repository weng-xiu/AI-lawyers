package ai.lawyers.framework.websocket.voice;

import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import jakarta.websocket.Session;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * N1（语雀 7.3）：PBX 下行音频 Sink 真实实现——复用 fork 的 WebSocket 会话，
 * 以 mod_audio_stream 对称帧回写音频，供 FreeSWITCH 边收边播（caller 可听见机器人播报）。
 *
 * <p>协议前提：mod_audio_stream 为<b>双向</b> WebSocket，FreeSWITCH 侧同一连接上
 * 上行（PBX→后端，见 {@link PbxMediaForkHandler#handleAudioJson}）与下行
 * （后端→PBX，本类）对称。下行帧复用上行 {@code {"type":"audio","data":"<base64>"}}
 * 结构；采样率已在 fork start 帧协商（{@code session.sampleRate}），故下行帧不重复声明。</p>
 *
 * <p>并发：下行写入发生在 TTS 合成回调线程，经 {@link DownlinkDebouncer} 的
 * {@code synchronized} 方法串行化，故本类无锁；同时本连接此前无其它主动下行，
 * 本 sink 是唯一的 {@code getBasicRemote()} 发送者，满足 JSR-356「同一 RemoteEndpoint
 * 禁止多线程并发发送」约束。</p>
 *
 * <p>协议格式为基于上行对称的<b>推断</b>（语雀 7.3.1 选型矩阵未给出下行帧权威证据），
 * 真实 FreeSWITCH 联调若字段不同，只需改 {@link #encodeAudioFrame} 一处。</p>
 *
 * @author ai-lawyers
 */
public class PbxWsDownlinkSink implements PbxDownlinkAudioSink
{
    private static final Logger log = LoggerFactory.getLogger(PbxWsDownlinkSink.class);

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /** 下行帧 type（与上行 mod_audio_stream 一致） */
    static final String FRAME_TYPE = "audio";

    private final Session wsSession;

    private final String recordId;

    /** 是否已关闭（幂等；关闭后拒绝写入，避免向已释放连接发送） */
    private final AtomicBoolean closed = new AtomicBoolean();

    public PbxWsDownlinkSink(Session wsSession, String recordId)
    {
        this.wsSession = wsSession;
        this.recordId = recordId;
    }

    @Override
    public boolean write(byte[] pcm, int sampleRate)
    {
        if (closed.get() || pcm == null || pcm.length == 0)
        {
            return false;
        }
        Session session = wsSession;
        if (session == null || !session.isOpen())
        {
            log.debug("[PbxDownlink] 会话不可写，丢弃下行合片 recordId={} open={}", recordId,
                    session == null ? "null" : session.isOpen());
            return false;
        }
        try
        {
            session.getBasicRemote().sendText(encodeAudioFrame(pcm));
            return true;
        }
        catch (Exception e)
        {
            log.debug("[PbxDownlink] 下行发送异常（分片丢弃） recordId={} bytes={} rate={}: {}",
                    recordId, pcm.length, sampleRate, e.getMessage());
            return false;
        }
    }

    @Override
    public void close()
    {
        if (closed.compareAndSet(false, true))
        {
            log.debug("[PbxDownlink] sink 已关闭 recordId={}", recordId);
        }
    }

    /** mod_audio_stream 对称下行帧：{@code {"type":"audio","data":"<base64>"}} */
    private String encodeAudioFrame(byte[] pcm) throws Exception
    {
        Map<String, Object> frame = new LinkedHashMap<>(2);
        frame.put("type", FRAME_TYPE);
        frame.put("data", Base64.getEncoder().encodeToString(pcm));
        return MAPPER.writeValueAsString(frame);
    }
}
