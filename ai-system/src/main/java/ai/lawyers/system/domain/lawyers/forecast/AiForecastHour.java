package ai.lawyers.system.domain.lawyers.forecast;

import java.math.BigDecimal;

/**
 * 逐时话务预测行（P1-9，非持久化，接口出参）
 *
 * @author ai-lawyers
 */
public class AiForecastHour
{
    /** 预测日期 yyyy-MM-dd */
    private String forecastDate;

    /** 星期（0=周日 … 6=周六） */
    private Integer weekday;

    /** 小时 0~23 */
    private Integer hour;

    /** 预测进线量（通） */
    private Integer volume;

    /** 应用的话务系数（假日/事件调整） */
    private BigDecimal factor;

    /** 日型（HOLIDAY/EVENT/WORKDAY/常规为 null） */
    private String dayType;

    /** 日型名称 */
    private String dayName;

    /** 建议坐席数（按 AHT/利用率推算） */
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

    public Integer getHour()
    {
        return hour;
    }

    public void setHour(Integer hour)
    {
        this.hour = hour;
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
