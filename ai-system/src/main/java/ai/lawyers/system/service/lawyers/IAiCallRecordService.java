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

    /** 按 FreeSWITCH 通道 UUID 查询话单 */
    public AiCallRecord selectAiCallRecordByCallUuid(String callUuid);

    /**
     * 更新录音信息（按 recordId 或 callUuid 关联）。
     *
     * @param aiCallRecord 录音字段：recordFile / recordingUrl / recordDuration /
     *                     asrStatus / transcript，至少设置 recordId 或 callUuid 之一
     * @return 受影响行数
     */
    public int updateRecordingInfo(AiCallRecord aiCallRecord);
}
