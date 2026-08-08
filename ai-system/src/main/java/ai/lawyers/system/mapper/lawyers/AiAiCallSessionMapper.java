package ai.lawyers.system.mapper.lawyers;

import java.util.List;
import ai.lawyers.system.domain.lawyers.AiAiCallSession;

/**
 * AI律师辅助会话 Mapper（独立链路，与 AiCallRecordMapper 互不涉及）
 */
public interface AiAiCallSessionMapper
{
    public AiAiCallSession selectAiAiCallSessionBySessionId(Long sessionId);

    public AiAiCallSession selectAiAiCallSessionByRecordId(Long recordId);

    public List<AiAiCallSession> selectAiAiCallSessionList(AiAiCallSession aiAiCallSession);

    public int insertAiAiCallSession(AiAiCallSession aiAiCallSession);

    public int updateAiAiCallSession(AiAiCallSession aiAiCallSession);

    public int deleteAiAiCallSessionBySessionId(Long sessionId);

    public int deleteAiAiCallSessionBySessionIds(Long[] sessionIds);
}
