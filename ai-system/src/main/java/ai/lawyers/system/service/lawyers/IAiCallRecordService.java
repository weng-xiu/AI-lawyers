package ai.lawyers.system.service.lawyers;

import java.util.List;
import ai.lawyers.system.domain.lawyers.AiCallRecord;

public interface IAiCallRecordService 
{
    public AiCallRecord selectAiCallRecordByRecordId(Long recordId);

    public List<AiCallRecord> selectAiCallRecordList(AiCallRecord aiCallRecord);

    public int insertAiCallRecord(AiCallRecord aiCallRecord);

    public int updateAiCallRecord(AiCallRecord aiCallRecord);

    public int deleteAiCallRecordByRecordId(Long recordId);

    public int deleteAiCallRecordByRecordIds(Long[] recordIds);

    public List<AiCallRecord> selectAiCallRecordByAgentId(Long agentId);

    public java.util.Map<String, Object> getCallStatistics();

    public List<java.util.Map<String, Object>> getCallStatisticsByAgent();

    public List<java.util.Map<String, Object>> getCallStatisticsByCategory();

    public List<java.util.Map<String, Object>> getCallStatisticsByDate(Integer days);

    /** 工作台汇总：今日统计 + 团队概况 + 最近通话 */
    public java.util.Map<String, Object> getWorkbenchSummary();
}
