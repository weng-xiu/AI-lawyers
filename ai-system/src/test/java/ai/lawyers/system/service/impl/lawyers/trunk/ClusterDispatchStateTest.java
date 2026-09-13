package ai.lawyers.system.service.impl.lawyers.trunk;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.Collections;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.test.util.ReflectionTestUtils;

import ai.lawyers.system.domain.lawyers.trunk.DialRequest;

/**
 * N2：集群调度状态底座（Redis 版）单测。
 *
 * <p>覆盖：全局并发原子占位（成功/已满/Redis 故障 fail-closed）、CPS 限速计数与过期、
 * 呼叫-线路映射、排队入队容量校验与出队反序列化。</p>
 *
 * @author ai-lawyers
 */
@SuppressWarnings({"unchecked", "rawtypes"})
class ClusterDispatchStateTest
{
    private StringRedisTemplate redis;
    private ValueOperations<String, String> valueOps;
    private ZSetOperations<String, String> zSetOps;
    private ClusterDispatchState state;

    @BeforeEach
    void setUp()
    {
        redis = mock(StringRedisTemplate.class);
        valueOps = mock(ValueOperations.class);
        zSetOps = mock(ZSetOperations.class);
        when(redis.opsForValue()).thenReturn(valueOps);
        when(redis.opsForZSet()).thenReturn(zSetOps);

        state = new ClusterDispatchState();
        ReflectionTestUtils.setField(state, "stringRedisTemplate", redis);
        ReflectionTestUtils.setField(state, "clusterEnabled", true);
    }

    // ---------- 全局并发 ----------

    @Test
    void acquireGlobalConcurrent_underLimit_returnsIncremented()
    {
        when(redis.execute(any(), any(), any())).thenReturn(5L);

        assertThat(state.acquireGlobalConcurrent(200)).isEqualTo(5L);
    }

    @Test
    void acquireGlobalConcurrent_atLimit_returnsMinusOne()
    {
        when(redis.execute(any(), any(), any())).thenReturn(-1L);

        assertThat(state.acquireGlobalConcurrent(200)).isEqualTo(-1L);
    }

    @Test
    void acquireGlobalConcurrent_redisFailure_failClosed()
    {
        when(redis.execute(any(), any(), any())).thenThrow(new RuntimeException("connection lost"));

        // fail-closed：Redis 异常按已满处理，避免突破运营商并发限额
        assertThat(state.acquireGlobalConcurrent(200)).isEqualTo(-1L);
    }

    @Test
    void releaseGlobalConcurrent_callsDecr()
    {
        state.releaseGlobalConcurrent();
        verify(valueOps, times(1)).decrement("call:dispatch:global-concurrent");
    }

    @Test
    void getGlobalConcurrent_parsesValue()
    {
        when(valueOps.get("call:dispatch:global-concurrent")).thenReturn("42");
        assertThat(state.getGlobalConcurrent()).isEqualTo(42);

        when(valueOps.get("call:dispatch:global-concurrent")).thenReturn(null);
        assertThat(state.getGlobalConcurrent()).isEqualTo(0);
    }

    @Test
    void resetGlobalConcurrent_deletesKey()
    {
        state.resetGlobalConcurrent();
        verify(redis, times(1)).delete("call:dispatch:global-concurrent");
    }

    // ---------- CPS 限速 ----------

    @Test
    void acquireCps_firstCount_setsExpireAndReturnsTrue()
    {
        when(valueOps.increment("call:dispatch:cps:7")).thenReturn(1L);

        assertThat(state.acquireCps(7L, 5)).isTrue();
        verify(redis, times(1)).expire(eq("call:dispatch:cps:7"), eq(1L), eq(TimeUnit.SECONDS));
    }

    @Test
    void acquireCps_underLimit_returnsTrueWithoutExpire()
    {
        when(valueOps.increment("call:dispatch:cps:7")).thenReturn(3L);

        assertThat(state.acquireCps(7L, 5)).isTrue();
        // 非首次计数不应重复设置过期
        verify(redis, never()).expire(anyString(), anyLong(), any());
    }

    @Test
    void acquireCps_overLimit_returnsFalse()
    {
        when(valueOps.increment("call:dispatch:cps:7")).thenReturn(6L);

        assertThat(state.acquireCps(7L, 5)).isFalse();
    }

