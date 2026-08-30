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
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.ThreadLocalRandom;

import javax.annotation.PostConstruct;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import ai.lawyers.common.utils.StringUtils;
import ai.lawyers.common.utils.sign.SecretCryptoUtils;
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

    /**
     * R1 舱壁：限制在途大模型调用并发数，防止慢响应耗尽 Tomcat 线程后雪崩传导到全系统。
     * 默认 32，可经 ai.model.max-inflight 调整（应与 DB/Redis 连接池、模型侧限流匹配）。
     */
    @Value("${ai.model.max-inflight:32}")
    private int maxInflight;

    /** R1 舱壁：获取在途许可的最长等待时间（毫秒），超时快速失败，避免请求无限排队 */
    @Value("${ai.model.acquire-timeout-ms:3000}")
    private long acquireTimeoutMs;

    private Semaphore inflightSemaphore;

    @PostConstruct
    public void initBulkhead()
    {
        int permits = maxInflight > 0 ? maxInflight : 32;
        inflightSemaphore = new Semaphore(permits, true);
        log.info("大模型调用舱壁初始化 maxInflight={} acquireTimeoutMs={}", permits, acquireTimeoutMs);
    }

    /** 携带 HTTP 状态码的模型调用异常，用于判断是否可重试 */
    private static class ModelHttpException extends Exception
    {
        private static final long serialVersionUID = 1L;
        private final int httpStatus;

        ModelHttpException(int httpStatus, String message)
        {
            super(message);
            this.httpStatus = httpStatus;
        }

        int getHttpStatus()
        {
            return httpStatus;
        }
    }

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
        // S6：apiKey 加密落库
        aiModelConfig.setApiKey(SecretCryptoUtils.encrypt(aiModelConfig.getApiKey()));
        if ("1".equals(aiModelConfig.getIsDefault()))
        {
            aiModelConfigMapper.updateDefaultConfig(aiModelConfig.getConfigId());
        }
        return aiModelConfigMapper.insertAiModelConfig(aiModelConfig);
    }

    @Override
    public int updateAiModelConfig(AiModelConfig aiModelConfig)
    {
        // S6：回显占位符 ****** 表示未修改密钥，保留库中旧值；否则加密新值落库
        if (SecretCryptoUtils.isMaskPlaceholder(aiModelConfig.getApiKey()))
        {
            aiModelConfig.setApiKey(null);
        }
        else
        {
            aiModelConfig.setApiKey(SecretCryptoUtils.encrypt(aiModelConfig.getApiKey()));
        }
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
    private String chat(AiModelConfig rawConfig, String systemPrompt, String userMessage, boolean jsonMode)
    {
        // S6：库中 apiKey 为密文，调用前解密（复制对象避免污染缓存对象）
        AiModelConfig config = rawConfig;
        if (StringUtils.isNotEmpty(rawConfig.getApiKey()))
        {
            config = rawConfig;
            config.setApiKey(SecretCryptoUtils.decrypt(rawConfig.getApiKey()));
        }
        String modelType = normalizeModelType(config.getModelType());
        int attempts = Math.max(1, config.getRetryCount() == null ? 1 : config.getRetryCount() + 1);
        Exception lastError = null;
        for (int attempt = 1; attempt <= attempts; attempt++)
        {
            // R1 舱壁：获取在途许可，限制并发模型调用数；获取不到快速失败，避免 Tomcat 线程被慢调用占满
            boolean acquired = false;
            try
            {
                acquired = inflightSemaphore.tryAcquire(acquireTimeoutMs, TimeUnit.MILLISECONDS);
                if (!acquired)
                {
                    throw new RuntimeException("AI模型调用繁忙（在途并发已达上限 " + maxInflight
                            + "），请稍后再试");
                }
                return "Claude".equalsIgnoreCase(modelType)
                        ? callClaude(config, systemPrompt, userMessage)
                        : callOpenAiCompatible(config, modelType, systemPrompt, userMessage, jsonMode);
            }
            catch (Exception e)
            {
                lastError = e;
                boolean retryable = isRetryable(e);
                log.warn("AI模型调用失败 configId={} attempt={}/{} retryable={} error={}",
                        config.getConfigId(), attempt, attempts, retryable, e.getMessage());
                // R2：仅对 429/5xx/网络超时等可重试错误重试；400/401/403 等立即失败，避免重试风暴
                if (!retryable || attempt >= attempts)
                {
                    break;
                }
                sleepBeforeRetry(attempt);
            }
            finally
            {
                if (acquired)
                {
                    inflightSemaphore.release();
                }
            }
        }
        throw new RuntimeException("调用AI模型失败：" + lastError.getMessage(), lastError);
    }

    /**
     * R2：判断异常是否值得重试。
     * 可重试：429（限流）、5xx（服务端错误）、网络 IO/超时（SocketTimeout/ConnectException 等）。
     * 不可重试：400/401/403/404 等客户端错误、响应格式错误。
     */
    private boolean isRetryable(Exception e)
    {
        if (e instanceof ModelHttpException)
        {
            int status = ((ModelHttpException) e).getHttpStatus();
            return status == 429 || (status >= 500 && status < 600);
        }
        if (e instanceof java.net.SocketTimeoutException
                || e instanceof java.net.ConnectException
                || e instanceof java.io.IOException)
        {
            return true;
        }
        // 舱壁繁忙属于瞬时过载，允许重试
        return e.getMessage() != null && e.getMessage().contains("在途并发已达上限");
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
        // R1：连接超时与读超时分开，连接超时短（快速发现不可达），读超时按模型生成耗时配置
        int readTimeoutSec = (config.getTimeout() == null || config.getTimeout() <= 0
                ? 60 : config.getTimeout());
        int connectTimeoutMs = 10 * 1000;
        int readTimeoutMs = readTimeoutSec * 1000;
        HttpURLConnection connection = null;
        boolean reusable = false;
        try
        {
            URL url = new URL(endpoint);
            connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("POST");
            connection.setConnectTimeout(connectTimeoutMs);
            connection.setReadTimeout(readTimeoutMs);
            connection.setDoOutput(true);
            connection.setUseCaches(false);
            // 复用底层 Keep-Alive 连接（HttpURLConnection 内置连接池），避免每次新建 TCP/TLS
            connection.setRequestProperty("Connection", "Keep-Alive");
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
                    throw new ModelHttpException(status, "模型返回空响应");
                }
                // 响应体已完整读取，连接可安全放回 Keep-Alive 池复用
                reusable = true;
                return MAPPER.readTree(responseText);
            }
            String errorMsg = extractError(responseText);
            // R2：抛出带状态码异常，由上层按 429/5xx 判定是否重试
            throw new ModelHttpException(status,
                    "HTTP " + status + (StringUtils.isEmpty(errorMsg) ? "" : "：" + errorMsg));
        }
        finally
        {
            // 成功且响应已读完时不 disconnect，交由 JDK Keep-Alive 缓存复用连接；失败则关闭
            if (connection != null && !reusable)
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

    /**
     * R2：指数退避 + 抖动。基础间隔 500ms，每次翻倍（500ms→1s→2s→4s...，上限 8s），
     * 并叠加 0~基础间隔的随机抖动，避免大量请求在同一时刻重试形成"重试风暴"。
     */
    private void sleepBeforeRetry(int attempt)
    {
        long base = Math.min(8000L, 500L * (1L << Math.min(attempt - 1, 4)));
        long jitter = ThreadLocalRandom.current().nextLong(0, Math.max(1L, base / 2));
        long sleepMs = base + jitter;
        try
        {
            log.info("AI模型调用将在 {}ms 后重试（第{}次）", sleepMs, attempt);
            Thread.sleep(sleepMs);
        }
        catch (InterruptedException e)
        {
            Thread.currentThread().interrupt();
        }
    }
}
