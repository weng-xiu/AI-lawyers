package ai.lawyers.system.mapper.lawyers;

import java.util.List;
import org.apache.ibatis.annotations.Param;
import ai.lawyers.system.domain.lawyers.AiMessage;

/**
 * 消息中心-站内信 Mapper
 *
 * @author ai-lawyers
 */
public interface AiMessageMapper
{
    public AiMessage selectAiMessageByMessageId(Long messageId);

    public List<AiMessage> selectAiMessageList(AiMessage aiMessage);

    public int insertAiMessage(AiMessage aiMessage);

    /** 标记单条已读（仅本人未读消息可更新，返回影响行数） */
    public int markRead(@Param("messageId") Long messageId, @Param("userId") Long userId);

    /** 全部已读（某人所有未读消息），返回影响行数 */
    public int markAllRead(@Param("userId") Long userId);

    /** 统计某人未读数 */
    public int countUnread(@Param("userId") Long userId);

    public int deleteAiMessageByMessageIds(@Param("ids") Long[] messageIds, @Param("userId") Long userId);
}
