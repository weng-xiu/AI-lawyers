package ai.lawyers.system.service.lawyers.queue;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.Date;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import com.fasterxml.jackson.databind.ObjectMapper;

import ai.lawyers.system.domain.lawyers.quality.AiAgentStatusLog;
import ai.lawyers.system.mapper.lawyers.quality.AiAgentStatusLogMapper;

/**
 * T4-3：{@link StatusLogDispatcher} 坐席状态流水生产端与消费端测试。
 *
 * <p>生产端：空坐席/状态未变化不入队、入队成功不落库、入队失败或异常降级同步落库；
 * 消费端：落库前回填上一条流水 duration（秒差），已回填/时间未推进/上条无时间不重复写，
 * 超长时长按 int 上限截断，落库异常不影响主流程，注册的消费者可解析消息体落库。</p>
 *
 * @author ai-lawyers
 */
class StatusLogDispatcherTest
{
    private static final Long AGENT_ID = 7L;

    private StreamQueueService streamQueueService;

    private AiAgentStatusLogMapper statusLogMapper;

    private StatusLogDispatcher dispatcher;

    @BeforeEach
    void setUp()
    {
        streamQueueService = mock(StreamQueueService.class);
        statusLogMapper = mock(AiAgentStatusLogMapper.class);
        dispatcher = new StatusLogDispatcher();
        ReflectionTestUtils.setField(dispatcher, "streamQueueService", streamQueueService);
        ReflectionTestUtils.setField(dispatcher, "statusLogMapper", statusLogMapper);
    }

    // ---------- 生产端 ----------

    @Test
    void logIgnoresNullAgentId()
    {
        dispatcher.log(null, 1L, "LOGIN", null, "READY", null, "IDLE", null, null);

        verifyNoInteractions(streamQueueService, statusLogMapper);
    }

    @Test
    void logSkipsWhenStatusUnchanged()
    {
        dispatcher.log(AGENT_ID, 1L, "HEARTBEAT", "READY", "READY", "IDLE", "IDLE", null, new Date());

        verifyNoInteractions(streamQueueService, statusLogMapper);
    }

    @Test
    void logEnqueuesWhenStatusChanged()
    {
        when(streamQueueService.enqueue(eq(QueueNames.STATUS_LOG), anyString())).thenReturn(true);

        dispatcher.log(AGENT_ID, 1L, "LOGIN", "OFFLINE", "READY", "IDLE", "IDLE", 9L, null);

        ArgumentCaptor<String> payload = ArgumentCaptor.forClass(String.class);
        verify(streamQueueService).enqueue(eq(QueueNames.STATUS_LOG), payload.capture());
        assertThat(payload.getValue()).contains("\"agentId\":7").contains("\"eventType\":\"LOGIN\"");
        verify(statusLogMapper, never()).insertAiAgentStatusLog(any());
    }

    @Test
    void logTreatsNullAndEmptyStatusAsDifferent()
    {
        when(streamQueueService.enqueue(eq(QueueNames.STATUS_LOG), anyString())).thenReturn(true);

        // 通话状态由 null 变为 IDLE，视为状态变化
        dispatcher.log(AGENT_ID, 1L, "ANSWER", "READY", "READY", null, "IDLE", null, null);

        verify(streamQueueService).enqueue(eq(QueueNames.STATUS_LOG), anyString());
    }

    @Test
    void logFallsBackToDbWhenQueueRejects()
    {
        when(streamQueueService.enqueue(eq(QueueNames.STATUS_LOG), anyString())).thenReturn(false);

        Date logTime = new Date();
        dispatcher.log(AGENT_ID, 3L, "LOGIN", "OFFLINE", "READY", null, "IDLE", 15L, logTime);

        ArgumentCaptor<AiAgentStatusLog> saved = ArgumentCaptor.forClass(AiAgentStatusLog.class);
        verify(statusLogMapper).insertAiAgentStatusLog(saved.capture());
        assertThat(saved.getValue().getAgentId()).isEqualTo(AGENT_ID);
        assertThat(saved.getValue().getUserId()).isEqualTo(3L);
        assertThat(saved.getValue().getRecordId()).isEqualTo(15L);
        assertThat(saved.getValue().getLogTime()).isEqualTo(logTime);
    }

