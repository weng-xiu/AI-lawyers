package ai.lawyers.system.domain.lawyers.outbound;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Date;
import org.junit.jupiter.api.Test;

/**
 * 外呼任务实体测试：属性读写、默认值与字符串输出。
 *
 * @author ai-lawyers
 */
class AiOutboundTaskTest
{
    @Test
    void 属性读写()
    {
        Date startTime = new Date();
        Date endTime = new Date();
        AiOutboundTask task = new AiOutboundTask();
        task.setTaskId(1L);
        task.setTaskName("批量回访");
        task.setTaskNo("OT20260101001");
        task.setTaskType("2");
        task.setCallerNumber("01000000000");
        task.setIvrFlowId(2L);
        task.setIvrFlowName("回访流程");
        task.setStartTime(startTime);
        task.setEndTime(endTime);
        task.setTotalCount(100);
        task.setCompletedCount(80);
        task.setAnsweredCount(60);
        task.setFailedCount(10);
        task.setNoAnswerCount(10);
        task.setStatus("1");
        task.setPriority(5);
        task.setRetryCount(3);
        task.setRetryInterval(30);
        task.setMaxConcurrent(20);
        task.setCallerName("张三");
        task.setCallerDeptId(3L);
        task.setDescription("季度回访任务");

        assertEquals(1L, task.getTaskId());
        assertEquals("批量回访", task.getTaskName());
        assertEquals("OT20260101001", task.getTaskNo());
        assertEquals("2", task.getTaskType());
        assertEquals("01000000000", task.getCallerNumber());
        assertEquals(2L, task.getIvrFlowId());
        assertEquals("回访流程", task.getIvrFlowName());
        assertEquals(startTime, task.getStartTime());
        assertEquals(endTime, task.getEndTime());
        assertEquals(100, task.getTotalCount());
        assertEquals(80, task.getCompletedCount());
        assertEquals(60, task.getAnsweredCount());
        assertEquals(10, task.getFailedCount());
        assertEquals(10, task.getNoAnswerCount());
        assertEquals("1", task.getStatus());
        assertEquals(5, task.getPriority());
        assertEquals(3, task.getRetryCount());
        assertEquals(30, task.getRetryInterval());
        assertEquals(20, task.getMaxConcurrent());
        assertEquals("张三", task.getCallerName());
        assertEquals(3L, task.getCallerDeptId());
        assertEquals("季度回访任务", task.getDescription());
    }

    @Test
    void 默认值为空()
    {
        AiOutboundTask task = new AiOutboundTask();

        assertNull(task.getTaskId());
        assertNull(task.getTaskName());
        assertNull(task.getTaskType());
        assertNull(task.getStatus());
        assertNull(task.getTotalCount());
        assertNull(task.getPriority());
    }

    @Test
    void 字符串输出包含关键字段()
    {
        AiOutboundTask task = new AiOutboundTask();
        task.setTaskId(1L);
        task.setTaskName("批量回访");
        task.setTaskNo("OT20260101001");
        task.setStatus("1");
        task.setTotalCount(100);

        String text = task.toString();

        assertTrue(text.contains("批量回访"));
        assertTrue(text.contains("OT20260101001"));
        assertTrue(text.contains("totalCount"));
    }
}
