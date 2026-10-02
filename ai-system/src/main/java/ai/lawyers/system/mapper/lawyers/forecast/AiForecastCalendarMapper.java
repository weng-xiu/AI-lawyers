package ai.lawyers.system.mapper.lawyers.forecast;

import java.util.Date;
import java.util.List;
import org.apache.ibatis.annotations.Param;
import ai.lawyers.system.domain.lawyers.forecast.AiForecastCalendar;

/**
 * 话务预测节假日/事件日历 Mapper（P1-9）
 *
 * @author ai-lawyers
 */
public interface AiForecastCalendarMapper
{
    /** 条件查询（日型/状态/日期区间） */
    List<AiForecastCalendar> selectCalendarList(AiForecastCalendar query);

    /** 按ID查询 */
    AiForecastCalendar selectCalendarById(Long calendarId);

    /** 查询日期区间内启用的日历条目（预测调整用） */
    List<AiForecastCalendar> selectActiveBetween(@Param("beginDate") Date beginDate,
                                                 @Param("endDate") Date endDate);

    /** 新增 */
    int insertCalendar(AiForecastCalendar calendar);

    /** 修改 */
    int updateCalendar(AiForecastCalendar calendar);

    /** 批量删除 */
    int deleteCalendarByIds(Long[] calendarIds);
}