    @Test
    void logFallsBackToDbWhenEnqueueThrows()
    {
        when(streamQueueService.enqueue(eq(QueueNames.STATUS_LOG), anyString()))
                .thenThrow(new IllegalStateException("redis down"));

        dispatcher.log(AGENT_ID, 1L, "LOGOUT", "READY", "OFFLINE", "IDLE", "IDLE", null, null);

        verify(statusLogMapper).insertAiAgentStatusLog(any(AiAgentStatusLog.class));
    }

    @Test
    void logDefaultsLogTimeToNow()
    {
        when(streamQueueService.enqueue(eq(QueueNames.STATUS_LOG), anyString())).thenReturn(false);
        long before = System.currentTimeMillis();

        dispatcher.log(AGENT_ID, 1L, "LOGIN", "OFFLINE", "READY", null, null, null, null);

        ArgumentCaptor<AiAgentStatusLog> saved = ArgumentCaptor.forClass(AiAgentStatusLog.class);
        verify(statusLogMapper).insertAiAgentStatusLog(saved.capture());
        assertThat(saved.getValue().getLogTime().getTime()).isGreaterThanOrEqualTo(before);
    }

    // ---------- 落库与 duration 回填 ----------

    @Test
    void persistBackfillsPreviousDurationInSeconds()
    {
        Date now = new Date(1_700_000_000_000L);
        AiAgentStatusLog prev = prevLog(100L, new Date(now.getTime() - 5_000L), null);
        when(statusLogMapper.selectLatestLog(AGENT_ID)).thenReturn(prev);
        when(streamQueueService.enqueue(eq(QueueNames.STATUS_LOG), anyString())).thenReturn(false);

        dispatcher.log(AGENT_ID, 1L, "BUSY", "READY", "BUSY", "IDLE", "IDLE", null, now);

        verify(statusLogMapper).updateDuration(100L, 5);
        verify(statusLogMapper).insertAiAgentStatusLog(any(AiAgentStatusLog.class));
    }

    @Test
    void persistSkipsBackfillWhenPreviousDurationAlreadySet()
    {
        Date now = new Date(1_700_000_000_000L);
        AiAgentStatusLog prev = prevLog(101L, new Date(now.getTime() - 9_000L), 30);
        when(statusLogMapper.selectLatestLog(AGENT_ID)).thenReturn(prev);
        when(streamQueueService.enqueue(eq(QueueNames.STATUS_LOG), anyString())).thenReturn(false);

        dispatcher.log(AGENT_ID, 1L, "BUSY", "READY", "BUSY", "IDLE", "IDLE", null, now);

        verify(statusLogMapper, never()).updateDuration(anyLong(), anyInt());
        verify(statusLogMapper).insertAiAgentStatusLog(any(AiAgentStatusLog.class));
    }

    @Test
    void persistSkipsBackfillWhenPreviousLogTimeMissing()
    {
        AiAgentStatusLog prev = prevLog(102L, null, null);
        when(statusLogMapper.selectLatestLog(AGENT_ID)).thenReturn(prev);
        when(streamQueueService.enqueue(eq(QueueNames.STATUS_LOG), anyString())).thenReturn(false);

        dispatcher.log(AGENT_ID, 1L, "BUSY", null, "BUSY", null, null, null, new Date());

        verify(statusLogMapper, never()).updateDuration(anyLong(), anyInt());
    }

