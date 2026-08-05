package ai.lawyers.system.domain.lawyers;

import java.util.Date;
import com.fasterxml.jackson.annotation.JsonFormat;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;
import ai.lawyers.common.annotation.Excel;
import ai.lawyers.common.core.domain.BaseEntity;

/**
 * 回访任务对象 ai_return_visit_task
 *
 * @author ai-lawyers
 */
public class AiReturnVisitTask extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    /** 任务ID */
    private Long taskId;

    /** 任务编号 */
    @Excel(name = "任务编号")
    private String taskNo;

    /** 来电号码 */
    @Excel(name = "来电号码")
    private String callerNumber;

    /** 来电人姓名 */
    @Excel(name = "来电人姓名")
    private String callerName;

    /** 计划回访时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @Excel(name = "计划回访时间", width = 30, dateFormat = "yyyy-MM-dd HH:mm:ss")
    private Date planTime;

    /** 实际回访时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @Excel(name = "实际回访时间", width = 30, dateFormat = "yyyy-MM-dd HH:mm:ss")
    private Date actualTime;

    /** 分配人/受理人 */
    @Excel(name = "受理人")
    private String assignee;

    /** 任务状态（0待回访 1已完成 2已逾期） */
    @Excel(name = "状态", readConverterExp = "0=待回访,1=已完成,2=已逾期")
    private String status;

    /** 优先级（1高 2中 3低） */
    @Excel(name = "优先级", readConverterExp = "1=高,2=中,3=低")
    private String priority;

    /** 回访结果 */
    @Excel(name = "回访结果")
    private String visitResult;

    public void setTaskId(Long taskId)
    {
        this.taskId = taskId;
    }

    public Long getTaskId()
    {
        return taskId;
    }

    public void setTaskNo(String taskNo)
    {
        this.taskNo = taskNo;
    }

    public String getTaskNo()
    {
        return taskNo;
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

    public void setPlanTime(Date planTime)
    {
        this.planTime = planTime;
    }

    public Date getPlanTime()
    {
        return planTime;
    }

    public void setActualTime(Date actualTime)
    {
        this.actualTime = actualTime;
    }

    public Date getActualTime()
    {
        return actualTime;
    }

    public void setAssignee(String assignee)
    {
        this.assignee = assignee;
    }

    public String getAssignee()
    {
        return assignee;
    }

    public void setStatus(String status)
    {
        this.status = status;
    }

    public String getStatus()
    {
        return status;
    }

    public void setPriority(String priority)
    {
        this.priority = priority;
    }

    public String getPriority()
    {
        return priority;
    }

    public void setVisitResult(String visitResult)
    {
        this.visitResult = visitResult;
    }

    public String getVisitResult()
    {
        return visitResult;
    }

    @Override
    public String toString() {
        return new ToStringBuilder(this, ToStringStyle.MULTI_LINE_STYLE)
            .append("taskId", getTaskId())
            .append("taskNo", getTaskNo())
            .append("callerNumber", getCallerNumber())
            .append("callerName", getCallerName())
            .append("planTime", getPlanTime())
            .append("actualTime", getActualTime())
            .append("assignee", getAssignee())
            .append("status", getStatus())
            .append("priority", getPriority())
            .append("visitResult", getVisitResult())
            .append("createBy", getCreateBy())
            .append("createTime", getCreateTime())
            .append("updateBy", getUpdateBy())
            .append("updateTime", getUpdateTime())
            .append("remark", getRemark())
            .toString();
    }
}
