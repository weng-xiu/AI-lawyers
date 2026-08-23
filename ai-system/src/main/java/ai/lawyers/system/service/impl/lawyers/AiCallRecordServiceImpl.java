package ai.lawyers.system.service.impl.lawyers;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import ai.lawyers.system.domain.lawyers.AiCallRecord;
import ai.lawyers.system.mapper.lawyers.AiCallRecordMapper;
import ai.lawyers.system.mapper.lawyers.AiCallAgentStatusMapper;
import ai.lawyers.system.service.lawyers.IAiCallRecordService;

@Service
public class AiCallRecordServiceImpl implements IAiCallRecordService 
{
    @Autowired
    private AiCallRecordMapper aiCallRecordMapper;

    @Autowired
    private AiCallAgentStatusMapper aiCallAgentStatusMapper;

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

    @Override
    public Map<String, Object> getWorkbenchSummary()
    {
        Map<String, Object> result = new HashMap<>();

        // 今日统计
        Map<String, Object> todayStats = aiCallRecordMapper.selectTodayCallStats();
        result.put("todayCallStats", todayStats);

        // 全部统计
        Map<String, Object> totalStats = aiCallRecordMapper.getCallStatistics();
        result.put("totalStats", totalStats);

        // 团队统计
        List<Map<String, Object>> teamStats = aiCallRecordMapper.getCallStatisticsByAgent();
        result.put("teamStats", teamStats);

        // 在线坐席
        result.put("onlineAgentCount", aiCallAgentStatusMapper.selectOnlineAgents().size());

        // 最近通话 Top 10
        result.put("recentCalls", aiCallRecordMapper.selectRecentCalls(10));

        return result;
    }

    @Override
    public AiCallRecord selectAiCallRecordByCallUuid(String callUuid)
    {
        return aiCallRecordMapper.selectAiCallRecordByCallUuid(callUuid);
    }

    @Override
    public int updateRecordingInfo(AiCallRecord aiCallRecord)
    {
        return aiCallRecordMapper.updateRecordingInfo(aiCallRecord);
    }
}
