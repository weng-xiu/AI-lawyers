package ai.lawyers.system.service.lawyers;

import java.util.List;
import ai.lawyers.system.domain.lawyers.AiMessage;

/**
 * 消息中心-站内信 Service
 *
 * @author ai-lawyers
 */
public interface IAiMessageService
{
    public AiMessage selectAiMessageByMessageId(Long messageId);

    public List<AiMessage> selectAiMessageList(AiMessage aiMessage);

    /** 插入一条站内信，返回影响行数 */
    public int insertAiMessage(AiMessage aiMessage);

    /** 标记单条已读（校验归属） */
    public int markRead(Long messageId, Long userId);

    /** 全部已读，返回影响条数 */
    public int markAllRead(Long userId);

    /** 未读数 */
    public int countUnread(Long userId);

    public int deleteAiMessageByMessageIds(Long[] messageIds, Long userId);
}
