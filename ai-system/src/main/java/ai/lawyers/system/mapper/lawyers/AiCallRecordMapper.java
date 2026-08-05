package ai.lawyers.system.mapper.lawyers;

import java.util.List;
import ai.lawyers.system.domain.lawyers.AiCallRecord;

public interface AiCallRecordMapper 
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

    /** 工作台：今日通话统计（todayCalls、todayServiceDuration） */
    public java.util.Map<String, Object> selectTodayCallStats();

    /** 工作台：最近通话记录 */
    public List<AiCallRecord> selectRecentCalls(Integer limit);

    /** 来电弹屏：来电人通话统计（callCount、monthCallCount、lastCallTime） */
    public java.util.Map<String, Object> selectCallerCallStats(String callerNumber);

    /** 来电弹屏：按号码查询历史通话 */
    public List<AiCallRecord> selectAiCallRecordByCallerNumber(@org.apache.ibatis.annotations.Param("callerNumber") String callerNumber, @org.apache.ibatis.annotations.Param("limit") Integer limit);
}
