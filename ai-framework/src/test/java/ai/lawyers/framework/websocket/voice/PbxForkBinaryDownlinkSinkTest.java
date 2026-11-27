package ai.lawyers.framework.websocket.voice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.nio.ByteBuffer;
import jakarta.websocket.RemoteEndpoint;
import jakarta.websocket.Session;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * N1①（语雀 7.3.3）：{@link PbxForkBinaryDownlinkSink} 二进制裸 PCM 下行测试，
 * 与 {@link PbxWsDownlinkSinkTest} 同构，仅传输编码由文本 base64 改为二进制帧。
 *
 * @author ai-lawyers
 */
class PbxForkBinaryDownlinkSinkTest
{
    private Session session;

    private RemoteEndpoint.Basic remote;

    private PbxForkBinaryDownlinkSink sink;

    @BeforeEach
    void setUp() throws Exception
    {
        session = mock(Session.class);
        remote = mock(RemoteEndpoint.Basic.class);
        when(session.getBasicRemote()).thenReturn(remote);
        when(session.isOpen()).thenReturn(true);
        sink = new PbxForkBinaryDownlinkSink(session, "rec-1");
    }

    @Test
    void write_openSession_sendsRawPcmBinaryFrame() throws Exception
    {
        byte[] pcm = new byte[] { 1, 2, 3, 4, 5, 6, 7, 8 };
        boolean ok = sink.write(pcm, 16000);
        assertThat(ok).isTrue();

        // mod_audio_fork 下行：裸 PCM 二进制帧，无 JSON/base64 封装，内容与入参逐字节一致
        org.mockito.ArgumentCaptor<ByteBuffer> cap = org.mockito.ArgumentCaptor.forClass(ByteBuffer.class);
        verify(remote).sendBinary(cap.capture());
        ByteBuffer sent = cap.getValue();
        byte[] got = new byte[sent.remaining()];
        sent.get(got);
        assertThat(got).isEqualTo(pcm);
        // 文本通道不应被使用
        verify(remote, never()).sendText(any(String.class));
    }

    @Test
    void write_nullOrEmpty_returnsFalseNoSend() throws Exception
    {
        assertThat(sink.write(null, 16000)).isFalse();
        assertThat(sink.write(new byte[0], 16000)).isFalse();
        verify(remote, never()).sendBinary(any(ByteBuffer.class));
    }

    @Test
    void write_sessionClosed_returnsFalseNoSend() throws Exception
    {
        when(session.isOpen()).thenReturn(false);
        assertThat(sink.write(new byte[] { 1 }, 16000)).isFalse();
        verify(remote, never()).sendBinary(any(ByteBuffer.class));
    }

    @Test
    void write_sendThrows_returnsFalseNoRetry() throws Exception
    {
        org.mockito.Mockito.doThrow(new IOException("boom"))
                .when(remote).sendBinary(any(ByteBuffer.class));
        assertThat(sink.write(new byte[] { 1, 2 }, 8000)).isFalse();
        verify(remote, org.mockito.Mockito.times(1)).sendBinary(any(ByteBuffer.class));
    }

    @Test
    void close_isIdempotent_andRejectsWritesAfterwards() throws Exception
    {
        sink.close();
        sink.close();
        assertThat(sink.write(new byte[] { 1 }, 16000)).isFalse();
        verify(remote, never()).sendBinary(any(ByteBuffer.class));
    }
}
