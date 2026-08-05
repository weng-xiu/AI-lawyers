package ai.lawyers.system.mapper.lawyers;

import java.util.List;
import ai.lawyers.system.domain.lawyers.AiChatMessage;

/**
 * 聊天消息Mapper接口
 *
 * @author ai-lawyers
 */
public interface AiChatMessageMapper
{
    public AiChatMessage selectAiChatMessageByMessageId(Long messageId);

    /** 按会话查询消息列表（按发送时间正序） */
    public List<AiChatMessage> selectAiChatMessageList(AiChatMessage aiChatMessage);

    public int insertAiChatMessage(AiChatMessage aiChatMessage);

    public int updateAiChatMessage(AiChatMessage aiChatMessage);

    public int deleteAiChatMessageByMessageId(Long messageId);

    public int deleteAiChatMessageByMessageIds(Long[] messageIds);

    /** 标记会话下所有客户消息为已读 */
    public int markReadBySessionId(Long sessionId);
}
