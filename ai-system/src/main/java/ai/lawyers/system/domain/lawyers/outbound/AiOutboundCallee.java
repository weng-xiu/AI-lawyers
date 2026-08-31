package ai.lawyers.system.domain.lawyers.outbound;

import java.util.Date;
import com.fasterxml.jackson.annotation.JsonFormat;
import ai.lawyers.common.annotation.Excel;
import ai.lawyers.common.core.domain.BaseEntity;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;

public class AiOutboundCallee extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    private Long calleeId;

    private Long taskId;

    @Excel(name = "被叫号码")
    private String calleeNumber;

    @Excel(name = "被叫姓名")
    private String calleeName;

    @Excel(name = "性别", readConverterExp = "0=男,1=女,2=未知")
    private String calleeGender;

    @Excel(name = "年龄")
    private Integer calleeAge;

    @Excel(name = "地址")
    private String calleeAddress;

    private String calleeParams;

    @Excel(name = "呼叫状态", readConverterExp = "0=待呼叫,1=呼叫中,2=已接通,3=未接,4=失败,5=已完成,6=已取消")
    private String callStatus;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @Excel(name = "呼叫时间", width = 30, dateFormat = "yyyy-MM-dd HH:mm:ss")
    private Date callTime;

    @Excel(name = "通话时长(秒)")
    private Integer callDuration;

    private Long recordId;

    private Long agentId;

    @Excel(name = "已重拨次数")
    private Integer retryTimes;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date lastRetryTime;

    /** T2-2 下次可重试时间（指数退避窗口，null 表示立即可呼叫） */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date nextRetryTime;

    @Excel(name = "失败原因")
    private String failReason;

    public void setCalleeId(Long calleeId) { this.calleeId = calleeId; }
    public Long getCalleeId() { return calleeId; }

    public void setTaskId(Long taskId) { this.taskId = taskId; }
    public Long getTaskId() { return taskId; }

    public void setCalleeNumber(String calleeNumber) { this.calleeNumber = calleeNumber; }
    public String getCalleeNumber() { return calleeNumber; }

    public void setCalleeName(String calleeName) { this.calleeName = calleeName; }
    public String getCalleeName() { return calleeName; }

    public void setCalleeGender(String calleeGender) { this.calleeGender = calleeGender; }
    public String getCalleeGender() { return calleeGender; }

    public void setCalleeAge(Integer calleeAge) { this.calleeAge = calleeAge; }
    public Integer getCalleeAge() { return calleeAge; }

    public void setCalleeAddress(String calleeAddress) { this.calleeAddress = calleeAddress; }
    public String getCalleeAddress() { return calleeAddress; }

    public void setCalleeParams(String calleeParams) { this.calleeParams = calleeParams; }
    public String getCalleeParams() { return calleeParams; }

    public void setCallStatus(String callStatus) { this.callStatus = callStatus; }
    public String getCallStatus() { return callStatus; }

    public void setCallTime(Date callTime) { this.callTime = callTime; }
    public Date getCallTime() { return callTime; }

    public void setCallDuration(Integer callDuration) { this.callDuration = callDuration; }
    public Integer getCallDuration() { return callDuration; }

    public void setRecordId(Long recordId) { this.recordId = recordId; }
    public Long getRecordId() { return recordId; }

    public void setAgentId(Long agentId) { this.agentId = agentId; }
    public Long getAgentId() { return agentId; }

    public void setRetryTimes(Integer retryTimes) { this.retryTimes = retryTimes; }
    public Integer getRetryTimes() { return retryTimes; }

    public void setLastRetryTime(Date lastRetryTime) { this.lastRetryTime = lastRetryTime; }
    public Date getLastRetryTime() { return lastRetryTime; }

    public void setNextRetryTime(Date nextRetryTime) { this.nextRetryTime = nextRetryTime; }
    public Date getNextRetryTime() { return nextRetryTime; }

    public void setFailReason(String failReason) { this.failReason = failReason; }
    public String getFailReason() { return failReason; }

    @Override
    public String toString() {
        return new ToStringBuilder(this, ToStringStyle.MULTI_LINE_STYLE)
            .append("calleeId", getCalleeId())
            .append("taskId", getTaskId())
            .append("calleeNumber", getCalleeNumber())
            .append("calleeName", getCalleeName())
            .append("callStatus", getCallStatus())
            .append("callTime", getCallTime())
            .append("retryTimes", getRetryTimes())
            .toString();
    }
}
