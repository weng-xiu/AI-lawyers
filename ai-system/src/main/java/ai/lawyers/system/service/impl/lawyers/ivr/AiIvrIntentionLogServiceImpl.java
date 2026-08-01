package ai.lawyers.system.service.impl.lawyers.ivr;

import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import ai.lawyers.system.domain.lawyers.ivr.AiIvrIntentionLog;
import ai.lawyers.system.mapper.lawyers.ivr.AiIvrIntentionLogMapper;
import ai.lawyers.system.service.lawyers.ivr.IAiIvrIntentionLogService;

@Service
public class AiIvrIntentionLogServiceImpl implements IAiIvrIntentionLogService
{
    @Autowired
    private AiIvrIntentionLogMapper aiIvrIntentionLogMapper;

    @Override
    public AiIvrIntentionLog selectAiIvrIntentionLogByLogId(Long logId)
    {
        return aiIvrIntentionLogMapper.selectAiIvrIntentionLogByLogId(logId);
    }

    @Override
    public List<AiIvrIntentionLog> selectAiIvrIntentionLogList(AiIvrIntentionLog aiIvrIntentionLog)
    {
        return aiIvrIntentionLogMapper.selectAiIvrIntentionLogList(aiIvrIntentionLog);
    }

    @Override
    public List<AiIvrIntentionLog> selectAiIvrIntentionLogByRecordId(Long recordId)
    {
        return aiIvrIntentionLogMapper.selectAiIvrIntentionLogByRecordId(recordId);
    }

    @Override
    public int insertAiIvrIntentionLog(AiIvrIntentionLog aiIvrIntentionLog)
    {
        return aiIvrIntentionLogMapper.insertAiIvrIntentionLog(aiIvrIntentionLog);
    }
}
