package ai.lawyers.system.service.lawyers.voice;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import ai.lawyers.common.utils.StringUtils;

/**
 * 通义千问 DashScope HTTP 语音合成（CosyVoice 异步任务）。
 *
 * <p>协议说明：先提交 multimodal-generation 异步任务，再轮询 tasks/{taskId}，
 * 成功后下载 output.audio.url 音频。仅需 HTTP + JSON，无 SDK 依赖。</p>
 */
@Service("dashScopeTtsEngine")
public class DashScopeTtsEngine implements TtsEngine
{
    private static final Logger log = LoggerFactory.getLogger(DashScopeTtsEngine.class);

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static final int MAX_POLLS = 30;

    private final VoiceProperties properties;

    public DashScopeTtsEngine(VoiceProperties properties)
    {
        this.properties = properties;
    }

    @Override
    public byte[] synthesize(String text, String format, int sampleRate, Map<String, Object> options)
    {
        if (StringUtils.isEmpty(properties.getDashscopeApiKey()))
        {
            log.warn("DashScope TTS 未配置 api-key，跳过合成");
            return new byte[0];
        }
        try
        {
            String taskId = submit(text, format, sampleRate, options);
            String audioUrl = waitForResult(taskId);
            if (StringUtils.isEmpty(audioUrl))
            {
                return new byte[0];
            }
            return download(audioUrl);
        }
        catch (Exception e)
        {
            log.warn("DashScope TTS 合成失败: {}", e.getMessage());
            return new byte[0];
        }
    }

    private String submit(String text, String format, int sampleRate, Map<String, Object> options) throws Exception
    {
        String endpoint = trimSlash(properties.getDashscopeBaseUrl())
                + "/services/aigc/multimodal-generation/generation";
        ObjectNode body = MAPPER.createObjectNode();
        body.put("model", option(options, "model", properties.getDashscopeTtsModel()));
        ObjectNode input = body.putObject("input");
        input.put("text", text);
        ObjectNode parameters = body.putObject("parameters");
        parameters.put("format", option(options, "format",
                StringUtils.isNotEmpty(format) ? format : properties.getDashscopeFormat()));
        parameters.put("sample_rate", optionInt(options, "sampleRate",
                sampleRate > 0 ? sampleRate : properties.getDashscopeSampleRate()));
        parameters.put("voice", option(options, "voice", properties.getDashscopeVoice()));
        parameters.put("rate", optionDouble(options, "rate", 1.0));
        parameters.put("pitch", optionDouble(options, "pitch", 1.0));

        JsonNode response = httpJson(endpoint, "POST", MAPPER.writeValueAsString(body),
                "application/json", true, 30000);
        String taskId = response.path("output").path("task_id").asText(null);
        if (StringUtils.isEmpty(taskId))
        {
            throw new IllegalStateException("DashScope TTS 提交失败：" + response);
        }
        return taskId;
    }

    private String waitForResult(String taskId) throws Exception
    {
        String endpoint = trimSlash(properties.getDashscopeBaseUrl()) + "/tasks/" + taskId;
        for (int i = 0; i < MAX_POLLS; i++)
        {
            JsonNode response = httpJson(endpoint, "GET", null, "application/json", true, 15000);
            String status = response.path("output").path("task_status").asText("");
            if ("SUCCEEDED".equalsIgnoreCase(status))
            {
                JsonNode audio = response.path("output").path("audio");
                String url = audio.path("url").asText(null);
                if (StringUtils.isEmpty(url) && audio.isTextual())
                {
                    url = audio.asText();
                }
                return url;
            }
            if ("FAILED".equalsIgnoreCase(status) || "CANCELED".equalsIgnoreCase(status))
            {
                throw new IllegalStateException("DashScope TTS 任务失败：" + response);
            }
            Thread.sleep(1000L);
        }
        throw new IllegalStateException("DashScope TTS 任务超时，taskId=" + taskId);
    }

    private byte[] download(String audioUrl) throws Exception
    {
        HttpURLConnection connection = (HttpURLConnection) new URL(audioUrl).openConnection();
        connection.setConnectTimeout(15000);
        connection.setReadTimeout(30000);
        connection.setRequestMethod("GET");
        try (InputStream in = connection.getInputStream();
             ByteArrayOutputStream out = new ByteArrayOutputStream())
        {
            byte[] buffer = new byte[8192];
            int n;
            while ((n = in.read(buffer)) > 0)
            {
                out.write(buffer, 0, n);
            }
            return out.toByteArray();
        }
        finally
        {
            connection.disconnect();
        }
    }

    private JsonNode httpJson(String endpoint, String method, String payload, String contentType,
                              boolean auth, int timeoutMs) throws Exception
    {
        HttpURLConnection connection = null;
        try
        {
            connection = (HttpURLConnection) new URL(endpoint).openConnection();
            connection.setRequestMethod(method);
            connection.setConnectTimeout(timeoutMs);
            connection.setReadTimeout(timeoutMs);
            connection.setRequestProperty("Content-Type", contentType);
            connection.setRequestProperty("Accept", "application/json");
            if (auth)
            {
                connection.setRequestProperty("Authorization",
                        "Bearer " + properties.getDashscopeApiKey());
                if ("POST".equalsIgnoreCase(method))
                {
                    connection.setRequestProperty("X-DashScope-Async", "enable");
                }
            }
            if (payload != null)
            {
                connection.setDoOutput(true);
                try (OutputStream out = connection.getOutputStream())
                {
                    out.write(payload.getBytes(StandardCharsets.UTF_8));
                    out.flush();
                }
            }
            int status = connection.getResponseCode();
            InputStream stream = status >= 200 && status < 300
                    ? connection.getInputStream() : connection.getErrorStream();
            String bodyText = readAll(stream);
            if (status < 200 || status >= 300)
            {
                throw new IllegalStateException("HTTP " + status + "：" + bodyText);
            }
            return MAPPER.readTree(bodyText);
        }
        finally
        {
            if (connection != null)
            {
                connection.disconnect();
            }
        }
    }

    private String readAll(InputStream in) throws Exception
    {
        if (in == null)
        {
            return "";
        }
        try (InputStream stream = in;
             ByteArrayOutputStream out = new ByteArrayOutputStream())
        {
            byte[] buffer = new byte[4096];
            int n;
            while ((n = stream.read(buffer)) > 0)
            {
                out.write(buffer, 0, n);
            }
            return new String(out.toByteArray(), StandardCharsets.UTF_8);
        }
    }

    private String trimSlash(String value)
    {
        if (value == null)
        {
            return "";
        }
        String result = value.trim();
        while (result.endsWith("/"))
        {
            result = result.substring(0, result.length() - 1);
        }
        return result;
    }

    private String option(Map<String, Object> options, String key, String fallback)
    {
        if (options == null || options.get(key) == null)
        {
            return fallback;
        }
        return String.valueOf(options.get(key));
    }

    private int optionInt(Map<String, Object> options, String key, int fallback)
    {
        if (options == null || options.get(key) == null)
        {
            return fallback;
        }
        try
        {
            return Integer.parseInt(String.valueOf(options.get(key)));
        }
        catch (Exception ignored)
        {
            return fallback;
        }
    }

    private double optionDouble(Map<String, Object> options, String key, double fallback)
    {
        if (options == null || options.get(key) == null)
        {
            return fallback;
        }
        try
        {
            return Double.parseDouble(String.valueOf(options.get(key)));
        }
        catch (Exception ignored)
        {
            return fallback;
        }
    }
}
