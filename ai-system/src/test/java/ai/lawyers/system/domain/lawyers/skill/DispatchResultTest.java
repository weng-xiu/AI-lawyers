package ai.lawyers.system.domain.lawyers.skill;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * 坐席分配结果测试：属性读写、工厂方法与默认值。
 *
 * @author ai-lawyers
 */
class DispatchResultTest
{
    @Test
    void 属性读写()
    {
        DispatchResult result = new DispatchResult();
        result.setSuccess(true);
        result.setAgentId(1L);
        result.setAgentName("张三");
        result.setAgentExtension("8001");
        result.setGroupId(2L);
        result.setGroupName("法律咨询组");
        result.setStrategy("round_robin");
        result.setQueueId(3L);
        result.setQueuePosition(1);
        result.setQueueSize(5);
        result.setMessage("已分配");

        assertTrue(result.isSuccess());
        assertEquals(1L, result.getAgentId());
        assertEquals("张三", result.getAgentName());
        assertEquals("8001", result.getAgentExtension());
        assertEquals(2L, result.getGroupId());
        assertEquals("法律咨询组", result.getGroupName());
        assertEquals("round_robin", result.getStrategy());
        assertEquals(3L, result.getQueueId());
        assertEquals(1, result.getQueuePosition());
        assertEquals(5, result.getQueueSize());
        assertEquals("已分配", result.getMessage());
    }

    @Test
    void 分配成功工厂方法()
    {
        DispatchResult result = DispatchResult.assigned(1L, "张三", 2L, "法律咨询组", "round_robin");

        assertTrue(result.isSuccess());
        assertEquals(1L, result.getAgentId());
        assertEquals("张三", result.getAgentName());
        assertEquals(2L, result.getGroupId());
        assertEquals("法律咨询组", result.getGroupName());
        assertEquals("round_robin", result.getStrategy());
        assertTrue(result.getMessage().contains("张三"));
    }

    @Test
    void 排队工厂方法()
    {
        DispatchResult result = DispatchResult.queued(3L, 2, 5, 4L, "法律咨询组");

        assertFalse(result.isSuccess());
        assertEquals(3L, result.getQueueId());
        assertEquals(2, result.getQueuePosition());
        assertEquals(5, result.getQueueSize());
        assertEquals(4L, result.getGroupId());
        assertEquals("法律咨询组", result.getGroupName());
        assertTrue(result.getMessage().contains("第2位"));
    }

    @Test
    void 失败工厂方法与默认值()
    {
        DispatchResult result = DispatchResult.failed("无可用技能组");

        assertFalse(result.isSuccess());
        assertEquals("无可用技能组", result.getMessage());
        assertNull(result.getAgentId());
        assertNull(result.getQueueId());
    }
}
