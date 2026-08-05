package ai.lawyers.system.service.lawyers;

import java.util.List;
import ai.lawyers.system.domain.lawyers.AiChatSession;

/**
 * 图文会话Service接口
 *
 * @author ai-lawyers
 */
public interface IAiChatSessionService
{
    public AiChatSession selectAiChatSessionBySessionId(Long sessionId);

    public List<AiChatSession> selectAiChatSessionList(AiChatSession aiChatSession);

    public int insertAiChatSession(AiChatSession aiChatSession);

    public int updateAiChatSession(AiChatSession aiChatSession);

    public int deleteAiChatSessionBySessionId(Long sessionId);

    public int deleteAiChatSessionBySessionIds(Long[] sessionIds);

    /** 会话统计 */
    public java.util.Map<String, Object> selectChatSessionStats();
}
