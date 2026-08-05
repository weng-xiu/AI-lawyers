package ai.lawyers.system.service.impl.lawyers;

import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import ai.lawyers.system.domain.lawyers.AiChatMessage;
import ai.lawyers.system.mapper.lawyers.AiChatMessageMapper;
import ai.lawyers.system.service.lawyers.IAiChatMessageService;

/**
 * 聊天消息Service实现
 *
 * @author ai-lawyers
 */
@Service
public class AiChatMessageServiceImpl implements IAiChatMessageService
{
    @Autowired
    private AiChatMessageMapper aiChatMessageMapper;

    @Override
    public AiChatMessage selectAiChatMessageByMessageId(Long messageId)
    {
        return aiChatMessageMapper.selectAiChatMessageByMessageId(messageId);
    }

    @Override
    public List<AiChatMessage> selectAiChatMessageList(AiChatMessage aiChatMessage)
    {
        return aiChatMessageMapper.selectAiChatMessageList(aiChatMessage);
    }

    @Override
    public int insertAiChatMessage(AiChatMessage aiChatMessage)
    {
        return aiChatMessageMapper.insertAiChatMessage(aiChatMessage);
    }

    @Override
    public int updateAiChatMessage(AiChatMessage aiChatMessage)
    {
        return aiChatMessageMapper.updateAiChatMessage(aiChatMessage);
    }

    @Override
    public int deleteAiChatMessageByMessageId(Long messageId)
    {
        return aiChatMessageMapper.deleteAiChatMessageByMessageId(messageId);
    }

    @Override
    public int deleteAiChatMessageByMessageIds(Long[] messageIds)
    {
        return aiChatMessageMapper.deleteAiChatMessageByMessageIds(messageIds);
    }

    @Override
    public int markReadBySessionId(Long sessionId)
    {
        return aiChatMessageMapper.markReadBySessionId(sessionId);
    }
}
