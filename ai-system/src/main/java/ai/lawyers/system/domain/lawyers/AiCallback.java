package ai.lawyers.system.domain.lawyers;

import java.util.Date;
import com.fasterxml.jackson.annotation.JsonFormat;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;
import ai.lawyers.common.annotation.Excel;
import ai.lawyers.common.annotation.Excel.ColumnType;
import ai.lawyers.common.core.domain.BaseEntity;

/**
 * 客户回访对象 ai_callback
 *
 * @author ai-lawyers
 */
public class AiCallback extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    /** 回访ID */
    private Long callbackId;

    /** 关联台账ID */
    private Long ledgerId;

    /** 来电号码 */
    @Excel(name = "来电号码")
    private String callerNumber;

    /** 来电人姓名 */
    @Excel(name = "来电人姓名")
    private String callerName;

    /** 回访时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @Excel(name = "回访时间", width = 30, dateFormat = "yyyy-MM-dd HH:mm:ss")
    private Date visitTime;

    /** 回访人 */
    @Excel(name = "回访人")
    private String visitBy;

    /** 满意度（1非常满意 2满意 3一般 4不满意） */
    @Excel(name = "满意度", readConverterExp = "1=非常满意,2=满意,3=一般,4=不满意")
    private String satisfaction;

    /** 回访意见 */
    @Excel(name = "回访意见")
    private String visitOpinion;

    /** 回访结果 */
    @Excel(name = "回访结果")
    private String visitResult;

    /** 状态（0待回访 1已完成 2无法联系） */
    @Excel(name = "状态", readConverterExp = "0=待回访,1=已完成,2=无法联系")
    private String status;

    public void setCallbackId(Long callbackId)
    {
        this.callbackId = callbackId;
    }

    public Long getCallbackId()
    {
        return callbackId;
    }

    public void setLedgerId(Long ledgerId)
    {
        this.ledgerId = ledgerId;
    }

    public Long getLedgerId()
    {
        return ledgerId;
    }

    public void setCallerNumber(String callerNumber)
    {
        this.callerNumber = callerNumber;
    }

    public String getCallerNumber()
    {
        return callerNumber;
    }

    public void setCallerName(String callerName)
    {
        this.callerName = callerName;
    }

    public String getCallerName()
    {
        return callerName;
    }

    public void setVisitTime(Date visitTime)
    {
        this.visitTime = visitTime;
    }

    public Date getVisitTime()
    {
        return visitTime;
    }

    public void setVisitBy(String visitBy)
    {
        this.visitBy = visitBy;
    }

    public String getVisitBy()
    {
        return visitBy;
    }

    public void setSatisfaction(String satisfaction)
    {
        this.satisfaction = satisfaction;
    }

    public String getSatisfaction()
    {
        return satisfaction;
    }

    public void setVisitOpinion(String visitOpinion)
    {
        this.visitOpinion = visitOpinion;
    }

    public String getVisitOpinion()
    {
        return visitOpinion;
    }

    public void setVisitResult(String visitResult)
    {
        this.visitResult = visitResult;
    }

    public String getVisitResult()
    {
        return visitResult;
    }

    public void setStatus(String status)
    {
        this.status = status;
    }

    public String getStatus()
    {
        return status;
    }

    @Override
    public String toString() {
        return new ToStringBuilder(this, ToStringStyle.MULTI_LINE_STYLE)
            .append("callbackId", getCallbackId())
            .append("ledgerId", getLedgerId())
            .append("callerNumber", getCallerNumber())
            .append("callerName", getCallerName())
            .append("visitTime", getVisitTime())
            .append("visitBy", getVisitBy())
            .append("satisfaction", getSatisfaction())
            .append("visitOpinion", getVisitOpinion())
            .append("visitResult", getVisitResult())
            .append("status", getStatus())
            .append("createBy", getCreateBy())
            .append("createTime", getCreateTime())
            .append("updateBy", getUpdateBy())
            .append("updateTime", getUpdateTime())
            .append("remark", getRemark())
            .toString();
    }
}
