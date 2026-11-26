package ai.lawyers.framework.websocket.voice;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import ai.lawyers.system.service.lawyers.voice.VoiceProperties;

/**
 * N1③（语雀 7.3）：{@link CosyVoice2StreamTtsEngine} 内网流式 TTS 引擎测试。
 *
 * <p>用 JDK 内置 HttpServer 模拟 OpenAI 兼容 /audio/speech：验证请求体契约、
 * 20ms 固定帧切分、残余尾帧、非 2xx/空配置的显式错误以及 barge-in 取消语义，全程无外网。</p>
 *
 * @author ai-lawyers
 */
class CosyVoice2StreamTtsEngineTest
{
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private HttpServer server;

    private VoiceProperties properties;

    private final AtomicReference<Integer> responseStatus = new AtomicReference<>(200);

    private final AtomicReference<byte[]> responsePcm = new AtomicReference<>(new byte[0]);

    private final AtomicReference<String> capturedBody = new AtomicReference<>();

    private final AtomicReference<String> capturedAuth = new AtomicReference<>();

    private final AtomicInteger calls = new AtomicInteger();

    /** 取消测试用：服务端在此等待，保证取消发生在响应读取期间 */
    private volatile CountDownLatch serverEnterGate;

    private volatile CountDownLatch cancelReleaseGate;

    @BeforeEach
    void setUp() throws IOException
    {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/audio/speech", exchange ->
        {
            calls.incrementAndGet();
            capturedBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            capturedAuth.set(exchange.getRequestHeaders().getFirst("Authorization"));
            byte[] pcm = responsePcm.get();
            exchange.sendResponseHeaders(responseStatus.get(),
                    responseStatus.get() == 200 ? pcm.length : 0);
            try (OutputStream out = exchange.getResponseBody())
            {
                if (responseStatus.get() == 200)
                {
                    if (serverEnterGate != null)
                    {
                        // 响应头已发出、连接已建立：通知测试可 cancel，再分批写体
                        serverEnterGate.countDown();
                        awaitQuietly(cancelReleaseGate);
                        for (int i = 0; i < pcm.length; i += 640)
                        {
                            int end = Math.min(i + 640, pcm.length);
                            out.write(pcm, i, end - i);
                            out.flush();
                            sleepQuietly(20);
                        }
                    }
                    else
                    {
                        out.write(pcm);
                    }
                }
            }
        });
        server.start();

