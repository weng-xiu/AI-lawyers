package ai.lawyers.system.domain.lawyers.skill;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Date;
import org.junit.jupiter.api.Test;

/**
 * 排队/分配流水实体测试：属性读写、默认值与字符串输出。
 *
 * @author ai-lawyers
 */
class AiCallQueueTest
{
    @Test
    void 属性读写()
    {
        Date enqueueTime = new Date();
        Date dequeueTime = new Date();
        Date answerTime = new Date();
        Date abandonTime = new Date();
        AiCallQueue queue = new AiCallQueue();
        queue.setQueueId(1L);
        queue.setSessionId("sess-001");
        queue.setRecordId(2L);
        queue.setCallerNumber("13800000000");
        queue.setGroupId(3L);
        queue.setEnqueueTime(enqueueTime);
        queue.setDequeueTime(dequeueTime);
        queue.setAgentId(4L);
        queue.setWaitDuration(30);
        queue.setQueueStatus("1");
        queue.setStrategyUsed("ROUND_ROBIN");
        queue.setPriority(5);
        queue.setAnswerTime(answerTime);
        queue.setRingDuration(8);
        queue.setAbandonTime(abandonTime);
        queue.setGroupName("法律咨询组");
        queue.setAgentName("张三");

        assertEquals(1L, queue.getQueueId());
        assertEquals("sess-001", queue.getSessionId());
        assertEquals(2L, queue.getRecordId());
        assertEquals("13800000000", queue.getCallerNumber());
        assertEquals(3L, queue.getGroupId());
        assertEquals(enqueueTime, queue.getEnqueueTime());
        assertEquals(dequeueTime, queue.getDequeueTime());
        assertEquals(4L, queue.getAgentId());
        assertEquals(30, queue.getWaitDuration());
        assertEquals("1", queue.getQueueStatus());
        assertEquals("ROUND_ROBIN", queue.getStrategyUsed());
        assertEquals(5, queue.getPriority());
        assertEquals(answerTime, queue.getAnswerTime());
        assertEquals(8, queue.getRingDuration());
        assertEquals(abandonTime, queue.getAbandonTime());
        assertEquals("法律咨询组", queue.getGroupName());
        assertEquals("张三", queue.getAgentName());
    }

    @Test
    void 默认值为空()
    {
        AiCallQueue queue = new AiCallQueue();

        assertNull(queue.getQueueId());
        assertNull(queue.getSessionId());
        assertNull(queue.getQueueStatus());
        assertNull(queue.getEnqueueTime());
        assertNull(queue.getAgentId());
        assertNull(queue.getWaitDuration());
    }

    @Test
    void 字符串输出包含关键字段()
    {
        AiCallQueue queue = new AiCallQueue();
        queue.setQueueId(1L);
        queue.setCallerNumber("13800000000");
        queue.setQueueStatus("0");
        queue.setWaitDuration(30);

        String text = queue.toString();

        assertTrue(text.contains("13800000000"));
        assertTrue(text.contains("queueStatus"));
        assertTrue(text.contains("waitDuration"));
    }
}