    @Test
    void persistSkipsBackfillWhenTimeNotAdvances()
    {
        Date now = new Date(1_700_000_000_000L);
        AiAgentStatusLog prev = prevLog(103L, now, null);
        when(statusLogMapper.selectLatestLog(AGENT_ID)).thenReturn(prev);
        when(streamQueueService.enqueue(eq(QueueNames.STATUS_LOG), anyString())).thenReturn(false);

        dispatcher.log(AGENT_ID, 1L, "BUSY", null, "BUSY", null, null, null, now);

        verify(statusLogMapper, never()).updateDuration(anyLong(), anyInt());
    }

    @Test
    void persistClampsHugeDurationToIntMax()
    {
        Date now = new Date(3_000_000_000_000L);
        AiAgentStatusLog prev = prevLog(104L, new Date(0L), null);
        when(statusLogMapper.selectLatestLog(AGENT_ID)).thenReturn(prev);
        when(streamQueueService.enqueue(eq(QueueNames.STATUS_LOG), anyString())).thenReturn(false);

        dispatcher.log(AGENT_ID, 1L, "BUSY", null, "BUSY", null, null, null, now);

        verify(statusLogMapper).updateDuration(104L, Integer.MAX_VALUE);
    }

    @Test
    void persistSwallowsMapperFailure()
    {
        when(statusLogMapper.selectLatestLog(AGENT_ID)).thenThrow(new RuntimeException("db down"));
        when(streamQueueService.enqueue(eq(QueueNames.STATUS_LOG), anyString())).thenReturn(false);

        dispatcher.log(AGENT_ID, 1L, "BUSY", null, "BUSY", null, null, null, new Date());

        verify(statusLogMapper, never()).insertAiAgentStatusLog(any());
    }

    // ---------- 消费端 ----------

    @Test
    void registeredConsumerPersistsPayload() throws Exception
    {
        dispatcher.init();

        ArgumentCaptor<StreamMessageHandler> handler = ArgumentCaptor.forClass(StreamMessageHandler.class);
        verify(streamQueueService).registerHandler(eq(QueueNames.STATUS_LOG), handler.capture());

        handler.getValue().onMessage(new ObjectMapper()
                .writeValueAsString(payloadEntry(AGENT_ID, "LOGIN", null)));

        ArgumentCaptor<AiAgentStatusLog> saved = ArgumentCaptor.forClass(AiAgentStatusLog.class);
        verify(statusLogMapper).insertAiAgentStatusLog(saved.capture());
        assertThat(saved.getValue().getAgentId()).isEqualTo(AGENT_ID);
        assertThat(saved.getValue().getEventType()).isEqualTo("LOGIN");
    }

    @Test
    void consumerBackfillsDurationBeforeInsert() throws Exception
    {
        Date now = new Date(1_700_000_000_000L);
        when(statusLogMapper.selectLatestLog(AGENT_ID))
                .thenReturn(prevLog(200L, new Date(now.getTime() - 12_000L), null));
        dispatcher.init();

        ArgumentCaptor<StreamMessageHandler> handler = ArgumentCaptor.forClass(StreamMessageHandler.class);
        verify(streamQueueService).registerHandler(eq(QueueNames.STATUS_LOG), handler.capture());
        handler.getValue().onMessage(new ObjectMapper().writeValueAsString(payloadEntry(AGENT_ID, "BUSY", now)));

        verify(statusLogMapper, times(1)).updateDuration(200L, 12);
        verify(statusLogMapper, times(1)).insertAiAgentStatusLog(any(AiAgentStatusLog.class));
    }

    private static AiAgentStatusLog payloadEntry(Long agentId, String eventType, Date logTime)
    {
        AiAgentStatusLog entry = new AiAgentStatusLog();
        entry.setAgentId(agentId);
        entry.setEventType(eventType);
        entry.setLogTime(logTime);
        return entry;
    }

    private static AiAgentStatusLog prevLog(Long logId, Date logTime, Integer duration)
    {
        AiAgentStatusLog prev = new AiAgentStatusLog();
        prev.setLogId(logId);
        prev.setAgentId(AGENT_ID);
        prev.setLogTime(logTime);
        prev.setDuration(duration);
        return prev;
    }
}
