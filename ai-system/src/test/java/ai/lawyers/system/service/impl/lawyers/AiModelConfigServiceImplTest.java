package ai.lawyers.system.service.impl.lawyers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.Semaphore;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.test.util.ReflectionTestUtils;
import com.sun.net.httpserver.HttpServer;
import ai.lawyers.common.utils.sign.SecretCryptoUtils;
import ai.lawyers.system.domain.lawyers.AiModelConfig;
import ai.lawyers.system.domain.lawyers.stat.AiModelCallLog;
import ai.lawyers.system.mapper.lawyers.AiModelConfigMapper;
import ai.lawyers.system.service.lawyers.metrics.HotlineMetrics;
import ai.lawyers.system.service.lawyers.stat.AiModelCallLogRecorder;
import ai.lawyers.system.service.lawyers.stat.ModelUsage;

/**
 * AI 模型配置服务单测。
 *
 * <p>使用本地 HttpServer 模拟 OpenAI 兼容与 Anthropic 协议端点，覆盖密钥加密/掩码回显、
 * 默认模型切换、OpenAI/Claude 报文组装与响应解析、JSON 模式、可重试与不可重试错误、
 * 舱壁并发拒绝、embedding 端点解析与向量解析、调用日志与指标上报。</p>
 *
 * @author ai-lawyers
 */
class AiModelConfigServiceImplTest
{
    private AiModelConfigServiceImpl service;

    @Mock private AiModelConfigMapper aiModelConfigMapper;
    @Mock private HotlineMetrics metrics;
    @Mock private AiModelCallLogRecorder callLogRecorder;

    private HttpServer server;
    private String baseUrl;
    private final List<String> paths = Collections.synchronizedList(new ArrayList<>());
    private final List<String> bodies = Collections.synchronizedList(new ArrayList<>());
    private final List<String> authHeaders = Collections.synchronizedList(new ArrayList<>());
    private final Queue<Stub> stubs = new LinkedList<>();

    @BeforeEach
    void setUp() throws Exception
    {
        MockitoAnnotations.openMocks(this);
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            paths.add(exchange.getRequestURI().getPath());
            bodies.add(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            authHeaders.add(exchange.getRequestHeaders().getFirst("Authorization"));
            Stub stub;
            synchronized (stubs)
            {
                stub = stubs.poll();
            }
            if (stub == null)
            {
                stub = new Stub(200, "{}");
            }
            byte[] payload = stub.body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(stub.status, payload.length);
            try (OutputStream out = exchange.getResponseBody())
            {
                out.write(payload);
            }
        });
        server.start();
        baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();

