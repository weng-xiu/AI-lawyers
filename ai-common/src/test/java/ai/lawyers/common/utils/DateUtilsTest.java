package ai.lawyers.common.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Date;

import org.junit.jupiter.api.Test;

/**
 * 时间工具测试：格式化、解析、时间差与类型转换。
 *
 * @author ai-lawyers
 */
class DateUtilsTest
{
    @Test
    void 当前时间方法_输出对应格式()
    {
        assertTrue(DateUtils.getDate().matches("\\d{4}-\\d{2}-\\d{2}"));
        assertTrue(DateUtils.getTime().matches("\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}"));
        assertTrue(DateUtils.dateTimeNow().matches("\\d{14}"));
        assertTrue(DateUtils.datePath().matches("\\d{4}/\\d{2}/\\d{2}"));
        assertTrue(DateUtils.dateTime().matches("\\d{8}"));
        assertNotNull(DateUtils.getNowDate());
    }

    @Test
    void parseDateToStr与dateTime_按指定格式互转()
    {
        Date date = DateUtils.dateTime("yyyy-MM-dd HH:mm:ss", "2024-03-05 08:09:10");

        assertEquals("2024-03-05 08:09:10", DateUtils.parseDateToStr(DateUtils.YYYY_MM_DD_HH_MM_SS, date));
        assertEquals("2024-03-05", DateUtils.dateTime(date));
        assertEquals("2024/03/05 08:09:10", DateUtils.parseDateToStr("yyyy/MM/dd HH:mm:ss", date));
    }

    @Test
    void dateTime_格式非法时抛运行时异常()
    {
        try
        {
            DateUtils.dateTime("yyyy-MM-dd", "不是日期");
        }
        catch (RuntimeException e)
        {
            assertNotNull(e.getCause());
            return;
        }
        throw new AssertionError("预期抛出运行时异常");
    }

    @Test
    void parseDate_兼容多格式并对空值与非法值返回null()
    {
        assertNull(DateUtils.parseDate(null));
        assertNull(DateUtils.parseDate("非法日期"));
        assertNotNull(DateUtils.parseDate("2024-01-02"));
        assertNotNull(DateUtils.parseDate("2024/01/02 03:04:05"));
        assertNotNull(DateUtils.parseDate("2024.01.02"));
    }

    @Test
    void differentDaysByMillisecond_取绝对天数()
    {
        Date early = DateUtils.dateTime("yyyy-MM-dd HH:mm:ss", "2024-01-01 00:00:00");
        Date late = DateUtils.dateTime("yyyy-MM-dd HH:mm:ss", "2024-01-04 12:00:00");

        assertEquals(3, DateUtils.differentDaysByMillisecond(early, late));
        assertEquals(3, DateUtils.differentDaysByMillisecond(late, early));
        assertEquals(0, DateUtils.differentDaysByMillisecond(early, early));
    }

    @Test
    void timeDistance_按天小时分钟拼接()
    {
        Date start = DateUtils.dateTime("yyyy-MM-dd HH:mm:ss", "2024-01-01 00:00:00");
        Date end = DateUtils.dateTime("yyyy-MM-dd HH:mm:ss", "2024-01-02 01:20:00");

        assertEquals("1天1小时20分钟", DateUtils.timeDistance(end, start));
        assertEquals("0天0小时0分钟", DateUtils.timeDistance(start, start));
    }

    @Test
    void toDate_LocalDate与LocalDateTime转为Date()
    {
        Date fromLocalDate = DateUtils.toDate(LocalDate.of(2024, 6, 18));
        assertEquals("2024-06-18 00:00:00",
                DateUtils.parseDateToStr(DateUtils.YYYY_MM_DD_HH_MM_SS, fromLocalDate));

        Date fromLocalDateTime = DateUtils.toDate(LocalDateTime.of(2024, 6, 18, 12, 30, 45));
        assertEquals("2024-06-18 12:30:45",
                DateUtils.parseDateToStr(DateUtils.YYYY_MM_DD_HH_MM_SS, fromLocalDateTime));
    }

    @Test
    void getServerStartDate_早于等于当前时间()
    {
        Date start = DateUtils.getServerStartDate();

        assertNotNull(start);
        assertTrue(start.getTime() <= System.currentTimeMillis());
    }
}
