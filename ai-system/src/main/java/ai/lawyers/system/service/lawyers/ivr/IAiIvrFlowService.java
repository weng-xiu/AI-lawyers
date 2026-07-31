package ai.lawyers.system.service.lawyers.ivr;

import java.util.List;
import ai.lawyers.system.domain.lawyers.ivr.AiIvrFlow;

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

    public int saveFlowDesign(Long flowId, String flowData);
}
