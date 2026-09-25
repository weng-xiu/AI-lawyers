package ai.lawyers.system.service.impl.lawyers;

import java.nio.charset.StandardCharsets;
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

import okhttp3.ConnectionPool;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;

import ai.lawyers.common.utils.StringUtils;
import ai.lawyers.common.utils.sign.SecretCryptoUtils;
import ai.lawyers.system.domain.lawyers.AiModelConfig;
import ai.lawyers.system.domain.lawyers.stat.AiModelCallLog;
import ai.lawyers.system.mapper.lawyers.AiModelConfigMapper;
import ai.lawyers.system.service.lawyers.IAiModelConfigService;
import ai.lawyers.system.service.lawyers.metrics.HotlineMetrics;
import ai.lawyers.system.service.lawyers.stat.AiModelCallLogRecorder;
import ai.lawyers.system.service.lawyers.stat.ModelUsage;

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

    /** T5-1：AI 调用指标上报（未引入 micrometer 时内部静默） */
    @Autowired(required = false)
    private HotlineMetrics metrics;

    /** P3-E5：大模型调用明细日志（Token/费用/耗时/场景），异步 best-effort，未装配时静默 */
    @Autowired(required = false)
    private AiModelCallLogRecorder callLogRecorder;

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

    /** T2-1/R1 连接池：最大空闲连接数（与在途舱壁匹配，默认 32） */
    @Value("${ai.model.http.maxIdleConnections:32}")
    private int maxIdleConnections;

    /** T2-1/R1 连接池中空闲连接保活时长（秒），超时关闭回收 */
    @Value("${ai.model.http.keepAliveSeconds:300}")
    private long keepAliveSeconds;

    /** T2-1/R1 建立 TCP/TLS 连接超时（毫秒），连接不可达快速失败 */
    @Value("${ai.model.http.connectTimeoutMs:10000}")
    private int connectTimeoutMs;

    /**
     * T3 RAG：embedding 专用模型配置ID（在 ai_model_config 中配置一条指向 /v1/embeddings
     * 兼容端点的记录，如本地/内网部署的 bge-small-zh）。配置后优先使用。
     */
    @Value("${ai.rag.embedding-config-id:0}")
    private Long embeddingConfigId;

    /** T3 RAG：embedding 模型名（configId 未配置时，用默认模型的 apiUrl/apiKey + 此模型名调用） */
    @Value("${ai.rag.embedding-model:}")
    private String embeddingModel;

    /** T2-1/R1 共享 OkHttp 客户端（连接池单例），所有模型调用复用，避免每次新建 TCP/TLS 握手 */
    private OkHttpClient sharedHttpClient;

    private static final MediaType JSON_MEDIA_TYPE =
            MediaType.parse("application/json; charset=utf-8");

    @PostConstruct
    public void initBulkhead()
    {
        int permits = maxInflight > 0 ? maxInflight : 32;
        inflightSemaphore = new Semaphore(permits, true);
        // T2-1/R1：连接池单例 + 共享客户端。读超时按各模型 config.timeout 用 newBuilder() 派生，
        // 连接池/调度资源仍共享，兼顾"不同模型不同生成超时"与"连接复用"。
        ConnectionPool pool = new ConnectionPool(
                maxIdleConnections > 0 ? maxIdleConnections : 32,
                keepAliveSeconds > 0 ? keepAliveSeconds : 300L, TimeUnit.SECONDS);
        sharedHttpClient = new OkHttpClient.Builder()
                .connectionPool(pool)
                .connectTimeout(connectTimeoutMs, TimeUnit.MILLISECONDS)
                .retryOnConnectionFailure(true)
                .build();
        // T5-1：AI 在途并发 gauge（= 已发放且未归还的许可数）
        if (metrics != null)
        {
            metrics.gaugeAiInflight(inflightSemaphore,
                    s -> (double) (permits - ((Semaphore) s).availablePermits()));
        }
        log.info("大模型调用舱壁初始化 maxInflight={} acquireTimeoutMs={}；OkHttp连接池 maxIdle={} keepAlive={}s",
                permits, acquireTimeoutMs, maxIdleConnections, keepAliveSeconds);
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

    /** chat 调用结果：文本 + Token 用量（P3-E5 落日志用） */
    private static class Reply
    {
        final String content;
        final ModelUsage usage;

        Reply(String content, ModelUsage usage)
        {
            this.content = content;
            this.usage = usage == null ? ModelUsage.ZERO : usage;
        }
    }

    /** embedding 调用结果：向量列表 + Token 用量 */
    private static class EmbedResult
    {
        final List<float[]> vectors;
        final ModelUsage usage;

        EmbedResult(List<float[]> vectors, ModelUsage usage)
        {
            this.vectors = vectors;
            this.usage = usage == null ? ModelUsage.ZERO : usage;
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
            String response = chat(config, "你是一个连接测试助手。", "请仅回复：连接成功",
                    false, AiModelCallLogRecorder.SCENE_TEST);
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
        return callAiModel(question, context, AiModelCallLogRecorder.SCENE_OTHER);
    }

    @Override
    public String callAiModel(String question, String context, String scene)
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
            return chat(config, DEFAULT_SYSTEM_PROMPT, user.toString(), false, scene);
        }
        catch (Exception e)
        {
            throw new RuntimeException("调用AI模型失败：" + e.getMessage(), e);
        }
    }

    @Override
    public String chat(String systemPrompt, String userMessage)
    {
        return chat(systemPrompt, userMessage, AiModelCallLogRecorder.SCENE_OTHER);
    }

    @Override
    public String chat(String systemPrompt, String userMessage, String scene)
    {
        AiModelConfig config = getDefaultAiModelConfig();
        if (config == null)
        {
            throw new IllegalStateException("未找到可用的AI模型配置，请联系管理员配置模型参数。");
        }
        return chat(config, systemPrompt, userMessage, false, scene);
    }

    @Override
    public String chatJson(String systemPrompt, String userMessage)
    {
        return chatJson(systemPrompt, userMessage, AiModelCallLogRecorder.SCENE_OTHER);
    }

    @Override
    public String chatJson(String systemPrompt, String userMessage, String scene)
    {
        AiModelConfig config = getDefaultAiModelConfig();
        if (config == null)
        {
            throw new IllegalStateException("未找到可用的AI模型配置，请联系管理员配置模型参数。");
        }
        return chat(config, systemPrompt, userMessage, true, scene);
    }

    /**
     * T3 RAG：批量生成文本向量（OpenAI 兼容 /embeddings）。
     * 模型选择：优先 embeddingConfigId 指定的专用配置（推荐，endpoint 指向 bge 等向量化服务）；
     * 否则用默认对话模型的 apiUrl/apiKey + embeddingModel 模型名（要求该平台同时提供 embeddings 端点）。
     * 任一条件不满足或调用失败均抛异常，由 RAG 检索方降级到关键词路，不影响问答主流程。
     */
    @Override
    public List<float[]> embedTexts(List<String> texts)
    {
        return embedTexts(texts, AiModelCallLogRecorder.SCENE_RAG);
    }

    @Override
    public List<float[]> embedTexts(List<String> texts, String scene)
    {
        if (texts == null || texts.isEmpty())
        {
            return java.util.Collections.emptyList();
        }
        AiModelConfig rawConfig = resolveEmbeddingConfig();
        if (rawConfig == null)
        {
            throw new IllegalStateException("未配置可用的 embedding 模型（请配置 ai.rag.embedding-config-id 或 ai.rag.embedding-model）");
        }
        // 复制对象后再解密 apiKey，避免明文密钥污染 MyBatis/调用方持有的原始对象
        AiModelConfig config = cloneConfig(rawConfig);
        if (StringUtils.isNotEmpty(rawConfig.getApiKey()))
        {
            config.setApiKey(SecretCryptoUtils.decrypt(rawConfig.getApiKey()));
        }
        boolean acquired = false;
        long start = System.currentTimeMillis();
        try
        {
            acquired = inflightSemaphore.tryAcquire(acquireTimeoutMs, TimeUnit.MILLISECONDS);
            if (!acquired)
            {
                // T5-1：舱壁拒绝（embedding）
                if (metrics != null)
                {
                    metrics.incrementAi("embed", "reject");
                }
                // P3-E5：舱壁拒绝落日志（不计费，仅反映过载）
                recordCallLog(AiModelCallLogRecorder.KIND_EMBED, scene, config, ModelUsage.ZERO,
                        start, 1, AiModelCallLog.RESULT_REJECT,
                        "embedding 服务繁忙（在途并发已达上限 " + maxInflight + "）");
                throw new RuntimeException("embedding 服务繁忙（在途并发已达上限 " + maxInflight + "）");
            }
            EmbedResult result = callEmbeddings(config, texts);
            // T5-1：embedding 成功 + 首响（调用耗时）
            if (metrics != null)
            {
                metrics.incrementAi("embed", "success");
                metrics.recordAiFirstResponse(System.currentTimeMillis() - start);
            }
            // P3-E5：成功落日志（Token 用量 + 费用快照）
            recordCallLog(AiModelCallLogRecorder.KIND_EMBED, scene, config, result.usage,
                    start, 1, AiModelCallLog.RESULT_SUCCESS, null);
            return result.vectors;
        }
        catch (Exception e)
        {
            // T5-1：舱壁拒绝已单独计 reject，此处不重复计 fail
            if (metrics != null && acquired)
            {
                metrics.incrementAi("embed", "fail");
            }
            // P3-E5：失败落日志（舱壁拒绝上面已记，此处不重复）
            if (acquired)
            {
                recordCallLog(AiModelCallLogRecorder.KIND_EMBED, scene, config, ModelUsage.ZERO,
                        start, 1, AiModelCallLog.RESULT_FAIL, e.getMessage());
            }
            throw new RuntimeException("生成文本向量失败：" + e.getMessage(), e);
        }
        finally
        {
            if (acquired)
            {
                inflightSemaphore.release();
            }
        }
    }

    /** P3-E5：统一落日志入口，记录器缺失/异常均不影响业务调用 */
    private void recordCallLog(String kind, String scene, AiModelConfig config, ModelUsage usage,
                               long startMillis, int attempts, String result, String failReason)
    {
        if (callLogRecorder == null)
        {
            return;
        }
        try
        {
            callLogRecorder.record(kind, scene, config, usage,
                    System.currentTimeMillis() - startMillis, attempts, result, failReason);
        }
        catch (Exception ex)
        {
            log.debug("P3-E5 调用日志记录异常（忽略）：{}", ex.getMessage());
        }
    }

    /** 浅拷贝配置对象，避免解密 apiKey 污染 MyBatis/缓存中的原始对象 */
    private AiModelConfig cloneConfig(AiModelConfig src)
    {
        AiModelConfig c = new AiModelConfig();
        c.setConfigId(src.getConfigId());
        c.setConfigName(src.getConfigName());
        c.setModelType(src.getModelType());
        c.setModelName(src.getModelName());
        c.setApiKey(src.getApiKey());
        c.setApiUrl(src.getApiUrl());
        c.setMaxTokens(src.getMaxTokens());
        c.setInputPrice(src.getInputPrice());
        c.setOutputPrice(src.getOutputPrice());
        c.setTemperature(src.getTemperature());
        c.setTopP(src.getTopP());
        c.setFrequencyPenalty(src.getFrequencyPenalty());
        c.setPresencePenalty(src.getPresencePenalty());
        c.setTimeout(src.getTimeout());
        c.setRetryCount(src.getRetryCount());
        c.setStatus(src.getStatus());
        c.setIsDefault(src.getIsDefault());
        return c;
    }

    private AiModelConfig resolveEmbeddingConfig()
    {
        if (embeddingConfigId != null && embeddingConfigId > 0)
        {
            return aiModelConfigMapper.selectAiModelConfigByConfigId(embeddingConfigId);
        }
        if (StringUtils.isNotEmpty(embeddingModel))
        {
            AiModelConfig base = getDefaultAiModelConfig();
            if (base != null)
            {
                AiModelConfig c = cloneConfig(base);
                c.setModelName(embeddingModel);
                return c;
            }
        }
        return null;
    }

    /** 调用 OpenAI 兼容 POST {apiUrl}/embeddings，解析 data[].embedding + usage 用量 */
    private EmbedResult callEmbeddings(AiModelConfig config, List<String> texts) throws Exception
    {
        String endpoint = endpointOf(config, "/embeddings");
        ObjectNode body = MAPPER.createObjectNode();
        body.put("model", StringUtils.isNotEmpty(config.getModelName()) ? config.getModelName()
                : (StringUtils.isNotEmpty(embeddingModel) ? embeddingModel : "bge-small-zh-v1.5"));
        ArrayNode input = body.putArray("input");
        for (String t : texts)
        {
            input.add(t == null ? "" : t);
        }

        Map<String, String> headers = new HashMap<>();
        headers.put("Content-Type", "application/json; charset=UTF-8");
        if (StringUtils.isNotEmpty(config.getApiKey()))
        {
            headers.put("Authorization", "Bearer " + config.getApiKey());
        }

        JsonNode root = httpPost(endpoint, headers, MAPPER.writeValueAsString(body), config);
        // P3-E5：embedding 用量多数实现仅回 total_tokens/prompt_tokens，统一归入输入侧
        ModelUsage parsed = ModelUsage.parseOpenAi(root);
        int promptTokens = parsed.getPromptTokens() > 0
                ? parsed.getPromptTokens() : parsed.getTotalTokens();
        ModelUsage usage = new ModelUsage(promptTokens, 0,
                parsed.getTotalTokens() > 0 ? parsed.getTotalTokens() : promptTokens);
        JsonNode data = root.path("data");
        if (!data.isArray() || data.size() != texts.size())
        {
            throw new IllegalStateException("embedding 响应 data 数量与入参不一致");
        }
        List<float[]> vectors = new java.util.ArrayList<>(data.size());
        for (JsonNode item : data)
        {
            JsonNode emb = item.path("embedding");
            if (!emb.isArray() || emb.size() == 0)
            {
                throw new IllegalStateException("embedding 响应缺少 embedding 向量字段");
            }
            float[] vec = new float[emb.size()];
            for (int i = 0; i < emb.size(); i++)
            {
                vec[i] = (float) emb.get(i).asDouble();
            }
            vectors.add(vec);
        }
        return new EmbedResult(vectors, usage);
    }

    /**
     * 真实 HTTP 调用。失败按配置重试，最终抛出异常由上层兜底。
     * P3-E5：终态（成功/失败/全程舱壁拒绝）异步落一条调用日志，attempt 透传实际尝试次数。
     */
    private String chat(AiModelConfig rawConfig, String systemPrompt, String userMessage,
                        boolean jsonMode, String scene)
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
        int lastAttempt = 0;
        // 只要有一次拿到过在途许可，终态失败即记 fail；全程被舱壁拒绝才记 reject
        boolean everAcquired = false;
        // T5-1：chat 首响计时（含重试等待，反映调用方真实等待）
        long start = System.currentTimeMillis();
        for (int attempt = 1; attempt <= attempts; attempt++)
        {
            // R1 舱壁：获取在途许可，限制并发模型调用数；获取不到快速失败，避免 Tomcat 线程被慢调用占满
            boolean acquired = false;
            try
            {
                acquired = inflightSemaphore.tryAcquire(acquireTimeoutMs, TimeUnit.MILLISECONDS);
                if (!acquired)
                {
                    // T5-1：舱壁拒绝（chat）
                    if (metrics != null)
                    {
                        metrics.incrementAi("chat", "reject");
                    }
                    throw new RuntimeException("AI模型调用繁忙（在途并发已达上限 " + maxInflight
                            + "），请稍后再试");
                }
                everAcquired = true;
                Reply reply = "Claude".equalsIgnoreCase(modelType)
                        ? callClaude(config, systemPrompt, userMessage)
                        : callOpenAiCompatible(config, modelType, systemPrompt, userMessage, jsonMode);
                // T5-1：chat 成功 + 首响耗时
                if (metrics != null)
                {
                    metrics.incrementAi("chat", "success");
                    metrics.recordAiFirstResponse(System.currentTimeMillis() - start);
                }
                // P3-E5：成功落日志（Token 用量 + 费用快照 + 实际尝试次数）
                recordCallLog(AiModelCallLogRecorder.KIND_CHAT, scene, config, reply.usage,
                        start, attempt, AiModelCallLog.RESULT_SUCCESS, null);
                return reply.content;
            }
            catch (Exception e)
            {
                lastError = e;
                lastAttempt = attempt;
                boolean retryable = isRetryable(e);
                log.warn("AI模型调用失败 configId={} attempt={}/{} retryable={} error={}",
                        config.getConfigId(), attempt, attempts, retryable, e.getMessage());
                // R2：仅对 429/5xx/网络超时等可重试错误重试；400/401/403 等立即失败，避免重试风暴
                if (!retryable || attempt >= attempts)
                {
                    // T5-1：终态失败（舱壁拒绝已在抛出前计 reject，不重复计 fail）
                    if (metrics != null && acquired)
                    {
                        metrics.incrementAi("chat", "fail");
                    }
                    break;
                }
                // T5-1：可重试错误计 retry（重试率 = retry / success+fail）
                if (metrics != null)
                {
                    metrics.incrementAi("chat", "retry");
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
        // P3-E5：终态失败/拒绝只落一条日志（重试过程不重复计费统计）
        String terminalResult = everAcquired
                ? AiModelCallLog.RESULT_FAIL : AiModelCallLog.RESULT_REJECT;
        recordCallLog(AiModelCallLogRecorder.KIND_CHAT, scene, config, ModelUsage.ZERO,
                start, Math.max(1, lastAttempt), terminalResult,
                lastError == null ? null : lastError.getMessage());
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

    private Reply callOpenAiCompatible(AiModelConfig config, String modelType, String systemPrompt,
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
        // P3-E5：解析 Token 用量（缺失不报错，计 0）
        ModelUsage usage = ModelUsage.parseOpenAi(root);
        JsonNode choices = root.path("choices");
        if (choices.isArray() && choices.size() > 0)
        {
            JsonNode content = choices.get(0).path("message").path("content");
            if (content.isTextual())
            {
                return new Reply(content.asText(), usage);
            }
            if (content.isArray() || content.isObject())
            {
                return new Reply(content.toString(), usage);
            }
        }
        JsonNode outputText = root.path("output").path("text");
        if (outputText.isTextual())
        {
            return new Reply(outputText.asText(), usage);
        }
        throw new IllegalStateException("模型响应缺少choices[0].message.content字段");
    }

    private Reply callClaude(AiModelConfig config, String systemPrompt, String userMessage) throws Exception
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
        // P3-E5：Claude usage 为 input_tokens/output_tokens
        ModelUsage usage = ModelUsage.parseClaude(root);
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
                return new Reply(sb.toString(), usage);
            }
        }
        throw new IllegalStateException("Claude响应缺少content字段");
    }

    /**
     * T2-1/R1：基于 OkHttp 连接池的 POST。共享客户端复用 TCP/TLS 连接；
     * 读超时按各模型 config.timeout 用 newBuilder() 派生（连接池/调度器仍共享）。
     * 响应体必须读完并关闭，连接才会被回收复用。
     */
    private JsonNode httpPost(String endpoint, Map<String, String> headers, String payload,
                              AiModelConfig config) throws Exception
    {
        int readTimeoutSec = (config.getTimeout() == null || config.getTimeout() <= 0
                ? 60 : config.getTimeout());
        OkHttpClient client = sharedHttpClient.newBuilder()
                .readTimeout(readTimeoutSec, TimeUnit.SECONDS)
                .build();

        Request.Builder builder = new Request.Builder()
                .url(endpoint)
                .post(RequestBody.create(JSON_MEDIA_TYPE, payload.getBytes(StandardCharsets.UTF_8)));
        for (Map.Entry<String, String> entry : headers.entrySet())
        {
            builder.header(entry.getKey(), entry.getValue());
        }

        // try-with-resources 保证 Response/ResponseBody 关闭，连接归还连接池
        try (Response response = client.newCall(builder.build()).execute())
        {
            ResponseBody body = response.body();
            String responseText = body == null ? "" : body.string();
            int status = response.code();
            if (response.isSuccessful())
            {
                if (StringUtils.isEmpty(responseText))
                {
                    throw new ModelHttpException(status, "模型返回空响应");
                }
                return MAPPER.readTree(responseText);
            }
            String errorMsg = extractError(responseText);
            // R2：抛出带状态码异常，由上层按 429/5xx 判定是否重试
            throw new ModelHttpException(status,
                    "HTTP " + status + (StringUtils.isEmpty(errorMsg) ? "" : "：" + errorMsg));
        }
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
