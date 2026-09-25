package ai.lawyers.system.service.lawyers.queue;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Range;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.RecordId;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.StreamOperations;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * P3-H1：死信列表解析、重投与删除测试。
 *
 * <p>核心回归点：重投必须把信封里的 payload 回投到 originStream（而非死信 Stream）、
 * 成功后清理死信记录；信封缺 originStream/payload 时拒绝重投且不误删。</p>
 *
 * @author ai-lawyers
 */
@SuppressWarnings({"unchecked", "rawtypes"})
class StreamDeadLetterTest
{
    private static final String QUEUE = "sms-send";
    private static final String DEAD_KEY = "stream:sms-send:dead";
    private static final String ORIGIN_KEY = "stream:sms-send";

    private StringRedisTemplate redis;
    private StreamOperations<String, Object, Object> ops;
    private StreamQueueService service;

    @BeforeEach
    void setUp()
    {
        redis = mock(StringRedisTemplate.class);
        ops = mock(StreamOperations.class);
        when(redis.opsForStream()).thenReturn(ops);
        service = new StreamQueueService();
        ReflectionTestUtils.setField(service, "stringRedisTemplate", redis);
    }

    private MapRecord<String, Object, Object> deadRecord(String id, String json)
    {
        Map<Object, Object> body = new java.util.HashMap<>();
        body.put(StreamQueueService.PAYLOAD_FIELD, json);
        return MapRecord.create(DEAD_KEY, body).withId(RecordId.of(id));
    }

    // ---------- deadLetterList ----------

    @Test
    void deadLetterList_parsesEnvelope()
    {
        String json = "{\"originStream\":\"" + ORIGIN_KEY + "\",\"originId\":\"9-0\","
                + "\"error\":\"boom\",\"payload\":{\"callee\":\"138****0001\"}}";
        when(ops.reverseRange(eq(DEAD_KEY), any(Range.class), any()))
                .thenReturn(Collections.singletonList(deadRecord("10-0", json)));

        List<Map<String, Object>> rows = service.deadLetterList(QUEUE, 0, 10);

        assertThat(rows).hasSize(1);
        Map<String, Object> row = rows.get(0);
        assertThat(row.get("id")).isEqualTo("10-0");
        assertThat(row.get("originStream")).isEqualTo(ORIGIN_KEY);
        assertThat(row.get("originId")).isEqualTo("9-0");
        assertThat(row.get("error")).isEqualTo("boom");
        assertThat(String.valueOf(row.get("payload"))).contains("138****0001");
    }

    @Test
    void deadLetterList_badJson_fallsBackToRaw()
    {
        when(ops.reverseRange(eq(DEAD_KEY), any(Range.class), any()))
                .thenReturn(Collections.singletonList(deadRecord("11-0", "not-json")));

        List<Map<String, Object>> rows = service.deadLetterList(QUEUE, 0, 10);

        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).get("raw")).isEqualTo("not-json");
    }

    // ---------- replayDeadLetter ----------

    @Test
    void replay_reEnqueuesToOriginStreamAndDeletesDead()
    {
        String json = "{\"originStream\":\"" + ORIGIN_KEY + "\",\"originId\":\"9-0\","
                + "\"error\":\"boom\",\"payload\":{\"callee\":\"138****0001\"}}";
        when(ops.range(eq(DEAD_KEY), any(Range.class)))
                .thenReturn(Collections.singletonList(deadRecord("10-0", json)));

        boolean ok = service.replayDeadLetter(QUEUE, "10-0");

        assertThat(ok).isTrue();
        // 回投到原始 Stream（而非死信 Stream）
        org.mockito.ArgumentCaptor<MapRecord> captor = org.mockito.ArgumentCaptor.forClass(MapRecord.class);
        verify(ops).add(captor.capture());
        assertThat(captor.getValue().getStream()).isEqualTo(ORIGIN_KEY);
        Map<Object, Object> body = (Map<Object, Object>) captor.getValue().getValue();
        assertThat(String.valueOf(body.get(StreamQueueService.PAYLOAD_FIELD)))
                .contains("138****0001");
        // 删除死信记录
        verify(ops).delete(DEAD_KEY, "10-0");
    }

    @Test
    void replay_recordMissing_returnsFalse()
    {
        when(ops.range(eq(DEAD_KEY), any(Range.class))).thenReturn(Collections.emptyList());

        assertThat(service.replayDeadLetter(QUEUE, "99-0")).isFalse();
        verify(ops, never()).add(any(MapRecord.class));
    }

    @Test
    void replay_envelopeIncomplete_returnsFalseAndKeepsDead()
    {
        // 缺 payload：拒绝重投，且不能删除死信（防止消息丢失）
        String json = "{\"originStream\":\"" + ORIGIN_KEY + "\",\"originId\":\"9-0\",\"error\":\"boom\"}";
        when(ops.range(eq(DEAD_KEY), any(Range.class)))
                .thenReturn(Collections.singletonList(deadRecord("10-0", json)));

        assertThat(service.replayDeadLetter(QUEUE, "10-0")).isFalse();
        verify(ops, never()).add(any(MapRecord.class));
        verify(ops, never()).delete(eq(DEAD_KEY), any(String.class));
    }

    // ---------- deleteDeadLetter ----------

    @Test
    void delete_removed_returnsTrue()
    {
        when(ops.delete(DEAD_KEY, "10-0")).thenReturn(1L);
        assertThat(service.deleteDeadLetter(QUEUE, "10-0")).isTrue();
    }

    @Test
    void delete_notFound_returnsFalse()
    {
        when(ops.delete(DEAD_KEY, "10-0")).thenReturn(0L);
        assertThat(service.deleteDeadLetter(QUEUE, "10-0")).isFalse();
    }
}
