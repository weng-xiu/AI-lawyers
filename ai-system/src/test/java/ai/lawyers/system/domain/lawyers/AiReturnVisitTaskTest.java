package ai.lawyers.system.domain.lawyers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Date;
import org.junit.jupiter.api.Test;

/**
 * 回访任务实体测试：属性读写、默认值与字符串输出。
 *
 * @author ai-lawyers
 */
class AiReturnVisitTaskTest
{
    @Test
    void 属性读写()
    {
        Date planTime = new Date();
        Date actualTime = new Date();
        AiReturnVisitTask task = new AiReturnVisitTask();
        task.setTaskId(1L);
        task.setTaskNo("RV20261118001");
        task.setCallerNumber("13600000000");
        task.setCallerName("钱八");
        task.setPlanTime(planTime);
        task.setActualTime(actualTime);
        task.setAssignee("回访员甲");
        task.setAssigneeId(2L);
        task.setStatus("1");
        task.setPriority("1");
        task.setVisitResult("客户表示满意");

        assertEquals(1L, task.getTaskId());
        assertEquals("RV20261118001", task.getTaskNo());
        assertEquals("13600000000", task.getCallerNumber());
        assertEquals("钱八", task.getCallerName());
        assertEquals(planTime, task.getPlanTime());
        assertEquals(actualTime, task.getActualTime());
        assertEquals("回访员甲", task.getAssignee());
        assertEquals(2L, task.getAssigneeId());
        assertEquals("1", task.getStatus());
        assertEquals("1", task.getPriority());
        assertEquals("客户表示满意", task.getVisitResult());
    }

    @Test
    void 默认值为空()
    {
        AiReturnVisitTask task = new AiReturnVisitTask();

        assertNull(task.getTaskId());
        assertNull(task.getTaskNo());
        assertNull(task.getCallerNumber());
        assertNull(task.getPlanTime());
        assertNull(task.getStatus());
        assertNull(task.getVisitResult());
    }

    @Test
    void 字符串输出包含任务编号与受理人()
    {
        AiReturnVisitTask task = new AiReturnVisitTask();
        task.setTaskNo("RV20261118002");
        task.setAssignee("回访员乙");

        String text = task.toString();
        assertTrue(text.contains("RV20261118002"));
        assertTrue(text.contains("回访员乙"));
    }
}
