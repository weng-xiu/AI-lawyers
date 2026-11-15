package ai.lawyers.system.service.lawyers.queue;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.Range;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.PendingMessage;
import org.springframework.data.redis.connection.stream.PendingMessages;
import org.springframework.data.redis.connection.stream.PendingMessagesSummary;
import org.springframework.data.redis.connection.stream.RecordId;
import org.springframework.data.redis.core.StreamOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * T2-3/P3-H1：{@link StreamQueueService} Redis Stream 队列底座测试。
 *
 * <p>覆盖开关与投递降级、pending/XLEN 指标口径与异常哨兵、注册队列只读视图、
 * 死信列表信封解析与解析失败兜底、死信重投/删除、消息处理 ACK 与失败转死信
 * （按 XPENDING 真实投递次数与重试开关）、退避上限、投递次数查询、XPENDING 行解析
 * 以及关闭幂等。</p>
 *
 * @author ai-lawyers
 */
class StreamQueueServiceTest
{
    private static final String QUEUE = "test";

    private static final String KEY = "stream:test";

    private static final String DEAD_KEY = "stream:test:dead";

    private static final String GROUP = "g";

    private StringRedisTemplate redisTemplate;

    private StreamOperations<String, Object, Object> streamOperations;

    private StreamQueueService service;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp()
    {
        redisTemplate = mock(StringRedisTemplate.class);
        streamOperations = mock(StreamOperations.class);
        when(redisTemplate.opsForStream()).thenReturn(streamOperations);

        service = new StreamQueueService();
        ReflectionTestUtils.setField(service, "stringRedisTemplate", redisTemplate);
        ReflectionTestUtils.setField(service, "enabled", true);
        ReflectionTestUtils.setField(service, "groupName", GROUP);
        ReflectionTestUtils.setField(service, "maxDeliveries", 5L);
        ReflectionTestUtils.setField(service, "retryEnabled", true);
        ReflectionTestUtils.setField(service, "blockMs", 100L);
        ReflectionTestUtils.setField(service, "reclaimMinIdleMs", 60000L);
    }

    // ---------- 投递 ----------

    @Test
    void enqueueSkippedWhenDisabled()
    {
        ReflectionTestUtils.setField(service, "enabled", false);

        assertThat(service.enqueue(QUEUE, "{}")).isFalse();
        assertThat(service.isEnabled()).isFalse();
        verify(redisTemplate, never()).opsForStream();
    }

    @Test
    void enqueueAddsPayloadRecord()
    {
        assertThat(service.enqueue(QUEUE, "{\"a\":1}")).isTrue();

        @SuppressWarnings("rawtypes")
        ArgumentCaptor<MapRecord> captor = ArgumentCaptor.forClass(MapRecord.class);
        verify(streamOperations).add(captor.capture());
        assertThat(captor.getValue().getStream()).isEqualTo(KEY);
        assertThat(((Map<?, ?>) captor.getValue().getValue()).get(StreamQueueService.PAYLOAD_FIELD))
                .isEqualTo("{\"a\":1}");
    }

    @Test
    void enqueueFallsBackOnRedisFailure()
    {
        when(streamOperations.add(any())).thenThrow(new IllegalStateException("redis down"));

        assertThat(service.enqueue(QUEUE, "{}")).isFalse();
    }

    // ---------- 指标 ----------

    @Test
    void pendingCountReturnsSummaryTotalAndSentinels()
    {
        PendingMessagesSummary summary = mock(PendingMessagesSummary.class);
        when(summary.getTotalPendingMessages()).thenReturn(7L);
        when(streamOperations.pending(eq(KEY), eq(GROUP))).thenReturn(summary);

        assertThat(service.pendingCount(QUEUE)).isEqualTo(7L);

        when(streamOperations.pending(eq(KEY), eq(GROUP))).thenReturn(null);
        assertThat(service.pendingCount(QUEUE)).isZero();

        when(streamOperations.pending(eq(KEY), eq(GROUP))).thenThrow(new IllegalStateException("down"));
        assertThat(service.pendingCount(QUEUE)).isEqualTo(-1L);
    }

    @Test
    void streamAndDeadLetterLengths()
    {
        when(streamOperations.size(KEY)).thenReturn(42L);
        when(streamOperations.size(DEAD_KEY)).thenReturn(3L);

        assertThat(service.streamLength(QUEUE)).isEqualTo(42L);
        assertThat(service.deadLetterCount(QUEUE)).isEqualTo(3L);

        when(streamOperations.size(KEY)).thenReturn(null);
        assertThat(service.streamLength(QUEUE)).isZero();

        when(streamOperations.size(DEAD_KEY)).thenThrow(new IllegalStateException("down"));
        assertThat(service.deadLetterCount(QUEUE)).isEqualTo(-1L);
    }

