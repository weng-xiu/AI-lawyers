package ai.lawyers.system.mapper.lawyers.ivr;

import java.util.List;
import ai.lawyers.system.domain.lawyers.ivr.AiIvrExecutionLog;

public interface AiIvrExecutionLogMapper
{
    public AiIvrExecutionLog selectAiIvrExecutionLogByExecId(Long execId);

    public List<AiIvrExecutionLog> selectAiIvrExecutionLogList(AiIvrExecutionLog aiIvrExecutionLog);

    public AiIvrExecutionLog selectAiIvrExecutionLogBySessionId(String sessionId);

    public int insertAiIvrExecutionLog(AiIvrExecutionLog aiIvrExecutionLog);

    public int updateAiIvrExecutionLog(AiIvrExecutionLog aiIvrExecutionLog);
}
