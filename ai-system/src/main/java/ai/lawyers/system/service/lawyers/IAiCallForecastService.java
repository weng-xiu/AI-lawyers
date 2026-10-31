package ai.lawyers.system.service.lawyers;

import java.util.List;
import java.util.Map;
import ai.lawyers.system.domain.lawyers.forecast.AiForecastCalendar;
import ai.lawyers.system.domain.lawyers.forecast.AiForecastHour;
import ai.lawyers.system.domain.lawyers.forecast.AiForecastSlot;

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
     * F9：未来 N 天 15 分钟粒度话务预测（weekday × 15 分钟剖面 × 日型系数）。
     *
     * <p>与小时粒度同源（ai_stat_minute call_total），但按 15 分钟聚合，
     * 让换班/坐席投放能落到 15 分钟颗粒度，而不是按小时近似。</p>
     *
     * @param days 预测天数（1~max-days）
     * @return 逐日逐时段预测行（每行字段含 slot 0~95、时段文本与建议坐席）
     */
    List<AiForecastSlot> slotPreview(int days);

    /**
     * F9：15 分钟预测 vs 实际对比验收。
     *
     * <p>剖面用「历史窗口剔除最近 compareDays 天」的数据计算，
     * 再与最近 compareDays 天实际话务对比，给出逐日与整体
     * MAE/MAPE/准确率，避免用同窗口数据自评。</p>
     *
     * @param compareDays 对比天数（1~max-compare-days，默认 forecast.compare-days）
     * @return available/mae/mape/accuracy/perDate/params
     */
    Map<String, Object> slotAccuracy(int compareDays);

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
