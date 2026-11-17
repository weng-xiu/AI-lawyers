package ai.lawyers.system.domain.lawyers.schedule;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Date;
import org.junit.jupiter.api.Test;

/**
 * 坐席排班实体测试：属性读写、默认值与字符串输出。
 *
 * @author ai-lawyers
 */
class AiAgentScheduleTest
{
    @Test
    void 属性读写()
    {
        Date checkInTime = new Date();
        Date checkOutTime = new Date();
        AiAgentSchedule schedule = new AiAgentSchedule();
        schedule.setScheduleId(1L);
        schedule.setAgentId(2L);
        schedule.setShiftId(3L);
        schedule.setScheduleDate("2026-01-05");
        schedule.setScheduleType(1);
        schedule.setStatus(1);
        schedule.setCheckInTime(checkInTime);
        schedule.setCheckOutTime(checkOutTime);

        assertEquals(1L, schedule.getScheduleId());
        assertEquals(2L, schedule.getAgentId());
        assertEquals(3L, schedule.getShiftId());
        assertEquals("2026-01-05", schedule.getScheduleDate());
        assertEquals(1, schedule.getScheduleType());
        assertEquals(1, schedule.getStatus());
        assertEquals(checkInTime, schedule.getCheckInTime());
        assertEquals(checkOutTime, schedule.getCheckOutTime());
    }

    @Test
    void 默认值为空()
    {
        AiAgentSchedule schedule = new AiAgentSchedule();

        assertNull(schedule.getScheduleId());
        assertNull(schedule.getAgentId());
        assertNull(schedule.getScheduleDate());
        assertNull(schedule.getScheduleType());
        assertNull(schedule.getStatus());
        assertNull(schedule.getCheckInTime());
    }

    @Test
    void 字符串输出包含关键字段()
    {
        AiAgentSchedule schedule = new AiAgentSchedule();
        schedule.setScheduleId(1L);
        schedule.setAgentId(2L);
        schedule.setScheduleDate("2026-01-05");
        schedule.setScheduleType(1);

        String text = schedule.toString();

        assertTrue(text.contains("2026-01-05"));
        assertTrue(text.contains("scheduleType"));
        assertTrue(text.contains("agentId"));
    }
}
