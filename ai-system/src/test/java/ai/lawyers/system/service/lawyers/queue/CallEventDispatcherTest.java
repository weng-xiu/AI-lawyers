package ai.lawyers.system.service.lawyers.queue;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import ai.lawyers.system.domain.lawyers.AiCallRecord;
import ai.lawyers.system.domain.lawyers.trunk.AiCallDialLog;
import ai.lawyers.system.mapper.lawyers.trunk.AiCallDialLogMapper;
import ai.lawyers.system.service.lawyers.IAiCallRecordService;
import ai.lawyers.system.service.lawyers.trunk.event.CallEvent;
import ai.lawyers.system.service.lawyers.trunk.event.CallEventBus;

/**
 * W1/P3-B2：{@link CallEventDispatcher} 生产端投递与消费端分发测试。
 *
 * <p>生产端：入队成功不直写、入队失败/异常降级同步、缺 ID 忽略；
 * 消费端：按 eventType 分发到话单服务/拨号日志 Mapper/PBX 事件总线，
 * 坏消息抛异常交由 {@link StreamQueueService} 重试并最终转死信（P5 点名缺测）。</p>
 *
 * @author ai-lawyers
 */
class CallEventDispatcherTest
{
    private final ObjectMapper mapper = new ObjectMapper();

    private StreamQueueService streamQueueService;

    private IAiCallRecordService callRecordService;

    private AiCallDialLogMapper dialLogMapper;

    private CallEventBus callEventBus;

    private CallEventDispatcher dispatcher;

    @BeforeEach
    void setUp()
    {
        streamQueueService = mock(StreamQueueService.class);
        callRecordService = mock(IAiCallRecordService.class);
        dialLogMapper = mock(AiCallDialLogMapper.class);
        callEventBus = mock(CallEventBus.class);
        dispatcher = new CallEventDispatcher();
        ReflectionTestUtils.setField(dispatcher, "streamQueueService", streamQueueService);
        ReflectionTestUtils.setField(dispatcher, "callRecordService", callRecordService);
        ReflectionTestUtils.setField(dispatcher, "dialLogMapper", dialLogMapper);
        ReflectionTestUtils.setField(dispatcher, "callEventBus", callEventBus);
    }

    // ---------- 消费组注册 ----------

    @Test
    void init_registersConsumerOnCallEventQueue()
    {
        dispatcher.init();

        ArgumentCaptor<StreamMessageHandler> handler = ArgumentCaptor.forClass(StreamMessageHandler.class);
        verify(streamQueueService).registerHandler(eq(QueueNames.CALL_EVENT), handler.capture());
        assertThat(handler.getValue()).isNotNull();
    }

    // ---------- 生产端：话单更新 ----------

    @Test
    void updateCallRecordAsync_enqueueSucceeded_skipsSyncWrite() throws Exception
    {
        AiCallRecord record = new AiCallRecord();
        record.setRecordId(1001L);
        when(streamQueueService.enqueue(eq(QueueNames.CALL_EVENT), anyString())).thenReturn(true);

        dispatcher.updateCallRecordAsync(record);

        ArgumentCaptor<String> payload = ArgumentCaptor.forClass(String.class);
        verify(streamQueueService).enqueue(eq(QueueNames.CALL_EVENT), payload.capture());
        JsonNode node = mapper.readTree(payload.getValue());
        assertThat(node.get("eventType").asText()).isEqualTo(CallEventDispatcher.UPDATE_CALL_RECORD);
        assertThat(node.get("callRecord").get("recordId").asLong()).isEqualTo(1001L);
        verify(callRecordService, never()).updateAiCallRecord(any());
    }

    @Test
    void updateCallRecordAsync_enqueueRefused_fallsBackToSyncWrite()
    {
        AiCallRecord record = new AiCallRecord();
        record.setRecordId(1002L);
        when(streamQueueService.enqueue(eq(QueueNames.CALL_EVENT), anyString())).thenReturn(false);

        dispatcher.updateCallRecordAsync(record);

        verify(callRecordService).updateAiCallRecord(record);
    }

    @Test
    void updateCallRecordAsync_enqueueThrows_fallsBackToSyncWrite()
    {
        AiCallRecord record = new AiCallRecord();
        record.setRecordId(1003L);
        when(streamQueueService.enqueue(eq(QueueNames.CALL_EVENT), anyString()))
                .thenThrow(new RuntimeException("redis down"));

        dispatcher.updateCallRecordAsync(record);

        verify(callRecordService).updateAiCallRecord(record);
    }

    @Test
    void updateCallRecordAsync_withoutRecordId_ignored()
    {
        dispatcher.updateCallRecordAsync(null);
        dispatcher.updateCallRecordAsync(new AiCallRecord());

        verify(streamQueueService, never()).enqueue(anyString(), anyString());
        verifyNoInteractions(callRecordService);
    }

    // ---------- 生产端：拨号日志更新 ----------

