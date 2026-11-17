package ai.lawyers.system.domain.lawyers.forecast;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.math.BigDecimal;
import java.util.Date;
import org.junit.jupiter.api.Test;

/**
 * 话务预测节假日日历实体测试：属性读写与默认值。
 *
 * @author ai-lawyers
 */
class AiForecastCalendarTest
{
    @Test
    void 属性读写()
    {
        Date calendarDate = new Date();
        Date beginDate = new Date();
        Date endDate = new Date();
        BigDecimal coefficient = new BigDecimal("1.35");
        AiForecastCalendar calendar = new AiForecastCalendar();
        calendar.setCalendarId(1L);
        calendar.setCalendarDate(calendarDate);
        calendar.setDayType("HOLIDAY");
        calendar.setDayName("国庆节");
        calendar.setCoefficient(coefficient);
        calendar.setStatus("0");
        calendar.setBeginDate(beginDate);
        calendar.setEndDate(endDate);

        assertEquals(1L, calendar.getCalendarId());
        assertEquals(calendarDate, calendar.getCalendarDate());
        assertEquals("HOLIDAY", calendar.getDayType());
        assertEquals("国庆节", calendar.getDayName());
        assertEquals(coefficient, calendar.getCoefficient());
        assertEquals("0", calendar.getStatus());
        assertEquals(beginDate, calendar.getBeginDate());
        assertEquals(endDate, calendar.getEndDate());
    }

    @Test
    void 默认值为空()
    {
        AiForecastCalendar calendar = new AiForecastCalendar();

        assertNull(calendar.getCalendarId());
        assertNull(calendar.getCalendarDate());
        assertNull(calendar.getDayType());
        assertNull(calendar.getCoefficient());
        assertNull(calendar.getBeginDate());
        assertNull(calendar.getEndDate());
    }
}
