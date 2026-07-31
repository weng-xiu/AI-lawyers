package ai.lawyers.system.service.impl.lawyers.ivr;

import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import ai.lawyers.system.domain.lawyers.ivr.AiIvrFlow;
import ai.lawyers.system.mapper.lawyers.ivr.AiIvrFlowMapper;
import ai.lawyers.system.service.lawyers.ivr.IAiIvrFlowService;

@Service
public class AiIvrFlowServiceImpl implements IAiIvrFlowService
{
    @Autowired
    private AiIvrFlowMapper aiIvrFlowMapper;

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
    public int saveFlowDesign(Long flowId, String flowData)
    {
        AiIvrFlow flow = new AiIvrFlow();
        flow.setFlowId(flowId);
        flow.setFlowData(flowData);
        return aiIvrFlowMapper.updateAiIvrFlow(flow);
    }
}
