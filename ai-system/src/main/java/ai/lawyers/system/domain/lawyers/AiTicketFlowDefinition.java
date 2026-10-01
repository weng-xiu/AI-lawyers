package ai.lawyers.system.domain.lawyers;

import ai.lawyers.common.core.domain.BaseEntity;

/**
 * P1-6：工单流程状态机定义 ai_ticket_flow_definition。
 *
 * <p>一行一条"源状态 + 动作 → 目标状态"规则，含允许角色；配置即生效，
 * 新工单类型不改代码。不引入 Flowable，保持最小复杂度。</p>
 *
 * @author ai-lawyers
 */
public class AiTicketFlowDefinition extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    private Long flowId;
    private String flowCode;
    private String statusFrom;
    private String actionCode;
    private String actionName;
    private String targetStatus;
    private String roleKey;
    private Integer sortNo;
    private String status;

    public Long getFlowId() { return flowId; }
    public void setFlowId(Long flowId) { this.flowId = flowId; }

    public String getFlowCode() { return flowCode; }
    public void setFlowCode(String flowCode) { this.flowCode = flowCode; }

    public String getStatusFrom() { return statusFrom; }
    public void setStatusFrom(String statusFrom) { this.statusFrom = statusFrom; }

    public String getActionCode() { return actionCode; }
    public void setActionCode(String actionCode) { this.actionCode = actionCode; }

    public String getActionName() { return actionName; }
    public void setActionName(String actionName) { this.actionName = actionName; }

    public String getTargetStatus() { return targetStatus; }
    public void setTargetStatus(String targetStatus) { this.targetStatus = targetStatus; }

    public String getRoleKey() { return roleKey; }
    public void setRoleKey(String roleKey) { this.roleKey = roleKey; }

    public Integer getSortNo() { return sortNo; }
    public void setSortNo(Integer sortNo) { this.sortNo = sortNo; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
}
