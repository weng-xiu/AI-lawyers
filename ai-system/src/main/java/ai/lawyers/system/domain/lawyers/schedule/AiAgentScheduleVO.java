package ai.lawyers.system.domain.lawyers.schedule;

import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;

/**
 * 坐席排班列表展示对象
 *
 * 在排班基础字段上扩展坐席名称、班次名称与班次起止时间，用于列表/日历展示。
 */
public class AiAgentScheduleVO extends AiAgentSchedule
{
    private static final long serialVersionUID = 1L;

    /** 坐席名称（来自 ai_call_agent_status.agent_name） */
    private String agentName;

    /** 班次名称（来自 ai_work_shift.shift_name） */
    private String shiftName;

    /** 班次上班时间(HH:mm) */
    private String startTime;

    /** 班次下班时间(HH:mm) */
    private String endTime;

    public String getAgentName()
    {
        return agentName;
    }

    public void setAgentName(String agentName)
    {
        this.agentName = agentName;
    }

    public String getShiftName()
    {
        return shiftName;
    }

    public void setShiftName(String shiftName)
    {
        this.shiftName = shiftName;
    }

    public String getStartTime()
    {
        return startTime;
    }

    public void setStartTime(String startTime)
    {
        this.startTime = startTime;
    }

    public String getEndTime()
    {
        return endTime;
    }

    public void setEndTime(String endTime)
    {
        this.endTime = endTime;
    }

    @Override
    public String toString()
    {
        return new ToStringBuilder(this, ToStringStyle.MULTI_LINE_STYLE)
            .append("scheduleId", getScheduleId())
            .append("agentId", getAgentId())
            .append("agentName", getAgentName())
            .append("shiftId", getShiftId())
            .append("shiftName", getShiftName())
            .append("scheduleDate", getScheduleDate())
            .append("startTime", getStartTime())
            .append("endTime", getEndTime())
            .append("scheduleType", getScheduleType())
            .append("status", getStatus())
            .append("checkInTime", getCheckInTime())
            .append("checkOutTime", getCheckOutTime())
            .toString();
    }
}
