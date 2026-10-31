package ai.lawyers.system.domain.lawyers.schedule;

import java.util.Date;
import com.fasterxml.jackson.annotation.JsonFormat;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;
import ai.lawyers.common.core.domain.BaseEntity;

/**
 * 坐席换班申请对象 ai_shift_change_request（F9）
 *
 * <p>闭环口径：坐席提交申请 → 班组长审批 → 审批通过后由
 * {@code IAiShiftChangeService} 自动改写 {@code ai_agent_schedule}；
 * 审批前不动排班，驳回/撤销不产生副作用。</p>
 */
public class AiShiftChangeRequest extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    /** 变更类型：互换（申请人班次与对方班次互换） */
    public static final String TYPE_SWAP = "SWAP";

    /** 变更类型：转班（申请人排班整条转给接收坐席） */
    public static final String TYPE_TRANSFER = "TRANSFER";

    /** 状态：待审批 */
    public static final String STATUS_PENDING = "0";

    /** 状态：已通过 */
    public static final String STATUS_APPROVED = "1";

    /** 状态：已驳回 */
    public static final String STATUS_REJECTED = "2";

    /** 状态：已撤销 */
    public static final String STATUS_CANCELLED = "3";

    /** 申请ID */
    private Long requestId;

    /** 申请人坐席ID */
    private Long applicantAgentId;

    /** 申请人待变更排班ID */
    private Long applicantScheduleId;

    /** 变更类型 SWAP互换 TRANSFER转班 */
    private String changeType;

    /** 接收坐席ID（互换时取对方排班坐席，转班由申请人指定） */
    private Long targetAgentId;

    /** 对方排班ID（互换必填，转班为空） */
    private Long targetScheduleId;

    /** 申请原因 */
    private String reason;

    /** 状态 0待审批 1已通过 2已驳回 3已撤销 */
    private String status;

    /** 审批人 */
    private String approver;

    /** 审批时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date approveTime;

    /** 审批意见 */
    private String approveRemark;

    public Long getRequestId()
    {
        return requestId;
    }

    public void setRequestId(Long requestId)
    {
        this.requestId = requestId;
    }

    public Long getApplicantAgentId()
    {
        return applicantAgentId;
    }

    public void setApplicantAgentId(Long applicantAgentId)
    {
        this.applicantAgentId = applicantAgentId;
    }

    public Long getApplicantScheduleId()
    {
        return applicantScheduleId;
    }

    public void setApplicantScheduleId(Long applicantScheduleId)
    {
        this.applicantScheduleId = applicantScheduleId;
    }

    public String getChangeType()
    {
        return changeType;
    }

    public void setChangeType(String changeType)
    {
        this.changeType = changeType;
    }

    public Long getTargetAgentId()
    {
        return targetAgentId;
    }

    public void setTargetAgentId(Long targetAgentId)
    {
        this.targetAgentId = targetAgentId;
    }

    public Long getTargetScheduleId()
    {
        return targetScheduleId;
    }

    public void setTargetScheduleId(Long targetScheduleId)
    {
        this.targetScheduleId = targetScheduleId;
    }

    public String getReason()
    {
        return reason;
    }

    public void setReason(String reason)
    {
        this.reason = reason;
    }

    public String getStatus()
    {
        return status;
    }

    public void setStatus(String status)
    {
        this.status = status;
    }

    public String getApprover()
    {
        return approver;
    }

    public void setApprover(String approver)
    {
        this.approver = approver;
    }

    public Date getApproveTime()
    {
        return approveTime;
    }

    public void setApproveTime(Date approveTime)
    {
        this.approveTime = approveTime;
    }

    public String getApproveRemark()
    {
        return approveRemark;
    }

    public void setApproveRemark(String approveRemark)
    {
        this.approveRemark = approveRemark;
    }

    @Override
    public String toString()
    {
        return new ToStringBuilder(this, ToStringStyle.MULTI_LINE_STYLE)
            .append("requestId", getRequestId())
            .append("applicantAgentId", getApplicantAgentId())
            .append("applicantScheduleId", getApplicantScheduleId())
            .append("changeType", getChangeType())
            .append("targetAgentId", getTargetAgentId())
            .append("targetScheduleId", getTargetScheduleId())
            .append("reason", getReason())
            .append("status", getStatus())
            .append("approver", getApprover())
            .append("approveTime", getApproveTime())
            .append("approveRemark", getApproveRemark())
            .append("createBy", getCreateBy())
            .append("createTime", getCreateTime())
            .append("updateBy", getUpdateBy())
            .append("updateTime", getUpdateTime())
            .toString();
    }
}
