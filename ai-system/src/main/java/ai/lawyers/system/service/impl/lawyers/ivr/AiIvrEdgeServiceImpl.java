package ai.lawyers.system.service.impl.lawyers.ivr;

import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import ai.lawyers.system.domain.lawyers.ivr.AiIvrEdge;
import ai.lawyers.system.mapper.lawyers.ivr.AiIvrEdgeMapper;
import ai.lawyers.system.service.lawyers.ivr.IAiIvrEdgeService;

@Service
public class AiIvrEdgeServiceImpl implements IAiIvrEdgeService
{
    @Autowired
    private AiIvrEdgeMapper aiIvrEdgeMapper;

    @Override
    public AiIvrEdge selectAiIvrEdgeByEdgeId(Long edgeId)
    {
        return aiIvrEdgeMapper.selectAiIvrEdgeByEdgeId(edgeId);
    }

    @Override
    public List<AiIvrEdge> selectAiIvrEdgeList(AiIvrEdge aiIvrEdge)
    {
        return aiIvrEdgeMapper.selectAiIvrEdgeList(aiIvrEdge);
    }

    @Override
    public List<AiIvrEdge> selectAiIvrEdgeByFlowId(Long flowId)
    {
        return aiIvrEdgeMapper.selectAiIvrEdgeByFlowId(flowId);
    }

    @Override
    public int insertAiIvrEdge(AiIvrEdge aiIvrEdge)
    {
        return aiIvrEdgeMapper.insertAiIvrEdge(aiIvrEdge);
    }

    @Override
    public int batchInsertEdges(Long flowId, List<AiIvrEdge> edges)
    {
        if (edges == null || edges.isEmpty()) {
            return 0;
        }
        int count = 0;
        for (AiIvrEdge edge : edges) {
            edge.setFlowId(flowId);
            count += aiIvrEdgeMapper.insertAiIvrEdge(edge);
        }
        return count;
    }

    @Override
    public int updateAiIvrEdge(AiIvrEdge aiIvrEdge)
    {
        return aiIvrEdgeMapper.updateAiIvrEdge(aiIvrEdge);
    }

    @Override
    public int deleteAiIvrEdgeByEdgeId(Long edgeId)
    {
        return aiIvrEdgeMapper.deleteAiIvrEdgeByEdgeId(edgeId);
    }

    @Override
    public int deleteAiIvrEdgeByFlowId(Long flowId)
    {
        return aiIvrEdgeMapper.deleteAiIvrEdgeByFlowId(flowId);
    }

    @Override
    public int deleteAiIvrEdgeByEdgeIds(Long[] edgeIds)
    {
        return aiIvrEdgeMapper.deleteAiIvrEdgeByEdgeIds(edgeIds);
    }

    @Override
    public int saveFlowEdges(Long flowId, List<AiIvrEdge> edges)
    {
        aiIvrEdgeMapper.deleteAiIvrEdgeByFlowId(flowId);
        return batchInsertEdges(flowId, edges);
    }
}
