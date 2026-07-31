package ai.lawyers.system.domain.lawyers.ivr;

import ai.lawyers.common.core.domain.BaseEntity;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;

public class AiIvrNode extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    private Long nodeId;

    private Long flowId;

    private String nodeType;

    private String nodeName;

    private String nodeConfig;

    private Integer positionX;

    private Integer positionY;

    private Integer sortOrder;

    public void setNodeId(Long nodeId) { this.nodeId = nodeId; }
    public Long getNodeId() { return nodeId; }

    public void setFlowId(Long flowId) { this.flowId = flowId; }
    public Long getFlowId() { return flowId; }

    public void setNodeType(String nodeType) { this.nodeType = nodeType; }
    public String getNodeType() { return nodeType; }

    public void setNodeName(String nodeName) { this.nodeName = nodeName; }
    public String getNodeName() { return nodeName; }

    public void setNodeConfig(String nodeConfig) { this.nodeConfig = nodeConfig; }
    public String getNodeConfig() { return nodeConfig; }

    public void setPositionX(Integer positionX) { this.positionX = positionX; }
    public Integer getPositionX() { return positionX; }

    public void setPositionY(Integer positionY) { this.positionY = positionY; }
    public Integer getPositionY() { return positionY; }

    public void setSortOrder(Integer sortOrder) { this.sortOrder = sortOrder; }
    public Integer getSortOrder() { return sortOrder; }

    @Override
    public String toString() {
        return new ToStringBuilder(this, ToStringStyle.MULTI_LINE_STYLE)
            .append("nodeId", getNodeId())
            .append("flowId", getFlowId())
            .append("nodeType", getNodeType())
            .append("nodeName", getNodeName())
            .append("positionX", getPositionX())
            .append("positionY", getPositionY())
            .append("sortOrder", getSortOrder())
            .toString();
    }
}
