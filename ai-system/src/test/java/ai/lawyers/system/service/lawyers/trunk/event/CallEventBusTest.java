package ai.lawyers.system.service.lawyers.trunk.event;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import ai.lawyers.system.service.lawyers.queue.QueueNames;
import ai.lawyers.system.service.lawyers.queue.StreamQueueService;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** B2：统一事件总线单测——入队成功不直调 / 入队失败降级 / 开关关闭降级 */
class CallEventBusTest
{
    private CallEventBus bus;
    private StreamQueueService streamQueueService;
    private CallEventProcessor processor;

    @BeforeEach
    void setUp()
    {
        bus = new CallEventBus();
        streamQueueService = mock(StreamQueueService.class);
        processor = mock(CallEventProcessor.class);
        ReflectionTestUtils.setField(bus, "streamQueueService", streamQueueService);
        ReflectionTestUtils.setField(bus, "processor", processor);
        ReflectionTestUtils.setField(bus, "streamEnabled", true);
    }

    @Test
    void enqueueSuccessSkipsDirectHandle()
    {
        when(streamQueueService.enqueue(eq(QueueNames.CALL_EVENT), anyString())).thenReturn(true);

        bus.dispatch(CallEvent.of("ESL:10.0.0.1", CallEvent.ANSWERED, "uuid-1"));

        verify(streamQueueService).enqueue(eq(QueueNames.CALL_EVENT), contains("PBX_EVENT"));
        verify(processor, never()).handle(any());
    }

    @Test
    void enqueueFalseFallsBackToDirectHandle()
    {
        when(streamQueueService.enqueue(anyString(), anyString())).thenReturn(false);

        CallEvent event = CallEvent.of("AMI:10.0.0.8", CallEvent.HANGUP, "uuid-2");
        bus.dispatch(event);

        verify(processor).handle(event);
    }

    @Test
    void enqueueExceptionFallsBackToDirectHandle()
    {
        when(streamQueueService.enqueue(anyString(), anyString()))
                .thenThrow(new RuntimeException("redis down"));

        CallEvent event = CallEvent.of("AMI:10.0.0.8", CallEvent.RINGING, "uuid-3");
        bus.dispatch(event);

        verify(processor).handle(event);
    }

    @Test
    void streamDisabledGoesDirectlyToProcessor()
    {
        ReflectionTestUtils.setField(bus, "streamEnabled", false);

        CallEvent event = CallEvent.of("ESL:10.0.0.1", CallEvent.DTMF, "uuid-4");
        bus.dispatch(event);

        verify(streamQueueService, never()).enqueue(anyString(), anyString());
        verify(processor).handle(event);
    }
}