        service = new AiModelConfigServiceImpl();
        ReflectionTestUtils.setField(service, "aiModelConfigMapper", aiModelConfigMapper);
        ReflectionTestUtils.setField(service, "metrics", metrics);
        ReflectionTestUtils.setField(service, "callLogRecorder", callLogRecorder);
        ReflectionTestUtils.setField(service, "maxInflight", 32);
        ReflectionTestUtils.setField(service, "acquireTimeoutMs", 500L);
        ReflectionTestUtils.setField(service, "maxIdleConnections", 32);
        ReflectionTestUtils.setField(service, "keepAliveSeconds", 300L);
        ReflectionTestUtils.setField(service, "connectTimeoutMs", 3000);
        ReflectionTestUtils.setField(service, "embeddingConfigId", 0L);
        ReflectionTestUtils.setField(service, "embeddingModel", "");
        service.initBulkhead();
    }

    @AfterEach
    void tearDown()
    {
        if (server != null)
        {
            server.stop(0);
        }
    }

    // ---------------- 配置 CRUD ----------------

    @Test
    void crud_delegatesToMapper()
    {
        AiModelConfig query = new AiModelConfig();
        when(aiModelConfigMapper.selectAiModelConfigByConfigId(1L)).thenReturn(query);
        when(aiModelConfigMapper.selectAiModelConfigList(query))
                .thenReturn(Collections.singletonList(query));
        when(aiModelConfigMapper.selectDefaultAiModelConfig()).thenReturn(query);
        when(aiModelConfigMapper.updateDefaultConfig(1L)).thenReturn(1);
        when(aiModelConfigMapper.deleteAiModelConfigByConfigIds(any())).thenReturn(2);

        assertEquals(query, service.selectAiModelConfigByConfigId(1L));
        assertEquals(1, service.selectAiModelConfigList(query).size());
        assertEquals(query, service.getDefaultAiModelConfig());
        assertEquals(1, service.setDefaultConfig(1L));
        assertEquals(2, service.deleteAiModelConfigByConfigIds(new Long[]{1L, 2L}));
        service.deleteAiModelConfigByConfigId(1L);

        verify(aiModelConfigMapper).deleteAiModelConfigByConfigId(1L);
    }

    @Test
    void insert_encryptsApiKeyAndResetsDefault()
    {
        AiModelConfig config = config("/chat", "OpenAI");
        config.setApiKey("sk-plain");
        config.setIsDefault("1");
        when(aiModelConfigMapper.insertAiModelConfig(config)).thenReturn(1);

        assertEquals(1, service.insertAiModelConfig(config));

        assertFalse("sk-plain".equals(config.getApiKey()));
        assertEquals("sk-plain", SecretCryptoUtils.decrypt(config.getApiKey()));
        verify(aiModelConfigMapper).updateDefaultConfig(1L);
    }

    @Test
    void insert_notDefault_keepsOtherDefaults()
    {
        AiModelConfig config = config("/chat", "OpenAI");
        config.setApiKey("sk-plain");
        config.setIsDefault("0");

        service.insertAiModelConfig(config);

        verify(aiModelConfigMapper, never()).updateDefaultConfig(anyLong());
    }

    @Test
    void update_maskPlaceholder_keepsOldKey()
    {
        AiModelConfig config = config("/chat", "OpenAI");
        config.setApiKey("******");

        service.updateAiModelConfig(config);

        assertNull(config.getApiKey());
        verify(aiModelConfigMapper).updateAiModelConfig(config);
    }

    @Test
    void update_newKey_encryptsAndSwitchesDefault()
    {
        AiModelConfig config = config("/chat", "OpenAI");
        config.setApiKey("sk-new");
        config.setIsDefault("1");

        service.updateAiModelConfig(config);

        assertEquals("sk-new", SecretCryptoUtils.decrypt(config.getApiKey()));
        verify(aiModelConfigMapper).updateDefaultConfig(1L);
    }

    // ---------------- 连通性测试 ----------------

    @Test
    void testConnection_configMissing_false()
    {
        assertFalse(service.testAiModelConnection(9L));
        verify(aiModelConfigMapper, never()).selectDefaultAiModelConfig();
    }

    @Test
    void testConnection_emptyReply_false()
    {
        AiModelConfig config = config(baseUrl, "OpenAI");
        when(aiModelConfigMapper.selectAiModelConfigByConfigId(1L)).thenReturn(config);
        enqueue(200, "{\"choices\":[{\"message\":{\"content\":\"\"}}]}");

        assertFalse(service.testAiModelConnection(1L));
    }

    @Test
    void testConnection_httpError_false()
    {
        AiModelConfig config = config(baseUrl, "OpenAI");
        when(aiModelConfigMapper.selectAiModelConfigByConfigId(1L)).thenReturn(config);
        enqueue(500, "{\"error\":\"boom\"}");

        assertFalse(service.testAiModelConnection(1L));
    }

    // ---------------- 对话调用 ----------------

    @Test
    void callAiModel_noDefault_returnsHint()
    {
        String result = service.callAiModel("问题", null);

        assertEquals("未找到可用的AI模型配置，请联系管理员配置模型参数。", result);
        verify(aiModelConfigMapper, never()).selectAiModelConfigByConfigId(anyLong());
    }

    @Test
    void callAiModel_success_withContext()
    {
        AiModelConfig config = config(baseUrl, "OpenAI");
        when(aiModelConfigMapper.selectDefaultAiModelConfig()).thenReturn(config);
        enqueue(200, "{\"choices\":[{\"message\":{\"content\":\"答复\"}}],"
                + "\"usage\":{\"prompt_tokens\":10,\"completion_tokens\":5,\"total_tokens\":15}}");

        assertEquals("答复", service.callAiModel("怎么办", "案件背景"));

        assertEquals("/chat/completions", paths.get(0));
        assertTrue(bodies.get(0).contains("背景信息：案件背景"));
        assertTrue(bodies.get(0).contains("用户问题：怎么办"));
        assertTrue(bodies.get(0).contains("公共法律服务热线AI助手"));

        ArgumentCaptor<ModelUsage> usageCaptor = ArgumentCaptor.forClass(ModelUsage.class);
        verify(callLogRecorder).record(eq(AiModelCallLogRecorder.KIND_CHAT),
                eq(AiModelCallLogRecorder.SCENE_OTHER), eq(config), usageCaptor.capture(),
                anyLong(), eq(1), eq(AiModelCallLog.RESULT_SUCCESS), isNull());
        assertEquals(10, usageCaptor.getValue().getPromptTokens());
        assertEquals(15, usageCaptor.getValue().getTotalTokens());
        verify(metrics).incrementAi("chat", "success");
    }

    @Test
    void callAiModel_failure_wrapsException()
    {
        AiModelConfig config = config(baseUrl, "OpenAI");
        when(aiModelConfigMapper.selectDefaultAiModelConfig()).thenReturn(config);
        enqueue(400, "{\"error\":{\"message\":\"invalid key\"}}");

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> service.callAiModel("问题", null));
        assertTrue(ex.getMessage().contains("调用AI模型失败"));
        assertTrue(ex.getMessage().contains("400"));
        verify(metrics).incrementAi("chat", "fail");
    }

    @Test
    void chat_noConfig_throws()
    {
        assertThrows(IllegalStateException.class, () -> service.chat("sys", "user"));
        assertThrows(IllegalStateException.class, () -> service.chatJson("sys", "user"));
    }

    @Test
    void chatJson_setsResponseFormatAndOmitsBlankSystem()
    {
        AiModelConfig config = config(baseUrl, "OpenAI");
        when(aiModelConfigMapper.selectDefaultAiModelConfig()).thenReturn(config);
        enqueue(200, "{\"choices\":[{\"message\":{\"content\":\"{\\\"a\\\":1}\"}}]}");

        assertEquals("{\"a\":1}", service.chatJson("", "抽取", "extract"));

        String body = bodies.get(0);
        assertTrue(body.contains("\"response_format\":{\"type\":\"json_object\"}"));
        assertFalse(body.contains("\"role\":\"system\""));
        verify(callLogRecorder).record(eq(AiModelCallLogRecorder.KIND_CHAT),
                eq("extract"), eq(config), any(ModelUsage.class), anyLong(), eq(1),
                eq(AiModelCallLog.RESULT_SUCCESS), isNull());
    }

    @Test
    void chat_contentArray_returnsJsonText()
    {
        AiModelConfig config = config(baseUrl, "OpenAI");
        when(aiModelConfigMapper.selectDefaultAiModelConfig()).thenReturn(config);
        enqueue(200, "{\"choices\":[{\"message\":{\"content\":[{\"type\":\"text\",\"text\":\"hi\"}]}}]}");

        assertEquals("[{\"type\":\"text\",\"text\":\"hi\"}]", service.chat("sys", "user"));
    }

    @Test
    void chat_outputTextFallback()
    {
        AiModelConfig config = config(baseUrl, "OpenAI");
        when(aiModelConfigMapper.selectDefaultAiModelConfig()).thenReturn(config);
        enqueue(200, "{\"output\":{\"text\":\"兜底文本\"}}");

        assertEquals("兜底文本", service.chat("sys", "user"));
    }

    @Test
    void chat_missingContent_throws()
    {
        AiModelConfig config = config(baseUrl, "OpenAI");
        when(aiModelConfigMapper.selectDefaultAiModelConfig()).thenReturn(config);
        enqueue(200, "{\"choices\":[]}");

        RuntimeException ex = assertThrows(RuntimeException.class, () -> service.chat("sys", "user"));
        assertTrue(ex.getMessage().contains("缺少choices"));
    }

    @Test
    void chat_claude_protocolAndTextMerge()
    {
        AiModelConfig config = config(baseUrl, "Claude");
        config.setApiKey("sk-ant");
        when(aiModelConfigMapper.selectDefaultAiModelConfig()).thenReturn(config);
        enqueue(200, "{\"content\":[{\"type\":\"text\",\"text\":\"A\"},"
                + "{\"type\":\"thinking\",\"text\":\"X\"},{\"type\":\"text\",\"text\":\"B\"}],"
                + "\"usage\":{\"input_tokens\":3,\"output_tokens\":4}}");

        assertEquals("AB", service.chat("system", "user"));

        assertEquals("/messages", paths.get(0));
        assertTrue(bodies.get(0).contains("\"system\":\"system\""));
        ArgumentCaptor<ModelUsage> usageCaptor = ArgumentCaptor.forClass(ModelUsage.class);
        verify(callLogRecorder).record(eq(AiModelCallLogRecorder.KIND_CHAT),
                anyString(), eq(config), usageCaptor.capture(), anyLong(), eq(1),
                eq(AiModelCallLog.RESULT_SUCCESS), isNull());
        assertEquals(3, usageCaptor.getValue().getPromptTokens());
        assertEquals(7, usageCaptor.getValue().getTotalTokens());
    }

    @Test
    void chat_encryptedKey_sentDecrypted()
    {
        AiModelConfig config = config(baseUrl, "OpenAI");
        config.setApiKey(SecretCryptoUtils.encrypt("sk-secret"));
        when(aiModelConfigMapper.selectDefaultAiModelConfig()).thenReturn(config);
        enqueue(200, "{\"choices\":[{\"message\":{\"content\":\"ok\"}}]}");

        service.chat("sys", "user");

        assertEquals("Bearer sk-secret", authHeaders.get(0));
    }

    @Test
    void chat_retryableError_retried()
    {
        AiModelConfig config = config(baseUrl, "OpenAI");
        config.setRetryCount(1);
        when(aiModelConfigMapper.selectDefaultAiModelConfig()).thenReturn(config);
        enqueue(500, "{\"error\":\"overloaded\"}");
        enqueue(200, "{\"choices\":[{\"message\":{\"content\":\"第二次成功\"}}]}");

        assertEquals("第二次成功", service.chat("sys", "user"));

        assertEquals(2, paths.size());
        verify(metrics).incrementAi("chat", "retry");
        verify(metrics).incrementAi("chat", "success");
        verify(callLogRecorder).record(anyString(), anyString(), eq(config),
                any(ModelUsage.class), anyLong(), eq(2), eq(AiModelCallLog.RESULT_SUCCESS), isNull());
    }

    @Test
    void chat_clientError_notRetried()
    {
        AiModelConfig config = config(baseUrl, "OpenAI");
        config.setRetryCount(2);
        when(aiModelConfigMapper.selectDefaultAiModelConfig()).thenReturn(config);
        enqueue(401, "unauthorized");

        assertThrows(RuntimeException.class, () -> service.chat("sys", "user"));

        assertEquals(1, paths.size());
        verify(metrics).incrementAi("chat", "fail");
        ArgumentCaptor<String> reason = ArgumentCaptor.forClass(String.class);
        verify(callLogRecorder).record(anyString(), anyString(), eq(config),
                any(ModelUsage.class), anyLong(), eq(1), eq(AiModelCallLog.RESULT_FAIL),
                reason.capture());
        assertTrue(reason.getValue().contains("401"));
        assertTrue(reason.getValue().contains("unauthorized"));
    }

    @Test
    void chat_apiUrlWithV1AndSlash_normalizedEndpoint()
    {
        AiModelConfig config = config(baseUrl + "/v1/", "OpenAI");
        when(aiModelConfigMapper.selectDefaultAiModelConfig()).thenReturn(config);
        enqueue(200, "{\"choices\":[{\"message\":{\"content\":\"ok\"}}]}");

        service.chat("", "user");

        assertEquals("/chat/completions", paths.get(0));
        assertFalse(bodies.get(0).contains("\"role\":\"system\""));
        assertFalse(bodies.get(0).contains("max_tokens"));
    }

    // ---------------- 舱壁 ----------------

    @Test
    void chat_bulkheadReject_throwsBusy()
    {
        AiModelConfigServiceImpl limited = newService(1);
        AiModelConfig config = config(baseUrl, "OpenAI");
        when(aiModelConfigMapper.selectDefaultAiModelConfig()).thenReturn(config);
        Semaphore semaphore = (Semaphore) ReflectionTestUtils.getField(limited, "inflightSemaphore");
        semaphore.acquireUninterruptibly();

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> limited.chat("sys", "user"));
        assertTrue(ex.getMessage().contains("调用AI模型失败"));
        assertTrue(ex.getMessage().contains("在途并发已达上限 1"));
        verify(metrics).incrementAi("chat", "reject");
        verify(callLogRecorder).record(anyString(), anyString(), eq(config),
                any(ModelUsage.class), anyLong(), eq(1), eq(AiModelCallLog.RESULT_REJECT),
                anyString());
        assertTrue(paths.isEmpty());
    }

    @Test
    void embedTexts_bulkheadReject_throwsBusy()
    {
        AiModelConfigServiceImpl limited = newService(1);
        Semaphore semaphore = (Semaphore) ReflectionTestUtils.getField(limited, "inflightSemaphore");
        semaphore.acquireUninterruptibly();
        ReflectionTestUtils.setField(limited, "embeddingModel", "bge");
        when(aiModelConfigMapper.selectDefaultAiModelConfig())
                .thenReturn(config(baseUrl, "OpenAI"));

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> limited.embedTexts(Collections.singletonList("文本")));
        assertTrue(ex.getMessage().contains("生成文本向量失败"));
        assertTrue(ex.getMessage().contains("embedding 服务繁忙"));
        verify(metrics).incrementAi("embed", "reject");
    }

    // ---------------- Embedding ----------------

    @Test
    void embedTexts_emptyInput_returnsEmpty()
    {
        assertTrue(service.embedTexts(null).isEmpty());
        assertTrue(service.embedTexts(Collections.emptyList()).isEmpty());
        verify(aiModelConfigMapper, never()).selectDefaultAiModelConfig();
    }

    @Test
    void embedTexts_noConfig_throws()
    {
        assertThrows(IllegalStateException.class,
                () -> service.embedTexts(Collections.singletonList("文本")));
    }

    @Test
    void embedTexts_dedicatedConfig_parsesVectors()
    {
        AiModelConfig embedding = config(baseUrl, "OpenAI");
        embedding.setConfigId(5L);
        embedding.setModelName("bge-small-zh");
        ReflectionTestUtils.setField(service, "embeddingConfigId", 5L);
        when(aiModelConfigMapper.selectAiModelConfigByConfigId(5L)).thenReturn(embedding);
        enqueue(200, "{\"data\":[{\"embedding\":[0.1,0.2]},{\"embedding\":[0.3]}],"
                + "\"usage\":{\"total_tokens\":7}}");

        List<float[]> vectors = service.embedTexts(Arrays.asList("甲", null));

        assertEquals(2, vectors.size());
        assertEquals(2, vectors.get(0).length);
        assertEquals(0.3f, vectors.get(1)[0], 1e-6);
        assertEquals("/embeddings", paths.get(0));
        assertTrue(bodies.get(0).contains("\"model\":\"bge-small-zh\""));
        assertTrue(bodies.get(0).contains("\"input\":[\"甲\",\"\"]"));
        verify(metrics).incrementAi("embed", "success");
        ArgumentCaptor<ModelUsage> usageCaptor = ArgumentCaptor.forClass(ModelUsage.class);
        ArgumentCaptor<AiModelConfig> configCaptor = ArgumentCaptor.forClass(AiModelConfig.class);
        verify(callLogRecorder).record(eq(AiModelCallLogRecorder.KIND_EMBED),
                eq(AiModelCallLogRecorder.SCENE_RAG), configCaptor.capture(), usageCaptor.capture(),
                anyLong(), eq(1), eq(AiModelCallLog.RESULT_SUCCESS), isNull());
        assertEquals(Long.valueOf(5L), configCaptor.getValue().getConfigId());
        assertEquals(7, usageCaptor.getValue().getPromptTokens());
    }

    @Test
    void embedTexts_fallbackToDefaultModelName()
    {
        AiModelConfig base = config(baseUrl, "OpenAI");
        ReflectionTestUtils.setField(service, "embeddingModel", "text-embedding-3-small");
        when(aiModelConfigMapper.selectDefaultAiModelConfig()).thenReturn(base);
        enqueue(200, "{\"data\":[{\"embedding\":[1.0]}]}");

        List<float[]> vectors = service.embedTexts(Collections.singletonList("文本"));

        assertEquals(1, vectors.size());
        assertTrue(bodies.get(0).contains("\"model\":\"text-embedding-3-small\""));
    }

    @Test
    void embedTexts_vectorCountMismatch_throws()
    {
        AiModelConfig embedding = config(baseUrl, "OpenAI");
        ReflectionTestUtils.setField(service, "embeddingConfigId", 5L);
        when(aiModelConfigMapper.selectAiModelConfigByConfigId(5L)).thenReturn(embedding);
        enqueue(200, "{\"data\":[{\"embedding\":[0.1]}]}");

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> service.embedTexts(Arrays.asList("甲", "乙")));
        assertTrue(ex.getMessage().contains("数量与入参不一致"));
        verify(metrics).incrementAi("embed", "fail");
        verify(callLogRecorder).record(eq(AiModelCallLogRecorder.KIND_EMBED), anyString(),
                any(), any(ModelUsage.class), anyLong(), eq(1), eq(AiModelCallLog.RESULT_FAIL),
                anyString());
    }

    @Test
    void embedTexts_missingVector_throws()
    {
        AiModelConfig embedding = config(baseUrl, "OpenAI");
        ReflectionTestUtils.setField(service, "embeddingConfigId", 5L);
        when(aiModelConfigMapper.selectAiModelConfigByConfigId(5L)).thenReturn(embedding);
        enqueue(200, "{\"data\":[{\"foo\":1}]}");

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> service.embedTexts(Collections.singletonList("甲")));
        assertTrue(ex.getMessage().contains("缺少 embedding 向量字段"));
    }

    // ---------------- 日志容错 ----------------

    @Test
    void recordCallLog_recorderFailure_swallowed()
    {
        AiModelConfig config = config(baseUrl, "OpenAI");
        when(aiModelConfigMapper.selectDefaultAiModelConfig()).thenReturn(config);
        enqueue(200, "{\"choices\":[{\"message\":{\"content\":\"ok\"}}]}");
        doThrow(new RuntimeException("log down")).when(callLogRecorder)
                .record(anyString(), anyString(), any(), any(), anyLong(), eq(1), anyString(), any());

        assertEquals("ok", service.chat("sys", "user"));
    }

    // ---------------- 辅助 ----------------

    private AiModelConfigServiceImpl newService(int maxInflight)
    {
        AiModelConfigServiceImpl instance = new AiModelConfigServiceImpl();
        ReflectionTestUtils.setField(instance, "aiModelConfigMapper", aiModelConfigMapper);
        ReflectionTestUtils.setField(instance, "metrics", metrics);
        ReflectionTestUtils.setField(instance, "callLogRecorder", callLogRecorder);
        ReflectionTestUtils.setField(instance, "maxInflight", maxInflight);
        ReflectionTestUtils.setField(instance, "acquireTimeoutMs", 50L);
        ReflectionTestUtils.setField(instance, "maxIdleConnections", 32);
        ReflectionTestUtils.setField(instance, "keepAliveSeconds", 300L);
        ReflectionTestUtils.setField(instance, "connectTimeoutMs", 3000);
        ReflectionTestUtils.setField(instance, "embeddingConfigId", 0L);
        ReflectionTestUtils.setField(instance, "embeddingModel", "");
        instance.initBulkhead();
        return instance;
    }

    private AiModelConfig config(String apiUrl, String modelType)
    {
        AiModelConfig config = new AiModelConfig();
        config.setConfigId(1L);
        config.setConfigName("默认模型");
        config.setModelType(modelType);
        config.setModelName("m1");
        config.setApiUrl(apiUrl);
        config.setTimeout(5);
        config.setRetryCount(0);
        config.setIsDefault("1");
        config.setStatus("0");
        return config;
    }

    private void enqueue(int status, String body)
    {
        synchronized (stubs)
        {
            stubs.add(new Stub(status, body));
        }
    }

    private static final class Stub
    {
        private final int status;
        private final String body;

        private Stub(int status, String body)
        {
            this.status = status;
            this.body = body;
        }
    }
}
