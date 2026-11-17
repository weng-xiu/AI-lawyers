package ai.lawyers.system.domain.lawyers.trunk;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * 外呼结果测试：成功/失败工厂与属性读写。
 *
 * @author ai-lawyers
 */
class DialResultTest
{
    @Test
    void 成功工厂填充呼叫标识与时间()
    {
        DialResult result = DialResult.ok("uuid-001");

        assertTrue(result.isSuccess());
        assertEquals("uuid-001", result.getCallUuid());
        assertNotNull(result.getDialTime());
        assertNull(result.getErrorCode());
    }

    @Test
    void 失败工厂填充错误信息()
    {
        DialResult result = DialResult.fail("NO_TRUNK", "无可用线路");

        assertFalse(result.isSuccess());
        assertEquals("NO_TRUNK", result.getErrorCode());
        assertEquals("无可用线路", result.getMessage());
        assertNotNull(result.getDialTime());
        assertNull(result.getCallUuid());
    }

    @Test
    void 属性读写()
    {
        DialResult result = new DialResult();
        result.setSuccess(true);
        result.setCallUuid("uuid-002");
        result.setDialStatus("ANSWERED");
        result.setTrunkId(3L);
        result.setTrunkCode("TRUNK-A");
        result.setTrunkCarrier("UNICOM");
        result.setCalleeCarrier("MOBILE");
        result.setRouteStrategy("PREFIX");
        result.setFailoverCount(1);
        result.setFailoverTrunks("TRUNK-B");
        result.setQueueWaitMs(120L);
        result.setSipCode(200);
        result.setHangupCause("NORMAL_CLEARING");
        result.setLogId(9L);

        assertTrue(result.isSuccess());
        assertEquals("uuid-002", result.getCallUuid());
        assertEquals("ANSWERED", result.getDialStatus());
        assertEquals(3L, result.getTrunkId());
        assertEquals("TRUNK-A", result.getTrunkCode());
        assertEquals("UNICOM", result.getTrunkCarrier());
        assertEquals("MOBILE", result.getCalleeCarrier());
        assertEquals("PREFIX", result.getRouteStrategy());
        assertEquals(1, result.getFailoverCount());
        assertEquals("TRUNK-B", result.getFailoverTrunks());
        assertEquals(120L, result.getQueueWaitMs());
        assertEquals(200, result.getSipCode());
        assertEquals("NORMAL_CLEARING", result.getHangupCause());
        assertEquals(9L, result.getLogId());
    }

    @Test
    void 默认值()
    {
        DialResult result = new DialResult();

        assertFalse(result.isSuccess());
        assertEquals(0, result.getFailoverCount());
        assertEquals(0L, result.getQueueWaitMs());
        assertNull(result.getDialTime());
    }
}
