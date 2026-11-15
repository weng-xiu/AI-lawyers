package ai.lawyers.system.service.lawyers.queue;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.Collections;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import com.fasterxml.jackson.databind.ObjectMapper;

import ai.lawyers.common.core.domain.entity.SysUser;
import ai.lawyers.system.domain.lawyers.AiMessage;
import ai.lawyers.system.service.ISysUserService;
import ai.lawyers.system.service.lawyers.CallEventPublisher;
import ai.lawyers.system.service.lawyers.IAiMessageService;

/**
 * T5-3：{@link MessageNotifyDispatcher} 消息中心生产端与消费端测试。
 *
 * <p>生产端：空接收人忽略、入队成功不直写、入队失败/异常降级同步落库、
 * 标题与正文超长截断、发送人缺省 system；广播按在职用户去重投递、查询失败静默；
 * 落库后经 WebSocket 推送，推送/落库异常不影响主流程；注册的消费者可解析消息体落库。</p>
 *
 * @author ai-lawyers
 */
class MessageNotifyDispatcherTest
{
    private static final Long RECEIVER_ID = 66L;

    private StreamQueueService streamQueueService;

    private IAiMessageService messageService;

    private ISysUserService userService;

    private CallEventPublisher callEventPublisher;

    private MessageNotifyDispatcher dispatcher;

    @BeforeEach
    void setUp()
    {
        streamQueueService = mock(StreamQueueService.class);
        messageService = mock(IAiMessageService.class);
        userService = mock(ISysUserService.class);
        callEventPublisher = mock(CallEventPublisher.class);
        dispatcher = new MessageNotifyDispatcher();
        ReflectionTestUtils.setField(dispatcher, "streamQueueService", streamQueueService);
        ReflectionTestUtils.setField(dispatcher, "messageService", messageService);
        ReflectionTestUtils.setField(dispatcher, "userService", userService);
        ReflectionTestUtils.setField(dispatcher, "callEventPublisher", callEventPublisher);
    }

    // ---------- 单发 ----------

    @Test
    void notifyIgnoresNullReceiver()
    {
        dispatcher.notify(null, "QUALITY_REJECT", "标题", "内容", "quality", 1L, "system");

        verifyNoInteractions(streamQueueService, messageService);
    }

    @Test
    void notifyEnqueuesWhenStreamAvailable()
    {
        when(streamQueueService.enqueue(eq(QueueNames.MESSAGE_NOTIFY), anyString())).thenReturn(true);

        dispatcher.notify(RECEIVER_ID, "TICKET_ASSIGN", "工单分配", "请及时处理", "ticket", 9L, "admin");

        verify(streamQueueService).enqueue(eq(QueueNames.MESSAGE_NOTIFY), anyString());
        verify(messageService, never()).insertAiMessage(any());
    }

    @Test
    void notifyFallsBackToDbWhenQueueRejects()
    {
        when(streamQueueService.enqueue(eq(QueueNames.MESSAGE_NOTIFY), anyString())).thenReturn(false);

        dispatcher.notify(RECEIVER_ID, "RISK", "风险预警", "发现高风险通话", "risk", 12L, null);

        ArgumentCaptor<AiMessage> saved = ArgumentCaptor.forClass(AiMessage.class);
        verify(messageService).insertAiMessage(saved.capture());
        AiMessage msg = saved.getValue();
        assertThat(msg.getReceiverUserId()).isEqualTo(RECEIVER_ID);
        assertThat(msg.getMsgType()).isEqualTo("RISK");
        assertThat(msg.getBizType()).isEqualTo("risk");
        assertThat(msg.getBizId()).isEqualTo(12L);
        assertThat(msg.getSender()).isEqualTo("system");
        assertThat(msg.getIsRead()).isEqualTo("0");
    }

    @Test
    void notifyFallsBackToDbWhenEnqueueThrows()
    {
        when(streamQueueService.enqueue(eq(QueueNames.MESSAGE_NOTIFY), anyString()))
                .thenThrow(new IllegalStateException("redis down"));

        dispatcher.notify(RECEIVER_ID, "RISK", "风险预警", "内容", "risk", 1L, "admin");

        verify(messageService).insertAiMessage(any(AiMessage.class));
    }

    @Test
    void notifyTruncatesOversizedTitleAndContent()
    {
        when(streamQueueService.enqueue(eq(QueueNames.MESSAGE_NOTIFY), anyString())).thenReturn(false);

        dispatcher.notify(RECEIVER_ID, "RISK", repeat('T', 201), repeat('C', 1001), "risk", 1L, "admin");

        ArgumentCaptor<AiMessage> saved = ArgumentCaptor.forClass(AiMessage.class);
        verify(messageService).insertAiMessage(saved.capture());
        assertThat(saved.getValue().getTitle()).hasSize(200);
        assertThat(saved.getValue().getContent()).hasSize(1000);
    }