    @Test
    void updateDialLogAsync_enqueueSucceeded_skipsSyncWrite() throws Exception
    {
        AiCallDialLog dialLog = new AiCallDialLog();
        dialLog.setLogId(2001L);
        when(streamQueueService.enqueue(eq(QueueNames.CALL_EVENT), anyString())).thenReturn(true);

        dispatcher.updateDialLogAsync(dialLog);

        ArgumentCaptor<String> payload = ArgumentCaptor.forClass(String.class);
        verify(streamQueueService).enqueue(eq(QueueNames.CALL_EVENT), payload.capture());
        JsonNode node = mapper.readTree(payload.getValue());
        assertThat(node.get("eventType").asText()).isEqualTo(CallEventDispatcher.UPDATE_DIAL_LOG);
        assertThat(node.get("dialLog").get("logId").asLong()).isEqualTo(2001L);
        verifyNoInteractions(dialLogMapper);
    }

    @Test
    void updateDialLogAsync_enqueueRefused_fallsBackToSyncWrite()
    {
        AiCallDialLog dialLog = new AiCallDialLog();
        dialLog.setLogId(2002L);
        when(streamQueueService.enqueue(eq(QueueNames.CALL_EVENT), anyString())).thenReturn(false);

        dispatcher.updateDialLogAsync(dialLog);

        verify(dialLogMapper).updateAiCallDialLog(dialLog);
    }

    @Test
    void updateDialLogAsync_withoutLogId_ignored()
    {
        dispatcher.updateDialLogAsync(null);
        dispatcher.updateDialLogAsync(new AiCallDialLog());

        verify(streamQueueService, never()).enqueue(anyString(), anyString());
        verifyNoInteractions(dialLogMapper);
    }

    // ---------- 消费端：按 eventType 分发 ----------

    @Test
    void consumer_dispatchesUpdateCallRecord() throws Exception
    {
        consumer().onMessage("{\"eventType\":\"UPDATE_CALL_RECORD\",\"callRecord\":{\"recordId\":7001}}");

        ArgumentCaptor<AiCallRecord> record = ArgumentCaptor.forClass(AiCallRecord.class);
        verify(callRecordService).updateAiCallRecord(record.capture());
        assertThat(record.getValue().getRecordId()).isEqualTo(7001L);
        verifyNoInteractions(dialLogMapper, callEventBus);
    }

    @Test
    void consumer_dispatchesUpdateDialLog() throws Exception
    {
        consumer().onMessage("{\"eventType\":\"UPDATE_DIAL_LOG\",\"dialLog\":{\"logId\":7002}}");

        ArgumentCaptor<AiCallDialLog> dialLog = ArgumentCaptor.forClass(AiCallDialLog.class);
        verify(dialLogMapper).updateAiCallDialLog(dialLog.capture());
        assertThat(dialLog.getValue().getLogId()).isEqualTo(7002L);
        verifyNoInteractions(callRecordService, callEventBus);
    }

    @Test
    void consumer_routesPbxEventToEventBus() throws Exception
    {
        consumer().onMessage("{\"eventType\":\"PBX_EVENT\",\"callEvent\":"
                + "{\"eventName\":\"INBOUND\",\"sessionId\":\"sess-1\"}}");

        ArgumentCaptor<CallEvent> event = ArgumentCaptor.forClass(CallEvent.class);
        verify(callEventBus).onStreamEvent(event.capture());
        assertThat(event.getValue().getEventName()).isEqualTo(CallEvent.INBOUND);
        assertThat(event.getValue().getSessionId()).isEqualTo("sess-1");
        verifyNoInteractions(callRecordService, dialLogMapper);
    }

    @Test
    void consumer_ignoresUnknownTypeAndMissingPayload() throws Exception
    {
        StreamMessageHandler handler = consumer();

        // 未知类型 / 缺 eventType / 事件类型对但无载荷：静默忽略，不误写库
        handler.onMessage("{\"eventType\":\"NOT_EXIST\"}");
        handler.onMessage("{\"callRecord\":{\"recordId\":1}}");
        handler.onMessage("{\"eventType\":\"UPDATE_CALL_RECORD\"}");
        handler.onMessage("{\"eventType\":\"UPDATE_DIAL_LOG\"}");
        handler.onMessage("{\"eventType\":\"PBX_EVENT\"}");

        verifyNoInteractions(callRecordService, dialLogMapper, callEventBus);
    }

    @Test
    void consumer_badJson_throwsForStreamRetry()
    {
        // 解析失败必须外抛：StreamQueueService 据此不 ACK → 退避重投 → 超限转死信
        assertThatThrownBy(() -> consumer().onMessage("not-json"))
                .isInstanceOf(Exception.class);
    }

    private StreamMessageHandler consumer()
    {
        dispatcher.init();
        ArgumentCaptor<StreamMessageHandler> handler = ArgumentCaptor.forClass(StreamMessageHandler.class);
        verify(streamQueueService).registerHandler(eq(QueueNames.CALL_EVENT), handler.capture());
        return handler.getValue();
    }
}
