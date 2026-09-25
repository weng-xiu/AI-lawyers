package ai.lawyers.system.service.lawyers.stat;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import ai.lawyers.system.domain.lawyers.AiModelConfig;
import ai.lawyers.system.domain.lawyers.stat.AiModelCallLog;
import ai.lawyers.system.mapper.lawyers.stat.AiModelCallLogMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * P3-E5 大模型调用埋点测试：Token 用量解析、费用折算、记录器异步落库/开关/字段快照。
 *
 * @author ai-lawyers
 */
class AiModelCallLogRecorderTest
{
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private AiModelCallLogMapper mapper;
    private AiModelCallLogRecorder recorder;

    @BeforeEach
    void setUp() throws Exception
    {
        mapper = mock(AiModelCallLogMapper.class);
        recorder = new AiModelCallLogRecorder();
        setField(recorder, "mapper", mapper);
        setField(recorder, "enabled", true);
        recorder.init();
    }

    // ================= ModelUsage 解析 =================

    @Test
    void parseOpenAi_normal() throws Exception
    {
        JsonNode root = MAPPER.readTree(
                "{\"usage\":{\"prompt_tokens\":120,\"completion_tokens\":80,\"total_tokens\":200}}");
        ModelUsage u = ModelUsage.parseOpenAi(root);
        assertThat(u.getPromptTokens()).isEqualTo(120);
        assertThat(u.getCompletionTokens()).isEqualTo(80);
        assertThat(u.getTotalTokens()).isEqualTo(200);
    }

    @Test
    void parseOpenAi_missingUsage_returnsZero() throws Exception
    {
        assertThat(ModelUsage.parseOpenAi(MAPPER.readTree("{\"choices\":[]}"))).isSameAs(ModelUsage.ZERO);
        assertThat(ModelUsage.parseOpenAi(null)).isSameAs(ModelUsage.ZERO);
    }

    @Test
    void parseOpenAi_totalMissing_sumsPromptAndCompletion() throws Exception
    {
        JsonNode root = MAPPER.readTree(
                "{\"usage\":{\"prompt_tokens\":12,\"completion_tokens\":8}}");
        ModelUsage u = ModelUsage.parseOpenAi(root);
        assertThat(u.getTotalTokens()).isEqualTo(20);
    }

    @Test
    void parseOpenAi_negativeClampedToZero() throws Exception
    {
        JsonNode root = MAPPER.readTree(
                "{\"usage\":{\"prompt_tokens\":-5,\"completion_tokens\":-1,\"total_tokens\":-9}}");
        ModelUsage u = ModelUsage.parseOpenAi(root);
        assertThat(u.getPromptTokens()).isZero();
        assertThat(u.getCompletionTokens()).isZero();
        assertThat(u.getTotalTokens()).isZero();
    }

    @Test
    void parseClaude_inputOutputAndTotal() throws Exception
    {
        JsonNode root = MAPPER.readTree(
                "{\"usage\":{\"input_tokens\":300,\"output_tokens\":150}}");
        ModelUsage u = ModelUsage.parseClaude(root);
        assertThat(u.getPromptTokens()).isEqualTo(300);
        assertThat(u.getCompletionTokens()).isEqualTo(150);
        assertThat(u.getTotalTokens()).isEqualTo(450);
    }

    // ================= calcCost 费用折算 =================

    @Test
    void calcCost_nullPrice_isZero()
    {
        assertThat(AiModelCallLogRecorder.calcCost(1000, 500, null, null))
                .isEqualByComparingTo("0.000000");
    }

    @Test
    void calcCost_perThousandTokens()
    {
        // 1000 输入 Token × 0.01 元/千 = 0.01 元
        assertThat(AiModelCallLogRecorder.calcCost(1000, 0, new BigDecimal("0.01"), null))
                .isEqualByComparingTo("0.010000");
        // 500 输出 Token × 0.02 元/千 = 0.01 元；合计 0.02
        assertThat(AiModelCallLogRecorder.calcCost(1000, 500,
                new BigDecimal("0.01"), new BigDecimal("0.02")))
                .isEqualByComparingTo("0.020000");
    }

    @Test
    void calcCost_scale6HalfUp()
    {
        // 1 Token × 0.1234567 元/千 = 0.0001234567 → 6 位 HALF_UP = 0.000123
        assertThat(AiModelCallLogRecorder.calcCost(1, 0, new BigDecimal("0.1234567"), null))
                .isEqualByComparingTo("0.000123");
        // 1 Token × 0.000005 元/千 = 0.000000005 → 6 位 HALF_UP = 0.000001（入）
        assertThat(AiModelCallLogRecorder.calcCost(200, 0, new BigDecimal("0.000005"), null))
                .isEqualByComparingTo("0.000001");
    }

    // ================= Recorder 行为 =================

    @Test
    void record_disabled_neverInsert() throws Exception
    {
        AiModelCallLogRecorder r = new AiModelCallLogRecorder();
        AiModelCallLogMapper m = mock(AiModelCallLogMapper.class);
        setField(r, "mapper", m);
        setField(r, "enabled", false);
        // 未调 init（executor 为 null）也不应抛异常：开关短路在前
        r.record(AiModelCallLogRecorder.KIND_CHAT, AiModelCallLogRecorder.SCENE_AGENT,
                null, ModelUsage.ZERO, 10L, 1, AiModelCallLog.RESULT_SUCCESS, null);
        verify(m, never()).insertAiModelCallLog(any());
    }

