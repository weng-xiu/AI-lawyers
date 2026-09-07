package ai.lawyers.system.service.lawyers.queue;

import javax.annotation.PostConstruct;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import com.fasterxml.jackson.databind.ObjectMapper;
import ai.lawyers.common.core.domain.entity.SysUser;
import ai.lawyers.system.domain.lawyers.AiMessage;
import ai.lawyers.system.service.ISysUserService;
import ai.lawyers.system.service.lawyers.CallEventPublisher;
import ai.lawyers.system.service.lawyers.IAiMessageService;

/**
 * T5-3 消息中心：message-notify 队列生产者 + 消费者。
 *
 * <p>业务事件（质检驳回/工单分配/风险预警等）调 {@link #notify} 投递站内信；
 * Stream 不可用时同步降级直写。消费端落库后经 WebSocket（{@link CallEventPublisher}）
 * 向接收人推送 MESSAGE_NOTIFY 事件，前端铃铛实时刷新未读数。
 * 队列消费/定时任务线程无 HTTP 上下文，接收人 userId 由业务方算好放入消息体。</p>
 *
 * @author ai-lawyers
 */
@Component
public class MessageNotifyDispatcher
{
    private static final Logger log = LoggerFactory.getLogger(MessageNotifyDispatcher.class);

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /** WebSocket 推送事件类型（前端 callSocket.on('MESSAGE_NOTIFY', ...) 监听） */
    public static final String WS_EVENT = "MESSAGE_NOTIFY";

    @Autowired
    private StreamQueueService streamQueueService;

    @Autowired
    private IAiMessageService messageService;

    /** 广播时查询全部在职用户（selectUserList 无 @DataScope 切面时数据范围为空，线程上下文安全） */
    @Autowired
    private ISysUserService userService;

    /** 实时推送通道：框架模块提供 WebSocket 实现，未引入时静默（站内信仍落库） */
    @Autowired(required = false)
    private CallEventPublisher callEventPublisher;

    @PostConstruct
    public void init()
    {
        streamQueueService.registerHandler(QueueNames.MESSAGE_NOTIFY, payload ->
        {
            AiMessage msg = MAPPER.readValue(payload, AiMessage.class);
            persist(msg);
        });
        log.info("消息中心消费者已注册 queue={}", QueueNames.MESSAGE_NOTIFY);
    }

    /**
     * 投递一条站内信（异步）。接收人 userId 必填，为空直接忽略。
     */
    public void notify(Long receiverUserId, String msgType, String title, String content,
                       String bizType, Long bizId, String sender)
    {
        if (receiverUserId == null)
        {
            return;
        }
        AiMessage msg = buildMessage(receiverUserId, msgType, title, content, bizType, bizId, sender);
        try
        {
            if (streamQueueService.enqueue(QueueNames.MESSAGE_NOTIFY, MAPPER.writeValueAsString(msg)))
            {
                return;
            }
        }
        catch (Exception e)
        {
            log.warn("站内信投递队列失败，降级同步落库 receiver={} title={}: {}",
                    receiverUserId, title, e.getMessage());
        }
        persist(msg);
    }

    /**
     * 广播一条站内信（异步）：发送给全部状态正常的用户。
     * 适用于无明确接收人的业务事件（如风险预警生成）。
     */
    public void broadcast(String msgType, String title, String content,
                          String bizType, Long bizId, String sender)
    {
        Set<Long> receiverIds = new LinkedHashSet<>();
        try
        {
            SysUser query = new SysUser();
            query.setStatus("0");
            List<SysUser> users = userService.selectUserList(query);
            if (users != null)
            {
                for (SysUser u : users)
                {
                    if (u.getUserId() != null)
                    {
                        receiverIds.add(u.getUserId());
                    }
                }
            }
        }
        catch (Exception e)
        {
            log.warn("站内信广播查询接收人失败 title={}: {}", title, e.getMessage());
            return;
        }
        for (Long receiverId : receiverIds)
        {
            notify(receiverId, msgType, title, content, bizType, bizId, sender);
        }
        if (receiverIds.isEmpty())
        {
            log.warn("站内信广播无有效接收人 title={}", title);
        }
    }

    private AiMessage buildMessage(Long receiverUserId, String msgType, String title, String content,
                                   String bizType, Long bizId, String sender)
    {
        AiMessage msg = new AiMessage();
        msg.setReceiverUserId(receiverUserId);
        msg.setMsgType(msgType);
        msg.setTitle(truncate(title, 200));
        msg.setContent(truncate(content, 1000));
        msg.setBizType(bizType);
        msg.setBizId(bizId);
        msg.setSender(sender == null ? "system" : sender);
        msg.setIsRead("0");
        return msg;
    }

    /** 落库 + WebSocket 实时推送（消费端与同步降级共用） */
    private void persist(AiMessage msg)
    {
        try
        {
            messageService.insertAiMessage(msg);
            if (callEventPublisher != null && msg.getReceiverUserId() != null)
            {
                try
                {
                    callEventPublisher.publishToUser(msg.getReceiverUserId(), WS_EVENT, msg);
                }
                catch (Exception we)
                {
                    // 推送失败不影响站内信落库
                    log.debug("站内信 WebSocket 推送失败 receiver={}: {}",
                            msg.getReceiverUserId(), we.getMessage());
                }
            }
        }
        catch (Exception e)
        {
            log.warn("站内信落库失败 receiver={} title={}: {}",
                    msg.getReceiverUserId(), msg.getTitle(), e.getMessage());
        }
    }

    private static String truncate(String s, int max)
    {
        if (s == null)
        {
            return null;
        }
        return s.length() > max ? s.substring(0, max) : s;
    }
}
