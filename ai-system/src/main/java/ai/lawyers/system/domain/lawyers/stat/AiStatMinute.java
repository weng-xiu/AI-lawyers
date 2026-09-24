package ai.lawyers.system.domain.lawyers.stat;

import java.math.BigDecimal;
import java.util.Date;
import com.fasterxml.jackson.annotation.JsonFormat;

/**
 * 分钟级物化统计对象 ai_stat_minute（P3-F3）
 *
 * @author ai-lawyers
 */
public class AiStatMinute
{
    private static final long serialVersionUID = 1L;

    /** 主键 */
    private Long statId;

    /** 统计时间（分钟级） */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date statTime;

    /** 统计维度（ALL=全局） */
    private String dimension;

    /** 指标键 */
    private String metricKey;

    /** 指标值 */
    private BigDecimal metricValue;

    /** 写入时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date createTime;

    public Long getStatId()
    {
        return statId;
    }

    public void setStatId(Long statId)
    {
        this.statId = statId;
    }

    public Date getStatTime()
    {
        return statTime;
    }

    public void setStatTime(Date statTime)
    {
        this.statTime = statTime;
    }

    public String getDimension()
    {
        return dimension;
    }

    public void setDimension(String dimension)
    {
        this.dimension = dimension;
    }

    public String getMetricKey()
    {
        return metricKey;
    }

    public void setMetricKey(String metricKey)
    {
        this.metricKey = metricKey;
    }

    public BigDecimal getMetricValue()
    {
        return metricValue;
    }

    public void setMetricValue(BigDecimal metricValue)
    {
        this.metricValue = metricValue;
    }

    public Date getCreateTime()
    {
        return createTime;
    }

    public void setCreateTime(Date createTime)
    {
        this.createTime = createTime;
    }
}
