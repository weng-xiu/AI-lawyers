package ai.lawyers.framework.websocket.voice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Base64;
import javax.websocket.Session;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.test.util.ReflectionTestUtils;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * P3-B3：{@link PbxMediaForkHandler} 协议翻译测试。
 *
 * @author ai-lawyers
 */
class PbxMediaForkHandlerTest
{
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private Session wsSession;

    private VoiceSessionManager manager;

    private VoiceSession voiceSession;

    private PbxMediaForkProperties properties;

    private PbxMediaForkHandler handler;

    @BeforeEach
    void setUp()
    {
        wsSession = Mockito.mock(Session.class);
        when(wsSession.getId()).thenReturn("fork-1");
        manager = Mockito.mock(VoiceSessionManager.class);
        voiceSession = Mockito.mock(VoiceSession.class);
        properties = new PbxMediaForkProperties();
        ReflectionTestUtils.setField(properties, "enabled", true);
        ReflectionTestUtils.setField(properties, "authKey", "");
        ReflectionTestUtils.setField(properties, "defaultRate", 16000);
        when(manager.register(any(Session.class), eq("2000"), eq("caller")))
                .thenReturn(voiceSession);
        handler = new PbxMediaForkHandler("2000", wsSession, manager, properties);
    }

    // ---------- start：两类协议 ----------

    @Test
    void modAudioForkStart_nestedSampleRate_registersCallerAndSendsSynthStart() throws Exception
    {
        boolean ok = handler.handleText(
                "{\"type\":\"start\",\"data\":{\"sampleRate\":16000,\"channels\":1,\"callID\":\"abc\"}}");
        assertThat(ok).isTrue();

        verify(manager).register(wsSession, "2000", "caller");
        ArgumentCaptor<String> cap = ArgumentCaptor.forClass(String.class);
        verify(voiceSession).handleText(cap.capture());
        JsonNode synth = MAPPER.readTree(cap.getValue());
        assertThat(synth.path("type").asText()).isEqualTo("start");
        assertThat(synth.path("format").asText()).isEqualTo("pcm");
        assertThat(synth.path("sampleRate").asInt()).isEqualTo(16000);
    }

    @Test
    void modAudioStreamStart_topLevelSampleRate() throws Exception
    {
        handler.handleText("{\"type\":\"start\",\"sampleRate\":8000}");

        ArgumentCaptor<String> cap = ArgumentCaptor.forClass(String.class);
        verify(voiceSession).handleText(cap.capture());
        assertThat(MAPPER.readTree(cap.getValue()).path("sampleRate").asInt()).isEqualTo(8000);
    }

    @Test
    void start_withoutRate_usesDefault() throws Exception
    {
        handler.handleText("{\"type\":\"start\",\"data\":{}}");

        ArgumentCaptor<String> cap = ArgumentCaptor.forClass(String.class);
        verify(voiceSession).handleText(cap.capture());
        assertThat(MAPPER.readTree(cap.getValue()).path("sampleRate").asInt()).isEqualTo(16000);
    }

    @Test
    void duplicateStart_registeredOnce()
    {
        handler.handleText("{\"type\":\"start\",\"data\":{\"sampleRate\":16000}}");
        handler.handleText("{\"type\":\"start\",\"data\":{\"sampleRate\":16000}}");
        verify(manager, Mockito.times(1)).register(any(Session.class), any(String.class), any(String.class));
    }

    // ---------- 音频 ----------

    @Test
    void binaryAfterStart_forwardedToCallerSession()
    {
        handler.handleText("{\"type\":\"start\",\"data\":{\"sampleRate\":16000}}");
        byte[] pcm = new byte[320];
        handler.handleBinary(pcm);
        verify(voiceSession).handleBinary(pcm);
    }

    @Test
    void binaryBeforeStart_dropped()
    {
        handler.handleBinary(new byte[320]);
        verify(voiceSession, never()).handleBinary(Mockito.any(byte[].class));
    }

