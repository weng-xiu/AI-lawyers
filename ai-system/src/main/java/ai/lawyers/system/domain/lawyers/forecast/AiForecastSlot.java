package ai.lawyers.system.domain.lawyers.forecast;

import java.math.BigDecimal;

/**
 * 15 分钟粒度话务预测行（F9，非持久化，接口出参）
 *
 * <p>按「日期 + 15 分钟时段」出参：slot 0~95（0 表示 00:00-00:15），
 * 便于排到 15 分钟粒度的换班与坐席投放，而不必再按小时近似。</p>
 */
public class AiForecastSlot
{
    /** 预测日期 yyyy-MM-dd */
    private String forecastDate;

    /** 星期（0=周日 … 6=周六） */
    private Integer weekday;

    /** 时段序号 0~95（slot = hour * 4 + minute / 15） */
    private Integer slot;

    /** 时段开始时间 HH:mm */
    private String beginTime;

    /** 时段结束时间 HH:mm（末时段为 24:00） */
    private String endTime;

    /** 时段文本 HH:mm-HH:mm（图表与表格直接展示） */
    private String timeRange;

    /** 预测进线量（通，15 分钟内） */
    private Integer volume;

    /** 应用的话务系数（假日/事件调整） */
    private BigDecimal factor;

    /** 日型（HOLIDAY/EVENT/WORKDAY/常规为 null） */
    private String dayType;

    /** 日型名称 */
    private String dayName;

    /** 该 15 分钟时段建议坐席数 */
    private Integer agents;

    public String getForecastDate()
    {
        return forecastDate;
    }

    public void setForecastDate(String forecastDate)
    {
        this.forecastDate = forecastDate;
    }

    public Integer getWeekday()
    {
        return weekday;
    }

    public void setWeekday(Integer weekday)
    {
        this.weekday = weekday;
    }

    public Integer getSlot()
    {
        return slot;
    }

    public void setSlot(Integer slot)
    {
        this.slot = slot;
    }

    public String getBeginTime()
    {
        return beginTime;
    }

    public void setBeginTime(String beginTime)
    {
        this.beginTime = beginTime;
    }

    public String getEndTime()
    {
        return endTime;
    }

    public void setEndTime(String endTime)
    {
        this.endTime = endTime;
    }

    public String getTimeRange()
    {
        return timeRange;
    }

    public void setTimeRange(String timeRange)
    {
        this.timeRange = timeRange;
    }

    public Integer getVolume()
    {
        return volume;
    }

    public void setVolume(Integer volume)
    {
        this.volume = volume;
    }

    public BigDecimal getFactor()
    {
        return factor;
    }

    public void setFactor(BigDecimal factor)
    {
        this.factor = factor;
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

    public Integer getAgents()
    {
        return agents;
    }

    public void setAgents(Integer agents)
    {
        this.agents = agents;
    }
}