    @Test
    void registeredQueuesIsReadOnly()
    {
        service.registerHandler(QUEUE, message -> { });

        assertThat(service.registeredQueues()).containsExactly(QUEUE);
        assertThatThrownBy(() -> service.registeredQueues().add("x"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void registrationDoesNotStartConsumerBeforeInit()
    {
        service.registerHandler(QUEUE, message -> { });

        verify(redisTemplate, never()).hasKey(anyString());
    }

    // ---------- 死信列表 ----------

    @Test
    @SuppressWarnings("unchecked")
    void deadLetterListParsesEnvelope() throws Exception
    {
        Map<String, Object> envelope = new HashMap<>();
        envelope.put("originStream", KEY);
        envelope.put("originId", "1-0");
        envelope.put("error", "boom");
        envelope.put("payload", Collections.singletonMap("a", 1));
        MapRecord<String, Object, Object> record = mock(MapRecord.class);
        when(record.getId()).thenReturn(RecordId.of("9-0"));
        when(record.getValue()).thenReturn(Collections.singletonMap(
                StreamQueueService.PAYLOAD_FIELD, new ObjectMapper().writeValueAsString(envelope)));
        when(streamOperations.reverseRange(eq(DEAD_KEY), any(Range.class), any())).thenReturn(
                Collections.singletonList(record));

        List<Map<String, Object>> rows = service.deadLetterList(QUEUE, 0, 10);

        assertThat(rows).hasSize(1);
        assertThat(rows.get(0))
                .containsEntry("id", "9-0")
                .containsEntry("originStream", KEY)
                .containsEntry("originId", "1-0")
                .containsEntry("error", "boom")
                .containsEntry("payload", "{\"a\":1}");
    }

    @Test
    @SuppressWarnings("unchecked")
    void deadLetterListKeepsRawPayloadWhenUnparseable()
    {
        MapRecord<String, Object, Object> record = mock(MapRecord.class);
        when(record.getId()).thenReturn(RecordId.of("9-0"));
        when(record.getValue()).thenReturn(Collections.singletonMap(
                StreamQueueService.PAYLOAD_FIELD, "not-json"));
        when(streamOperations.reverseRange(eq(DEAD_KEY), any(Range.class), any())).thenReturn(
                Collections.singletonList(record));

        List<Map<String, Object>> rows = service.deadLetterList(QUEUE, 0, 10);

        assertThat(rows.get(0)).containsEntry("raw", "not-json");
    }

    @Test
    void deadLetterListReturnsEmptyOnNullOrFailure()
    {
        when(streamOperations.reverseRange(eq(DEAD_KEY), any(Range.class), any())).thenReturn(null);
        assertThat(service.deadLetterList(QUEUE, 0, 10)).isEmpty();

        when(streamOperations.reverseRange(eq(DEAD_KEY), any(Range.class), any()))
                .thenThrow(new IllegalStateException("down"));
        assertThat(service.deadLetterList(QUEUE, 0, 10)).isEmpty();
    }

    // ---------- 死信重投与删除 ----------

    @Test
    @SuppressWarnings("unchecked")
    void replayDeadLetterRepublishesAndDeletes() throws Exception
    {
        Map<String, Object> envelope = new HashMap<>();
        envelope.put("originStream", KEY);
        envelope.put("payload", Collections.singletonMap("a", 1));
        MapRecord<String, Object, Object> record = mock(MapRecord.class);
        when(record.getValue()).thenReturn(Collections.singletonMap(
                StreamQueueService.PAYLOAD_FIELD, new ObjectMapper().writeValueAsString(envelope)));
        when(streamOperations.range(eq(DEAD_KEY), any(Range.class))).thenReturn(Collections.singletonList(record));

        assertThat(service.replayDeadLetter(QUEUE, "9-0")).isTrue();

        @SuppressWarnings("rawtypes")
        ArgumentCaptor<MapRecord> captor = ArgumentCaptor.forClass(MapRecord.class);
        verify(streamOperations).add(captor.capture());
        assertThat(captor.getValue().getStream()).isEqualTo(KEY);
        assertThat(((Map<?, ?>) captor.getValue().getValue()).get(StreamQueueService.PAYLOAD_FIELD))
                .isEqualTo("{\"a\":1}");
        verify(streamOperations).delete(DEAD_KEY, "9-0");
    }

    @Test
    @SuppressWarnings("unchecked")
    void replayDeadLetterRejectsMissingEnvelope()
    {
        MapRecord<String, Object, Object> record = mock(MapRecord.class);
        when(record.getValue()).thenReturn(Collections.singletonMap(
                StreamQueueService.PAYLOAD_FIELD, "{\"originStream\":\"stream:test\"}"));
        when(streamOperations.range(eq(DEAD_KEY), any(Range.class))).thenReturn(Collections.singletonList(record));

        assertThat(service.replayDeadLetter(QUEUE, "9-0")).isFalse();
        verify(streamOperations, never()).add(any());
    }

    @Test
    void replayDeadLetterHandlesMissingRecordAndFailure()
    {
        when(streamOperations.range(eq(DEAD_KEY), any(Range.class))).thenReturn(Collections.emptyList());
        assertThat(service.replayDeadLetter(QUEUE, "9-0")).isFalse();

        when(streamOperations.range(eq(DEAD_KEY), any(Range.class))).thenThrow(new IllegalStateException("down"));
        assertThat(service.replayDeadLetter(QUEUE, "9-0")).isFalse();
    }

    @Test
    void deleteDeadLetterReportsAffectedRows()
    {
        when(streamOperations.delete(DEAD_KEY, "9-0")).thenReturn(1L);
        assertThat(service.deleteDeadLetter(QUEUE, "9-0")).isTrue();

        when(streamOperations.delete(DEAD_KEY, "9-0")).thenReturn(0L);
        assertThat(service.deleteDeadLetter(QUEUE, "9-0")).isFalse();

        when(streamOperations.delete(DEAD_KEY, "9-0")).thenThrow(new IllegalStateException("down"));
        assertThat(service.deleteDeadLetter(QUEUE, "9-0")).isFalse();
    }

    // ---------- 消费与重试 ----------

    @Test
    @SuppressWarnings("unchecked")
    void processRecordAcksOnSuccess()
    {
        MapRecord<String, Object, Object> record = record("1-0", "{\"a\":1}");

        ReflectionTestUtils.invokeMethod(service, "processRecord", QUEUE, KEY,
                (StreamMessageHandler) message -> { }, record);

        verify(streamOperations).acknowledge(KEY, GROUP, RecordId.of("1-0"));
        verify(streamOperations, never()).add(any());
    }

    @Test
    @SuppressWarnings("unchecked")
    void processRecordToleratesMissingHandler()
    {
        ReflectionTestUtils.invokeMethod(service, "processRecord", QUEUE, KEY, null, record("1-0", "{}"));

        verify(streamOperations).acknowledge(KEY, GROUP, RecordId.of("1-0"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void processRecordMovesToDeadLetterAfterMaxDeliveries()
    {
        PendingMessages messages = pendingMessages(5L);
        when(streamOperations.pending(eq(KEY), eq(GROUP), any(Range.class), eq(1L)))
                .thenReturn(messages);
        MapRecord<String, Object, Object> record = record("1-0", "{\"a\":1}");

        ReflectionTestUtils.invokeMethod(service, "processRecord", QUEUE, KEY,
                (StreamMessageHandler) message -> {
                    throw new IllegalStateException("boom\"x");
                }, record);

        @SuppressWarnings("rawtypes")
        ArgumentCaptor<MapRecord> captor = ArgumentCaptor.forClass(MapRecord.class);
        verify(streamOperations).add(captor.capture());
        assertThat(captor.getValue().getStream()).isEqualTo(DEAD_KEY);
        String dead = (String) ((Map<?, ?>) captor.getValue().getValue()).get(StreamQueueService.PAYLOAD_FIELD);
        assertThat(dead)
                .contains("\"originStream\":\"stream:test\"")
                .contains("\"originId\":\"1-0\"")
                .contains("\"error\":\"boom\\\"x\"")
                .contains("\"payload\":{\"a\":1}");
        verify(streamOperations).acknowledge(KEY, GROUP, RecordId.of("1-0"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void processRecordKeepsPendingBelowMaxDeliveries()
    {
        PendingMessages messages = pendingMessages(1L);
        when(streamOperations.pending(eq(KEY), eq(GROUP), any(Range.class), eq(1L)))
                .thenReturn(messages);

        ReflectionTestUtils.invokeMethod(service, "processRecord", QUEUE, KEY,
                (StreamMessageHandler) message -> {
                    throw new IllegalStateException("boom");
                }, record("1-0", "{}"));

        verify(streamOperations, never()).acknowledge(anyString(), anyString(), any(RecordId.class));
        verify(streamOperations, never()).add(any());
    }

    @Test
    @SuppressWarnings("unchecked")
    void processRecordDeadLettersImmediatelyWhenRetryDisabled()
    {
        ReflectionTestUtils.setField(service, "retryEnabled", false);

        ReflectionTestUtils.invokeMethod(service, "processRecord", QUEUE, KEY,
                (StreamMessageHandler) message -> {
                    throw new IllegalStateException("boom");
                }, record("1-0", "{}"));

        verify(streamOperations).acknowledge(KEY, GROUP, RecordId.of("1-0"));
        verify(streamOperations).add(any());
    }

    @Test
    void ackFailureIsSwallowed()
    {
        doThrow(new IllegalStateException("down")).when(streamOperations)
                .acknowledge(anyString(), anyString(), any(RecordId.class));

        ReflectionTestUtils.invokeMethod(service, "processRecord", QUEUE, KEY,
                (StreamMessageHandler) message -> { }, record("1-0", "{}"));

        verify(streamOperations).acknowledge(KEY, GROUP, RecordId.of("1-0"));
    }

    @Test
    void backoffStartsAt500AndCapsAt8s()
    {
        assertThat(StreamQueueService.backoffMs(0)).isEqualTo(500L);
        assertThat(StreamQueueService.backoffMs(1)).isEqualTo(500L);
        assertThat(StreamQueueService.backoffMs(2)).isEqualTo(1000L);
        assertThat(StreamQueueService.backoffMs(20)).isEqualTo(8000L);
    }

    @Test
    @SuppressWarnings("unchecked")
    void deliveryAttemptReadsTotalDeliveryCount()
    {
        PendingMessages messages = pendingMessages(3L);
        when(streamOperations.pending(eq(KEY), eq(GROUP), any(Range.class), eq(1L)))
                .thenReturn(messages);

        assertThat(service.deliveryAttempt(KEY, RecordId.of("1-0"))).isEqualTo(3L);
    }

    @Test
    void deliveryAttemptFallsBackToOne()
    {
        PendingMessages messages = pendingMessages(0L);
        when(streamOperations.pending(eq(KEY), eq(GROUP), any(Range.class), eq(1L)))
                .thenReturn(messages);
        assertThat(service.deliveryAttempt(KEY, RecordId.of("1-0"))).isEqualTo(1L);

        when(streamOperations.pending(eq(KEY), eq(GROUP), any(Range.class), anyLong()))
                .thenThrow(new IllegalStateException("down"));
        assertThat(service.deliveryAttempt(KEY, RecordId.of("1-0"))).isEqualTo(1L);
    }

    @Test
    void parsePendingRowExtractsFields()
    {
        List<Object> row = Arrays.asList(
                "1-0".getBytes(StandardCharsets.UTF_8),
                "consumer-1".getBytes(StandardCharsets.UTF_8),
                " 120 ".getBytes(StandardCharsets.UTF_8),
                "3".getBytes(StandardCharsets.UTF_8));

        StreamQueueService.PendingInfo info = StreamQueueService.parsePendingRow(row);

        assertThat(info.id).isEqualTo("1-0");
        assertThat(info.consumer).isEqualTo("consumer-1");
        assertThat(info.idleMs).isEqualTo(120L);
        assertThat(info.deliveryCount).isEqualTo(3L);
    }

    @Test
    void parsePendingRowRejectsMalformedRows()
    {
        assertThat(StreamQueueService.parsePendingRow(null)).isNull();
        assertThat(StreamQueueService.parsePendingRow(Collections.emptyList())).isNull();
        assertThat(StreamQueueService.parsePendingRow(Arrays.asList("1-0".getBytes(), "c".getBytes(),
                "abc".getBytes(), "1".getBytes()))).isNull();
    }

    // ---------- 初始化与关闭 ----------

    @Test
    void initDisabledDoesNotCreatePool()
    {
        StreamQueueService disabled = new StreamQueueService();
        ReflectionTestUtils.setField(disabled, "enabled", false);

        disabled.init();

        assertThat(ReflectionTestUtils.getField(disabled, "listenerPool")).isNull();
        disabled.shutdown();
    }

    @Test
    void shutdownWithoutPoolIsNoop()
    {
        service.shutdown();
    }

    @Test
    void shutdownStopsListenerPool() throws Exception
    {
        ExecutorService pool = mock(ExecutorService.class);
        when(pool.awaitTermination(8L, TimeUnit.SECONDS)).thenReturn(true);
        ReflectionTestUtils.setField(service, "listenerPool", pool);

        service.shutdown();

        verify(pool).shutdown();
        verify(pool, never()).shutdownNow();
    }

    @SuppressWarnings("unchecked")
    private static MapRecord<String, Object, Object> record(String id, String payload)
    {
        MapRecord<String, Object, Object> record = mock(MapRecord.class);
        when(record.getId()).thenReturn(RecordId.of(id));
        Map<Object, Object> value = new HashMap<>();
        value.put(StreamQueueService.PAYLOAD_FIELD, payload);
        when(record.getValue()).thenReturn(value);
        return record;
    }

    @SuppressWarnings("unchecked")
    private static PendingMessages pendingMessages(long deliveryCount)
    {
        PendingMessage message = mock(PendingMessage.class);
        when(message.getId()).thenReturn(RecordId.of("1-0"));
        when(message.getTotalDeliveryCount()).thenReturn(deliveryCount);
        return new PendingMessages(GROUP, Collections.singletonList(message));
    }
}
