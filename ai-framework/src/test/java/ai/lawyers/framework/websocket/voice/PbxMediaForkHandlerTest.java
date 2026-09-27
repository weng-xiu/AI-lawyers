package ai.lawyers.framework.websocket.voice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Base64;
import javax.websocket.Session;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.test.util.ReflectionTestUtils;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import ai.lawyers.system.service.lawyers.metrics.HotlineMetrics;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

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
        when(manager.register(any(Session.class), any(String.class), eq("caller")))
                .thenReturn(voiceSession);
        handler = new PbxMediaForkHandler("2000", wsSession, manager, properties);
    }

    @AfterEach
    void tearDown()
    {
        // start 成功的 handler 会进入端点静态活跃集，每个用例后必须收敛
        PbxMediaWebSocketServer.onForkClosed(handler);
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

    // ---------- M3-3：实机联调指标 ----------

    @Test
    void metrics_trafficCountersAndActiveGaugeSource() throws Exception
    {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        PbxMediaForkHandler h = new PbxMediaForkHandler("2001", wsSession,
                manager, properties, metricsWith(registry));
        try
        {
            h.handleText("{\"type\":\"start\",\"sampleRate\":16000}");
            assertThat(PbxMediaWebSocketServer.activeCount())
                    .as("start 受理后进入活跃集（静态计数，本类用例可能并存故只断言>=1）")
                    .isGreaterThanOrEqualTo(1);

            h.handleBinary(new byte[320]);
            String b64 = Base64.getEncoder().encodeToString(new byte[] { 1, 2, 3, 4, 5 });
            h.handleText("{\"type\":\"audio\",\"data\":\"" + b64 + "\"}");

            assertThat(registry.find("hotline_pbx.fork.bytes.total").counter().count())
                    .isEqualTo(325.0);
            assertThat(registry.find("hotline_pbx.fork.audio.frame.total")
                    .tag("kind", "binary").counter().count()).isEqualTo(1);
            assertThat(registry.find("hotline_pbx.fork.audio.frame.total")
                    .tag("kind", "json").counter().count()).isEqualTo(1);

            // 收到音频后静默年龄应接近 0
            assertThat(h.silenceAgeSeconds(System.nanoTime())).isBetween(0.0, 1.0);
        }
        finally
        {
            PbxMediaWebSocketServer.onForkClosed(h);
        }
    }

    @Test
    void metrics_badJsonAndBadBase64Counted() throws Exception
    {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        PbxMediaForkHandler h = new PbxMediaForkHandler("2002", wsSession,
                manager, properties, metricsWith(registry));
        try
        {
            h.handleText("not-json");
            h.handleText("{\"type\":\"start\",\"sampleRate\":16000}");
            h.handleText("{\"type\":\"audio\",\"data\":\"@@@bad\"}");

            assertThat(registry.find("hotline_pbx.fork.bad.frame.total")
                    .tag("reason", "bad_json").counter().count()).isEqualTo(1);
            assertThat(registry.find("hotline_pbx.fork.bad.frame.total")
                    .tag("reason", "bad_base64").counter().count()).isEqualTo(1);
        }
        finally
        {
            PbxMediaWebSocketServer.onForkClosed(h);
        }
    }

    @Test
    void metrics_startWithoutAudio_silenceGrowsFromOpen() throws Exception
    {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        PbxMediaForkHandler h = new PbxMediaForkHandler("2003", wsSession,
                manager, properties, metricsWith(registry));
        try
        {
            h.handleText("{\"type\":\"start\"}");
            double age1 = h.silenceAgeSeconds(System.nanoTime());
            // 无音频帧时静默年龄从受理时刻起算，非负
            assertThat(age1).isGreaterThanOrEqualTo(0);
            h.handleText("{\"type\":\"stop\"}");
            assertThat(h.silenceAgeSeconds(System.nanoTime()))
                    .as("stop 后不再计异常静默")
                    .isEqualTo(0);
            assertThat(h.isStopReceived()).isTrue();
        }
        finally
        {
            PbxMediaWebSocketServer.onForkClosed(h);
        }
    }

    /** 反射注入 MeterRegistry（HotlineMetrics 字段私有，无 setter） */
    private static HotlineMetrics metricsWith(SimpleMeterRegistry registry) throws Exception
    {
        HotlineMetrics m = new HotlineMetrics();
        java.lang.reflect.Field f = HotlineMetrics.class.getDeclaredField("registry");
        f.setAccessible(true);
        f.set(m, registry);
        return m;
    }
}
