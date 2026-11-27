package ai.lawyers.framework.websocket.voice;

import java.nio.ByteBuffer;
import jakarta.websocket.Session;

/**
 * N1（语雀 7.3.3 N1①）：PBX 下行音频 Sink——mod_audio_fork 二进制帧实现。
 *
 * <p>与 mod_audio_fork 上行通道对称：上行是 FreeSWITCH 经 {@code @OnMessage}
 * 直推<b>二进制裸 PCM</b>（见 {@link PbxMediaWebSocketServer#onBinaryMessage}），
 * 下行回写同样以裸 PCM 二进制帧经同一 fork 会话发回，FreeSWITCH 边收边播。
 * 与 {@link PbxWsDownlinkSink}（mod_audio_stream 文本 base64）的区别仅在传输编码，
 * 故复用 {@link AbstractPbxWsDownlinkSink} 的守卫与异常归一。</p>
 *
 * <p>PCM 约定与上行一致：16bit 小端、单声道；采样率在 fork start 帧已协商，
 * 二进制帧不携带采样率元数据。{@code ByteBuffer.wrap} 为只读视图，零拷贝发送。</p>
 *
 * @author ai-lawyers
 */
public class PbxForkBinaryDownlinkSink extends AbstractPbxWsDownlinkSink
{
    public PbxForkBinaryDownlinkSink(Session wsSession, String recordId)
    {
        super(wsSession, recordId);
    }

    @Override
    protected void sendFrame(Session session, byte[] pcm, int sampleRate) throws Exception
    {
        // mod_audio_fork 对称下行：直接发二进制裸 PCM，无 JSON/base64 封装
        session.getBasicRemote().sendBinary(ByteBuffer.wrap(pcm));
    }
}
