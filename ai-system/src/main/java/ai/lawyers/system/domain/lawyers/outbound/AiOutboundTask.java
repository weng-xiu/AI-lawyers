package ai.lawyers.system.domain.lawyers.outbound;

import java.util.Date;
import com.fasterxml.jackson.annotation.JsonFormat;
import ai.lawyers.common.annotation.Excel;
import ai.lawyers.common.core.domain.BaseEntity;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;

public class AiOutboundTask extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    private Long taskId;

    @Excel(name = "任务名称")
    private String taskName;

    @Excel(name = "任务编号")
    private String taskNo;

    @Excel(name = "任务类型", readConverterExp = "1=批量外呼,2=回访,3=通知")
    private String taskType;

    @Excel(name = "主叫号码")
    private String callerNumber;

    private Long ivrFlowId;

    @Excel(name = "IVR流程名称")
    private String ivrFlowName;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @Excel(name = "开始时间", width = 30, dateFormat = "yyyy-MM-dd HH:mm:ss")
    private Date startTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @Excel(name = "结束时间", width = 30, dateFormat = "yyyy-MM-dd HH:mm:ss")
    private Date endTime;

    @Excel(name = "总号码数")
    private Integer totalCount;

    @Excel(name = "已完成数")
    private Integer completedCount;

    @Excel(name = "已接通数")
    private Integer answeredCount;

    @Excel(name = "失败数")
    private Integer failedCount;

    @Excel(name = "未接数")
    private Integer noAnswerCount;

    @Excel(name = "状态", readConverterExp = "0=待执行,1=执行中,2=已完成,3=已暂停,4=已终止")
    private String status;

    @Excel(name = "优先级")
    private Integer priority;

    @Excel(name = "重拨次数")
    private Integer retryCount;

    @Excel(name = "重拨间隔(分钟)")
    private Integer retryInterval;

    @Excel(name = "最大并发数")
    private Integer maxConcurrent;

    @Excel(name = "发起人")
    private String callerName;

    private Long callerDeptId;

    @Excel(name = "任务说明")
    private String description;

    public void setTaskId(Long taskId) { this.taskId = taskId; }
    public Long getTaskId() { return taskId; }

    public void setTaskName(String taskName) { this.taskName = taskName; }
    public String getTaskName() { return taskName; }

    public void setTaskNo(String taskNo) { this.taskNo = taskNo; }
    public String getTaskNo() { return taskNo; }

    public void setTaskType(String taskType) { this.taskType = taskType; }
    public String getTaskType() { return taskType; }

    public void setCallerNumber(String callerNumber) { this.callerNumber = callerNumber; }
    public String getCallerNumber() { return callerNumber; }

    public void setIvrFlowId(Long ivrFlowId) { this.ivrFlowId = ivrFlowId; }
    public Long getIvrFlowId() { return ivrFlowId; }

    public void setIvrFlowName(String ivrFlowName) { this.ivrFlowName = ivrFlowName; }
    public String getIvrFlowName() { return ivrFlowName; }

    public void setStartTime(Date startTime) { this.startTime = startTime; }
    public Date getStartTime() { return startTime; }

    public void setEndTime(Date endTime) { this.endTime = endTime; }
    public Date getEndTime() { return endTime; }

    public void setTotalCount(Integer totalCount) { this.totalCount = totalCount; }
    public Integer getTotalCount() { return totalCount; }

    public void setCompletedCount(Integer completedCount) { this.completedCount = completedCount; }
    public Integer getCompletedCount() { return completedCount; }

    public void setAnsweredCount(Integer answeredCount) { this.answeredCount = answeredCount; }
    public Integer getAnsweredCount() { return answeredCount; }

    public void setFailedCount(Integer failedCount) { this.failedCount = failedCount; }
    public Integer getFailedCount() { return failedCount; }

    public void setNoAnswerCount(Integer noAnswerCount) { this.noAnswerCount = noAnswerCount; }
    public Integer getNoAnswerCount() { return noAnswerCount; }

    public void setStatus(String status) { this.status = status; }
    public String getStatus() { return status; }

    public void setPriority(Integer priority) { this.priority = priority; }
    public Integer getPriority() { return priority; }

    public void setRetryCount(Integer retryCount) { this.retryCount = retryCount; }
    public Integer getRetryCount() { return retryCount; }

    public void setRetryInterval(Integer retryInterval) { this.retryInterval = retryInterval; }
    public Integer getRetryInterval() { return retryInterval; }

    public void setMaxConcurrent(Integer maxConcurrent) { this.maxConcurrent = maxConcurrent; }
    public Integer getMaxConcurrent() { return maxConcurrent; }

    public void setCallerName(String callerName) { this.callerName = callerName; }
    public String getCallerName() { return callerName; }

    public void setCallerDeptId(Long callerDeptId) { this.callerDeptId = callerDeptId; }
    public Long getCallerDeptId() { return callerDeptId; }

    public void setDescription(String description) { this.description = description; }
    public String getDescription() { return description; }

    @Override
    public String toString() {
        return new ToStringBuilder(this, ToStringStyle.MULTI_LINE_STYLE)
            .append("taskId", getTaskId())
            .append("taskName", getTaskName())
            .append("taskNo", getTaskNo())
            .append("taskType", getTaskType())
            .append("status", getStatus())
            .append("totalCount", getTotalCount())
            .append("completedCount", getCompletedCount())
            .append("answeredCount", getAnsweredCount())
            .toString();
    }
}
