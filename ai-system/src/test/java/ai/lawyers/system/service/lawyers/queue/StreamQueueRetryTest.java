package ai.lawyers.system.service.lawyers.queue;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Range;
import org.springframework.data.redis.connection.stream.Consumer;
import org.springframework.data.redis.connection.stream.PendingMessage;
import org.springframework.data.redis.connection.stream.PendingMessages;
import org.springframework.data.redis.connection.stream.RecordId;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.StreamOperations;
import org.springframework.test.util.ReflectionTestUtils;

import ai.lawyers.system.service.lawyers.queue.StreamQueueService.PendingInfo;

/**
 * N1：Stream 真实投递计数（XPENDING）、退避算法与 XPENDING 行解析测试。
 *
 * <p>核心回归点：旧实现 {@code deliveryAttempt} 恒返回 maxDeliveries，导致首败即转死信、
 * 退避重投完全失效；本测试钉住"按 XPENDING 真实次数判定"的新语义。</p>
 *
 * @author ai-lawyers
 */
@SuppressWarnings({"unchecked", "rawtypes"})
class StreamQueueRetryTest
{
    private static final String KEY = "stream:sms-send";
    private static final String GROUP = "ai-lawyers";

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
        ReflectionTestUtils.setField(service, "groupName", GROUP);
        ReflectionTestUtils.setField(service, "maxDeliveries", 5L);
        ReflectionTestUtils.setField(service, "retryEnabled", true);
    }

    // ---------- deliveryAttempt：真实投递计数 ----------

    @Test
    void deliveryAttempt_pendingFound_returnsRealCount()
    {
        // XPENDING 显示已投递 3 次 → 必须返回 3（旧实现会错误返回 5）
        PendingMessage pm = new PendingMessage(RecordId.of("10-0"),
                Consumer.from(GROUP, "consumer-1"), Duration.ofSeconds(2), 3L);
        when(ops.pending(eq(KEY), eq(GROUP), any(Range.class), eq(1L)))
                .thenReturn(new PendingMessages(GROUP, Collections.singletonList(pm)));

        assertThat(service.deliveryAttempt(KEY, RecordId.of("10-0"))).isEqualTo(3L);
    }

    @Test
    void deliveryAttempt_pendingEmpty_returnsOneConservatively()
    {
        // 查不到（消息恰被 ACK/接管）：保守按 1 次，继续重试而非误转死信
        when(ops.pending(eq(KEY), eq(GROUP), any(Range.class), eq(1L)))
                .thenReturn(new PendingMessages(GROUP, Collections.emptyList()));

        assertThat(service.deliveryAttempt(KEY, RecordId.of("11-0"))).isEqualTo(1L);
    }

    @Test
    void deliveryAttempt_queryThrows_returnsOneConservatively()
    {
        // Redis 异常时同样保守按 1 次，不因查询失败把消息打入死信
        when(ops.pending(eq(KEY), eq(GROUP), any(Range.class), eq(1L)))
                .thenThrow(new RuntimeException("MOVED cluster slot"));

        assertThat(service.deliveryAttempt(KEY, RecordId.of("12-0"))).isEqualTo(1L);
    }

    // ---------- backoffMs：退避序列 ----------

    @Test
    void backoff_growsLinearlyAndCapsAtEightSeconds()
    {
        assertThat(StreamQueueService.backoffMs(1)).isEqualTo(500L);
        assertThat(StreamQueueService.backoffMs(2)).isEqualTo(1000L);
        assertThat(StreamQueueService.backoffMs(4)).isEqualTo(2000L);
        // 封顶 8s
        assertThat(StreamQueueService.backoffMs(100)).isEqualTo(8000L);
        // 非法入参按首次投递处理
        assertThat(StreamQueueService.backoffMs(0)).isEqualTo(500L);
        assertThat(StreamQueueService.backoffMs(-3)).isEqualTo(500L);
    }

    // ---------- parsePendingRow：Lettuce 原始 XPENDING 行 ----------

    @Test
    void parsePendingRow_validRow_allFieldsParsed()
    {
        List<Object> row = Arrays.asList(
                bytes("10-0"), bytes("consumer-1"), bytes("65000"), bytes("3"));

        PendingInfo info = StreamQueueService.parsePendingRow(row);

        assertThat(info).isNotNull();
        assertThat(info.id).isEqualTo("10-0");
        assertThat(info.consumer).isEqualTo("consumer-1");
        assertThat(info.idleMs).isEqualTo(65000L);
        assertThat(info.deliveryCount).isEqualTo(3L);
    }

    @Test
    void parsePendingRow_tooFewFields_returnsNull()
    {
        List<Object> row = Arrays.asList(bytes("10-0"), bytes("consumer-1"));
        assertThat(StreamQueueService.parsePendingRow(row)).isNull();
    }

    @Test
    void parsePendingRow_nonListOrBadNumber_returnsNull()
    {
        assertThat(StreamQueueService.parsePendingRow("not-a-list")).isNull();
        List<Object> bad = Arrays.asList(
                bytes("10-0"), bytes("c"), bytes("not-a-number"), bytes("3"));
        assertThat(StreamQueueService.parsePendingRow(bad)).isNull();
    }

    private static byte[] bytes(String s)
    {
        return s.getBytes(StandardCharsets.UTF_8);
    }
}
