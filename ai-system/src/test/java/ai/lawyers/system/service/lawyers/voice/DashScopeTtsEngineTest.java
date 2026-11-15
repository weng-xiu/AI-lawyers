package ai.lawyers.system.service.lawyers.voice;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import com.sun.net.httpserver.HttpServer;

/**
 * DashScopeTtsEngine 单测：未配置 key 短路、异步任务提交/轮询/音频下载、
 * 失败状态、缺少 taskId 与空音频 URL 的容错。
 *
 * @author ai-lawyers
 */
class DashScopeTtsEngineTest
{
    private HttpServer server;
    private VoiceProperties properties;
    private DashScopeTtsEngine engine;

    private final AtomicReference<String> submitResponse = new AtomicReference<>();
    private final AtomicReference<String> pollResponse = new AtomicReference<>();
    private final AtomicReference<String> audioUrl = new AtomicReference<>();
    private final AtomicInteger submitCalls = new AtomicInteger();
    private final AtomicInteger pollCalls = new AtomicInteger();

    @BeforeEach
    void setUp() throws IOException
    {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange ->
        {
            String path = exchange.getRequestURI().getPath();
            byte[] body;
            if (path.endsWith("/generation"))
            {
                submitCalls.incrementAndGet();
                body = (submitResponse.get() == null ? "{}" : submitResponse.get())
                        .getBytes(StandardCharsets.UTF_8);
            }
            else if (path.startsWith("/tasks/"))
            {
                pollCalls.incrementAndGet();
                body = (pollResponse.get() == null ? "{}" : pollResponse.get())
                        .getBytes(StandardCharsets.UTF_8);
            }
            else
            {
                body = "AUDIO".getBytes(StandardCharsets.UTF_8);
            }
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream out = exchange.getResponseBody())
            {
                out.write(body);
            }
        });
        server.start();
        String base = "http://127.0.0.1:" + server.getAddress().getPort();
        audioUrl.set(base + "/audio");

        properties = new VoiceProperties();
        properties.setDashscopeApiKey("test-key");
        properties.setDashscopeBaseUrl(base + "/");
        properties.setDashscopeTtsModel("cosyvoice-v1");
        properties.setDashscopeFormat("wav");
        properties.setDashscopeSampleRate(16000);
        properties.setDashscopeVoice("longxiaochun");
        engine = new DashScopeTtsEngine(properties);
    }

    @AfterEach
    void tearDown()
    {
        server.stop(0);
    }

    @Test
    void missingApiKeyReturnsEmptyWithoutHttp()
    {
        properties.setDashscopeApiKey("");

        assertThat(engine.synthesize("你好", "wav", 16000, null)).isEmpty();
        assertThat(submitCalls.get()).isZero();
    }

    @Test
    void submitsPollsAndDownloadsAudio()
    {
        submitResponse.set("{\"output\":{\"task_id\":\"t1\"}}");
        pollResponse.set("{\"output\":{\"task_status\":\"SUCCEEDED\",\"audio\":{\"url\":\""
                + audioUrl.get() + "\"}}}");

        byte[] audio = engine.synthesize("你好", "wav", 16000, null);

        assertThat(new String(audio, StandardCharsets.UTF_8)).isEqualTo("AUDIO");
        assertThat(submitCalls.get()).isEqualTo(1);
        assertThat(pollCalls.get()).isEqualTo(1);
    }

    @Test
    void supportsTextualAudioUrl()
    {
        submitResponse.set("{\"output\":{\"task_id\":\"t2\"}}");
        pollResponse.set("{\"output\":{\"task_status\":\"SUCCEEDED\",\"audio\":\""
                + audioUrl.get() + "\"}}");

        assertThat(engine.synthesize("你好", "wav", 16000, null)).isNotEmpty();
    }

    @Test
    void failedTaskReturnsEmpty()
    {
        submitResponse.set("{\"output\":{\"task_id\":\"t3\"}}");
        pollResponse.set("{\"output\":{\"task_status\":\"FAILED\"}}");

        assertThat(engine.synthesize("你好", "wav", 16000, null)).isEmpty();
    }

    @Test
    void missingTaskIdReturnsEmpty()
    {
        submitResponse.set("{\"output\":{}}");

        assertThat(engine.synthesize("你好", "wav", 16000, null)).isEmpty();
        assertThat(pollCalls.get()).isZero();
    }

    @Test
    void succeededWithoutAudioUrlReturnsEmpty()
    {
        submitResponse.set("{\"output\":{\"task_id\":\"t4\"}}");
        pollResponse.set("{\"output\":{\"task_status\":\"SUCCEEDED\"}}");

        assertThat(engine.synthesize("你好", "wav", 16000, null)).isEmpty();
    }

    @Test
    void optionsOverrideProperties()
    {
        submitResponse.set("{\"output\":{\"task_id\":\"t5\"}}");
        pollResponse.set("{\"output\":{\"task_status\":\"FAILED\"}}");
        Map<String, Object> options = new HashMap<>();
        options.put("model", "custom-model");
        options.put("sampleRate", "8000");
        options.put("voice", "custom-voice");

        engine.synthesize("你好", "mp3", 16000, options);

        assertThat(submitCalls.get()).isEqualTo(1);
    }
}