        properties = new VoiceProperties();
        properties.setCosyvoice2TtsBaseUrl("http://127.0.0.1:" + server.getAddress().getPort() + "/v1");
        properties.setCosyvoice2TtsModel("CosyVoice2-0.5B");
        properties.setCosyvoice2Voice("longxiaochun");
        properties.setCosyvoice2ApiKey("cv2-key");
    }

    @AfterEach
    void tearDown()
    {
        if (cancelReleaseGate != null)
        {
            cancelReleaseGate.countDown();
        }
        server.stop(0);
    }

    private static void awaitQuietly(CountDownLatch gate)
    {
        if (gate == null)
        {
            return;
        }
        try
        {
            gate.await(5, TimeUnit.SECONDS);
        }
        catch (InterruptedException e)
        {
            Thread.currentThread().interrupt();
        }
    }

    private static void sleepQuietly(long ms)
    {
        try
        {
            Thread.sleep(ms);
        }
        catch (InterruptedException e)
        {
            Thread.currentThread().interrupt();
        }
    }

    private static final class CollectingCallback implements TtsStreamCallback
    {
        final List<byte[]> audio = Collections.synchronizedList(new ArrayList<>());

        final List<String> errors = Collections.synchronizedList(new ArrayList<>());

        final CountDownLatch done = new CountDownLatch(1);

        volatile boolean ended;

        @Override
        public void onAudio(int seq, int chunkCount, byte[] pcm)
        {
            audio.add(pcm);
        }

        @Override
        public void onEnd()
        {
            ended = true;
            done.countDown();
        }

        @Override
        public void onError(String code, String message)
        {
            errors.add(code);
            done.countDown();
        }

        boolean awaitDone() throws InterruptedException
        {
            return done.await(10, TimeUnit.SECONDS);
        }
    }

    @Test
    void engineCode_isCosyvoice2()
    {
        assertThat(new CosyVoice2StreamTtsEngine(properties).engineCode()).isEqualTo("cosyvoice2");
    }

    @Test
    void missingBaseUrl_returnsCancelledAndEngineUnavailable()
    {
        properties.setCosyvoice2TtsBaseUrl("");
        CollectingCallback cb = new CollectingCallback();

        StreamSynthesis handle = new CosyVoice2StreamTtsEngine(properties)
                .synthesizeStream("你好", null, 16000, cb);

        assertThat(handle.isCancelled()).isTrue();
        assertThat(cb.errors).containsExactly("ENGINE_UNAVAILABLE");
        assertThat(calls.get()).isZero();
    }

    @Test
    void blankText_andBadSampleRate_rejectWithoutHttp()
    {
        CosyVoice2StreamTtsEngine engine = new CosyVoice2StreamTtsEngine(properties);
        CollectingCallback cb1 = new CollectingCallback();
        assertThat(engine.synthesizeStream("  ", null, 16000, cb1).isCancelled()).isTrue();
        assertThat(cb1.errors).containsExactly("TTS_BAD_REQUEST");

        CollectingCallback cb2 = new CollectingCallback();
        assertThat(engine.synthesizeStream("你好", null, 11025, cb2).isCancelled()).isTrue();
        assertThat(cb2.errors).containsExactly("TTS_BAD_REQUEST");
        assertThat(calls.get()).isZero();
    }

    @Test
    void synthesize_postsOpenAiContractAndFrames20msPcm() throws Exception
    {
        // 16kHz 单声道 S16LE：20ms = 640 字节；给 2.5 帧，验证整帧切分 + 残余尾帧
        responsePcm.set(new byte[1600]);
        CollectingCallback cb = new CollectingCallback();

        StreamSynthesis handle = new CosyVoice2StreamTtsEngine(properties)
                .synthesizeStream("您好，律师", null, 16000, cb);

        assertThat(cb.awaitDone()).isTrue();
        assertThat(handle.isCancelled()).isFalse();
        assertThat(cb.ended).isTrue();
        assertThat(cb.errors).isEmpty();
        // 2 个完整帧 + 1 个尾帧
        assertThat(cb.audio).hasSize(3);
        assertThat(cb.audio.get(0)).hasSize(640);
        assertThat(cb.audio.get(1)).hasSize(640);
        assertThat(cb.audio.get(2)).hasSize(320);

        JsonNode req = MAPPER.readTree(capturedBody.get());
        assertThat(req.path("model").asText()).isEqualTo("CosyVoice2-0.5B");
        assertThat(req.path("input").asText()).isEqualTo("您好，律师");
        assertThat(req.path("voice").asText()).isEqualTo("longxiaochun");
        assertThat(req.path("response_format").asText()).isEqualTo("pcm");
        assertThat(req.path("sample_rate").asInt()).isEqualTo(16000);
        assertThat(capturedAuth.get()).isEqualTo("Bearer cv2-key");
    }

    @Test
    void synthesize_voiceOverrideAnd8kFrameSize() throws Exception
    {
        // 8kHz：20ms = 320 字节
        responsePcm.set(new byte[320]);
        CollectingCallback cb = new CollectingCallback();

        new CosyVoice2StreamTtsEngine(properties)
                .synthesizeStream("测试", "custom-voice", 8000, cb);

        assertThat(cb.awaitDone()).isTrue();
        assertThat(cb.audio).hasSize(1);
        assertThat(cb.audio.get(0)).hasSize(320);
        JsonNode req = MAPPER.readTree(capturedBody.get());
        assertThat(req.path("voice").asText()).isEqualTo("custom-voice");
        assertThat(req.path("sample_rate").asInt()).isEqualTo(8000);
    }

    @Test
    void synthesize_emptyAuthorizationHeader_whenNoApiKey() throws Exception
    {
        properties.setCosyvoice2ApiKey("");
        responsePcm.set(new byte[640]);
        CollectingCallback cb = new CollectingCallback();

        new CosyVoice2StreamTtsEngine(properties).synthesizeStream("测试", null, 16000, cb);

        assertThat(cb.awaitDone()).isTrue();
        assertThat(cb.errors).isEmpty();
        assertThat(capturedAuth.get()).isNull();
    }

    @Test
    void non2xx_response_callsHttpError() throws Exception
    {
        responseStatus.set(503);
        CollectingCallback cb = new CollectingCallback();

        new CosyVoice2StreamTtsEngine(properties).synthesizeStream("测试", null, 16000, cb);

        assertThat(cb.awaitDone()).isTrue();
        assertThat(cb.ended).isFalse();
        assertThat(cb.errors).containsExactly("TTS_HTTP_ERROR");
        assertThat(cb.audio).isEmpty();
    }

    @Test
    void cancel_duringStream_stopsAudioAndNoEnd() throws Exception
    {
        serverEnterGate = new CountDownLatch(1);
        cancelReleaseGate = new CountDownLatch(1);
        // 足够大的响应体，配合服务端门控保证 cancel 发生在读取期间
        responsePcm.set(new byte[640 * 50]);
        CollectingCallback cb = new CollectingCallback();

        CosyVoice2StreamTtsEngine engine = new CosyVoice2StreamTtsEngine(properties);
        StreamSynthesis handle = engine.synthesizeStream("测试", null, 16000, cb);
        assertThat(serverEnterGate.await(5, TimeUnit.SECONDS)).isTrue();

        handle.cancel();
        assertThat(handle.isCancelled()).isTrue();
        cancelReleaseGate.countDown();

        // 给工作线程时间收敛；取消后不允许 onEnd
        Thread.sleep(500);
        assertThat(cb.ended).isFalse();
        assertThat(cb.errors).isEmpty();
    }
}
