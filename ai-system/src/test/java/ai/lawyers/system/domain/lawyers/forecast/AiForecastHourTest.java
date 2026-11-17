package ai.lawyers.system.domain.lawyers.forecast;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

/**
 * 逐时话务预测行实体测试：属性读写与默认值。
 *
 * @author ai-lawyers
 */
class AiForecastHourTest
{
    @Test
    void 属性读写()
    {
        BigDecimal factor = new BigDecimal("1.20");
        AiForecastHour hour = new AiForecastHour();
        hour.setForecastDate("2026-11-18");
        hour.setWeekday(3);
        hour.setHour(9);
        hour.setVolume(120);
        hour.setFactor(factor);
        hour.setDayType("EVENT");
        hour.setDayName("普法宣传周");
        hour.setAgents(15);

        assertEquals("2026-11-18", hour.getForecastDate());
        assertEquals(3, hour.getWeekday());
        assertEquals(9, hour.getHour());
        assertEquals(120, hour.getVolume());
        assertEquals(factor, hour.getFactor());
        assertEquals("EVENT", hour.getDayType());
        assertEquals("普法宣传周", hour.getDayName());
        assertEquals(15, hour.getAgents());
    }

    @Test
    void 默认值为空()
    {
        AiForecastHour hour = new AiForecastHour();

        assertNull(hour.getForecastDate());
        assertNull(hour.getWeekday());
        assertNull(hour.getHour());
        assertNull(hour.getVolume());
        assertNull(hour.getFactor());
        assertNull(hour.getAgents());
    }
}
