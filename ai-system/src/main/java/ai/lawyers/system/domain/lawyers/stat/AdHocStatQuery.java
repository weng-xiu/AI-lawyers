package ai.lawyers.system.domain.lawyers.stat;

import java.util.List;

/**
 * 报表自助分析查询条件（F6）
 *
 * <p>时间区间为半开 [beginTime, endTime)；metrics/dimensions 为白名单键集合（逗号串由 Controller 拆分）。</p>
 *
 * @author ai-lawyers
 */
public class AdHocStatQuery
{
    /** 开始时间（yyyy-MM-dd HH:mm:ss / yyyy-MM-dd HH:mm / yyyy-MM-dd 均可） */
    private String beginTime;

    /** 结束时间（不含，半开区间） */
    private String endTime;

    /** 聚合粒度：minute/hour/day，非法值回退 day */
    private String granularity;

    /** 指标键集合（白名单校验） */
    private List<String> metrics;

    /** 维度键集合：空=仅全局；1 个=预聚合维度行；≥2 个=跨维度实时查询 */
    private List<String> dimensions;

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

    public String getGranularity()
    {
        return granularity;
    }

    public void setGranularity(String granularity)
    {
        this.granularity = granularity;
    }

    public List<String> getMetrics()
    {
        return metrics;
    }

    public void setMetrics(List<String> metrics)
    {
        this.metrics = metrics;
    }

    public List<String> getDimensions()
    {
        return dimensions;
    }

    public void setDimensions(List<String> dimensions)
    {
        this.dimensions = dimensions;
    }
}