    @Test
    void notifyKeepsGivenSender()
    {
        when(streamQueueService.enqueue(eq(QueueNames.MESSAGE_NOTIFY), anyString())).thenReturn(false);

        dispatcher.notify(RECEIVER_ID, "RISK", "标题", "内容", "risk", 1L, "质检员A");

        ArgumentCaptor<AiMessage> saved = ArgumentCaptor.forClass(AiMessage.class);
        verify(messageService).insertAiMessage(saved.capture());
        assertThat(saved.getValue().getSender()).isEqualTo("质检员A");
    }

    // ---------- 落库与 WebSocket 推送 ----------

    @Test
    void persistPushesMessageToReceiver()
    {
        when(streamQueueService.enqueue(eq(QueueNames.MESSAGE_NOTIFY), anyString())).thenReturn(false);

        dispatcher.notify(RECEIVER_ID, "RISK", "标题", "内容", "risk", 1L, "admin");

        ArgumentCaptor<AiMessage> pushed = ArgumentCaptor.forClass(AiMessage.class);
        verify(callEventPublisher).publishToUser(eq(RECEIVER_ID), eq(MessageNotifyDispatcher.WS_EVENT),
                pushed.capture());
        assertThat(pushed.getValue().getReceiverUserId()).isEqualTo(RECEIVER_ID);
    }

    @Test
    void pushFailureDoesNotBreakPersist()
    {
        when(streamQueueService.enqueue(eq(QueueNames.MESSAGE_NOTIFY), anyString())).thenReturn(false);
        org.mockito.Mockito.doThrow(new IllegalStateException("socket closed"))
                .when(callEventPublisher).publishToUser(anyLong(), anyString(), any());

        dispatcher.notify(RECEIVER_ID, "RISK", "标题", "内容", "risk", 1L, "admin");

        verify(messageService).insertAiMessage(any(AiMessage.class));
    }

    @Test
    void persistSkipsPushWhenPublisherAbsent()
    {
        ReflectionTestUtils.setField(dispatcher, "callEventPublisher", null);
        when(streamQueueService.enqueue(eq(QueueNames.MESSAGE_NOTIFY), anyString())).thenReturn(false);

        dispatcher.notify(RECEIVER_ID, "RISK", "标题", "内容", "risk", 1L, "admin");

        verify(messageService).insertAiMessage(any(AiMessage.class));
    }

    @Test
    void insertFailureIsSwallowed()
    {
        when(streamQueueService.enqueue(eq(QueueNames.MESSAGE_NOTIFY), anyString())).thenReturn(false);
        when(messageService.insertAiMessage(any(AiMessage.class))).thenThrow(new RuntimeException("db down"));

        dispatcher.notify(RECEIVER_ID, "RISK", "标题", "内容", "risk", 1L, "admin");

        // 异常被吞掉，不向调用方抛出（此处能正常返回即通过）
        verify(messageService).insertAiMessage(any(AiMessage.class));
    }

    // ---------- 广播 ----------

    @Test
    void broadcastNotifiesDistinctActiveUsers()
    {
        when(userService.selectUserList(any(SysUser.class)))
                .thenReturn(Arrays.asList(user(1L), user(2L), user(1L), user(null)));

        dispatcher.broadcast("RISK", "风险预警", "内容", "risk", 5L, "system");

        verify(streamQueueService, times(2)).enqueue(eq(QueueNames.MESSAGE_NOTIFY), anyString());
    }

    @Test
    void broadcastSkipsWhenUserQueryFails()
    {
        when(userService.selectUserList(any(SysUser.class))).thenThrow(new RuntimeException("db down"));

        dispatcher.broadcast("RISK", "风险预警", "内容", "risk", 5L, "system");

        verifyNoInteractions(streamQueueService, messageService);
    }

    @Test
    void broadcastDoesNothingWhenNoActiveUser()
    {
        when(userService.selectUserList(any(SysUser.class))).thenReturn(Collections.emptyList());

        dispatcher.broadcast("RISK", "风险预警", "内容", "risk", 5L, "system");

        verifyNoInteractions(streamQueueService, messageService);
    }

    // ---------- 消费端 ----------

    @Test
    void registeredConsumerPersistsPayload() throws Exception
    {
        dispatcher.init();

        ArgumentCaptor<StreamMessageHandler> handler = ArgumentCaptor.forClass(StreamMessageHandler.class);
        verify(streamQueueService).registerHandler(eq(QueueNames.MESSAGE_NOTIFY), handler.capture());

        AiMessage payload = new AiMessage();
        payload.setReceiverUserId(RECEIVER_ID);
        payload.setTitle("站内信");
        handler.getValue().onMessage(new ObjectMapper().writeValueAsString(payload));

        ArgumentCaptor<AiMessage> saved = ArgumentCaptor.forClass(AiMessage.class);
        verify(messageService).insertAiMessage(saved.capture());
        assertThat(saved.getValue().getReceiverUserId()).isEqualTo(RECEIVER_ID);
        verify(callEventPublisher).publishToUser(eq(RECEIVER_ID), eq(MessageNotifyDispatcher.WS_EVENT), any());
    }

    private static SysUser user(Long userId)
    {
        SysUser u = new SysUser();
        u.setUserId(userId);
        return u;
    }

    private static String repeat(char c, int count)
    {
        char[] chars = new char[count];
        Arrays.fill(chars, c);
        return new String(chars);
    }
}
