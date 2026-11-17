package ai.lawyers.system.domain.lawyers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Date;
import org.junit.jupiter.api.Test;

/**
 * 客户回访实体测试：属性读写、默认值与字符串输出。
 *
 * @author ai-lawyers
 */
class AiCallbackTest
{
    @Test
    void 属性读写()
    {
        Date visitTime = new Date();
        AiCallback callback = new AiCallback();
        callback.setCallbackId(1L);
        callback.setLedgerId(2L);
        callback.setCallerNumber("13400000000");
        callback.setCallerName("赵先生");
        callback.setVisitTime(visitTime);
        callback.setVisitBy("客服02");
        callback.setVisitById(9L);
        callback.setSatisfaction("1");
        callback.setVisitOpinion("服务态度很好");
        callback.setVisitResult("已解决");
        callback.setStatus("1");

        assertEquals(1L, callback.getCallbackId());
        assertEquals(2L, callback.getLedgerId());
        assertEquals("13400000000", callback.getCallerNumber());
        assertEquals("赵先生", callback.getCallerName());
        assertEquals(visitTime, callback.getVisitTime());
        assertEquals("客服02", callback.getVisitBy());
        assertEquals(9L, callback.getVisitById());
        assertEquals("1", callback.getSatisfaction());
        assertEquals("服务态度很好", callback.getVisitOpinion());
        assertEquals("已解决", callback.getVisitResult());
        assertEquals("1", callback.getStatus());
    }

    @Test
    void 默认值为空()
    {
        AiCallback callback = new AiCallback();

        assertNull(callback.getCallbackId());
        assertNull(callback.getLedgerId());
        assertNull(callback.getCallerNumber());
        assertNull(callback.getCallerName());
        assertNull(callback.getVisitTime());
        assertNull(callback.getVisitBy());
        assertNull(callback.getVisitById());
        assertNull(callback.getSatisfaction());
        assertNull(callback.getVisitOpinion());
        assertNull(callback.getVisitResult());
        assertNull(callback.getStatus());
    }

    @Test
    void 字符串输出包含来电号码与回访结果()
    {
        AiCallback callback = new AiCallback();
        callback.setCallerNumber("13100000000");
        callback.setVisitResult("客户已满意");

        String text = callback.toString();
        assertTrue(text.contains("13100000000"));
        assertTrue(text.contains("客户已满意"));
    }
}
