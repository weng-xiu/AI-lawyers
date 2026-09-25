package ai.lawyers.framework.websocket.voice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Base64;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import javax.websocket.RemoteEndpoint;
import javax.websocket.Session;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * {@link VoiceSession} 帧协议与生命周期测试（P3-A1）：
 * 不启 Spring/Tomcat，用 mock Session + BasicRemote 捕获下发帧，
 * 覆盖 start/audio/tts/bargein/stop 全链路、错误矩阵与保序语义。
 *
 * @author ai-lawyers
 */
class VoiceSessionTest
{
    private static final long WAIT_MS = 8000L;

    private final ObjectMapper mapper = new ObjectMapper();

    private Session wsSession;

    private RemoteEndpoint.Basic basic;

    private CopyOnWriteArrayList<JsonNode> frames;

    private AtomicInteger consumed;

    private VoiceSession session;

    @BeforeEach
    void setUp() throws Exception
    {
        frames = new CopyOnWriteArrayList<>();
        consumed = new AtomicInteger();
        wsSession = mock(Session.class);
        basic = mock(RemoteEndpoint.Basic.class);
        when(wsSession.getId()).thenReturn("c1");
        when(wsSession.isOpen()).thenReturn(true);
        when(wsSession.getBasicRemote()).thenReturn(basic);
        doAnswer(inv -> {
            try
            {
                frames.add(mapper.readTree(inv.getArgument(0, String.class)));
            }
            catch (java.io.IOException e)
            {
                throw new RuntimeException(e);
            }
            return null;
        }).when(basic).sendText(org.mockito.ArgumentMatchers.anyString());
        session = new VoiceSession(wsSession, "sess-1", "agent", 1000);
    }

    @AfterEach
    void tearDown()
    {
        session.shutdown();
    }

    // ---------- ASR 全链路 ----------

    @Test
    void startAudioStop_emitsStartedPartialFinalInOrder() throws Exception
    {
        session.sendConnected();
        assertThat(await(n -> type(n, "connected")).path("sessionId").asText()).isEqualTo("sess-1");

        session.handleText("{\"type\":\"start\",\"engine\":\"mock\",\"format\":\"pcm\",\"sampleRate\":16000}");
        JsonNode started = await(n -> type(n, "started"));
        assertThat(started.path("engine").asText()).isEqualTo("mock");
        assertThat(started.path("sampleRate").asInt()).isEqualTo(16000);

        String b64 = Base64.getEncoder().encodeToString(new byte[64]);
        session.handleText("{\"type\":\"audio\",\"seq\":1,\"data\":\"" + b64 + "\"}");
        JsonNode partial = await(n -> type(n, "asr_partial"));
        assertThat(partial.path("text").asText()).contains("1 帧").contains("64 字节");

        session.handleText("{\"type\":\"stop\"}");
        JsonNode fin = await(n -> type(n, "asr_final"));
        assertThat(fin.path("text").asText()).contains("【Mock 识别结果】").contains("1 帧");
        assertThat(session.isAsrActive()).isFalse();
    }

    @Test
    void binaryPcmAfterStart_isFedToAsr() throws Exception
    {
        session.handleText("{\"type\":\"start\"}");
        await(n -> type(n, "started"));
        session.handleBinary(new byte[128]);
        JsonNode partial = await(n -> type(n, "asr_partial"));
        assertThat(partial.path("text").asText()).contains("128 字节");
    }

    // ---------- 错误矩阵 ----------

    @Test
    void audioBeforeStart_errorNotStarted() throws Exception
    {
        String b64 = Base64.getEncoder().encodeToString(new byte[16]);
        session.handleText("{\"type\":\"audio\",\"data\":\"" + b64 + "\"}");
        JsonNode err = await(n -> type(n, "error"));
        assertThat(err.path("code").asText()).isEqualTo("NOT_STARTED");
    }

    @Test
    void binaryBeforeStart_errorNotStarted() throws Exception
    {
        session.handleBinary(new byte[16]);
        JsonNode err = await(n -> type(n, "error"));
        assertThat(err.path("code").asText()).isEqualTo("NOT_STARTED");
    }

    @Test
    void realEngineRequested_errorExplicitNoFakeFallback() throws Exception
    {
        session.handleText("{\"type\":\"start\",\"engine\":\"dashscope\"}");
        JsonNode err = await(n -> type(n, "error"));
        assertThat(err.path("code").asText()).isEqualTo("ENGINE_UNAVAILABLE");
        assertThat(session.isAsrActive()).isFalse();
    }

