package ai.lawyers.system.domain.lawyers.ivr;

import ai.lawyers.common.core.domain.BaseEntity;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;

public class AiIvrEdge extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    private Long edgeId;
    private Long flowId;
    private Long sourceNodeId;
    private Long targetNodeId;
    private String edgeLabel;
    private String conditionExpr;
    private Integer sortOrder;

    public void setEdgeId(Long edgeId) { this.edgeId = edgeId; }
    public Long getEdgeId() { return edgeId; }

    public void setFlowId(Long flowId) { this.flowId = flowId; }
    public Long getFlowId() { return flowId; }

    public void setSourceNodeId(Long sourceNodeId) { this.sourceNodeId = sourceNodeId; }
    public Long getSourceNodeId() { return sourceNodeId; }

    public void setTargetNodeId(Long targetNodeId) { this.targetNodeId = targetNodeId; }
    public Long getTargetNodeId() { return targetNodeId; }

    public void setEdgeLabel(String edgeLabel) { this.edgeLabel = edgeLabel; }
    public String getEdgeLabel() { return edgeLabel; }

    public void setConditionExpr(String conditionExpr) { this.conditionExpr = conditionExpr; }
    public String getConditionExpr() { return conditionExpr; }

    public void setSortOrder(Integer sortOrder) { this.sortOrder = sortOrder; }
    public Integer getSortOrder() { return sortOrder; }

    @Override
    public String toString() {
        return new ToStringBuilder(this, ToStringStyle.MULTI_LINE_STYLE)
            .append("edgeId", getEdgeId())
            .append("flowId", getFlowId())
            .append("sourceNodeId", getSourceNodeId())
            .append("targetNodeId", getTargetNodeId())
            .append("edgeLabel", getEdgeLabel())
            .append("conditionExpr", getConditionExpr())
            .toString();
    }
}
