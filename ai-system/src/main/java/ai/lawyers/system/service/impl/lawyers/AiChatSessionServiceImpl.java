package ai.lawyers.system.service.impl.lawyers;

import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import ai.lawyers.system.domain.lawyers.AiChatSession;
import ai.lawyers.system.mapper.lawyers.AiChatSessionMapper;
import ai.lawyers.system.service.lawyers.IAiChatSessionService;

/**
 * 图文会话Service实现
 *
 * @author ai-lawyers
 */
@Service
public class AiChatSessionServiceImpl implements IAiChatSessionService
{
    @Autowired
    private AiChatSessionMapper aiChatSessionMapper;

    @Override
    public AiChatSession selectAiChatSessionBySessionId(Long sessionId)
    {
        return aiChatSessionMapper.selectAiChatSessionBySessionId(sessionId);
    }

    @Override
    public List<AiChatSession> selectAiChatSessionList(AiChatSession aiChatSession)
    {
        return aiChatSessionMapper.selectAiChatSessionList(aiChatSession);
    }

    @Override
    public int insertAiChatSession(AiChatSession aiChatSession)
    {
        return aiChatSessionMapper.insertAiChatSession(aiChatSession);
    }

    @Override
    public int updateAiChatSession(AiChatSession aiChatSession)
    {
        return aiChatSessionMapper.updateAiChatSession(aiChatSession);
    }

    @Override
    public int deleteAiChatSessionBySessionId(Long sessionId)
    {
        return aiChatSessionMapper.deleteAiChatSessionBySessionId(sessionId);
    }

    @Override
    public int deleteAiChatSessionBySessionIds(Long[] sessionIds)
    {
        return aiChatSessionMapper.deleteAiChatSessionBySessionIds(sessionIds);
    }

    @Override
    public java.util.Map<String, Object> selectChatSessionStats()
    {
        return aiChatSessionMapper.selectChatSessionStats();
    }
}