    @Test
    void badFormatAndSampleRate_rejected() throws Exception
    {
        session.handleText("{\"type\":\"start\",\"format\":\"wav\"}");
        assertThat(await(n -> type(n, "error")).path("code").asText()).isEqualTo("BAD_FORMAT");

        session.handleText("{\"type\":\"start\",\"sampleRate\":44100}");
        assertThat(await(n -> type(n, "error")).path("code").asText()).isEqualTo("BAD_SAMPLE_RATE");
    }

    @Test
    void unknownTypeAndBadJson_errorFrames() throws Exception
    {
        session.handleText("{\"type\":\"nope\"}");
        assertThat(await(n -> type(n, "error")).path("code").asText()).isEqualTo("UNKNOWN_TYPE");

        session.handleText("not-json{{");
        assertThat(await(n -> type(n, "error")).path("code").asText()).isEqualTo("BAD_FRAME");
    }

    @Test
    void ttsParamValidation() throws Exception
    {
        session.handleText("{\"type\":\"tts\",\"text\":\"\"}");
        assertThat(await(n -> type(n, "error")).path("code").asText()).isEqualTo("INVALID_PARAM");

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < VoiceSession.TTS_TEXT_MAX + 1; i++)
        {
            sb.append('x');
        }
        session.handleText("{\"type\":\"tts\",\"text\":\"" + sb + "\"}");
        assertThat(await(n -> type(n, "error")).path("code").asText()).isEqualTo("TEXT_TOO_LONG");
    }

    // ---------- TTS 分片与 barge-in ----------

    @Test
    void tts_emitsAudioChunksThenTtsEnd() throws Exception
    {
        session.handleText("{\"type\":\"start\"}");
        await(n -> type(n, "started"));
        session.handleText("{\"type\":\"tts\",\"text\":\"你好\"}");

        JsonNode end = await(n -> type(n, "tts_end"));
        assertThat(end.path("interrupted").asBoolean()).isFalse();

        List<JsonNode> audio = frames.stream()
                .filter(n -> type(n, "tts_audio")).collect(Collectors.toList());
        // 2 字 → 160ms → 8 片
        assertThat(audio).hasSize(8);
        for (int i = 0; i < 8; i++)
        {
            JsonNode c = audio.get(i);
            assertThat(c.path("seq").asInt()).isEqualTo(i);
            assertThat(c.path("sampleRate").asInt()).isEqualTo(16000);
            assertThat(c.path("format").asText()).isEqualTo("pcm");
            byte[] pcm = Base64.getDecoder().decode(c.path("data").asText());
            assertThat(pcm).hasSize(640);
        }
        assertThat(audio.get(7).path("isEnd").asBoolean()).isTrue();
        assertThat(audio.get(0).path("isEnd").asBoolean()).isFalse();
    }

    @Test
    void bargein_interruptsTtsWithInterruptedEndAndNoMoreAudio() throws Exception
    {
        session.handleText("{\"type\":\"start\"}");
        await(n -> type(n, "started"));

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 50; i++)
        {
            sb.append('法');
        }
        session.handleText("{\"type\":\"tts\",\"text\":\"" + sb + "\"}");
        assertThat(await(n -> type(n, "tts_audio"))).isNotNull();

        session.handleText("{\"type\":\"bargein\"}");
        JsonNode end = await(n -> type(n, "tts_end") && n.path("interrupted").asBoolean(false));
        assertThat(end.path("reason").asText()).isEqualTo("bargein");

        // 打断后再观察 600ms：interrupted 帧之后不得再有 tts_audio
        Thread.sleep(600);
        int endIdx = -1;
        for (int i = 0; i < frames.size(); i++)
        {
            JsonNode n = frames.get(i);
            if (type(n, "tts_end") && n.path("interrupted").asBoolean(false))
            {
                endIdx = i;
                break;
            }
        }
        assertThat(endIdx).isGreaterThanOrEqualTo(0);
        for (int i = endIdx + 1; i < frames.size(); i++)
        {
            assertThat(type(frames.get(i), "tts_audio")).isFalse();
        }
    }

    // ---------- 工具 ----------

    private boolean type(JsonNode n, String t)
    {
        return t.equals(n.path("type").asText());
    }

    private JsonNode await(Predicate<JsonNode> predicate) throws InterruptedException
    {
        long deadline = System.currentTimeMillis() + WAIT_MS;
        while (System.currentTimeMillis() < deadline)
        {
            int from;
            synchronized (frames)
            {
                from = consumed.get();
                for (int i = from; i < frames.size(); i++)
                {
                    JsonNode n = frames.get(i);
                    if (predicate.test(n))
                    {
                        consumed.set(i + 1);
                        return n;
                    }
                }
            }
            Thread.sleep(20);
        }
        throw new AssertionError("等待帧超时，已收到: "
                + frames.stream().map(n -> n.path("type").asText()).collect(Collectors.joining(",")));
    }
}
