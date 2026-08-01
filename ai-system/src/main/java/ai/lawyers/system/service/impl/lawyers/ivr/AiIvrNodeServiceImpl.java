package ai.lawyers.system.service.impl.lawyers.ivr;

import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import ai.lawyers.system.domain.lawyers.ivr.AiIvrNode;
import ai.lawyers.system.mapper.lawyers.ivr.AiIvrNodeMapper;
import ai.lawyers.system.service.lawyers.ivr.IAiIvrNodeService;

@Service
public class AiIvrNodeServiceImpl implements IAiIvrNodeService
{
    @Autowired
    private AiIvrNodeMapper aiIvrNodeMapper;

    @Override
    public AiIvrNode selectAiIvrNodeByNodeId(Long nodeId)
    {
        return aiIvrNodeMapper.selectAiIvrNodeByNodeId(nodeId);
    }

    @Override
    public List<AiIvrNode> selectAiIvrNodeList(AiIvrNode aiIvrNode)
    {
        return aiIvrNodeMapper.selectAiIvrNodeList(aiIvrNode);
    }

    @Override
    public List<AiIvrNode> selectAiIvrNodeByFlowId(Long flowId)
    {
        return aiIvrNodeMapper.selectAiIvrNodeByFlowId(flowId);
    }

    @Override
    public int insertAiIvrNode(AiIvrNode aiIvrNode)
    {
        return aiIvrNodeMapper.insertAiIvrNode(aiIvrNode);
    }

    @Override
    public int batchInsertNodes(Long flowId, List<AiIvrNode> nodes)
    {
        if (nodes == null || nodes.isEmpty()) {
            return 0;
        }
        int count = 0;
        for (AiIvrNode node : nodes) {
            node.setFlowId(flowId);
            count += aiIvrNodeMapper.insertAiIvrNode(node);
        }
        return count;
    }

    @Override
    public int updateAiIvrNode(AiIvrNode aiIvrNode)
    {
        return aiIvrNodeMapper.updateAiIvrNode(aiIvrNode);
    }

    @Override
    public int deleteAiIvrNodeByNodeId(Long nodeId)
    {
        return aiIvrNodeMapper.deleteAiIvrNodeByNodeId(nodeId);
    }

    @Override
    public int deleteAiIvrNodeByFlowId(Long flowId)
    {
        return aiIvrNodeMapper.deleteAiIvrNodeByFlowId(flowId);
    }

    @Override
    public int deleteAiIvrNodeByNodeIds(Long[] nodeIds)
    {
        return aiIvrNodeMapper.deleteAiIvrNodeByNodeIds(nodeIds);
    }

    @Override
    public int saveFlowNodes(Long flowId, List<AiIvrNode> nodes)
    {
        aiIvrNodeMapper.deleteAiIvrNodeByFlowId(flowId);
        return batchInsertNodes(flowId, nodes);
    }
}
