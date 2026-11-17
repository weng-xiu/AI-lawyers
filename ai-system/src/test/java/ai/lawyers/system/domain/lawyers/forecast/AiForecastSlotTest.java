package ai.lawyers.system.domain.lawyers.forecast;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

/**
 * 15分钟粒度话务预测行实体测试：属性读写与默认值。
 *
 * @author ai-lawyers
 */
class AiForecastSlotTest
{
    @Test
    void 属性读写()
    {
        BigDecimal factor = new BigDecimal("0.85");
        AiForecastSlot slot = new AiForecastSlot();
        slot.setForecastDate("2026-11-18");
        slot.setWeekday(3);
        slot.setSlot(36);
        slot.setBeginTime("09:00");
        slot.setEndTime("09:15");
        slot.setTimeRange("09:00-09:15");
        slot.setVolume(30);
        slot.setFactor(factor);
        slot.setDayType("WORKDAY");
        slot.setDayName("调休补班");
        slot.setAgents(5);

        assertEquals("2026-11-18", slot.getForecastDate());
        assertEquals(3, slot.getWeekday());
        assertEquals(36, slot.getSlot());
        assertEquals("09:00", slot.getBeginTime());
        assertEquals("09:15", slot.getEndTime());
        assertEquals("09:00-09:15", slot.getTimeRange());
        assertEquals(30, slot.getVolume());
        assertEquals(factor, slot.getFactor());
        assertEquals("WORKDAY", slot.getDayType());
        assertEquals("调休补班", slot.getDayName());
        assertEquals(5, slot.getAgents());
    }

    @Test
    void 默认值为空()
    {
        AiForecastSlot slot = new AiForecastSlot();

        assertNull(slot.getForecastDate());
        assertNull(slot.getWeekday());
        assertNull(slot.getSlot());
        assertNull(slot.getBeginTime());
        assertNull(slot.getEndTime());
        assertNull(slot.getTimeRange());
        assertNull(slot.getVolume());
        assertNull(slot.getAgents());
    }
}
