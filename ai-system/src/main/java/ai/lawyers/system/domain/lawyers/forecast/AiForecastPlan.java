package ai.lawyers.system.domain.lawyers.forecast;

import java.util.Date;
import com.fasterxml.jackson.annotation.JsonFormat;

/**
 * 排班计划确认快照对象 ai_forecast_plan（P1-9）
 *
 * <p>班组长确认预测/排班建议时保存 JSON 快照：预测基线与系数随后续话务
 * 数据漂移，快照保证"当时确认的是什么"可追溯。</p>
 *
 * @author ai-lawyers
 */
public class AiForecastPlan
{
    private static final long serialVersionUID = 1L;

    /** 计划ID */
    private Long planId;

    /** 预测起始日 */
    @JsonFormat(pattern = "yyyy-MM-dd", timezone = "GMT+8")
    private Date beginDate;

    /** 预测截止日 */
    @JsonFormat(pattern = "yyyy-MM-dd", timezone = "GMT+8")
    private Date endDate;

    /** 预测总进线量 */
    private Integer totalVolume;

    /** 峰值时段（如 10:00） */
    private String peakHour;

    /** 峰值所需坐席数 */
    private Integer peakAgents;

    /** 逐日逐时预测与排班建议快照 JSON */
    private String payloadJson;

    /** 确认人（班组长） */
    private String confirmedBy;

    /** 确认时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date confirmedTime;

    /** 备注 */
    private String remark;

    /** 创建时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date createTime;

    public Long getPlanId()
    {
        return planId;
    }

    public void setPlanId(Long planId)
    {
        this.planId = planId;
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

    public Integer getTotalVolume()
    {
        return totalVolume;
    }

    public void setTotalVolume(Integer totalVolume)
    {
        this.totalVolume = totalVolume;
    }

    public String getPeakHour()
    {
        return peakHour;
    }

    public void setPeakHour(String peakHour)
    {
        this.peakHour = peakHour;
    }

    public Integer getPeakAgents()
    {
        return peakAgents;
    }

    public void setPeakAgents(Integer peakAgents)
    {
        this.peakAgents = peakAgents;
    }

    public String getPayloadJson()
    {
        return payloadJson;
    }

    public void setPayloadJson(String payloadJson)
    {
        this.payloadJson = payloadJson;
    }

    public String getConfirmedBy()
    {
        return confirmedBy;
    }

    public void setConfirmedBy(String confirmedBy)
    {
        this.confirmedBy = confirmedBy;
    }

    public Date getConfirmedTime()
    {
        return confirmedTime;
    }

    public void setConfirmedTime(Date confirmedTime)
    {
        this.confirmedTime = confirmedTime;
    }

    public String getRemark()
    {
        return remark;
    }

    public void setRemark(String remark)
    {
        this.remark = remark;
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
