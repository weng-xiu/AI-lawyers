package ai.lawyers.system.domain.lawyers;

import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;
import ai.lawyers.common.annotation.Excel;
import ai.lawyers.common.core.domain.BaseEntity;

/**
 * 工单 SLA 策略对象 ai_sla_policy（F9）
 *
 * <p>按业务类型 + 优先级设定响应/办结时限、预警阈值与逐级升级角色链；
 * TicketSlaScheduleTask 超时扫描时按策略匹配升级。</p>
 *
 * @author ai-lawyers
 */
public class AiSlaPolicy extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    private Long policyId;

    @Excel(name = "策略名称")
    private String policyName;

    /** 业务类型 TICKET/LEGAL_AID/MEDIATION/NOTARY/FORENSIC/ARBITRATION/HOTLINE_12345 */
    @Excel(name = "业务类型")
    private String bizType;

    /** 优先级 1紧急 2普通 3低 */
    @Excel(name = "优先级", readConverterExp = "1=紧急,2=普通,3=低")
    private String priority;

    /** 响应时限（分钟） */
    @Excel(name = "响应时限(分)")
    private Integer respondMinutes;

    /** 办结时限（分钟） */
    @Excel(name = "办结时限(分)")
    private Integer resolveMinutes;

    /** 预警阈值（百分比） */
    @Excel(name = "预警阈值(%)")
    private Integer warnThreshold;

    /** 逐级升级角色链（逗号分隔角色key） */
    private String escalateRoles;

    /** 状态 0启用 1停用 */
    @Excel(name = "状态", readConverterExp = "0=启用,1=停用")
    private String status;

    public Long getPolicyId() { return policyId; }
    public void setPolicyId(Long policyId) { this.policyId = policyId; }

    public String getPolicyName() { return policyName; }
    public void setPolicyName(String policyName) { this.policyName = policyName; }

    public String getBizType() { return bizType; }
    public void setBizType(String bizType) { this.bizType = bizType; }

    public String getPriority() { return priority; }
    public void setPriority(String priority) { this.priority = priority; }

    public Integer getRespondMinutes() { return respondMinutes; }
    public void setRespondMinutes(Integer respondMinutes) { this.respondMinutes = respondMinutes; }

    public Integer getResolveMinutes() { return resolveMinutes; }
    public void setResolveMinutes(Integer resolveMinutes) { this.resolveMinutes = resolveMinutes; }

    public Integer getWarnThreshold() { return warnThreshold; }
    public void setWarnThreshold(Integer warnThreshold) { this.warnThreshold = warnThreshold; }

    public String getEscalateRoles() { return escalateRoles; }
    public void setEscalateRoles(String escalateRoles) { this.escalateRoles = escalateRoles; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    @Override
    public String toString()
    {
        return new ToStringBuilder(this, ToStringStyle.MULTI_LINE_STYLE)
                .append("policyId", getPolicyId())
                .append("policyName", getPolicyName())
                .append("bizType", getBizType())
                .append("priority", getPriority())
                .append("respondMinutes", getRespondMinutes())
                .append("resolveMinutes", getResolveMinutes())
                .append("warnThreshold", getWarnThreshold())
                .append("escalateRoles", getEscalateRoles())
                .append("status", getStatus())
                .toString();
    }
}
