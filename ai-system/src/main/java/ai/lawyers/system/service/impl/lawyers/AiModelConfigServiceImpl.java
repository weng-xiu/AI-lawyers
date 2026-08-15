package ai.lawyers.system.service.impl.lawyers;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import ai.lawyers.common.utils.StringUtils;
import ai.lawyers.system.domain.lawyers.AiModelConfig;
import ai.lawyers.system.mapper.lawyers.AiModelConfigMapper;
import ai.lawyers.system.service.lawyers.IAiModelConfigService;

/**
 * AI模型参数配置Service业务层处理。
 *
 * <p>融入 SmartCall 设计：模型调用统一为 OpenAI 兼容 Chat Completions 协议
 * （DashScope/DeepSeek/智谱/本地 vLLM/Ollama 均可通过 apiUrl 指向兼容端点），
 * Claude 走 Anthropic Messages 协议。支持 JSON 输出模式，供意图识别、信息抽取、
 * 情感分析等 IVR 节点复用。</p>
 *
 * @author ai-lawyers
 * @date 2025-07-15
 */
@Service
public class AiModelConfigServiceImpl implements IAiModelConfigService
{
    private static final Logger log = LoggerFactory.getLogger(AiModelConfigServiceImpl.class);

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static final String DEFAULT_SYSTEM_PROMPT =
            "你是一名专业的公共法律服务热线AI助手，回答应准确、简洁、符合中国法律法规，"
                    + "并提示用户涉及重大权益时寻求专业律师帮助。";

    @Autowired
    private AiModelConfigMapper aiModelConfigMapper;

    @Override
    public AiModelConfig selectAiModelConfigByConfigId(Long configId)
    {
        return aiModelConfigMapper.selectAiModelConfigByConfigId(configId);
    }

    @Override
    public List<AiModelConfig> selectAiModelConfigList(AiModelConfig aiModelConfig)
    {
        return aiModelConfigMapper.selectAiModelConfigList(aiModelConfig);
    }

    @Override
    public int insertAiModelConfig(AiModelConfig aiModelConfig)
    {
        if ("1".equals(aiModelConfig.getIsDefault()))
        {
            aiModelConfigMapper.updateDefaultConfig(aiModelConfig.getConfigId());
        }
        return aiModelConfigMapper.insertAiModelConfig(aiModelConfig);
    }

    @Override
    public int updateAiModelConfig(AiModelConfig aiModelConfig)
    {
        if ("1".equals(aiModelConfig.getIsDefault()))
        {
            aiModelConfigMapper.updateDefaultConfig(aiModelConfig.getConfigId());
        }
        return aiModelConfigMapper.updateAiModelConfig(aiModelConfig);
    }

    @Override
    public int deleteAiModelConfigByConfigIds(Long[] configIds)
    {
        return aiModelConfigMapper.deleteAiModelConfigByConfigIds(configIds);
    }

    @Override
    public int deleteAiModelConfigByConfigId(Long configId)
    {
        return aiModelConfigMapper.deleteAiModelConfigByConfigId(configId);
    }

    @Override
    public AiModelConfig getDefaultAiModelConfig()
    {
        return aiModelConfigMapper.selectDefaultAiModelConfig();
    }

    @Override
    public int setDefaultConfig(Long configId)
    {
        return aiModelConfigMapper.updateDefaultConfig(configId);
    }

    @Override
    public boolean testAiModelConnection(Long configId)
    {
        AiModelConfig config = selectAiModelConfigByConfigId(configId);
        if (config == null)
        {
            return false;
        }
        try
        {
            String response = chat(config, "你是一个连接测试助手。", "请仅回复：连接成功", false);
            return StringUtils.isNotEmpty(response);
        }
        catch (Exception e)
        {
            log.warn("AI模型连接测试失败 configId={} error={}", configId, e.getMessage());
            return false;
        }
    }

    @Override
    public String callAiModel(String question, String context)
    {
        AiModelConfig config = getDefaultAiModelConfig();
        if (config == null)
        {
            return "未找到可用的AI模型配置，请联系管理员配置模型参数。";
        }
        StringBuilder user = new StringBuilder();
        if (StringUtils.isNotEmpty(context))
        {
            user.append("背景信息：").append(context).append("\n\n");
        }
        user.append("用户问题：").append(question);
        try
        {
            return chat(config, DEFAULT_SYSTEM_PROMPT, user.toString(), false);
        }
        catch (Exception e)
        {
            throw new RuntimeException("调用AI模型失败：" + e.getMessage(), e);
        }
    }

    @Override
    public String chat(String systemPrompt, String userMessage)
    {
        AiModelConfig config = getDefaultAiModelConfig();
        if (config == null)
        {
            throw new IllegalStateException("未找到可用的AI模型配置，请联系管理员配置模型参数。");
        }
        return chat(config, systemPrompt, userMessage, false);
    }

