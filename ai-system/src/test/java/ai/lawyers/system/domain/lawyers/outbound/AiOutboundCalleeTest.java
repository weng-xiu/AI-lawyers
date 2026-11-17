package ai.lawyers.system.domain.lawyers.outbound;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Date;
import org.junit.jupiter.api.Test;

/**
 * 外呼被叫实体测试：属性读写、默认值与字符串输出。
 *
 * @author ai-lawyers
 */
class AiOutboundCalleeTest
{
    @Test
    void 属性读写()
    {
        Date callTime = new Date();
        Date lastRetryTime = new Date();
        Date nextRetryTime = new Date();
        AiOutboundCallee callee = new AiOutboundCallee();
        callee.setCalleeId(1L);
        callee.setTaskId(2L);
        callee.setCalleeNumber("13800000000");
        callee.setCalleeName("张三");
        callee.setCalleeGender("0");
        callee.setCalleeAge(30);
        callee.setCalleeAddress("北京市朝阳区");
        callee.setCalleeParams("{\"source\":\"crm\"}");
        callee.setCallStatus("0");
        callee.setCallTime(callTime);
        callee.setCallDuration(60);
        callee.setRecordId(3L);
        callee.setAgentId(4L);
        callee.setRetryTimes(1);
        callee.setLastRetryTime(lastRetryTime);
        callee.setNextRetryTime(nextRetryTime);
        callee.setFailReason("用户忙");

        assertEquals(1L, callee.getCalleeId());
        assertEquals(2L, callee.getTaskId());
        assertEquals("13800000000", callee.getCalleeNumber());
        assertEquals("张三", callee.getCalleeName());
        assertEquals("0", callee.getCalleeGender());
        assertEquals(30, callee.getCalleeAge());
        assertEquals("北京市朝阳区", callee.getCalleeAddress());
        assertEquals("{\"source\":\"crm\"}", callee.getCalleeParams());
        assertEquals("0", callee.getCallStatus());
        assertEquals(callTime, callee.getCallTime());
        assertEquals(60, callee.getCallDuration());
        assertEquals(3L, callee.getRecordId());
        assertEquals(4L, callee.getAgentId());
        assertEquals(1, callee.getRetryTimes());
        assertEquals(lastRetryTime, callee.getLastRetryTime());
        assertEquals(nextRetryTime, callee.getNextRetryTime());
        assertEquals("用户忙", callee.getFailReason());
    }

    @Test
    void 默认值为空()
    {
        AiOutboundCallee callee = new AiOutboundCallee();

        assertNull(callee.getCalleeId());
        assertNull(callee.getTaskId());
        assertNull(callee.getCalleeNumber());
        assertNull(callee.getCallStatus());
        assertNull(callee.getRetryTimes());
        assertNull(callee.getNextRetryTime());
    }

    @Test
    void 字符串输出包含关键字段()
    {
        AiOutboundCallee callee = new AiOutboundCallee();
        callee.setCalleeId(1L);
        callee.setCalleeNumber("13800000000");
        callee.setCalleeName("张三");
        callee.setCallStatus("2");

        String text = callee.toString();

        assertTrue(text.contains("13800000000"));
        assertTrue(text.contains("张三"));
        assertTrue(text.contains("callStatus"));
    }
}
