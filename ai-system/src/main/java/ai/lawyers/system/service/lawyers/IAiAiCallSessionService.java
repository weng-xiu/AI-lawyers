package ai.lawyers.system.service.lawyers;

import java.util.List;
import ai.lawyers.system.domain.lawyers.AiAiCallSession;

/**
 * AI律师辅助会话服务（独立链路）
 *
 * 与人工接听服务（IAiCallRecordService / IAiCallAgentStatusService）完全独立：
 *  - 仅以 recordId 关联人工通话，不修改人工通话状态；
 *  - 拥有独立的会话状态管理（AiAssistSessionStatusEnum）；
 *  - 提供独立的触发、分析、推荐、小结处理函数。
 */
public interface IAiAiCallSessionService
{
    /** 按会话ID查询 */
    public AiAiCallSession selectAiAiCallSessionBySessionId(Long sessionId);

    /** 按人工通话记录ID查询当前AI辅助会话 */
    public AiAiCallSession selectAiAiCallSessionByRecordId(Long recordId);

    public List<AiAiCallSession> selectAiAiCallSessionList(AiAiCallSession aiAiCallSession);

    public int insertAiAiCallSession(AiAiCallSession aiAiCallSession);

    public int updateAiAiCallSession(AiAiCallSession aiAiCallSession);

    public int deleteAiAiCallSessionBySessionId(Long sessionId);

    public int deleteAiAiCallSessionBySessionIds(Long[] sessionIds);

    /**
     * 独立触发：人工坐席接听后启动 AI 辅助会话（初始化状态，不干预人工通话）
     * @param recordId   人工通话记录ID
     * @param agentId    人工坐席ID
     * @param callerPhone 主叫号码
     * @param callerName  客户姓名
     * @return 新建的AI辅助会话
     */
    public AiAiCallSession startAssistSession(Long recordId, Long agentId, String callerPhone, String callerName);

    /**
     * 独立处理函数：基于通话内容分析意图并生成法条/话术推荐（状态：分析中→推荐中）
     * @param sessionId  AI辅助会话ID
     * @param callContent 人工通话内容（用于意图识别）
     * @return 更新后的AI辅助会话
     */
    public AiAiCallSession analyzeAndRecommend(Long sessionId, String callContent);

    /**
     * 独立处理函数：生成通话小结并结束AI辅助会话（状态：已小结→已结束）
     * @param sessionId  AI辅助会话ID
     * @param callContent 人工通话完整内容
     * @return 更新后的AI辅助会话
     */
    public AiAiCallSession summarizeAndEnd(Long sessionId, String callContent);
}
