package ai.lawyers.system.service.lawyers.ivr;

import java.util.List;
import ai.lawyers.system.domain.lawyers.ivr.AiIvrFlow;
import ai.lawyers.system.domain.lawyers.ivr.AiIvrNode;
import ai.lawyers.system.domain.lawyers.ivr.AiIvrEdge;

public interface IAiIvrFlowService
{
    public AiIvrFlow selectAiIvrFlowByFlowId(Long flowId);

    public List<AiIvrFlow> selectAiIvrFlowList(AiIvrFlow aiIvrFlow);

    public int insertAiIvrFlow(AiIvrFlow aiIvrFlow);

    public int updateAiIvrFlow(AiIvrFlow aiIvrFlow);

    public int deleteAiIvrFlowByFlowId(Long flowId);

    public int deleteAiIvrFlowByFlowIds(Long[] flowIds);

    public AiIvrFlow selectDefaultFlow();

    public List<AiIvrFlow> selectPublishedFlows();

    public int publishFlow(Long flowId);

    /**
     * 保存IVR流程设计（流程定义JSON + 节点 + 连线，整体覆盖保存）
     *
     * @param flowId 流程ID
     * @param flowData 流程定义JSON（LogicFlow格式）
     * @param nodes 节点列表（nodeId可为前端临时ID，保存后自动重映射）
     * @param edges 连线列表（sourceNodeId/targetNodeId为前端临时节点ID，保存后自动重映射）
     * @return 结果
     */
    public int saveFlowDesign(Long flowId, String flowData, List<AiIvrNode> nodes, List<AiIvrEdge> edges);
}