    @Test
    void record_withConfig_snapshotAndCost() throws Exception
    {
        AiModelConfig config = new AiModelConfig();
        config.setConfigId(7L);
        config.setConfigName("DeepSeek 生产配置");
        config.setModelType("OpenAI");
        config.setModelName("deepseek-chat");
        config.setInputPrice(new BigDecimal("0.001"));
        config.setOutputPrice(new BigDecimal("0.002"));

        recorder.record(AiModelCallLogRecorder.KIND_CHAT, AiModelCallLogRecorder.SCENE_SUMMARY,
                config, new ModelUsage(1000, 500, 1500),
                320L, 2, AiModelCallLog.RESULT_SUCCESS, null);
        recorder.shutdown();

        ArgumentCaptor<AiModelCallLog> captor = ArgumentCaptor.forClass(AiModelCallLog.class);
        verify(mapper).insertAiModelCallLog(captor.capture());
        AiModelCallLog log = captor.getValue();
        assertThat(log.getKind()).isEqualTo("chat");
        assertThat(log.getScene()).isEqualTo("summary");
        assertThat(log.getConfigId()).isEqualTo(7L);
        assertThat(log.getConfigName()).isEqualTo("DeepSeek 生产配置");
        assertThat(log.getModelName()).isEqualTo("deepseek-chat");
        assertThat(log.getPromptTokens()).isEqualTo(1000);
        assertThat(log.getCompletionTokens()).isEqualTo(500);
        assertThat(log.getTotalTokens()).isEqualTo(1500);
        // 0.001*1 + 0.002*0.5 = 0.002
        assertThat(log.getCostAmount()).isEqualByComparingTo("0.002000");
        assertThat(log.getInputPrice()).isEqualByComparingTo("0.001");
        assertThat(log.getElapsedMs()).isEqualTo(320);
        assertThat(log.getAttempts()).isEqualTo(2);
        assertThat(log.getResult()).isEqualTo(AiModelCallLog.RESULT_SUCCESS);
        assertThat(log.getCallTime()).isNotNull();
    }

    @Test
    void record_withoutConfig_rerankSnapshot() throws Exception
    {
        recorder.recordWithoutConfig(AiModelCallLogRecorder.KIND_RERANK,
                AiModelCallLogRecorder.SCENE_RAG, "rerank", "bge-reranker-base",
                ModelUsage.ZERO, 55L, 1, AiModelCallLog.RESULT_SUCCESS, null);
        recorder.shutdown();

        ArgumentCaptor<AiModelCallLog> captor = ArgumentCaptor.forClass(AiModelCallLog.class);
        verify(mapper).insertAiModelCallLog(captor.capture());
        AiModelCallLog log = captor.getValue();
        assertThat(log.getKind()).isEqualTo("rerank");
        assertThat(log.getScene()).isEqualTo("rag");
        assertThat(log.getConfigId()).isNull();
        assertThat(log.getModelType()).isEqualTo("rerank");
        assertThat(log.getModelName()).isEqualTo("bge-reranker-base");
        assertThat(log.getTotalTokens()).isZero();
        assertThat(log.getCostAmount()).isEqualByComparingTo("0.000000");
        assertThat(log.getElapsedMs()).isEqualTo(55);
    }

    @Test
    void record_emptyKindAndResult_useDefaults() throws Exception
    {
        recorder.record("", "", null, ModelUsage.ZERO, 1L, 0, "", null);
        recorder.shutdown();

        ArgumentCaptor<AiModelCallLog> captor = ArgumentCaptor.forClass(AiModelCallLog.class);
        verify(mapper).insertAiModelCallLog(captor.capture());
        AiModelCallLog log = captor.getValue();
        assertThat(log.getKind()).isEqualTo("chat");
        assertThat(log.getScene()).isEqualTo("other");
        assertThat(log.getAttempts()).isEqualTo(1);
        assertThat(log.getResult()).isEqualTo(AiModelCallLog.RESULT_FAIL);
    }

    @Test
    void record_longFailReason_truncatedTo500() throws Exception
    {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 600; i++)
        {
            sb.append('x');
        }
        recorder.record(AiModelCallLogRecorder.KIND_CHAT, AiModelCallLogRecorder.SCENE_OTHER,
                null, ModelUsage.ZERO, 1L, 1, AiModelCallLog.RESULT_FAIL, sb.toString());
        recorder.shutdown();

        ArgumentCaptor<AiModelCallLog> captor = ArgumentCaptor.forClass(AiModelCallLog.class);
        verify(mapper).insertAiModelCallLog(captor.capture());
        assertThat(captor.getValue().getFailReason()).hasSize(500);
    }

    private static void setField(Object obj, String name, Object value) throws Exception
    {
        Field f = obj.getClass().getDeclaredField(name);
        f.setAccessible(true);
        f.set(obj, value);
    }
}
