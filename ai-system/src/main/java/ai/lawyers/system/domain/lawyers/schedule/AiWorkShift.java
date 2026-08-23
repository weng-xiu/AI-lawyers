package ai.lawyers.system.domain.lawyers.schedule;

import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;
import ai.lawyers.common.annotation.Excel;
import ai.lawyers.common.core.domain.BaseEntity;

/**
 * 班次定义对象 ai_work_shift
 *
 * 描述坐席排班可用的班次模板，包含上/下班时间、跨天标记与休息时段。
 */
public class AiWorkShift extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    /** 班次ID */
    private Long shiftId;

    /** 班次名称(如早班/晚班) */
    @Excel(name = "班次名称")
    private String shiftName;

    /** 上班时间(HH:mm) */
    @Excel(name = "上班时间")
    private String startTime;

    /** 下班时间(HH:mm) */
    @Excel(name = "下班时间")
    private String endTime;

    /** 是否跨天 0否 1是 */
    @Excel(name = "是否跨天", readConverterExp = "0=否,1=是")
    private Integer isOvernight;

    /** 休息开始时间(HH:mm) */
    @Excel(name = "休息开始")
    private String breakStart;

    /** 休息结束时间(HH:mm) */
    @Excel(name = "休息结束")
    private String breakEnd;

    /** 状态 0停用 1启用 */
    @Excel(name = "状态", readConverterExp = "0=停用,1=启用")
    private Integer status;

    public Long getShiftId()
    {
        return shiftId;
    }

    public void setShiftId(Long shiftId)
    {
        this.shiftId = shiftId;
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

    public Integer getIsOvernight()
    {
        return isOvernight;
    }

    public void setIsOvernight(Integer isOvernight)
    {
        this.isOvernight = isOvernight;
    }

    public String getBreakStart()
    {
        return breakStart;
    }

    public void setBreakStart(String breakStart)
    {
        this.breakStart = breakStart;
    }

    public String getBreakEnd()
    {
        return breakEnd;
    }

    public void setBreakEnd(String breakEnd)
    {
        this.breakEnd = breakEnd;
    }

    public Integer getStatus()
    {
        return status;
    }

    public void setStatus(Integer status)
    {
        this.status = status;
    }

    @Override
    public String toString()
    {
        return new ToStringBuilder(this, ToStringStyle.MULTI_LINE_STYLE)
            .append("shiftId", getShiftId())
            .append("shiftName", getShiftName())
            .append("startTime", getStartTime())
            .append("endTime", getEndTime())
            .append("isOvernight", getIsOvernight())
            .append("breakStart", getBreakStart())
            .append("breakEnd", getBreakEnd())
            .append("status", getStatus())
            .append("createBy", getCreateBy())
            .append("createTime", getCreateTime())
            .append("updateBy", getUpdateBy())
            .append("updateTime", getUpdateTime())
            .append("remark", getRemark())
            .toString();
    }
}
