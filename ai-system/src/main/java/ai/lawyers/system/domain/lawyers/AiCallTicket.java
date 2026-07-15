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

    @Override
    public String toString() {
        return new ToStringBuilder(this,ToStringStyle.MULTI_LINE_STYLE)
            .append("ticketId", getTicketId())
            .append("ticketNo", getTicketNo())
            .append("recordId", getRecordId())
            .append("title", getTitle())
            .append("content", getContent())
            .append("priority", getPriority())
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
