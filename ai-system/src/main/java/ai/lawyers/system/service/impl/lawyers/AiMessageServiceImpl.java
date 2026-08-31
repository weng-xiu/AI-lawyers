package ai.lawyers.system.service.impl.lawyers;

import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import ai.lawyers.common.utils.StringUtils;
import ai.lawyers.system.domain.lawyers.AiMessage;
import ai.lawyers.system.mapper.lawyers.AiMessageMapper;
import ai.lawyers.system.service.lawyers.IAiMessageService;

/**
 * 消息中心-站内信 Service 实现。
 *
 * <p>业务事件不直接调本类，而是经 MessageNotifyDispatcher（message-notify 队列）
 * 异步落库 + WebSocket 实时推送铃铛；Stream 不可用时 dispatcher 同步降级直调本类。</p>
 *
 * @author ai-lawyers
 */
@Service
public class AiMessageServiceImpl implements IAiMessageService
{
    @Autowired
    private AiMessageMapper aiMessageMapper;

    @Override
    public AiMessage selectAiMessageByMessageId(Long messageId)
    {
        return aiMessageMapper.selectAiMessageByMessageId(messageId);
    }

    @Override
    public List<AiMessage> selectAiMessageList(AiMessage aiMessage)
    {
        return aiMessageMapper.selectAiMessageList(aiMessage);
    }

    @Override
    public int insertAiMessage(AiMessage aiMessage)
    {
        if (StringUtils.isEmpty(aiMessage.getIsRead()))
        {
            aiMessage.setIsRead("0");
        }
        if (StringUtils.isEmpty(aiMessage.getMsgType()))
        {
            aiMessage.setMsgType("9");
        }
        if (StringUtils.isEmpty(aiMessage.getPriority()))
        {
            aiMessage.setPriority("2");
        }
        if (StringUtils.isEmpty(aiMessage.getSender()))
        {
            aiMessage.setSender("system");
        }
        return aiMessageMapper.insertAiMessage(aiMessage);
    }

    @Override
    public int markRead(Long messageId, Long userId)
    {
        return aiMessageMapper.markRead(messageId, userId);
    }

    @Override
    public int markAllRead(Long userId)
    {
        return aiMessageMapper.markAllRead(userId);
    }

    @Override
    public int countUnread(Long userId)
    {
        if (userId == null)
        {
            return 0;
        }
        return aiMessageMapper.countUnread(userId);
    }

    @Override
    public int deleteAiMessageByMessageIds(Long[] messageIds, Long userId)
    {
        if (messageIds == null || messageIds.length == 0 || userId == null)
        {
            return 0;
        }
        return aiMessageMapper.deleteAiMessageByMessageIds(messageIds, userId);
    }
}