    @Override
    public String chatJson(String systemPrompt, String userMessage)
    {
        AiModelConfig config = getDefaultAiModelConfig();
        if (config == null)
        {
            throw new IllegalStateException("未找到可用的AI模型配置，请联系管理员配置模型参数。");
        }
        return chat(config, systemPrompt, userMessage, true);
    }

    /**
     * 真实 HTTP 调用。失败按配置重试，最终抛出异常由上层兜底。
     */
    private String chat(AiModelConfig config, String systemPrompt, String userMessage, boolean jsonMode)
    {
        String modelType = normalizeModelType(config.getModelType());
        int attempts = Math.max(1, config.getRetryCount() == null ? 1 : config.getRetryCount() + 1);
        Exception lastError = null;
        for (int attempt = 1; attempt <= attempts; attempt++)
        {
            try
            {
                return "Claude".equalsIgnoreCase(modelType)
                        ? callClaude(config, systemPrompt, userMessage)
                        : callOpenAiCompatible(config, modelType, systemPrompt, userMessage, jsonMode);
            }
            catch (Exception e)
            {
                lastError = e;
                log.warn("AI模型调用失败 configId={} attempt={}/{} error={}",
                        config.getConfigId(), attempt, attempts, e.getMessage());
                if (attempt < attempts)
                {
                    sleepBeforeRetry(attempt);
                }
            }
        }
        throw new RuntimeException("调用AI模型失败：" + lastError.getMessage(), lastError);
    }

    private String callOpenAiCompatible(AiModelConfig config, String modelType, String systemPrompt,
                                        String userMessage, boolean jsonMode) throws Exception
    {
        String endpoint = endpointOf(config, "/chat/completions");
        ObjectNode body = MAPPER.createObjectNode();
        body.put("model", modelNameOf(config, "gpt-3.5-turbo"));
        body.put("stream", false);
        if (config.getMaxTokens() != null)
        {
            body.put("max_tokens", config.getMaxTokens());
        }
        if (config.getTemperature() != null)
        {
            body.put("temperature", config.getTemperature());
        }
        if (config.getTopP() != null)
        {
            body.put("top_p", config.getTopP());
        }
        if (jsonMode)
        {
            ObjectNode format = body.putObject("response_format");
            format.put("type", "json_object");
        }
        ArrayNode messages = body.putArray("messages");
        if (StringUtils.isNotEmpty(systemPrompt))
        {
            ObjectNode sys = messages.addObject();
            sys.put("role", "system");
            sys.put("content", systemPrompt);
        }
        ObjectNode user = messages.addObject();
        user.put("role", "user");
        user.put("content", userMessage);

        Map<String, String> headers = new HashMap<>();
        headers.put("Content-Type", "application/json; charset=UTF-8");
        if (StringUtils.isNotEmpty(config.getApiKey()))
        {
            headers.put("Authorization", "Bearer " + config.getApiKey());
        }

        JsonNode root = httpPost(endpoint, headers, MAPPER.writeValueAsString(body), config);
        JsonNode choices = root.path("choices");
        if (choices.isArray() && choices.size() > 0)
        {
            JsonNode content = choices.get(0).path("message").path("content");
            if (content.isTextual())
            {
                return content.asText();
            }
            if (content.isArray() || content.isObject())
            {
                return content.toString();
            }
        }
        JsonNode outputText = root.path("output").path("text");
        if (outputText.isTextual())
        {
            return outputText.asText();
        }
        throw new IllegalStateException("模型响应缺少choices[0].message.content字段");
    }

    private String callClaude(AiModelConfig config, String systemPrompt, String userMessage) throws Exception
    {
        String endpoint = endpointOf(config, "/messages");
        ObjectNode body = MAPPER.createObjectNode();
        body.put("model", modelNameOf(config, "claude-3-5-sonnet-latest"));
        body.put("max_tokens", config.getMaxTokens() == null ? 1024 : config.getMaxTokens());
        if (config.getTemperature() != null)
        {
            body.put("temperature", config.getTemperature());
        }
        if (StringUtils.isNotEmpty(systemPrompt))
        {
            body.put("system", systemPrompt);
        }
        ArrayNode messages = body.putArray("messages");
        ObjectNode user = messages.addObject();
        user.put("role", "user");
        user.put("content", userMessage);

        Map<String, String> headers = new HashMap<>();
        headers.put("Content-Type", "application/json; charset=UTF-8");
        headers.put("x-api-key", config.getApiKey() == null ? "" : config.getApiKey());
        headers.put("anthropic-version", "2023-06-01");

        JsonNode root = httpPost(endpoint, headers, MAPPER.writeValueAsString(body), config);
        JsonNode content = root.path("content");
        if (content.isArray() && content.size() > 0)
        {
            StringBuilder sb = new StringBuilder();
            for (JsonNode item : content)
            {
                if (item.path("type").asText("text").equals("text") && item.path("text").isTextual())
                {
                    sb.append(item.path("text").asText());
                }
            }
            if (sb.length() > 0)
            {
                return sb.toString();
            }
        }
        throw new IllegalStateException("Claude响应缺少content字段");
    }

