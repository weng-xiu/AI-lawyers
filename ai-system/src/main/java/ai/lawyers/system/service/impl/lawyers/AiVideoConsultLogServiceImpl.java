package ai.lawyers.system.service.impl.lawyers;

import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import ai.lawyers.system.domain.lawyers.AiVideoConsultLog;
import ai.lawyers.system.mapper.lawyers.AiVideoConsultLogMapper;
import ai.lawyers.system.service.lawyers.IAiVideoConsultLogService;

@Service
public class AiVideoConsultLogServiceImpl implements IAiVideoConsultLogService
{
    @Autowired
    private AiVideoConsultLogMapper aiVideoConsultLogMapper;

    @Override
    public AiVideoConsultLog selectAiVideoConsultLogByLogId(Long logId) {
        return aiVideoConsultLogMapper.selectAiVideoConsultLogByLogId(logId);
    }

    @Override
    public List<AiVideoConsultLog> selectAiVideoConsultLogList(AiVideoConsultLog log) {
        return aiVideoConsultLogMapper.selectAiVideoConsultLogList(log);
    }

    @Override
    public int insertAiVideoConsultLog(AiVideoConsultLog log) {
        return aiVideoConsultLogMapper.insertAiVideoConsultLog(log);
    }
}
