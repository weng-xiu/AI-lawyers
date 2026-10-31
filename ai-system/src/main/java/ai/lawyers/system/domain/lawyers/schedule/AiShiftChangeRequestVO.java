package ai.lawyers.system.domain.lawyers.schedule;

/**
 * 换班申请列表展示对象（F9，非持久化）
 *
 * <p>列表页需要同时展示申请人/接收坐席姓名与双方班次，
 * 避免前端为每行再发查询，故由 Mapper 一次 join 出展示字段。</p>
 */
public class AiShiftChangeRequestVO extends AiShiftChangeRequest
{
    private static final long serialVersionUID = 1L;

    /** 申请人坐席姓名 */
    private String applicantAgentName;

    /** 接收坐席姓名 */
    private String targetAgentName;

    /** 申请人当前班次名称 */
    private String applicantShiftName;

    /** 接受坐席（互换时）当前班次名称 */
    private String targetShiftName;

    /** 申请人排班日期 yyyy-MM-dd */
    private String applicantScheduleDate;

    /** 对方排班日期 yyyy-MM-dd */
    private String targetScheduleDate;

    public String getApplicantAgentName()
    {
        return applicantAgentName;
    }

    public void setApplicantAgentName(String applicantAgentName)
    {
        this.applicantAgentName = applicantAgentName;
    }

    public String getTargetAgentName()
    {
        return targetAgentName;
    }

    public void setTargetAgentName(String targetAgentName)
    {
        this.targetAgentName = targetAgentName;
    }

    public String getApplicantShiftName()
    {
        return applicantShiftName;
    }

    public void setApplicantShiftName(String applicantShiftName)
    {
        this.applicantShiftName = applicantShiftName;
    }

    public String getTargetShiftName()
    {
        return targetShiftName;
    }

    public void setTargetShiftName(String targetShiftName)
    {
        this.targetShiftName = targetShiftName;
    }

    public String getApplicantScheduleDate()
    {
        return applicantScheduleDate;
    }

    public void setApplicantScheduleDate(String applicantScheduleDate)
    {
        this.applicantScheduleDate = applicantScheduleDate;
    }

    public String getTargetScheduleDate()
    {
        return targetScheduleDate;
    }

    public void setTargetScheduleDate(String targetScheduleDate)
    {
        this.targetScheduleDate = targetScheduleDate;
    }
}
