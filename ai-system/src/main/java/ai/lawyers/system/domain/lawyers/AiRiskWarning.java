package ai.lawyers.system.domain.lawyers;

import java.util.Date;
import com.fasterxml.jackson.annotation.JsonFormat;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;
import ai.lawyers.common.annotation.Excel;
import ai.lawyers.common.core.domain.BaseEntity;

/**
 * 智能风险预警记录对象 ai_risk_warning
 */
public class AiRiskWarning extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    private Long warningId;

    @Excel(name = "预警类型", readConverterExp = "敏感词=敏感词,情绪异常=情绪异常,异常行为=异常行为,合规风险=合规风险")
    private String warningType;

    @Excel(name = "预警级别", readConverterExp = "1=高,2=中,3=低")
    private String warningLevel;

    @Excel(name = "来源类型", readConverterExp = "通话=通话,图文=图文,视频=视频")
    private String sourceType;

    private Long sourceId;

    @Excel(name = "触发内容")
    private String content;

    @Excel(name = "客户姓名")
    private String customerName;

    @Excel(name = "状态", readConverterExp = "0=待处理,1=处理中,2=已处理,3=已忽略")
    private String status;

    @Excel(name = "处理人姓名")
    private String handlerName;

    /** 处理人用户ID */
    private Long handlerId;

    @Excel(name = "处理结果")
    private String handleResult;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @Excel(name = "处理时间", width = 30, dateFormat = "yyyy-MM-dd HH:mm:ss")
    private Date handleTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @Excel(name = "触发时间", width = 30, dateFormat = "yyyy-MM-dd HH:mm:ss")
    private Date triggerTime;

    public void setWarningId(Long warningId)
    {
        this.warningId = warningId;
    }

    public Long getWarningId()
    {
        return warningId;
    }

    public void setWarningType(String warningType)
    {
        this.warningType = warningType;
    }

    public String getWarningType()
    {
        return warningType;
    }

    public void setWarningLevel(String warningLevel)
    {
        this.warningLevel = warningLevel;
    }

    public String getWarningLevel()
    {
        return warningLevel;
    }

    public void setSourceType(String sourceType)
    {
        this.sourceType = sourceType;
    }

    public String getSourceType()
    {
        return sourceType;
    }

    public void setSourceId(Long sourceId)
    {
        this.sourceId = sourceId;
    }

    public Long getSourceId()
    {
        return sourceId;
    }

    public void setContent(String content)
    {
        this.content = content;
    }

    public String getContent()
    {
        return content;
    }

    public void setCustomerName(String customerName)
    {
        this.customerName = customerName;
    }

    public String getCustomerName()
    {
        return customerName;
    }

    public void setStatus(String status)
    {
        this.status = status;
    }

    public String getStatus()
    {
        return status;
    }

    public void setHandlerName(String handlerName)
    {
        this.handlerName = handlerName;
    }

    public String getHandlerName()
    {
        return handlerName;
    }

    public void setHandlerId(Long handlerId)
    {
        this.handlerId = handlerId;
    }

    public Long getHandlerId()
    {
        return handlerId;
    }

    public void setHandleResult(String handleResult)
    {
        this.handleResult = handleResult;
    }

    public String getHandleResult()
    {
        return handleResult;
    }

    public void setHandleTime(Date handleTime)
    {
        this.handleTime = handleTime;
    }

    public Date getHandleTime()
    {
        return handleTime;
    }

    public void setTriggerTime(Date triggerTime)
    {
        this.triggerTime = triggerTime;
    }

    public Date getTriggerTime()
    {
        return triggerTime;
    }

    @Override
    public String toString() {
        return new ToStringBuilder(this, ToStringStyle.MULTI_LINE_STYLE)
            .append("warningId", getWarningId())
            .append("warningType", getWarningType())
            .append("warningLevel", getWarningLevel())
            .append("sourceType", getSourceType())
            .append("sourceId", getSourceId())
            .append("content", getContent())
            .append("customerName", getCustomerName())
            .append("status", getStatus())
            .append("handlerName", getHandlerName())
            .append("handleResult", getHandleResult())
            .append("handleTime", getHandleTime())
            .append("triggerTime", getTriggerTime())
            .append("createTime", getCreateTime())
            .append("remark", getRemark())
            .toString();
    }
}
