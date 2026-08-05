package ai.lawyers.system.service.lawyers;

import java.util.List;
import ai.lawyers.system.domain.lawyers.AiChatMessage;

/**
 * 聊天消息Service接口
 *
 * @author ai-lawyers
 */
public interface IAiChatMessageService
{
    public AiChatMessage selectAiChatMessageByMessageId(Long messageId);

    public List<AiChatMessage> selectAiChatMessageList(AiChatMessage aiChatMessage);

    public int insertAiChatMessage(AiChatMessage aiChatMessage);

    public int updateAiChatMessage(AiChatMessage aiChatMessage);

    public int deleteAiChatMessageByMessageId(Long messageId);

    public int deleteAiChatMessageByMessageIds(Long[] messageIds);

    /** 标记会话下所有客户消息为已读 */
    public int markReadBySessionId(Long sessionId);
}
