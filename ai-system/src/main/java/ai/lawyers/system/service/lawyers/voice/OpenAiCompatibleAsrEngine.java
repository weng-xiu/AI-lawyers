package ai.lawyers.system.service.lawyers.voice;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import ai.lawyers.common.utils.StringUtils;

/**
 * OpenAI/Whisper 兼容的 HTTP 语音识别引擎（multipart 文件转写）。
 *
 * <p>可对接 faster-whisper-server、vLLM 或任意实现 /audio/transcriptions
 * 接口的 ASR 服务；阿里云 NLS、电信 ASR 也可通过内网适配网关接入同一协议。</p>
 */
@Service("openAiCompatibleAsrEngine")
public class OpenAiCompatibleAsrEngine implements AsrEngine
{
    private static final Logger log = LoggerFactory.getLogger(OpenAiCompatibleAsrEngine.class);

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final VoiceProperties properties;

    public OpenAiCompatibleAsrEngine(VoiceProperties properties)
    {
        this.properties = properties;
    }

    @Override
    public String transcribe(byte[] audio, String format, int sampleRate, Map<String, Object> options)
    {
        if (audio == null || audio.length == 0)
        {
            return "";
        }
        try
        {
            String endpoint = trimSlash(properties.getAsrBaseUrl()) + "/audio/transcriptions";
            String fileName = "audio_" + UUID.randomUUID().toString().replace("-", "")
                    + "." + (StringUtils.isEmpty(format) ? "wav" : format);
            String model = option(options, "model", properties.getAsrModel());
            String language = option(options, "language", properties.getAsrLanguage());
            String responseText = multipartPost(endpoint, audio, fileName, model, language);
            JsonNode root = MAPPER.readTree(responseText);
            String text = root.path("text").asText(null);
            if (text == null && root.path("data").path("text").isTextual())
            {
                text = root.path("data").path("text").asText();
            }
            return text == null ? "" : text;
        }
        catch (Exception e)
        {
            log.warn("ASR 转写失败: {}", e.getMessage());
            return "";
        }
    }

    private String multipartPost(String endpoint, byte[] audio, String fileName, String model,
                                 String language) throws Exception
    {
        String boundary = "----SmartCall" + UUID.randomUUID().toString().replace("-", "");
        HttpURLConnection connection = null;
        try
        {
            connection = (HttpURLConnection) new URL(endpoint).openConnection();
            connection.setRequestMethod("POST");
            connection.setConnectTimeout(30000);
            connection.setReadTimeout(60000);
            connection.setDoOutput(true);
            connection.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + boundary);
            if (StringUtils.isNotEmpty(properties.getAsrApiKey()))
            {
                connection.setRequestProperty("Authorization", "Bearer " + properties.getAsrApiKey());
            }
            try (OutputStream out = connection.getOutputStream())
            {
                writeField(out, boundary, "model", model);
                writeField(out, boundary, "language", language);
                writeField(out, boundary, "response_format", "json");
                out.write(("--" + boundary + "\r\n").getBytes(StandardCharsets.UTF_8));
                out.write(("Content-Disposition: form-data; name=\"file\"; filename=\"" + fileName
                        + "\"\r\n").getBytes(StandardCharsets.UTF_8));
                out.write("Content-Type: application/octet-stream\r\n\r\n".getBytes(StandardCharsets.UTF_8));
                out.write(audio);
                out.write(("\r\n--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));
                out.flush();
            }
            int status = connection.getResponseCode();
            InputStream stream = status >= 200 && status < 300
                    ? connection.getInputStream() : connection.getErrorStream();
            String body = readAll(stream);
            if (status < 200 || status >= 300)
            {
                throw new IllegalStateException("HTTP " + status + "：" + body);
            }
            return body;
        }
        finally
        {
            if (connection != null)
            {
                connection.disconnect();
            }
        }
    }

    private void writeField(OutputStream out, String boundary, String name, String value) throws Exception
    {
        StringBuilder sb = new StringBuilder();
        sb.append("--").append(boundary).append("\r\n");
        sb.append("Content-Disposition: form-data; name=\"").append(name).append("\"\r\n\r\n");
        sb.append(value).append("\r\n");
        out.write(sb.toString().getBytes(StandardCharsets.UTF_8));
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
}
