package ai.lawyers.framework.websocket.voice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
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
import ai.lawyers.system.service.lawyers.metrics.HotlineMetrics;

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

    // ---------- A4：VAD 自动 barge-in ----------

    /** 生成 frames 个 20ms 帧的 440Hz 正弦 PCM（16kHz S16LE，RMS/ZCR 均在有声区间） */
    private static byte[] sine16k(int frames)
    {
        byte[] pcm = new byte[frames * 320 * 2];
        for (int i = 0; i < frames * 320; i++)
        {
            short v = (short) (10000 * Math.sin(2 * Math.PI * 440 * i / 16000.0));
            pcm[i * 2] = (byte) (v & 0xFF);
            pcm[i * 2 + 1] = (byte) (v >> 8);
        }
        return pcm;
    }

    @Test
    void vadSpeechStart_duringTts_interruptsWithVadReason() throws Exception
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

        // 播报中喂入 3 帧（60ms）语音 → 自动打断
        session.handleBinary(sine16k(3));
        await(n -> type(n, "vad_speech_start"));
        JsonNode end = await(n -> type(n, "tts_end") && n.path("interrupted").asBoolean(false));
        assertThat(end.path("reason").asText()).isEqualTo("vad_speech_start");
    }

    @Test
    void vadSpeechCycle_withoutTts_onlyVadFramesNoTtsEnd() throws Exception
    {
        session.handleText("{\"type\":\"start\"}");
        await(n -> type(n, "started"));

        session.handleBinary(sine16k(3));
        await(n -> type(n, "vad_speech_start"));
        // 400ms 尾静默判结束
        session.handleBinary(new byte[20 * 320 * 2]);
        await(n -> type(n, "vad_speech_end"));

        // 全程无 TTS：不得出现任何 tts_end
        for (JsonNode n : frames)
        {
            assertThat(type(n, "tts_end")).isFalse();
        }
    }

    @Test
    void vadDisabled_noVadFrames() throws Exception
    {
        session.shutdown();
        session = new VoiceSession(wsSession, "sess-1", "agent", 1000, null, false);
        session.handleText("{\"type\":\"start\"}");
        await(n -> type(n, "started"));

        session.handleBinary(sine16k(5));
        Thread.sleep(500);
        for (JsonNode n : frames)
        {
            assertThat(type(n, "vad_speech_start")).isFalse();
            assertThat(type(n, "vad_speech_end")).isFalse();
        }
    }

    // ---------- A5：延迟预算埋点 ----------

    @Test
    void metrics_fullPipelineRecorded() throws Exception
    {
        HotlineMetrics m = mock(HotlineMetrics.class);
        session.shutdown();
        session = new VoiceSession(wsSession, "sess-1", "agent", 1000, null, true, m);
        session.handleText("{\"type\":\"start\"}");
        await(n -> type(n, "started"));

        // VAD 语音起始 → recordVoiceVadMs
        session.handleBinary(sine16k(3));
        await(n -> type(n, "vad_speech_start"));
        verify(m, org.mockito.Mockito.timeout(2000).times(1)).recordVoiceVadMs(anyLong(), eq("sess-1"));

        // TTS 首包 → recordVoiceTtsFirstMs + recordVoiceE2eFirstMs（仅一次）
        session.handleText("{\"type\":\"tts\",\"text\":\"你好\"}");
        await(n -> type(n, "tts_audio"));
        verify(m, org.mockito.Mockito.timeout(2000).times(1)).recordVoiceTtsFirstMs(anyLong(), eq("sess-1"));
        verify(m, org.mockito.Mockito.timeout(2000).times(1)).recordVoiceE2eFirstMs(anyLong(), eq("sess-1"));

        // 手动 bargein → recordVoiceBargeinStopMs(trigger=bargein)
        session.handleText("{\"type\":\"bargein\"}");
        verify(m, org.mockito.Mockito.timeout(2000).times(1))
                .recordVoiceBargeinStopMs(anyLong(), eq("bargein"), eq("sess-1"));
    }

    @Test
    void metrics_vadBargeinTriggerRecorded() throws Exception
    {
        HotlineMetrics m = mock(HotlineMetrics.class);
        session.shutdown();
        session = new VoiceSession(wsSession, "sess-1", "agent", 1000, null, true, m);
        session.handleText("{\"type\":\"start\"}");
        await(n -> type(n, "started"));

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 50; i++)
        {
            sb.append('法');
        }
        session.handleText("{\"type\":\"tts\",\"text\":\"" + sb + "\"}");
        await(n -> type(n, "tts_audio"));

        // 播报中 VAD 自动打断 → trigger=vad
        session.handleBinary(sine16k(3));
        await(n -> type(n, "vad_speech_start"));
        verify(m, org.mockito.Mockito.timeout(2000).times(1))
                .recordVoiceBargeinStopMs(anyLong(), eq("vad"), eq("sess-1"));
    }

    @Test
    void metrics_nullSafeWhenAbsent() throws Exception
    {
        // metrics=null（6 参构造兜底）全链路不抛异常
        session.shutdown();
        session = new VoiceSession(wsSession, "sess-1", "agent", 1000, null, true, null);
        session.handleText("{\"type\":\"start\"}");
        await(n -> type(n, "started"));
        session.handleBinary(sine16k(3));
        await(n -> type(n, "vad_speech_start"));
        session.handleText("{\"type\":\"tts\",\"text\":\"你好\"}");
        await(n -> type(n, "tts_audio"));
        session.handleText("{\"type\":\"bargein\"}");
        await(n -> type(n, "tts_end") && n.path("interrupted").asBoolean(false));
    }

    // ---------- E3：语音机器人（ask 帧 / robot 自动应答） ----------

    /** 构建带机器人服务的会话（robotService mock 固定返回 result） */
    private ai.lawyers.system.service.lawyers.voice.robot.VoiceRobotService setupRobotSession(
            ai.lawyers.system.service.lawyers.voice.robot.VoiceRobotResult result) throws Exception
    {
        ai.lawyers.system.service.lawyers.voice.robot.VoiceRobotService robot =
                mock(ai.lawyers.system.service.lawyers.voice.robot.VoiceRobotService.class);
        when(robot.answer(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString()))
                .thenReturn(result);
        session.shutdown();
        session = new VoiceSession(wsSession, "sess-1", "agent", 1000, null, true, null, robot);
        return robot;
    }

    @Test
    void askFrame_answerDeltaDoneThenAutoTts() throws Exception
    {
        ai.lawyers.system.service.lawyers.voice.robot.VoiceRobotResult r =
                ai.lawyers.system.service.lawyers.voice.robot.VoiceRobotResult.ok("您可以主张经济补偿。");
        r.setRagHits(1);
        r.setSources(java.util.Collections.singletonList("劳动合同法 第四十六条"));
        ai.lawyers.system.service.lawyers.voice.robot.VoiceRobotService robot = setupRobotSession(r);

        session.handleText("{\"type\":\"start\"}");
        await(n -> type(n, "started"));
        session.handleText("{\"type\":\"ask\",\"text\":\"被辞退怎么赔偿\"}");

        JsonNode delta = await(n -> type(n, "answer_delta"));
        assertThat(delta.path("turnId").asLong()).isEqualTo(1L);
        assertThat(delta.path("text").asText()).isEqualTo("您可以主张经济补偿。");
        JsonNode done = await(n -> type(n, "answer_done"));
        assertThat(done.path("ragHits").asInt()).isEqualTo(1);
        assertThat(done.path("sources").get(0).asText()).isEqualTo("劳动合同法 第四十六条");
        assertThat(done.path("degraded").asBoolean()).isFalse();
        // 应答自动进入 TTS 播报
        assertThat(await(n -> type(n, "tts_audio"))).isNotNull();
        verify(robot, org.mockito.Mockito.timeout(2000).times(1)).answer(eq("被辞退怎么赔偿"), eq("sess-1"));
    }

    @Test
    void askValidations_andRobotUnavailable() throws Exception
    {
        // 默认会话（robotService=null）：参数校验先于可用性
        session.handleText("{\"type\":\"ask\",\"text\":\"\"}");
        assertThat(await(n -> type(n, "error")).path("code").asText()).isEqualTo("INVALID_PARAM");

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 501; i++)
        {
            sb.append('问');
        }
        session.handleText("{\"type\":\"ask\",\"text\":\"" + sb + "\"}");
        assertThat(await(n -> type(n, "error")).path("code").asText()).isEqualTo("TEXT_TOO_LONG");

        session.handleText("{\"type\":\"ask\",\"text\":\"合法问题\"}");
        assertThat(await(n -> type(n, "error")).path("code").asText()).isEqualTo("ROBOT_UNAVAILABLE");
    }

    @Test
    void robotMode_asrFinalAutoTriggersAnswer() throws Exception
    {
        ai.lawyers.system.service.lawyers.voice.robot.VoiceRobotService robot = setupRobotSession(
                ai.lawyers.system.service.lawyers.voice.robot.VoiceRobotResult.ok("建议先协商，协商不成可申请劳动仲裁。"));

        // 自定义注册表：ASR 用可控 stub（engineCode=mock 使 TTS 仍走 mock）
        final AsrStreamCallback[] cbHolder = new AsrStreamCallback[1];
        StreamAsrEngine stubAsr = new StreamAsrEngine()
        {
            @Override
            public String engineCode()
            {
                return "mock";
            }

            @Override
            public void open(VoiceAsrContext ctx, AsrStreamCallback cb)
            {
                cbHolder[0] = cb;
            }

            @Override
            public void feed(byte[] frame)
            {
            }

            @Override
            public void close()
            {
            }
        };
        VoiceEngineRegistry reg = new VoiceEngineRegistry()
        {
            @Override
            public StreamAsrEngine createAsr(String code)
            {
                return stubAsr;
            }
        };
        session.shutdown();
        session = new VoiceSession(wsSession, "sess-1", "caller", 1000, reg, true, null, robot);

        session.handleText("{\"type\":\"start\",\"robot\":true}");
        await(n -> type(n, "started"));
        // 模拟真实引擎产出 final → robot 模式自动触发回合
        cbHolder[0].onFinal("借钱不还怎么办");

        JsonNode delta = await(n -> type(n, "answer_delta"));
        assertThat(delta.path("text").asText()).contains("劳动仲裁");
        verify(robot, org.mockito.Mockito.timeout(2000).times(1)).answer(eq("借钱不还怎么办"), eq("sess-1"));
    }

    @Test
    void newAsk_supersedesStaleTurn_noLateAnswer() throws Exception
    {
        // 第一次调用阻塞直至放行，模拟慢 LLM；第二次立即返回
        final java.util.concurrent.CountDownLatch slow = new java.util.concurrent.CountDownLatch(1);
        ai.lawyers.system.service.lawyers.voice.robot.VoiceRobotService robot =
                mock(ai.lawyers.system.service.lawyers.voice.robot.VoiceRobotService.class);
        when(robot.answer(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString()))
                .thenAnswer(inv -> {
                    String q = inv.getArgument(0, String.class);
                    if (q.contains("第一问"))
                    {
                        slow.await(5, TimeUnit.SECONDS);
                        return ai.lawyers.system.service.lawyers.voice.robot.VoiceRobotResult.ok("第一问答复");
                    }
                    return ai.lawyers.system.service.lawyers.voice.robot.VoiceRobotResult.ok("第二问答复");
                });
        session.shutdown();
        session = new VoiceSession(wsSession, "sess-1", "agent", 1000, null, true, null, robot);

        session.handleText("{\"type\":\"start\"}");
        await(n -> type(n, "started"));
        session.handleText("{\"type\":\"ask\",\"text\":\"第一问\"}");
        // 等回合1进入阻塞的 LLM 调用，再发第二问取代之
        verify(robot, org.mockito.Mockito.timeout(2000).times(1))
                .answer(eq("第一问"), eq("sess-1"));
        session.handleText("{\"type\":\"ask\",\"text\":\"第二问\"}");
        slow.countDown();

        // 回合1 结果产出时已被取代 → 不下发；只见 turnId=2 的应答
        JsonNode delta = await(n -> type(n, "answer_delta"));
        assertThat(delta.path("turnId").asLong()).isEqualTo(2L);
        assertThat(delta.path("text").asText()).isEqualTo("第二问答复");
        await(n -> type(n, "answer_done"));
        Thread.sleep(300);
        for (JsonNode n : frames)
        {
            if (type(n, "answer_delta"))
            {
                assertThat(n.path("turnId").asLong()).isEqualTo(2L);
            }
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
