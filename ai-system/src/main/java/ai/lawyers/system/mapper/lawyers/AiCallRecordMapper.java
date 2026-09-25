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

    /** 按 FreeSWITCH 通道 UUID 查询话单（ESL 事件回写时使用） */
    public AiCallRecord selectAiCallRecordByCallUuid(String callUuid);

    /**
     * 按 recordId 或 callUuid 更新录音信息。
     * <p>{@link AiCallRecord#getRecordId()} 不为空时按主键更新，否则按
     * {@link AiCallRecord#getCallUuid()} 更新。</p>
     */
    public int updateRecordingInfo(AiCallRecord aiCallRecord);

    /**
     * P3-E3：AI 小结状态 CAS 抢占（仅当当前状态=fromStatus 时改为 toStatus）。
     * @return 影响行数；0 表示状态已被其他线程改变（抢占失败）
     */
    public int casAiSummaryStatus(@org.apache.ibatis.annotations.Param("recordId") Long recordId,
                                  @org.apache.ibatis.annotations.Param("fromStatus") String fromStatus,
                                  @org.apache.ibatis.annotations.Param("toStatus") String toStatus);

    /** P3-E3：AI 小结生成成功回写（ai_summary + 状态2 + 生成时间，清空失败原因） */
    public int updateAiSummarySuccess(AiCallRecord aiCallRecord);

    /** P3-E3：AI 小结生成失败回写（状态3 + 失败原因） */
    public int updateAiSummaryFail(@org.apache.ibatis.annotations.Param("recordId") Long recordId,
                                   @org.apache.ibatis.annotations.Param("failReason") String failReason);
}
