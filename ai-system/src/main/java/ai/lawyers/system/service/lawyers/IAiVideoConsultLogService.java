package ai.lawyers.system.service.lawyers;

import java.util.List;
import ai.lawyers.system.domain.lawyers.AiVideoConsultLog;

public interface IAiVideoConsultLogService
{
    public AiVideoConsultLog selectAiVideoConsultLogByLogId(Long logId);
    public List<AiVideoConsultLog> selectAiVideoConsultLogList(AiVideoConsultLog aiVideoConsultLog);
    public int insertAiVideoConsultLog(AiVideoConsultLog aiVideoConsultLog);
}
