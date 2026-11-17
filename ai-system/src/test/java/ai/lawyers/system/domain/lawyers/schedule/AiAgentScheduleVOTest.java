package ai.lawyers.system.domain.lawyers.schedule;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * 坐席排班展示对象测试：扩展字段读写、继承字段与字符串输出。
 *
 * @author ai-lawyers
 */
class AiAgentScheduleVOTest
{
    @Test
    void 属性读写()
    {
        AiAgentScheduleVO vo = new AiAgentScheduleVO();
        vo.setScheduleId(1L);
        vo.setAgentId(2L);
        vo.setShiftId(3L);
        vo.setScheduleDate("2026-01-05");
        vo.setScheduleType(1);
        vo.setStatus(1);
        vo.setAgentName("张三");
        vo.setShiftName("早班");
        vo.setStartTime("08:00");
        vo.setEndTime("16:00");

        assertEquals(1L, vo.getScheduleId());
        assertEquals(2L, vo.getAgentId());
        assertEquals(3L, vo.getShiftId());
        assertEquals("2026-01-05", vo.getScheduleDate());
        assertEquals(1, vo.getScheduleType());
        assertEquals(1, vo.getStatus());
        assertEquals("张三", vo.getAgentName());
        assertEquals("早班", vo.getShiftName());
        assertEquals("08:00", vo.getStartTime());
        assertEquals("16:00", vo.getEndTime());
    }

    @Test
    void 默认值为空()
    {
        AiAgentScheduleVO vo = new AiAgentScheduleVO();

        assertNull(vo.getScheduleId());
        assertNull(vo.getAgentName());
        assertNull(vo.getShiftName());
        assertNull(vo.getStartTime());
        assertNull(vo.getEndTime());
    }

    @Test
    void 字符串输出包含扩展字段()
    {
        AiAgentScheduleVO vo = new AiAgentScheduleVO();
        vo.setScheduleId(1L);
        vo.setAgentName("张三");
        vo.setShiftName("早班");
        vo.setStartTime("08:00");

        String text = vo.toString();

        assertTrue(text.contains("张三"));
        assertTrue(text.contains("早班"));
        assertTrue(text.contains("startTime"));
    }
}
