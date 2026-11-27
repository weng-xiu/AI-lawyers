package ai.lawyers.framework.websocket.voice;

import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import jakarta.websocket.Session;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * N1（语雀 7.3）：PBX 下行音频 Sink——mod_audio_stream 文本帧实现。
 *
 * <p>复用 fork 的 WebSocket 会话，以 mod_audio_stream 对称帧回写音频，供 FreeSWITCH
 * 边收边播（caller 可听见机器人播报）。</p>
 *
 * <p>协议前提：mod_audio_stream 为<b>双向</b> WebSocket，FreeSWITCH 侧同一连接上
 * 上行（PBX→后端，见 {@link PbxMediaForkHandler#handleAudioJson}）与下行
 * （后端→PBX，本类）对称。下行帧复用上行 {@code {"type":"audio","data":"<base64>"}}
 * 结构；采样率已在 fork start 帧协商（{@code session.sampleRate}），故下行帧不重复声明。</p>
 *
 * <p>关闭/会话守卫与异常归一在 {@link AbstractPbxWsDownlinkSink}，本类只负责文本帧编码。
 * 协议格式为基于上行对称的<b>推断</b>（语雀 7.3.1 选型矩阵未给出下行帧权威证据），
 * 真实 FreeSWITCH 联调若字段不同，只需改 {@link #sendFrame} 一处。</p>
 *
 * @author ai-lawyers
 */
public class PbxWsDownlinkSink extends AbstractPbxWsDownlinkSink
{
    private static final ObjectMapper MAPPER = new ObjectMapper();

    /** 下行帧 type（与上行 mod_audio_stream 一致） */
    static final String FRAME_TYPE = "audio";

    public PbxWsDownlinkSink(Session wsSession, String recordId)
    {
        super(wsSession, recordId);
    }

    @Override
    protected void sendFrame(Session session, byte[] pcm, int sampleRate) throws Exception
    {
        // mod_audio_stream 对称下行帧：{"type":"audio","data":"<base64>"}
        Map<String, Object> frame = new LinkedHashMap<>(2);
        frame.put("type", FRAME_TYPE);
        frame.put("data", Base64.getEncoder().encodeToString(pcm));
        session.getBasicRemote().sendText(MAPPER.writeValueAsString(frame));
    }
}
