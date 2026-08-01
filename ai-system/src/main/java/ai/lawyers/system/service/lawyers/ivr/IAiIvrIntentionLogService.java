package ai.lawyers.system.service.lawyers.ivr;

import java.util.List;
import ai.lawyers.system.domain.lawyers.ivr.AiIvrIntentionLog;

public interface IAiIvrIntentionLogService
{
    public AiIvrIntentionLog selectAiIvrIntentionLogByLogId(Long logId);

    public List<AiIvrIntentionLog> selectAiIvrIntentionLogList(AiIvrIntentionLog aiIvrIntentionLog);

    public List<AiIvrIntentionLog> selectAiIvrIntentionLogByRecordId(Long recordId);

    public int insertAiIvrIntentionLog(AiIvrIntentionLog aiIvrIntentionLog);
}
