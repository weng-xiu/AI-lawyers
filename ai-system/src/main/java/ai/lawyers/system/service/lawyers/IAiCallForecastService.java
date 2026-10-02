package ai.lawyers.system.service.lawyers;

import java.util.List;
import java.util.Map;
import ai.lawyers.system.domain.lawyers.forecast.AiForecastCalendar;
import ai.lawyers.system.domain.lawyers.forecast.AiForecastHour;

/**
 * 话务预测与智能排班 Service 接口（P1-9）
 *
 * @author ai-lawyers
 */
public interface IAiCallForecastService
{
    /**
     * 未来 N 天逐时话务预测（hour-of-week 基线 × 假日/事件系数）。
     *
     * @param days 预测天数（1~max-days）
     * @return 逐时预测行（按日期+小时升序）
     */
    List<AiForecastHour> preview(int days);

    /**
     * 排班建议：预测结果 + 逐日汇总（总话量/峰值时段/峰值坐席）。
     *
     * @param days 预测天数
     * @return totalVolume/peakHour/peakAgents/perDay/params 汇总结构
     */
    Map<String, Object> staffing(int days);

    /**
     * 班组长确认：把当前预测/排班建议存为快照留痕。
     *
     * @param days        预测天数
     * @param remark      备注
     * @param confirmedBy 确认人
     * @return 快照计划ID
     */
    Long confirm(int days, String remark, String confirmedBy);

    /** 日历列表 */
    List<AiForecastCalendar> selectCalendarList(AiForecastCalendar query);

    /** 新增日历（日期唯一） */
    int insertCalendar(AiForecastCalendar calendar);

    /** 修改日历 */
    int updateCalendar(AiForecastCalendar calendar);

    /** 删除日历 */
    int deleteCalendarByIds(Long[] calendarIds);
}
