package ai.lawyers.system.domain.lawyers;

import ai.lawyers.common.core.domain.BaseEntity;

/**
 * F2：工单流程画布节点布局 ai_ticket_flow_layout。
 *
 * <p>一行表示某流程下某个状态节点在画布中的坐标；布局与流转规则解耦，
 * 规则增删不影响节点位置，布局缺失时前端回退到默认排布。</p>
 *
 * @author ai-lawyers
 */
public class AiTicketFlowLayout extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    private Long layoutId;
    private String flowCode;
    private String statusCode;
    private Integer posX;
    private Integer posY;

    public Long getLayoutId() { return layoutId; }
    public void setLayoutId(Long layoutId) { this.layoutId = layoutId; }

    public String getFlowCode() { return flowCode; }
    public void setFlowCode(String flowCode) { this.flowCode = flowCode; }

    public String getStatusCode() { return statusCode; }
    public void setStatusCode(String statusCode) { this.statusCode = statusCode; }

    public Integer getPosX() { return posX; }
    public void setPosX(Integer posX) { this.posX = posX; }

    public Integer getPosY() { return posY; }
    public void setPosY(Integer posY) { this.posY = posY; }
}
