package ai.lawyers.system.domain.lawyers.schedule;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * 班次定义实体测试：属性读写、默认值与字符串输出。
 *
 * @author ai-lawyers
 */
class AiWorkShiftTest
{
    @Test
    void 属性读写()
    {
        AiWorkShift shift = new AiWorkShift();
        shift.setShiftId(1L);
        shift.setShiftName("早班");
        shift.setStartTime("08:00");
        shift.setEndTime("16:00");
        shift.setIsOvernight(0);
        shift.setBreakStart("12:00");
        shift.setBreakEnd("13:00");
        shift.setStatus(1);

        assertEquals(1L, shift.getShiftId());
        assertEquals("早班", shift.getShiftName());
        assertEquals("08:00", shift.getStartTime());
        assertEquals("16:00", shift.getEndTime());
        assertEquals(0, shift.getIsOvernight());
        assertEquals("12:00", shift.getBreakStart());
        assertEquals("13:00", shift.getBreakEnd());
        assertEquals(1, shift.getStatus());
    }

    @Test
    void 默认值为空()
    {
        AiWorkShift shift = new AiWorkShift();

        assertNull(shift.getShiftId());
        assertNull(shift.getShiftName());
        assertNull(shift.getStartTime());
        assertNull(shift.getEndTime());
        assertNull(shift.getStatus());
    }

    @Test
    void 字符串输出包含关键字段()
    {
        AiWorkShift shift = new AiWorkShift();
        shift.setShiftId(1L);
        shift.setShiftName("夜班");
        shift.setStartTime("22:00");
        shift.setIsOvernight(1);

        String text = shift.toString();

        assertTrue(text.contains("夜班"));
        assertTrue(text.contains("22:00"));
        assertTrue(text.contains("isOvernight"));
    }
}
