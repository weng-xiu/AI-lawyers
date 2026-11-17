package ai.lawyers.system.domain.lawyers.trunk;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * 外呼请求测试：默认参数与属性读写。
 *
 * @author ai-lawyers
 */
class DialRequestTest
{
    @Test
    void 默认参数()
    {
        DialRequest request = new DialRequest();

        assertEquals(100, request.getPriority());
        assertEquals(45, request.getRingTimeout());
        assertTrue(request.isAllowFailover());
        assertEquals(2, request.getMaxFailover());
        assertTrue(request.isEnableRecord());
        assertEquals("BRIDGE_AGENT", request.getAnswerAction());
        assertNull(request.getCalleeNumber());
    }

    @Test
    void 两参构造器()
    {
        DialRequest request = new DialRequest("13800138000", 9L);

        assertEquals("13800138000", request.getCalleeNumber());
        assertEquals(9L, request.getAgentId());
    }

    @Test
    void 属性读写()
    {
        DialRequest request = new DialRequest();
        request.setCalleeNumber("13900139000");
        request.setCallerNumber("01088886666");
        request.setAgentId(12L);
        request.setAgentExtension("1001");
        request.setRecordId(88L);
        request.setTaskId(66L);
        request.setCalleeId(77L);
        request.setAssignTrunkCode("TRUNK-A");
        request.setAssignCarrier("MOBILE");
        request.setPriority(10);
        request.setRingTimeout(30);
        request.setAllowFailover(false);
        request.setMaxFailover(1);
        request.setEnableRecord(false);
        request.setAnswerAction("IVR");
        request.setIvrFlowId(5L);
        request.setCreateBy("admin");
        request.setRemark("回访外呼");

        assertEquals("13900139000", request.getCalleeNumber());
        assertEquals("01088886666", request.getCallerNumber());
        assertEquals(12L, request.getAgentId());
        assertEquals("1001", request.getAgentExtension());
        assertEquals(88L, request.getRecordId());
        assertEquals(66L, request.getTaskId());
        assertEquals(77L, request.getCalleeId());
        assertEquals("TRUNK-A", request.getAssignTrunkCode());
        assertEquals("MOBILE", request.getAssignCarrier());
        assertEquals(10, request.getPriority());
        assertEquals(30, request.getRingTimeout());
        assertFalse(request.isAllowFailover());
        assertEquals(1, request.getMaxFailover());
        assertFalse(request.isEnableRecord());
        assertEquals("IVR", request.getAnswerAction());
        assertEquals(5L, request.getIvrFlowId());
        assertEquals("admin", request.getCreateBy());
        assertEquals("回访外呼", request.getRemark());
    }
}
