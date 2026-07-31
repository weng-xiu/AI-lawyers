package ai.lawyers.system.mapper.lawyers.ivr;

import java.util.List;
import ai.lawyers.system.domain.lawyers.ivr.AiIvrFlow;

public interface AiIvrFlowMapper
{
    public AiIvrFlow selectAiIvrFlowByFlowId(Long flowId);

    public List<AiIvrFlow> selectAiIvrFlowList(AiIvrFlow aiIvrFlow);

    public int insertAiIvrFlow(AiIvrFlow aiIvrFlow);

    public int updateAiIvrFlow(AiIvrFlow aiIvrFlow);

    public int deleteAiIvrFlowByFlowId(Long flowId);

    public int deleteAiIvrFlowByFlowIds(Long[] flowIds);

    public AiIvrFlow selectDefaultFlow();

    public List<AiIvrFlow> selectPublishedFlows();
}