    @Test
    void audioJsonBase64_decodedAndForwarded() throws Exception
    {
        handler.handleText("{\"type\":\"start\",\"sampleRate\":16000}");
        byte[] pcm = new byte[] { 1, 2, 3, 4, 5 };
        String b64 = Base64.getEncoder().encodeToString(pcm);

        handler.handleText("{\"type\":\"audio\",\"data\":\"" + b64 + "\"}");
        verify(voiceSession).handleBinary(pcm);
    }

    @Test
    void audioJson_altKeyAndBadBase64_noThrow()
    {
        handler.handleText("{\"type\":\"start\",\"sampleRate\":16000}");
        handler.handleText("{\"type\":\"audio\",\"payload\":\"\"}");
        handler.handleText("{\"type\":\"audio\",\"audio\":\"@@@not-base64\"}");
        verify(voiceSession, never()).handleBinary(Mockito.any(byte[].class));
    }

    @Test
    void audioJsonBeforeStart_dropped()
    {
        handler.handleText("{\"type\":\"audio\",\"data\":\"AAAA\"}");
        verify(voiceSession, never()).handleBinary(Mockito.any(byte[].class));
    }

    // ---------- stop / end ----------

    @Test
    void stopFrame_forwardsStopAndMarksInactive()
    {
        handler.handleText("{\"type\":\"start\",\"sampleRate\":16000}");
        handler.handleText("{\"type\":\"stop\"}");
        verify(voiceSession).handleText("{\"type\":\"stop\"}");

        // stop 后音频不再转发
        handler.handleBinary(new byte[10]);
        verify(voiceSession, never()).handleBinary(Mockito.any(byte[].class));
    }

    @Test
    void endFrame_aliasForStop()
    {
        handler.handleText("{\"type\":\"start\",\"sampleRate\":16000}");
        handler.handleText("{\"type\":\"end\"}");
        verify(voiceSession).handleText("{\"type\":\"stop\"}");
    }

    // ---------- 异常路径 ----------

    @Test
    void invalidJson_returnsFalse()
    {
        assertThat(handler.handleText("not-json")).isFalse();
    }

    @Test
    void unknownType_ignoredReturnsTrue()
    {
        assertThat(handler.handleText("{\"type\":\"ping\"}")).isTrue();
    }

    @Test
    void registerReturnsNull_notStartedAudioDropped()
    {
        when(manager.register(any(Session.class), any(String.class), any(String.class)))
                .thenReturn(null);
        handler.handleText("{\"type\":\"start\",\"sampleRate\":16000}");
        handler.handleBinary(new byte[10]);
        verify(voiceSession, never()).handleText(Mockito.anyString());
        verify(voiceSession, never()).handleBinary(Mockito.any(byte[].class));
    }

    @Test
    void finish_whenActive_sendsStop()
    {
        handler.handleText("{\"type\":\"start\",\"sampleRate\":16000}");
        handler.finish();
        verify(voiceSession).handleText("{\"type\":\"stop\"}");
    }

    @Test
    void finish_withoutStart_noError()
    {
        handler.finish();
        verify(voiceSession, never()).handleText(Mockito.anyString());
    }

    // ---------- 端点共享密钥校验 ----------

    @Test
    void authKey_emptyConfigured_allows()
    {
        assertThat(PbxMediaWebSocketServer.checkAuthKey(null, "")).isTrue();
        assertThat(PbxMediaWebSocketServer.checkAuthKey("foo=bar", "")).isTrue();
    }

    @Test
    void authKey_matchingKey_passes()
    {
        assertThat(PbxMediaWebSocketServer.checkAuthKey("key=s3cr3t", "s3cr3t")).isTrue();
    }

    @Test
    void authKey_wrongOrMissing_fails()
    {
        assertThat(PbxMediaWebSocketServer.checkAuthKey("key=wrong", "s3cr3t")).isFalse();
        assertThat(PbxMediaWebSocketServer.checkAuthKey(null, "s3cr3t")).isFalse();
        assertThat(PbxMediaWebSocketServer.checkAuthKey("other=1", "s3cr3t")).isFalse();
    }

    @Test
    void authKey_urlEncodedValue_decodedBeforeCompare()
    {
        // 密钥含特殊字符时 PBX 需 URL encode
        assertThat(PbxMediaWebSocketServer.checkAuthKey("key=a%2Bb%2Fc", "a+b/c")).isTrue();
    }
}
