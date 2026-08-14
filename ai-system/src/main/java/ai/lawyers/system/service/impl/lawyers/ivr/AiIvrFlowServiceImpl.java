package ai.lawyers.system.service.impl.lawyers.ivr;

import java.util.List;
import java.util.HashMap;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ai.lawyers.system.domain.lawyers.ivr.AiIvrFlow;
import ai.lawyers.system.domain.lawyers.ivr.AiIvrNode;
import ai.lawyers.system.domain.lawyers.ivr.AiIvrEdge;
import ai.lawyers.system.mapper.lawyers.ivr.AiIvrFlowMapper;
import ai.lawyers.system.mapper.lawyers.ivr.AiIvrNodeMapper;
import ai.lawyers.system.mapper.lawyers.ivr.AiIvrEdgeMapper;
import ai.lawyers.system.service.lawyers.ivr.IAiIvrFlowService;

@Service
public class AiIvrFlowServiceImpl implements IAiIvrFlowService
{
    @Autowired
    private AiIvrFlowMapper aiIvrFlowMapper;

    @Autowired
    private AiIvrNodeMapper aiIvrNodeMapper;

    @Autowired
    private AiIvrEdgeMapper aiIvrEdgeMapper;

    @Override
    public AiIvrFlow selectAiIvrFlowByFlowId(Long flowId)
    {
        return aiIvrFlowMapper.selectAiIvrFlowByFlowId(flowId);
    }

    @Override
    public List<AiIvrFlow> selectAiIvrFlowList(AiIvrFlow aiIvrFlow)
    {
        return aiIvrFlowMapper.selectAiIvrFlowList(aiIvrFlow);
    }

    @Override
    public int insertAiIvrFlow(AiIvrFlow aiIvrFlow)
    {
        return aiIvrFlowMapper.insertAiIvrFlow(aiIvrFlow);
    }

    @Override
    public int updateAiIvrFlow(AiIvrFlow aiIvrFlow)
    {
        return aiIvrFlowMapper.updateAiIvrFlow(aiIvrFlow);
    }

    @Override
    public int deleteAiIvrFlowByFlowId(Long flowId)
    {
        return aiIvrFlowMapper.deleteAiIvrFlowByFlowId(flowId);
    }

    @Override
    public int deleteAiIvrFlowByFlowIds(Long[] flowIds)
    {
        return aiIvrFlowMapper.deleteAiIvrFlowByFlowIds(flowIds);
    }

    @Override
    public AiIvrFlow selectDefaultFlow()
    {
        return aiIvrFlowMapper.selectDefaultFlow();
    }

    @Override
    public List<AiIvrFlow> selectPublishedFlows()
    {
        return aiIvrFlowMapper.selectPublishedFlows();
    }

    @Override
    public int publishFlow(Long flowId)
    {
        AiIvrFlow flow = new AiIvrFlow();
        flow.setFlowId(flowId);
        flow.setStatus("1");
        return aiIvrFlowMapper.updateAiIvrFlow(flow);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int saveFlowDesign(Long flowId, String flowData, List<AiIvrNode> nodes, List<AiIvrEdge> edges)
    {
        if (flowId == null)
        {
            return 0;
        }
        // 1. 更新流程定义JSON
        AiIvrFlow flow = new AiIvrFlow();
        flow.setFlowId(flowId);
        flow.setFlowData(flowData);
        int rows = aiIvrFlowMapper.updateAiIvrFlow(flow);

        // 2. 删除旧节点和连线（整体覆盖保存）
        aiIvrNodeMapper.deleteAiIvrNodeByFlowId(flowId);
        aiIvrEdgeMapper.deleteAiIvrEdgeByFlowId(flowId);

        // 3. 重新插入节点，并记录前端临时ID到新ID的映射
        Map<Long, Long> idMap = new HashMap<Long, Long>();
        int nodeOrder = 0;
        if (nodes != null && !nodes.isEmpty())
        {
            for (AiIvrNode node : nodes)
            {
                Long tempId = node.getNodeId();
                node.setNodeId(null);
                node.setFlowId(flowId);
                if (node.getSortOrder() == null)
                {
                    node.setSortOrder(nodeOrder);
                }
                aiIvrNodeMapper.insertAiIvrNode(node);
                if (tempId != null && node.getNodeId() != null)
                {
                    idMap.put(tempId, node.getNodeId());
                }
                nodeOrder++;
            }
        }

        // 4. 重新插入连线，并将临时节点ID替换为数据库节点ID
        int edgeOrder = 0;
        if (edges != null && !edges.isEmpty())
        {
            for (AiIvrEdge edge : edges)
            {
                edge.setEdgeId(null);
                edge.setFlowId(flowId);
                if (edge.getSortOrder() == null)
                {
                    edge.setSortOrder(edgeOrder);
                }
                Long sourceId = edge.getSourceNodeId();
                Long targetId = edge.getTargetNodeId();
                if (sourceId != null && idMap.containsKey(sourceId))
                {
                    sourceId = idMap.get(sourceId);
                }
                if (targetId != null && idMap.containsKey(targetId))
                {
                    targetId = idMap.get(targetId);
                }
                edge.setSourceNodeId(sourceId);
                edge.setTargetNodeId(targetId);
                aiIvrEdgeMapper.insertAiIvrEdge(edge);
                edgeOrder++;
            }
        }
        return rows;
    }
}
