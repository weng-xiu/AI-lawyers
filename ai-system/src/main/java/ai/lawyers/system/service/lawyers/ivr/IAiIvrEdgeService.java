package ai.lawyers.system.service.lawyers.ivr;

import java.util.List;
import ai.lawyers.system.domain.lawyers.ivr.AiIvrEdge;

public interface IAiIvrEdgeService
{
    public AiIvrEdge selectAiIvrEdgeByEdgeId(Long edgeId);

    public List<AiIvrEdge> selectAiIvrEdgeList(AiIvrEdge aiIvrEdge);

    public List<AiIvrEdge> selectAiIvrEdgeByFlowId(Long flowId);

    public int insertAiIvrEdge(AiIvrEdge aiIvrEdge);

    public int batchInsertEdges(Long flowId, List<AiIvrEdge> edges);

    public int updateAiIvrEdge(AiIvrEdge aiIvrEdge);

    public int deleteAiIvrEdgeByEdgeId(Long edgeId);

    public int deleteAiIvrEdgeByFlowId(Long flowId);

    public int deleteAiIvrEdgeByEdgeIds(Long[] edgeIds);

    public int saveFlowEdges(Long flowId, List<AiIvrEdge> edges);
}
