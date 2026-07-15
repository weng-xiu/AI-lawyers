package ai.lawyers.system.service.impl.lawyers;

import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import ai.lawyers.system.domain.lawyers.AiCallRecord;
import ai.lawyers.system.mapper.lawyers.AiCallRecordMapper;
import ai.lawyers.system.service.lawyers.IAiCallRecordService;

@Service
public class AiCallRecordServiceImpl implements IAiCallRecordService 
{
    @Autowired
    private AiCallRecordMapper aiCallRecordMapper;

    @Override
    public AiCallRecord selectAiCallRecordByRecordId(Long recordId)
    {
        return aiCallRecordMapper.selectAiCallRecordByRecordId(recordId);
    }

    @Override
    public List<AiCallRecord> selectAiCallRecordList(AiCallRecord aiCallRecord)
    {
        return aiCallRecordMapper.selectAiCallRecordList(aiCallRecord);
    }

    @Override
    public int insertAiCallRecord(AiCallRecord aiCallRecord)
    {
        return aiCallRecordMapper.insertAiCallRecord(aiCallRecord);
    }

    @Override
    public int updateAiCallRecord(AiCallRecord aiCallRecord)
    {
        return aiCallRecordMapper.updateAiCallRecord(aiCallRecord);
    }

    @Override
    public int deleteAiCallRecordByRecordId(Long recordId)
    {
        return aiCallRecordMapper.deleteAiCallRecordByRecordId(recordId);
    }

    @Override
    public int deleteAiCallRecordByRecordIds(Long[] recordIds)
    {
        return aiCallRecordMapper.deleteAiCallRecordByRecordIds(recordIds);
    }

    @Override
    public List<AiCallRecord> selectAiCallRecordByAgentId(Long agentId)
    {
        return aiCallRecordMapper.selectAiCallRecordByAgentId(agentId);
    }

    @Override
    public java.util.Map<String, Object> getCallStatistics()
    {
        return aiCallRecordMapper.getCallStatistics();
    }

    @Override
    public List<java.util.Map<String, Object>> getCallStatisticsByAgent()
    {
        return aiCallRecordMapper.getCallStatisticsByAgent();
    }

    @Override
    public List<java.util.Map<String, Object>> getCallStatisticsByCategory()
    {
        return aiCallRecordMapper.getCallStatisticsByCategory();
    }

    @Override
    public List<java.util.Map<String, Object>> getCallStatisticsByDate(Integer days)
    {
        return aiCallRecordMapper.getCallStatisticsByDate(days);
    }
}
