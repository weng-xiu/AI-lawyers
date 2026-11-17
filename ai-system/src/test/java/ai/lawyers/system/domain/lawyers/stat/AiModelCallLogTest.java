package ai.lawyers.system.domain.lawyers.stat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.math.BigDecimal;
import java.util.Date;
import org.junit.jupiter.api.Test;

/**
 * 大模型调用明细日志实体测试：常量、属性读写与默认值。
 *
 * @author ai-lawyers
 */
class AiModelCallLogTest
{
    @Test
    void 结果常量()
    {
        assertEquals("1", AiModelCallLog.RESULT_SUCCESS);
        assertEquals("0", AiModelCallLog.RESULT_FAIL);
        assertEquals("2", AiModelCallLog.RESULT_REJECT);
    }

    @Test
    void 属性读写()
    {
        Date callTime = new Date();
        AiModelCallLog log = new AiModelCallLog();
        log.setLogId(1L);
        log.setCallTime(callTime);
        log.setKind("chat");
        log.setScene("intention");
        log.setConfigId(2L);
        log.setConfigName("主模型配置");
        log.setModelType("LLM");
        log.setModelName("doubao-pro");
        log.setPromptTokens(100);
        log.setCompletionTokens(50);
        log.setTotalTokens(150);
        log.setInputPrice(new BigDecimal("0.0008"));
        log.setOutputPrice(new BigDecimal("0.0020"));
        log.setCostAmount(new BigDecimal("0.00018"));
        log.setElapsedMs(800);
        log.setAttempts(2);
        log.setResult(AiModelCallLog.RESULT_SUCCESS);
        log.setFailReason("无");
        log.setTraceId("trace-001");

        assertEquals(1L, log.getLogId());
        assertEquals(callTime, log.getCallTime());
        assertEquals("chat", log.getKind());
        assertEquals("intention", log.getScene());
        assertEquals(2L, log.getConfigId());
        assertEquals("主模型配置", log.getConfigName());
        assertEquals("LLM", log.getModelType());
        assertEquals("doubao-pro", log.getModelName());
        assertEquals(100, log.getPromptTokens());
        assertEquals(50, log.getCompletionTokens());
        assertEquals(150, log.getTotalTokens());
        assertEquals(new BigDecimal("0.0008"), log.getInputPrice());
        assertEquals(new BigDecimal("0.0020"), log.getOutputPrice());
        assertEquals(new BigDecimal("0.00018"), log.getCostAmount());
        assertEquals(800, log.getElapsedMs());
        assertEquals(2, log.getAttempts());
        assertEquals(AiModelCallLog.RESULT_SUCCESS, log.getResult());
        assertEquals("无", log.getFailReason());
        assertEquals("trace-001", log.getTraceId());
    }

    @Test
    void 默认值为空()
    {
        AiModelCallLog log = new AiModelCallLog();

        assertNull(log.getLogId());
        assertNull(log.getCallTime());
        assertNull(log.getKind());
        assertNull(log.getTotalTokens());
        assertNull(log.getCostAmount());
        assertNull(log.getResult());
    }
}
