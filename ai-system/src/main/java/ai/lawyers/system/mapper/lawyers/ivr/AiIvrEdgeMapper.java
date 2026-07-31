package ai.lawyers.system.mapper.lawyers.ivr;

import java.util.List;
import ai.lawyers.system.domain.lawyers.ivr.AiIvrEdge;

public interface AiIvrEdgeMapper
{
    public AiIvrEdge selectAiIvrEdgeByEdgeId(Long edgeId);

    public List<AiIvrEdge> selectAiIvrEdgeList(AiIvrEdge aiIvrEdge);

    public List<AiIvrEdge> selectAiIvrEdgeByFlowId(Long flowId);

    public int insertAiIvrEdge(AiIvrEdge aiIvrEdge);

    public int updateAiIvrEdge(AiIvrEdge aiIvrEdge);

    public int deleteAiIvrEdgeByEdgeId(Long edgeId);

    public int deleteAiIvrEdgeByFlowId(Long flowId);

    public int deleteAiIvrEdgeByEdgeIds(Long[] edgeIds);
}