    @Test
    void acquireCps_zeroLimit_bypass()
    {
        assertThat(state.acquireCps(7L, 0)).isTrue();
        verify(valueOps, never()).increment(anyString());
    }

    @Test
    void acquireCps_redisFailure_failOpen()
    {
        when(valueOps.increment("call:dispatch:cps:7")).thenThrow(new RuntimeException("down"));

        // fail-open：CPS 为软限速，Redis 故障不阻断呼叫
        assertThat(state.acquireCps(7L, 5)).isTrue();
    }

    // ---------- 呼叫-线路占用 ----------

    @Test
    void trunkHolder_holdGetRelease()
    {
        state.holdTrunk("uuid-1", 7L);
        verify(valueOps).set(eq("call:dispatch:trunk:uuid-1"), eq("7"), eq(3600L), eq(TimeUnit.SECONDS));

        when(valueOps.get("call:dispatch:trunk:uuid-1")).thenReturn("7");
        assertThat(state.getTrunk("uuid-1")).isEqualTo(7L);

        state.releaseTrunk("uuid-1");
        verify(redis).delete("call:dispatch:trunk:uuid-1");
    }

    @Test
    void trunkHolder_getMiss_returnsNull()
    {
        when(valueOps.get("call:dispatch:trunk:missing")).thenReturn(null);
        assertThat(state.getTrunk("missing")).isNull();
    }

    // ---------- 排队队列 ----------

    @Test
    void enqueue_withinCapacity_returnsTrue()
    {
        when(redis.execute(any(), any(), any())).thenReturn(1L);

        DialRequest req = new DialRequest("13800138000", 1L);
        boolean ok = state.enqueue(req, 2000);

        assertThat(ok).isTrue();
        // 校验调用了入队 Lua 脚本（ZADD 原子容量校验）
        verify(redis, times(1)).execute(any(), any(), any());
    }

    @Test
    void enqueue_full_returnsFalse()
    {
        when(redis.execute(any(), any(), any())).thenReturn(0L);

        DialRequest req = new DialRequest("13800138000", 1L);
        assertThat(state.enqueue(req, 2000)).isFalse();
    }

    @Test
    void enqueue_redisFailure_returnsFalse()
    {
        when(redis.execute(any(), any(), any())).thenThrow(new RuntimeException("down"));

        DialRequest req = new DialRequest("13800138000", 1L);
        assertThat(state.enqueue(req, 2000)).isFalse();
    }

    @Test
    void pollQueue_nonEmpty_deserializesRequest() throws Exception
    {
        DialRequest req = new DialRequest("13800138000", 9L);
        req.setPriority(50);
        // 用真实 ObjectMapper 序列化一个 QueueItem 作为 Redis 返回的 member
        com.fasterxml.jackson.databind.ObjectMapper om = new com.fasterxml.jackson.databind.ObjectMapper();
        ClusterDispatchState.QueueItem item = new ClusterDispatchState.QueueItem();
        item.qid = "q-1";
        item.enqueueTime = 1700000000000L;
        item.request = req;
        String member = om.writeValueAsString(item);

        // Lua 返回 [member, score]
        when(redis.execute(any(), any(), any())).thenReturn(Arrays.asList(member, "501700000000000"));

        ClusterDispatchState.QueueItem got = state.pollQueue();

        assertThat(got).isNotNull();
        assertThat(got.qid).isEqualTo("q-1");
        assertThat(got.enqueueTime).isEqualTo(1700000000000L);
        assertThat(got.request.getCalleeNumber()).isEqualTo("13800138000");
        assertThat(got.request.getAgentId()).isEqualTo(9L);
        assertThat(got.request.getPriority()).isEqualTo(50);
    }

    @Test
    void pollQueue_empty_returnsNull()
    {
        when(redis.execute(any(), any(), any())).thenReturn(Collections.emptyList());
        assertThat(state.pollQueue()).isNull();
    }

    @Test
    void pollQueue_redisFailure_returnsNull()
    {
        when(redis.execute(any(), any(), any())).thenThrow(new RuntimeException("down"));
        assertThat(state.pollQueue()).isNull();
    }

    @Test
    void queueSize_returnsCard()
    {
        when(zSetOps.zCard("call:dispatch:queue")).thenReturn(7L);
        assertThat(state.queueSize()).isEqualTo(7L);
    }
}