    private JsonNode httpPost(String endpoint, Map<String, String> headers, String payload,
                              AiModelConfig config) throws Exception
    {
        int timeoutMs = (config.getTimeout() == null || config.getTimeout() <= 0
                ? 60 : config.getTimeout()) * 1000;
        HttpURLConnection connection = null;
        try
        {
            URL url = new URL(endpoint);
            connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("POST");
            connection.setConnectTimeout(timeoutMs);
            connection.setReadTimeout(timeoutMs);
            connection.setDoOutput(true);
            connection.setUseCaches(false);
            for (Map.Entry<String, String> entry : headers.entrySet())
            {
                connection.setRequestProperty(entry.getKey(), entry.getValue());
            }
            byte[] bytes = payload.getBytes(StandardCharsets.UTF_8);
            try (OutputStream out = connection.getOutputStream())
            {
                out.write(bytes);
                out.flush();
            }

            int status = connection.getResponseCode();
            String responseText = readBody(connection, status >= 200 && status < 300);
            if (status >= 200 && status < 300)
            {
                if (StringUtils.isEmpty(responseText))
                {
                    throw new IllegalStateException("模型返回空响应");
                }
                return MAPPER.readTree(responseText);
            }
            String errorMsg = extractError(responseText);
            throw new IllegalStateException("HTTP " + status + (StringUtils.isEmpty(errorMsg) ? "" : "：" + errorMsg));
        }
        finally
        {
            if (connection != null)
            {
                connection.disconnect();
            }
        }
    }

    private String readBody(HttpURLConnection connection, boolean successStream) throws Exception
    {
        StringBuilder sb = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                successStream ? connection.getInputStream() : connection.getErrorStream(),
                StandardCharsets.UTF_8)))
        {
            String line;
            while ((line = reader.readLine()) != null)
            {
                sb.append(line);
            }
        }
        return sb.toString();
    }

    private String extractError(String responseText)
    {
        if (StringUtils.isEmpty(responseText))
        {
            return "";
        }
        try
        {
            JsonNode root = MAPPER.readTree(responseText);
            JsonNode error = root.path("error");
            if (error.isTextual())
            {
                return error.asText();
            }
            if (error.isObject() && error.path("message").isTextual())
            {
                return error.path("message").asText();
            }
            if (root.path("message").isTextual())
            {
                return root.path("message").asText();
            }
        }
        catch (Exception ignored)
        {
            // 非JSON错误体，直接截取原文
        }
        return responseText.length() > 200 ? responseText.substring(0, 200) : responseText;
    }

    private String endpointOf(AiModelConfig config, String suffix)
    {
        if (StringUtils.isNotEmpty(config.getApiUrl()))
        {
            String url = config.getApiUrl().trim();
            if (url.endsWith("/"))
            {
                url = url.substring(0, url.length() - 1);
            }
            if (url.endsWith("/v1"))
            {
                url = url.substring(0, url.length() - 3);
            }
            return url + suffix;
        }
        String base = defaultEndpoint(config.getModelType());
        return base + suffix;
    }

    private String defaultEndpoint(String modelType)
    {
        String type = normalizeModelType(modelType);
        switch (type)
        {
            case "OpenAI":
                return "https://api.openai.com/v1";
            case "Claude":
                return "https://api.anthropic.com/v1";
            case "ChatGLM":
                return "https://open.bigmodel.cn/api/paas/v4";
            case "DeepSeek":
                return "https://api.deepseek.com/v1";
            case "DashScope":
                return "https://dashscope.aliyuncs.com/compatible-mode/v1";
            default:
                return "http://localhost:11434/v1";
        }
    }

    private String normalizeModelType(String modelType)
    {
        if (StringUtils.isEmpty(modelType))
        {
            return "local";
        }
        String type = modelType.trim();
        if ("gpt".equalsIgnoreCase(type))
        {
            return "OpenAI";
        }
        if ("qwen".equalsIgnoreCase(type) || "tongyi".equalsIgnoreCase(type))
        {
            return "DashScope";
        }
        return type;
    }

    private String modelNameOf(AiModelConfig config, String fallback)
    {
        return StringUtils.isNotEmpty(config.getModelName()) ? config.getModelName() : fallback;
    }

    private void sleepBeforeRetry(int attempt)
    {
        try
        {
            Thread.sleep(Math.min(2000, attempt * 500L));
        }
        catch (InterruptedException e)
        {
            Thread.currentThread().interrupt();
        }
    }
}
