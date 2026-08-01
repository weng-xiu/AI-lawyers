package ai.lawyers.system.service.impl.lawyers.ivr;

import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import ai.lawyers.system.domain.lawyers.ivr.AiIvrExecutionLog;
import ai.lawyers.system.mapper.lawyers.ivr.AiIvrExecutionLogMapper;
import ai.lawyers.system.service.lawyers.ivr.IAiIvrExecutionLogService;

@Service
public class AiIvrExecutionLogServiceImpl implements IAiIvrExecutionLogService
{
    @Autowired
    private AiIvrExecutionLogMapper aiIvrExecutionLogMapper;

    @Override
    public AiIvrExecutionLog selectAiIvrExecutionLogByExecId(Long execId)
    {
        return aiIvrExecutionLogMapper.selectAiIvrExecutionLogByExecId(execId);
    }

    @Override
    public List<AiIvrExecutionLog> selectAiIvrExecutionLogList(AiIvrExecutionLog aiIvrExecutionLog)
    {
        return aiIvrExecutionLogMapper.selectAiIvrExecutionLogList(aiIvrExecutionLog);
    }

    @Override
    public AiIvrExecutionLog selectAiIvrExecutionLogBySessionId(String sessionId)
    {
        return aiIvrExecutionLogMapper.selectAiIvrExecutionLogBySessionId(sessionId);
    }

    @Override
    public int insertAiIvrExecutionLog(AiIvrExecutionLog aiIvrExecutionLog)
    {
        return aiIvrExecutionLogMapper.insertAiIvrExecutionLog(aiIvrExecutionLog);
    }

    @Override
    public int updateAiIvrExecutionLog(AiIvrExecutionLog aiIvrExecutionLog)
    {
        return aiIvrExecutionLogMapper.updateAiIvrExecutionLog(aiIvrExecutionLog);
    }
}
