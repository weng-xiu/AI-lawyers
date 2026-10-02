package ai.lawyers.system.domain.lawyers.forecast;

import java.math.BigDecimal;
import java.util.Date;
import com.fasterxml.jackson.annotation.JsonFormat;
import ai.lawyers.common.core.domain.BaseEntity;

/**
 * 话务预测节假日/事件日历对象 ai_forecast_calendar（P1-9）
 *
 * <p>对 hour-of-week 基线剖面做日型调整：HOLIDAY 假日系数（配置统一值）、
 * EVENT 事件系数（按条配置，活动/舆情/政策发布）、WORKDAY 调休补班。</p>
 *
 * @author ai-lawyers
 */
public class AiForecastCalendar extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    /** 日历ID */
    private Long calendarId;

    /** 日期 */
    @JsonFormat(pattern = "yyyy-MM-dd", timezone = "GMT+8")
    private Date calendarDate;

    /** 日型 HOLIDAY假日 EVENT事件 WORKDAY调休补班 */
    private String dayType;

    /** 名称（如"国庆节""普法宣传周"） */
    private String dayName;

    /** 话务系数（EVENT 生效；1=不调整） */
    private BigDecimal coefficient;

    /** 状态 0启用 1停用 */
    private String status;

    /** 查询辅助：起始日期（仅入参） */
    @JsonFormat(pattern = "yyyy-MM-dd", timezone = "GMT+8")
    private Date beginDate;

    /** 查询辅助：截止日期（仅入参） */
    @JsonFormat(pattern = "yyyy-MM-dd", timezone = "GMT+8")
    private Date endDate;

    public Long getCalendarId()
    {
        return calendarId;
    }

    public void setCalendarId(Long calendarId)
    {
        this.calendarId = calendarId;
    }

    public Date getCalendarDate()
    {
        return calendarDate;
    }

    public void setCalendarDate(Date calendarDate)
    {
        this.calendarDate = calendarDate;
    }

    public String getDayType()
    {
        return dayType;
    }

    public void setDayType(String dayType)
    {
        this.dayType = dayType;
    }

    public String getDayName()
    {
        return dayName;
    }

    public void setDayName(String dayName)
    {
        this.dayName = dayName;
    }

    public BigDecimal getCoefficient()
    {
        return coefficient;
    }

    public void setCoefficient(BigDecimal coefficient)
    {
        this.coefficient = coefficient;
    }

    public String getStatus()
    {
        return status;
    }

    public void setStatus(String status)
    {
        this.status = status;
    }

    public Date getBeginDate()
    {
        return beginDate;
    }

    public void setBeginDate(Date beginDate)
    {
        this.beginDate = beginDate;
    }

    public Date getEndDate()
    {
        return endDate;
    }

    public void setEndDate(Date endDate)
    {
        this.endDate = endDate;
    }
}
