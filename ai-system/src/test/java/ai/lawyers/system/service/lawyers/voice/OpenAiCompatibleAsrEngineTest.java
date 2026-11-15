package ai.lawyers.system.service.lawyers.voice;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
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
 * OpenAiCompatibleAsrEngine 单测：空音频短路、multipart 转写成功、
 * data.text 兼容、非 2xx/非法 JSON/无效地址的容错与选项覆盖。
 *
 * @author ai-lawyers
 */
class OpenAiCompatibleAsrEngineTest
{
    private HttpServer server;
    private VoiceProperties properties;
    private OpenAiCompatibleAsrEngine engine;

    private final AtomicReference<String> responseBody = new AtomicReference<>("{}");
    private final AtomicReference<Integer> responseStatus = new AtomicReference<>(200);
    private final AtomicReference<String> capturedBody = new AtomicReference<>();
    private final AtomicInteger calls = new AtomicInteger();

    @BeforeEach
    void setUp() throws IOException
    {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/audio/transcriptions", exchange ->
        {
            calls.incrementAndGet();
            try (InputStream in = exchange.getRequestBody())
            {
                capturedBody.set(new String(in.readAllBytes(), StandardCharsets.UTF_8));
            }
            byte[] body = responseBody.get().getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(responseStatus.get(), body.length);
            try (OutputStream out = exchange.getResponseBody())
            {
                out.write(body);
            }
        });
        server.start();

        properties = new VoiceProperties();
        properties.setAsrBaseUrl("http://127.0.0.1:" + server.getAddress().getPort() + "/");
        properties.setAsrModel("whisper-1");
        properties.setAsrLanguage("zh");
        properties.setAsrApiKey("asr-key");
        engine = new OpenAiCompatibleAsrEngine(properties);
    }

    @AfterEach
    void tearDown()
    {
        server.stop(0);
    }

    @Test
    void emptyAudioReturnsEmptyWithoutHttp()
    {
        assertThat(engine.transcribe(null, "wav", 16000, null)).isEmpty();
        assertThat(engine.transcribe(new byte[0], "wav", 16000, null)).isEmpty();
        assertThat(calls.get()).isZero();
    }

    @Test
    void transcribesText()
    {
        responseBody.set("{\"text\":\"您好，这里是律所\"}");

        String text = engine.transcribe(new byte[] { 1, 2 }, "wav", 16000, null);

        assertThat(text).isEqualTo("您好，这里是律所");
    }

    @Test
    void supportsNestedDataText()
    {
        responseBody.set("{\"data\":{\"text\":\"嵌套文本\"}}");

        assertThat(engine.transcribe(new byte[] { 1 }, "wav", 16000, null)).isEqualTo("嵌套文本");
    }

    @Test
    void missingTextFieldReturnsEmpty()
    {
        responseBody.set("{}");

        assertThat(engine.transcribe(new byte[] { 1 }, "wav", 16000, null)).isEmpty();
    }

    @Test
    void non2xxReturnsEmpty()
    {
        responseStatus.set(500);
        responseBody.set("{\"error\":\"boom\"}");

        assertThat(engine.transcribe(new byte[] { 1 }, "wav", 16000, null)).isEmpty();
    }

    @Test
    void invalidJsonReturnsEmpty()
    {
        responseBody.set("not-json");

        assertThat(engine.transcribe(new byte[] { 1 }, "wav", 16000, null)).isEmpty();
    }

    @Test
    void invalidBaseUrlReturnsEmpty()
    {
        properties.setAsrBaseUrl("");

        assertThat(engine.transcribe(new byte[] { 1 }, "wav", 16000, null)).isEmpty();
        assertThat(calls.get()).isZero();
    }

    @Test
    void optionsOverrideModelAndLanguageInMultipart()
    {
        responseBody.set("{\"text\":\"ok\"}");
        Map<String, Object> options = new HashMap<>();
        options.put("model", "whisper-large");
        options.put("language", "en");

        engine.transcribe(new byte[] { 1 }, "mp3", 16000, options);

        assertThat(capturedBody.get()).contains("name=\"model\"\r\n\r\nwhisper-large")
                .contains("name=\"language\"\r\n\r\nen")
                .contains("filename=\"audio_")
                .contains(".mp3");
    }

    @Test
    void defaultFormatIsWavWhenBlank()
    {
        responseBody.set("{\"text\":\"ok\"}");

        engine.transcribe(new byte[] { 1 }, "", 16000, null);

        assertThat(capturedBody.get()).contains(".wav");
    }
}
