package ai.lawyers.system.domain.lawyers;

import java.util.Date;
import com.fasterxml.jackson.annotation.JsonFormat;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;
import ai.lawyers.common.annotation.Excel;
import ai.lawyers.common.annotation.Excel.ColumnType;
import ai.lawyers.common.core.domain.BaseEntity;

public class AiCallRecord extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    private Long recordId;

    @Excel(name = "来电号码")
    private String callerNumber;

    @Excel(name = "来电人姓名")
    private String callerName;

    @Excel(name = "来电人地址")
    private String callerAddress;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @Excel(name = "来电时间", width = 30, dateFormat = "yyyy-MM-dd HH:mm:ss")
    private Date callTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @Excel(name = "结束时间", width = 30, dateFormat = "yyyy-MM-dd HH:mm:ss")
    private Date endTime;

    @Excel(name = "通话时长(秒)", cellType = ColumnType.NUMERIC)
    private Integer duration;

    @Excel(name = "坐席ID", cellType = ColumnType.NUMERIC)
    private Long agentId;

    @Excel(name = "咨询分类ID", cellType = ColumnType.NUMERIC)
    private Long categoryId;

    @Excel(name = "咨询内容")
    private String content;

    @Excel(name = "解答内容")
    private String answer;

    @Excel(name = "状态", readConverterExp = "0=接通中,1=已完成,2=已转接,3=未接")
    private String status;

    @Excel(name = "转接记录ID", cellType = ColumnType.NUMERIC)
    private Long transferId;

    @Excel(name = "工单ID", cellType = ColumnType.NUMERIC)
    private Long ticketId;

    /** 通话通道UUID（FreeSWITCH Unique-ID），用于 ESL 事件关联 */
    private String callUuid;

    @Excel(name = "录音文件路径")
    private String recordFile;

    @Excel(name = "录音访问URL")
    private String recordingUrl;

    @Excel(name = "录音时长(秒)", cellType = ColumnType.NUMERIC)
    private Integer recordDuration;

    @Excel(name = "ASR转写状态", readConverterExp = "0=待转写,1=转写中,2=已完成,3=失败")
    private Integer asrStatus;

    @Excel(name = "ASR转写文本")
    private String transcript;

    private String agentName;

    private String categoryName;

    public void setRecordId(Long recordId) 
    {
        this.recordId = recordId;
    }

    public Long getRecordId() 
    {
        return recordId;
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
    public void setCallerAddress(String callerAddress) 
    {
        this.callerAddress = callerAddress;
    }

    public String getCallerAddress() 
    {
        return callerAddress;
    }
    public void setCallTime(Date callTime) 
    {
        this.callTime = callTime;
    }

    public Date getCallTime() 
    {
        return callTime;
    }
    public void setEndTime(Date endTime) 
    {
        this.endTime = endTime;
    }

    public Date getEndTime() 
    {
        return endTime;
    }
    public void setDuration(Integer duration) 
    {
        this.duration = duration;
    }

    public Integer getDuration() 
    {
        return duration;
    }
    public void setAgentId(Long agentId) 
    {
        this.agentId = agentId;
    }

    public Long getAgentId() 
    {
        return agentId;
    }
    public void setCategoryId(Long categoryId) 
    {
        this.categoryId = categoryId;
    }

    public Long getCategoryId() 
    {
        return categoryId;
    }
    public void setContent(String content) 
    {
        this.content = content;
    }

    public String getContent() 
    {
        return content;
    }
    public void setAnswer(String answer) 
    {
        this.answer = answer;
    }

    public String getAnswer() 
    {
        return answer;
    }
    public void setStatus(String status) 
    {
        this.status = status;
    }

    public String getStatus() 
    {
        return status;
    }
    public void setTransferId(Long transferId) 
    {
        this.transferId = transferId;
    }

    public Long getTransferId() 
    {
        return transferId;
    }
    public void setTicketId(Long ticketId) 
    {
        this.ticketId = ticketId;
    }

    public Long getTicketId() 
    {
        return ticketId;
    }

    public String getCallUuid()
    {
        return callUuid;
    }

    public void setCallUuid(String callUuid)
    {
        this.callUuid = callUuid;
    }

    public String getRecordFile()
    {
        return recordFile;
    }

    public void setRecordFile(String recordFile)
    {
        this.recordFile = recordFile;
    }

    public String getRecordingUrl()
    {
        return recordingUrl;
    }

    public void setRecordingUrl(String recordingUrl)
    {
        this.recordingUrl = recordingUrl;
    }

    public Integer getRecordDuration()
    {
        return recordDuration;
    }

    public void setRecordDuration(Integer recordDuration)
    {
        this.recordDuration = recordDuration;
    }

    public Integer getAsrStatus()
    {
        return asrStatus;
    }

    public void setAsrStatus(Integer asrStatus)
    {
        this.asrStatus = asrStatus;
    }

    public String getTranscript()
    {
        return transcript;
    }

    public void setTranscript(String transcript)
    {
        this.transcript = transcript;
    }

    public String getAgentName() 
    {
        return agentName;
    }

    public void setAgentName(String agentName) 
    {
        this.agentName = agentName;
    }

    public String getCategoryName() 
    {
        return categoryName;
    }

    public void setCategoryName(String categoryName) 
    {
        this.categoryName = categoryName;
    }

    @Override
    public String toString() {
        return new ToStringBuilder(this,ToStringStyle.MULTI_LINE_STYLE)
            .append("recordId", getRecordId())
            .append("callerNumber", getCallerNumber())
            .append("callerName", getCallerName())
            .append("callerAddress", getCallerAddress())
            .append("callTime", getCallTime())
            .append("endTime", getEndTime())
            .append("duration", getDuration())
            .append("agentId", getAgentId())
            .append("categoryId", getCategoryId())
            .append("content", getContent())
            .append("answer", getAnswer())
            .append("status", getStatus())
            .append("transferId", getTransferId())
            .append("ticketId", getTicketId())
            .append("callUuid", getCallUuid())
            .append("recordFile", getRecordFile())
            .append("recordingUrl", getRecordingUrl())
            .append("recordDuration", getRecordDuration())
            .append("asrStatus", getAsrStatus())
            .append("transcript", getTranscript())
            .append("agentName", getAgentName())
            .append("categoryName", getCategoryName())
            .append("createBy", getCreateBy())
            .append("createTime", getCreateTime())
            .append("updateBy", getUpdateBy())
            .append("updateTime", getUpdateTime())
            .append("remark", getRemark())
            .toString();
    }
}
