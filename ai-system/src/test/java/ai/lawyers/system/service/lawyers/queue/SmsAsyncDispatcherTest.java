package ai.lawyers.system.service.lawyers.queue;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Collections;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;
import ai.lawyers.system.service.lawyers.sms.ISmsService;

/**
 * T2-3：{@link SmsAsyncDispatcher} 短信异步队列接入测试。
 *
 * <p>覆盖消费者注册与消息解析、异步投递、队列禁用/异常降级同步发送、
 * 同步发送异常吞没与空参数序列化。</p>
 *
 * @author ai-lawyers
 */
class SmsAsyncDispatcherTest
{
    private StreamQueueService streamQueueService;

    private ISmsService smsService;

    private SmsAsyncDispatcher dispatcher;

    @BeforeEach
    void setUp()
    {
        streamQueueService = mock(StreamQueueService.class);
        smsService = mock(ISmsService.class);
        dispatcher = new SmsAsyncDispatcher();
        ReflectionTestUtils.setField(dispatcher, "streamQueueService", streamQueueService);
        ReflectionTestUtils.setField(dispatcher, "smsService", smsService);
    }

    @Test
    void initRegistersConsumerThatSendsSms() throws Exception
    {
        dispatcher.init();

        StreamMessageHandler handler = captureHandler();
        handler.onMessage("{\"phone\":\"13800000000\",\"templateId\":7,"
                + "\"params\":{\"name\":\"张三\"},\"sessionId\":\"s1\",\"recordId\":9}");

        verify(smsService).send(eq("13800000000"), eq(7L),
                eq(Collections.singletonMap("name", "张三")), eq("s1"), eq(9L));
    }

    @Test
    void consumerRejectsMalformedPayload()
    {
        dispatcher.init();

        assertThatThrownBy(() -> captureHandler().onMessage("not-json"))
                .isInstanceOf(Exception.class);
        verify(smsService, never()).send(anyString(), anyLong(), anyMap(), any(), any());
    }

    @Test
    void sendAsyncEnqueuesSerializedTask()
    {
        when(streamQueueService.enqueue(eq(QueueNames.SMS_SEND), anyString())).thenReturn(true);

        dispatcher.sendAsync("13800000000", 7L, Collections.singletonMap("code", "1234"), "s1", 9L);

        ArgumentCaptor<String> payload = ArgumentCaptor.forClass(String.class);
        verify(streamQueueService).enqueue(eq(QueueNames.SMS_SEND), payload.capture());
        assertThat(payload.getValue())
                .contains("\"phone\":\"13800000000\"")
                .contains("\"templateId\":7")
                .contains("\"code\":\"1234\"")
                .contains("\"sessionId\":\"s1\"")
                .contains("\"recordId\":9");
        verify(smsService, never()).send(anyString(), anyLong(), anyMap(), any(), any());
    }

    @Test
    void sendAsyncFallsBackToSyncWhenQueueDisabled()
    {
        when(streamQueueService.enqueue(eq(QueueNames.SMS_SEND), anyString())).thenReturn(false);

        dispatcher.sendAsync("13800000000", 7L, null, "s1", 9L);

        verify(smsService).send("13800000000", 7L, null, "s1", 9L);
    }

    @Test
    void sendAsyncFallsBackToSyncWhenEnqueueThrows()
    {
        when(streamQueueService.enqueue(eq(QueueNames.SMS_SEND), anyString()))
                .thenThrow(new IllegalStateException("redis down"));

        dispatcher.sendAsync("13800000000", 7L, Collections.singletonMap("a", "b"), null, null);

        verify(smsService).send(eq("13800000000"), eq(7L),
                eq(Collections.singletonMap("a", "b")), eq(null), eq(null));
    }

    @Test
    void sendAsyncSwallowsSyncFailure()
    {
        when(streamQueueService.enqueue(anyString(), anyString())).thenReturn(false);
        doThrow(new IllegalStateException("sms down")).when(smsService)
                .send(anyString(), anyLong(), anyMap(), any(), any());

        dispatcher.sendAsync("13800000000", 7L, null, null, null);
    }

    @Test
    void sendAsyncSerializesNullParamsAsEmptyMap()
    {
        when(streamQueueService.enqueue(anyString(), anyString())).thenReturn(true);

        dispatcher.sendAsync("13800000000", 7L, null, null, null);

        ArgumentCaptor<String> payload = ArgumentCaptor.forClass(String.class);
        verify(streamQueueService).enqueue(eq(QueueNames.SMS_SEND), payload.capture());
        assertThat(payload.getValue()).contains("\"params\":{}");
    }

    private StreamMessageHandler captureHandler()
    {
        ArgumentCaptor<StreamMessageHandler> captor = ArgumentCaptor.forClass(StreamMessageHandler.class);
        verify(streamQueueService).registerHandler(eq(QueueNames.SMS_SEND), captor.capture());
        return captor.getValue();
    }
}
