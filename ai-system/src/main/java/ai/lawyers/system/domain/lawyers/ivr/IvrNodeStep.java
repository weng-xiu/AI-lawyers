package ai.lawyers.system.domain.lawyers.ivr;

/**
 * IVR 流程执行步骤（节点快照）
 */
public class IvrNodeStep
{
    private Long nodeId;
    private String nodeType;
    private String nodeName;
    private String action;
    private String detail;

    public IvrNodeStep() {}

    public IvrNodeStep(Long nodeId, String nodeType, String nodeName, String action, String detail)
    {
        this.nodeId = nodeId;
        this.nodeType = nodeType;
        this.nodeName = nodeName;
        this.action = action;
        this.detail = detail;
    }

    public Long getNodeId() { return nodeId; }
    public void setNodeId(Long nodeId) { this.nodeId = nodeId; }

    public String getNodeType() { return nodeType; }
    public void setNodeType(String nodeType) { this.nodeType = nodeType; }

    public String getNodeName() { return nodeName; }
    public void setNodeName(String nodeName) { this.nodeName = nodeName; }

    public String getAction() { return action; }
    public void setAction(String action) { this.action = action; }

    public String getDetail() { return detail; }
    public void setDetail(String detail) { this.detail = detail; }
}
