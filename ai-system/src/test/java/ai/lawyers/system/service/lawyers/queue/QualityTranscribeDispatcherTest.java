package ai.lawyers.system.service.lawyers.queue;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;
import ai.lawyers.system.service.lawyers.quality.IAiQualityInspectionService;

/**
 * T4-1：{@link QualityTranscribeDispatcher} 质检转写队列接入测试。
 *
 * <p>覆盖消费者注册与 recordId 校验、开关/空 ID 跳过、异步投递与队列禁用/异常时同步降级，
 * 以及同步执行异常吞没。</p>
 *
 * @author ai-lawyers
 */
class QualityTranscribeDispatcherTest
{
    private StreamQueueService streamQueueService;

    private IAiQualityInspectionService qualityInspectionService;

    private QualityTranscribeDispatcher dispatcher;

    @BeforeEach
    void setUp()
    {
        streamQueueService = mock(StreamQueueService.class);
        qualityInspectionService = mock(IAiQualityInspectionService.class);
        dispatcher = new QualityTranscribeDispatcher();
        ReflectionTestUtils.setField(dispatcher, "streamQueueService", streamQueueService);
        ReflectionTestUtils.setField(dispatcher, "qualityInspectionService", qualityInspectionService);
        ReflectionTestUtils.setField(dispatcher, "qualityEnabled", true);
    }

    @Test
    void initRegistersConsumerThatRunsInspection() throws Exception
    {
        dispatcher.init();

        captureHandler().onMessage("{\"recordId\":9}");

        verify(qualityInspectionService).inspect(9L);
    }

    @Test
    void consumerSkipsPayloadWithoutRecordId() throws Exception
    {
        dispatcher.init();
        StreamMessageHandler handler = captureHandler();

        handler.onMessage("{}");
        handler.onMessage("{\"recordId\":null}");
        handler.onMessage("null");

        verify(qualityInspectionService, never()).inspect(anyLong());
    }

    @Test
    void enqueueSkipsWhenDisabledOrMissingRecordId()
    {
        ReflectionTestUtils.setField(dispatcher, "qualityEnabled", false);
        dispatcher.enqueue(9L);

        ReflectionTestUtils.setField(dispatcher, "qualityEnabled", true);
        dispatcher.enqueue(null);

        verify(streamQueueService, never()).enqueue(anyString(), anyString());
        verify(qualityInspectionService, never()).inspect(anyLong());
    }

    @Test
    void enqueuePublishesTask()
    {
        when(streamQueueService.enqueue(eq(QueueNames.QUALITY_TRANSCRIBE), anyString())).thenReturn(true);

        dispatcher.enqueue(9L);

        ArgumentCaptor<String> payload = ArgumentCaptor.forClass(String.class);
        verify(streamQueueService).enqueue(eq(QueueNames.QUALITY_TRANSCRIBE), payload.capture());
        assertThat(payload.getValue()).isEqualTo("{\"recordId\":9}");
        verify(qualityInspectionService, never()).inspect(anyLong());
    }

    @Test
    void enqueueFallsBackToSyncWhenQueueUnavailable()
    {
        when(streamQueueService.enqueue(anyString(), anyString())).thenReturn(false);

        dispatcher.enqueue(9L);

        verify(qualityInspectionService).inspect(9L);
    }

    @Test
    void enqueueFallsBackToSyncWhenQueueThrows()
    {
        when(streamQueueService.enqueue(anyString(), anyString()))
                .thenThrow(new IllegalStateException("redis down"));

        dispatcher.enqueue(9L);

        verify(qualityInspectionService).inspect(9L);
    }

    @Test
    void enqueueSwallowsSyncFailure()
    {
        when(streamQueueService.enqueue(anyString(), anyString())).thenReturn(false);
        doThrow(new IllegalStateException("inspect down")).when(qualityInspectionService).inspect(anyLong());

        dispatcher.enqueue(9L);

        verify(qualityInspectionService).inspect(9L);
    }

    private StreamMessageHandler captureHandler()
    {
        ArgumentCaptor<StreamMessageHandler> captor = ArgumentCaptor.forClass(StreamMessageHandler.class);
        verify(streamQueueService).registerHandler(eq(QueueNames.QUALITY_TRANSCRIBE), captor.capture());
        return captor.getValue();
    }
}
