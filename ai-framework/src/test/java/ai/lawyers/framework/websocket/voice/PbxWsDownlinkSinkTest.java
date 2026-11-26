package ai.lawyers.framework.websocket.voice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.util.Base64;
import jakarta.websocket.RemoteEndpoint;
import jakarta.websocket.Session;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * N1（语雀 7.3）：{@link PbxWsDownlinkSink} 下行真实实现契约测试。
 *
 * <p>锁定 mod_audio_stream 对称下行帧格式（{@code {"type":"audio","data":"<base64>"}}）、
 * 关闭/会话不可用/发送异常时的拒绝语义（返回 false 供上游丢帧埋点），以及 close 幂等。</p>
 *
 * @author ai-lawyers
 */
class PbxWsDownlinkSinkTest
{
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private Session wsSession;

    private RemoteEndpoint.Basic basic;

    private PbxWsDownlinkSink sink;

    @BeforeEach
    void setUp() throws IOException
    {
        wsSession = mock(Session.class);
        basic = mock(RemoteEndpoint.Basic.class);
        when(wsSession.getBasicRemote()).thenReturn(basic);
        when(wsSession.isOpen()).thenReturn(true);
        sink = new PbxWsDownlinkSink(wsSession, "1001");
    }

    @Test
    void write_encodesSymmetricAudioFrameAndAccepts() throws Exception
    {
        byte[] pcm = new byte[] {1, 2, 3, 4};

        assertThat(sink.write(pcm, 16000)).isTrue();

        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(basic).sendText(captor.capture());
        JsonNode frame = MAPPER.readTree(captor.getValue());
        assertThat(frame.path("type").asText()).isEqualTo("audio");
        assertThat(frame.path("data").asText())
                .isEqualTo(Base64.getEncoder().encodeToString(pcm));
    }

    @Test
    void write_whenClosed_returnsFalseAndDoesNotSend() throws IOException
    {
        sink.close();

        assertThat(sink.write(new byte[] {1}, 16000)).isFalse();
        verify(basic, never()).sendText(anyString());
    }

    @Test
    void write_nullPcm_returnsFalse() throws IOException
    {
        assertThat(sink.write(null, 16000)).isFalse();
        verify(basic, never()).sendText(anyString());
    }

    @Test
    void write_emptyPcm_returnsFalse() throws IOException
    {
        assertThat(sink.write(new byte[0], 16000)).isFalse();
        verify(basic, never()).sendText(anyString());
    }

    @Test
    void write_whenSessionClosed_returnsFalse() throws IOException
    {
        when(wsSession.isOpen()).thenReturn(false);

        assertThat(sink.write(new byte[] {1}, 16000)).isFalse();
        verify(basic, never()).sendText(anyString());
    }

    @Test
    void write_whenSendThrows_returnsFalse() throws IOException
    {
        doThrow(new IOException("boom")).when(basic).sendText(anyString());

        assertThat(sink.write(new byte[] {1}, 16000)).isFalse();
    }

    @Test
    void close_isIdempotent()
    {
        sink.close();
        sink.close();

        assertThat(sink.write(new byte[] {1}, 16000)).isFalse();
    }
}
