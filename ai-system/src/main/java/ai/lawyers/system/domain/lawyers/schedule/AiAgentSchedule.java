package ai.lawyers.system.domain.lawyers.schedule;

import java.util.Date;
import com.fasterxml.jackson.annotation.JsonFormat;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;
import ai.lawyers.common.annotation.Excel;
import ai.lawyers.common.core.domain.BaseEntity;

/**
 * 坐席排班对象 ai_agent_schedule
 *
 * 记录某坐席在某日的班次安排，以及签到/签退时间。同一坐席同一天仅允许一条有效排班。
 */
public class AiAgentSchedule extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    /** 排班ID */
    private Long scheduleId;

    /** 坐席ID */
    @Excel(name = "坐席ID")
    private Long agentId;

    /** 班次ID */
    @Excel(name = "班次ID")
    private Long shiftId;

    /** 排班日期(yyyy-MM-dd) */
    @Excel(name = "排班日期")
    private String scheduleDate;

    /** 排班类型 1正常 2加班 3调休 4请假 */
    @Excel(name = "排班类型", readConverterExp = "1=正常,2=加班,3=调休,4=请假")
    private Integer scheduleType;

    /** 状态 0已取消 1有效 */
    @Excel(name = "状态", readConverterExp = "0=已取消,1=有效")
    private Integer status;

    /** 签到时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @Excel(name = "签到时间", width = 30, dateFormat = "yyyy-MM-dd HH:mm:ss")
    private Date checkInTime;

    /** 签退时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @Excel(name = "签退时间", width = 30, dateFormat = "yyyy-MM-dd HH:mm:ss")
    private Date checkOutTime;

    public Long getScheduleId()
    {
        return scheduleId;
    }

    public void setScheduleId(Long scheduleId)
    {
        this.scheduleId = scheduleId;
    }

    public Long getAgentId()
    {
        return agentId;
    }

    public void setAgentId(Long agentId)
    {
        this.agentId = agentId;
    }

    public Long getShiftId()
    {
        return shiftId;
    }

    public void setShiftId(Long shiftId)
    {
        this.shiftId = shiftId;
    }

    public String getScheduleDate()
    {
        return scheduleDate;
    }

    public void setScheduleDate(String scheduleDate)
    {
        this.scheduleDate = scheduleDate;
    }

    public Integer getScheduleType()
    {
        return scheduleType;
    }

    public void setScheduleType(Integer scheduleType)
    {
        this.scheduleType = scheduleType;
    }

    public Integer getStatus()
    {
        return status;
    }

    public void setStatus(Integer status)
    {
        this.status = status;
    }

    public Date getCheckInTime()
    {
        return checkInTime;
    }

    public void setCheckInTime(Date checkInTime)
    {
        this.checkInTime = checkInTime;
    }

    public Date getCheckOutTime()
    {
        return checkOutTime;
    }

    public void setCheckOutTime(Date checkOutTime)
    {
        this.checkOutTime = checkOutTime;
    }

    @Override
    public String toString()
    {
        return new ToStringBuilder(this, ToStringStyle.MULTI_LINE_STYLE)
            .append("scheduleId", getScheduleId())
            .append("agentId", getAgentId())
            .append("shiftId", getShiftId())
            .append("scheduleDate", getScheduleDate())
            .append("scheduleType", getScheduleType())
            .append("status", getStatus())
            .append("checkInTime", getCheckInTime())
            .append("checkOutTime", getCheckOutTime())
            .append("createBy", getCreateBy())
            .append("createTime", getCreateTime())
            .append("updateBy", getUpdateBy())
            .append("updateTime", getUpdateTime())
            .append("remark", getRemark())
            .toString();
    }
}
