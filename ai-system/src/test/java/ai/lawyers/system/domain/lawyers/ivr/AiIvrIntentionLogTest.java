package ai.lawyers.system.domain.lawyers.ivr;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

/**
 * IVR意图识别日志实体测试：属性读写与默认值。
 *
 * @author ai-lawyers
 */
class AiIvrIntentionLogTest
{
    @Test
    void 属性读写()
    {
        AiIvrIntentionLog log = new AiIvrIntentionLog();
        log.setLogId(1L);
        log.setRecordId(2L);
        log.setSessionId("sess-001");
        log.setFlowId(3L);
        log.setNodeId(4L);
        log.setInputText("我要咨询离婚");
        log.setMatchedIntention("DIVORCE");
        log.setMatchedIntentionName("离婚咨询");
        log.setConfidence(new BigDecimal("0.92"));
        log.setMatchMethod("KEYWORD");
        log.setAllResults("[{\"code\":\"DIVORCE\",\"score\":0.92}]");

        assertEquals(1L, log.getLogId());
        assertEquals(2L, log.getRecordId());
        assertEquals("sess-001", log.getSessionId());
        assertEquals(3L, log.getFlowId());
        assertEquals(4L, log.getNodeId());
        assertEquals("我要咨询离婚", log.getInputText());
        assertEquals("DIVORCE", log.getMatchedIntention());
        assertEquals("离婚咨询", log.getMatchedIntentionName());
        assertEquals(new BigDecimal("0.92"), log.getConfidence());
        assertEquals("KEYWORD", log.getMatchMethod());
        assertEquals("[{\"code\":\"DIVORCE\",\"score\":0.92}]", log.getAllResults());
    }

    @Test
    void 默认值为空()
    {
        AiIvrIntentionLog log = new AiIvrIntentionLog();

        assertNull(log.getLogId());
        assertNull(log.getSessionId());
        assertNull(log.getInputText());
        assertNull(log.getMatchedIntention());
        assertNull(log.getConfidence());
        assertNull(log.getMatchMethod());
    }
}
