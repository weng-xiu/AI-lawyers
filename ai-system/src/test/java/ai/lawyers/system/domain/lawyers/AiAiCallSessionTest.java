package ai.lawyers.system.domain.lawyers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Date;
import org.junit.jupiter.api.Test;

/**
 * AI辅助会话实体测试：属性读写、默认值与字符串输出。
 *
 * @author ai-lawyers
 */
class AiAiCallSessionTest
{
    @Test
    void 属性读写()
    {
        Date startTime = new Date();
        Date endTime = new Date();
        AiAiCallSession session = new AiAiCallSession();
        session.setSessionId(1L);
        session.setRecordId(2L);
        session.setAgentId(3L);
        session.setCallerPhone("13800000000");
        session.setCallerName("张三");
        session.setSessionStatus("3");
        session.setIntentCategory("LABOR_DISPUTE");
        session.setRecommendLaws("[{\"law\":\"劳动合同法\"}]");
        session.setRecommendScripts("[{\"script\":\"建议先协商\"}]");
        session.setCallSummary("客户咨询劳动仲裁流程");
        session.setStartTime(startTime);
        session.setEndTime(endTime);

        assertEquals(1L, session.getSessionId());
        assertEquals(2L, session.getRecordId());
        assertEquals(3L, session.getAgentId());
        assertEquals("13800000000", session.getCallerPhone());
        assertEquals("张三", session.getCallerName());
        assertEquals("3", session.getSessionStatus());
        assertEquals("LABOR_DISPUTE", session.getIntentCategory());
        assertEquals("[{\"law\":\"劳动合同法\"}]", session.getRecommendLaws());
        assertEquals("[{\"script\":\"建议先协商\"}]", session.getRecommendScripts());
        assertEquals("客户咨询劳动仲裁流程", session.getCallSummary());
        assertEquals(startTime, session.getStartTime());
        assertEquals(endTime, session.getEndTime());
    }

    @Test
    void 默认值为空()
    {
        AiAiCallSession session = new AiAiCallSession();

        assertNull(session.getSessionId());
        assertNull(session.getRecordId());
        assertNull(session.getSessionStatus());
        assertNull(session.getIntentCategory());
        assertNull(session.getCallSummary());
        assertNull(session.getStartTime());
    }

    @Test
    void 字符串输出包含关键字段()
    {
        AiAiCallSession session = new AiAiCallSession();
        session.setSessionId(1L);
        session.setCallerPhone("13800000000");
        session.setSessionStatus("1");
        session.setIntentCategory("LABOR_DISPUTE");

        String text = session.toString();

        assertTrue(text.contains("13800000000"));
        assertTrue(text.contains("LABOR_DISPUTE"));
        assertTrue(text.contains("sessionStatus"));
    }
}
