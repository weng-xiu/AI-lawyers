package ai.lawyers.web.controller.lawyers.ivr;

import java.util.List;
import ai.lawyers.system.domain.lawyers.ivr.AiIvrNode;
import ai.lawyers.system.domain.lawyers.ivr.AiIvrEdge;

/**
 * IVR流程设计保存请求
 */
public class IvrFlowDesignRequest
{
    /** 流程ID */
    private Long flowId;

    /** 流程定义JSON（LogicFlow格式） */
    private String flowData;

    /** 节点列表（nodeId可为前端临时ID） */
    private List<AiIvrNode> nodes;

    /** 连线列表（引用前端临时节点ID） */
    private List<AiIvrEdge> edges;

    public Long getFlowId()
    {
        return flowId;
    }

    public void setFlowId(Long flowId)
    {
        this.flowId = flowId;
    }

    public String getFlowData()
    {
        return flowData;
    }

    public void setFlowData(String flowData)
    {
        this.flowData = flowData;
    }

    public List<AiIvrNode> getNodes()
    {
        return nodes;
    }

    public void setNodes(List<AiIvrNode> nodes)
    {
        this.nodes = nodes;
    }

    public List<AiIvrEdge> getEdges()
    {
        return edges;
    }

    public void setEdges(List<AiIvrEdge> edges)
    {
        this.edges = edges;
    }
}
