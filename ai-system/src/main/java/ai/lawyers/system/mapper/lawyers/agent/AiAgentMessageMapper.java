package ai.lawyers.system.mapper.lawyers.agent;

import java.util.List;
import org.apache.ibatis.annotations.Param;
import ai.lawyers.system.domain.lawyers.agent.AiAgentMessage;

/**
 * AI智能体对话消息Mapper接口
 *
 * @author ai-lawyers
 */
public interface AiAgentMessageMapper
{
    public AiAgentMessage selectAiAgentMessageByMessageId(Long messageId);

    public List<AiAgentMessage> selectAiAgentMessageList(AiAgentMessage aiAgentMessage);

    /**
     * 查询某会话最近 limit 条消息（按时间正序返回，便于拼接上下文）
     */
    public List<AiAgentMessage> selectRecentBySession(@Param("sessionId") String sessionId, @Param("limit") int limit);

    /**
     * 查询某会话当前最大轮次
     */
    public Integer selectMaxTurnNo(@Param("sessionId") String sessionId);

    public int insertAiAgentMessage(AiAgentMessage aiAgentMessage);

    public int deleteAiAgentMessageByMessageId(Long messageId);
}
