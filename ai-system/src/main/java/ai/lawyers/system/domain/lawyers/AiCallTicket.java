package ai.lawyers.system.domain.lawyers;

import java.util.Date;
import com.fasterxml.jackson.annotation.JsonFormat;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;
import ai.lawyers.common.annotation.Excel;
import ai.lawyers.common.annotation.Excel.ColumnType;
import ai.lawyers.common.core.domain.BaseEntity;

public class AiCallTicket extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    private Long ticketId;

    @Excel(name = "工单号")
    private String ticketNo;

    @Excel(name = "来电记录ID", cellType = ColumnType.NUMERIC)
    private Long recordId;

    @Excel(name = "工单标题")
    private String title;

    @Excel(name = "工单内容")
    private String content;

    @Excel(name = "优先级", readConverterExp = "1=紧急,2=普通,3=低")
    private String priority;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @Excel(name = "SLA截止时间", width = 30, dateFormat = "yyyy-MM-dd HH:mm:ss")
    private Date dueTime;

    @Excel(name = "是否超时", readConverterExp = "0=否,1=是")
    private Integer overtimeFlag;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @Excel(name = "最后提醒时间", width = 30, dateFormat = "yyyy-MM-dd HH:mm:ss")
    private Date remindTime;

    @Excel(name = "状态", readConverterExp = "0=待处理,1=处理中,2=已完成,3=已归档")
    private String status;

    @Excel(name = "处理人ID", cellType = ColumnType.NUMERIC)
    private Long assignUserId;

    @Excel(name = "处理人姓名")
    private String assignUserName;

    @Excel(name = "处理内容")
    private String processContent;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @Excel(name = "处理时间", width = 30, dateFormat = "yyyy-MM-dd HH:mm:ss")
    private Date processTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @Excel(name = "关闭时间", width = 30, dateFormat = "yyyy-MM-dd HH:mm:ss")
    private Date closeTime;

    private String callerNumber;

    private String callerName;

    /** 外部条线 LEGAL_AID/MEDIATION/NOTARY/FORENSIC/ARBITRATION/HOTLINE_12345（F3） */
    private String externalType;

    /** 协同机构ID（F3） */
    private Long externalOrgId;

    /** 外部工单号（F3） */
    private String externalTicketNo;

    /** 外部状态 PENDING/ACCEPTED/PROCESSING/DONE/REJECTED/FAILED（F3） */
    private String externalStatus;

    /** 外部状态最近回写时间（F3） */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date externalUpdateTime;

    /** 最近转出/接收时间（F3） */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date transferTime;

    /** 协同方向 OUT 转出 / IN 转入（F3） */
    private String direction;

    public void setTicketId(Long ticketId)
    {
        this.ticketId = ticketId;
    }

    public Long getTicketId() 
    {
        return ticketId;
    }
    public void setTicketNo(String ticketNo) 
    {
        this.ticketNo = ticketNo;
    }

    public String getTicketNo() 
    {
        return ticketNo;
    }
    public void setRecordId(Long recordId) 
    {
        this.recordId = recordId;
    }

    public Long getRecordId() 
    {
        return recordId;
    }
    public void setTitle(String title) 
    {
        this.title = title;
    }

    public String getTitle() 
    {
        return title;
    }
    public void setContent(String content) 
    {
        this.content = content;
    }

    public String getContent() 
    {
        return content;
    }
    public void setPriority(String priority) 
    {
        this.priority = priority;
    }

    public String getPriority() 
    {
        return priority;
    }
    public void setDueTime(Date dueTime)
    {
        this.dueTime = dueTime;
    }

    public Date getDueTime()
    {
        return dueTime;
    }

    public void setOvertimeFlag(Integer overtimeFlag)
    {
        this.overtimeFlag = overtimeFlag;
    }

    public Integer getOvertimeFlag()
    {
        return overtimeFlag;
    }

    public void setRemindTime(Date remindTime)
    {
        this.remindTime = remindTime;
    }

    public Date getRemindTime()
    {
        return remindTime;
    }
    public void setStatus(String status) 
    {
        this.status = status;
    }

    public String getStatus() 
    {
        return status;
    }
    public void setAssignUserId(Long assignUserId) 
    {
        this.assignUserId = assignUserId;
    }

    public Long getAssignUserId() 
    {
        return assignUserId;
    }
    public void setAssignUserName(String assignUserName) 
    {
        this.assignUserName = assignUserName;
    }

    public String getAssignUserName() 
    {
        return assignUserName;
    }
    public void setProcessContent(String processContent) 
    {
        this.processContent = processContent;
    }

    public String getProcessContent() 
    {
        return processContent;
    }
    public void setProcessTime(Date processTime) 
    {
        this.processTime = processTime;
    }

    public Date getProcessTime() 
    {
        return processTime;
    }
    public void setCloseTime(Date closeTime) 
    {
        this.closeTime = closeTime;
    }

    public Date getCloseTime() 
    {
        return closeTime;
    }

    public String getCallerNumber() 
    {
        return callerNumber;
    }

    public void setCallerNumber(String callerNumber) 
    {
        this.callerNumber = callerNumber;
    }

    public String getCallerName() 
    {
        return callerName;
    }

    public void setCallerName(String callerName)
    {
        this.callerName = callerName;
    }

    public String getExternalType() { return externalType; }
    public void setExternalType(String externalType) { this.externalType = externalType; }

    public Long getExternalOrgId() { return externalOrgId; }
    public void setExternalOrgId(Long externalOrgId) { this.externalOrgId = externalOrgId; }

    public String getExternalTicketNo() { return externalTicketNo; }
    public void setExternalTicketNo(String externalTicketNo) { this.externalTicketNo = externalTicketNo; }

    public String getExternalStatus() { return externalStatus; }
    public void setExternalStatus(String externalStatus) { this.externalStatus = externalStatus; }

    public Date getExternalUpdateTime() { return externalUpdateTime; }
    public void setExternalUpdateTime(Date externalUpdateTime) { this.externalUpdateTime = externalUpdateTime; }

    public Date getTransferTime() { return transferTime; }
    public void setTransferTime(Date transferTime) { this.transferTime = transferTime; }

    public String getDirection() { return direction; }
    public void setDirection(String direction) { this.direction = direction; }

    @Override
    public String toString() {
        return new ToStringBuilder(this,ToStringStyle.MULTI_LINE_STYLE)
            .append("ticketId", getTicketId())
            .append("ticketNo", getTicketNo())
            .append("recordId", getRecordId())
            .append("title", getTitle())
            .append("content", getContent())
            .append("priority", getPriority())
            .append("dueTime", getDueTime())
            .append("overtimeFlag", getOvertimeFlag())
            .append("remindTime", getRemindTime())
            .append("status", getStatus())
            .append("assignUserId", getAssignUserId())
            .append("assignUserName", getAssignUserName())
            .append("processContent", getProcessContent())
            .append("processTime", getProcessTime())
            .append("closeTime", getCloseTime())
            .append("callerNumber", getCallerNumber())
            .append("callerName", getCallerName())
            .append("createBy", getCreateBy())
            .append("createTime", getCreateTime())
            .append("updateBy", getUpdateBy())
            .append("updateTime", getUpdateTime())
            .append("remark", getRemark())
            .toString();
    }
}
