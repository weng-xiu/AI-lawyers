package ai.lawyers.system.mapper.lawyers.ivr;

import java.util.List;
import ai.lawyers.system.domain.lawyers.ivr.AiIvrNode;

public interface AiIvrNodeMapper
{
    public AiIvrNode selectAiIvrNodeByNodeId(Long nodeId);

    public List<AiIvrNode> selectAiIvrNodeList(AiIvrNode aiIvrNode);

    public List<AiIvrNode> selectAiIvrNodeByFlowId(Long flowId);

    public int insertAiIvrNode(AiIvrNode aiIvrNode);

    public int updateAiIvrNode(AiIvrNode aiIvrNode);

    public int deleteAiIvrNodeByNodeId(Long nodeId);

    public int deleteAiIvrNodeByFlowId(Long flowId);

    public int deleteAiIvrNodeByNodeIds(Long[] nodeIds);
}
