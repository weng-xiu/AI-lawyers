package ai.lawyers.system.service.impl.lawyers.outbound;

import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import ai.lawyers.system.domain.lawyers.outbound.AiOutboundCallee;
import ai.lawyers.system.mapper.lawyers.outbound.AiOutboundCalleeMapper;
import ai.lawyers.system.service.lawyers.outbound.IAiOutboundCalleeService;

@Service
public class AiOutboundCalleeServiceImpl implements IAiOutboundCalleeService
{
    @Autowired
    private AiOutboundCalleeMapper aiOutboundCalleeMapper;

    @Override
    public AiOutboundCallee selectAiOutboundCalleeByCalleeId(Long calleeId)
    {
        return aiOutboundCalleeMapper.selectAiOutboundCalleeByCalleeId(calleeId);
    }

    @Override
    public List<AiOutboundCallee> selectAiOutboundCalleeList(AiOutboundCallee aiOutboundCallee)
    {
        return aiOutboundCalleeMapper.selectAiOutboundCalleeList(aiOutboundCallee);
    }

    @Override
    public List<AiOutboundCallee> selectAiOutboundCalleeByTaskId(Long taskId)
    {
        return aiOutboundCalleeMapper.selectAiOutboundCalleeByTaskId(taskId);
    }

    @Override
    public int insertAiOutboundCallee(AiOutboundCallee aiOutboundCallee)
    {
        return aiOutboundCalleeMapper.insertAiOutboundCallee(aiOutboundCallee);
    }

    @Override
    public int batchInsertCallees(Long taskId, List<AiOutboundCallee> callees)
    {
        if (callees == null || callees.isEmpty()) {
            return 0;
        }
        for (AiOutboundCallee callee : callees) {
            callee.setTaskId(taskId);
        }
        return aiOutboundCalleeMapper.batchInsertCallees(callees);
    }

    @Override
    public int updateAiOutboundCallee(AiOutboundCallee aiOutboundCallee)
    {
        return aiOutboundCalleeMapper.updateAiOutboundCallee(aiOutboundCallee);
    }

    @Override
    public int deleteAiOutboundCalleeByCalleeId(Long calleeId)
    {
        return aiOutboundCalleeMapper.deleteAiOutboundCalleeByCalleeId(calleeId);
    }

    @Override
    public int deleteAiOutboundCalleeByTaskId(Long taskId)
    {
        return aiOutboundCalleeMapper.deleteAiOutboundCalleeByTaskId(taskId);
    }

    @Override
    public int deleteAiOutboundCalleeByCalleeIds(Long[] calleeIds)
    {
        return aiOutboundCalleeMapper.deleteAiOutboundCalleeByCalleeIds(calleeIds);
    }

    @Override
    public int importCalleesFromExcel(Long taskId, List<AiOutboundCallee> callees)
    {
        return batchInsertCallees(taskId, callees);
    }
}
