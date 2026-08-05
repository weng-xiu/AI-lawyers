package ai.lawyers.system.mapper.lawyers;

import java.util.List;
import ai.lawyers.system.domain.lawyers.AiChatSession;

/**
 * 图文会话Mapper接口
 *
 * @author ai-lawyers
 */
public interface AiChatSessionMapper
{
    public AiChatSession selectAiChatSessionBySessionId(Long sessionId);

    public List<AiChatSession> selectAiChatSessionList(AiChatSession aiChatSession);

    public int insertAiChatSession(AiChatSession aiChatSession);

    public int updateAiChatSession(AiChatSession aiChatSession);

    public int deleteAiChatSessionBySessionId(Long sessionId);

    public int deleteAiChatSessionBySessionIds(Long[] sessionIds);

    /** 会话统计：总数、进行中、已结束、今日新增 */
    public java.util.Map<String, Object> selectChatSessionStats();
}
