package ai.lawyers.system.service.impl.lawyers.trunk;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.test.util.ReflectionTestUtils;

import ai.lawyers.system.domain.lawyers.trunk.DialRequest;

/**
 * N2：集群调度状态底座（Redis 版）单测。
 *
 * <p>覆盖：全局并发原子占位（成功/已满/Redis 故障 fail-closed）、释放 Lua 下界保护、
 * 看门狗漂移矫正（漂移/无漂移/负值兜底/Redis 异常）、CPS 原子 INCR+PEXPIRE、
 * 呼叫-线路映射与 SCAN、排队入队容量校验与出队反序列化。</p>
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
    void releaseGlobalConcurrent_callsSafeDecrScript()
    {
        state.releaseGlobalConcurrent();
        // Lua 下界保护脚本：仅计数>0才 DECR，不带 ARGV
        verify(redis, times(1)).execute(any(),
                eq(Collections.singletonList("call:dispatch:global-concurrent")));
        verify(valueOps, never()).decrement(anyString());
    }

    @Test
    void releaseGlobalConcurrent_redisFailure_swallowed()
    {
        org.mockito.Mockito.doThrow(new RuntimeException("down"))
                .when(redis).execute(any(), any(java.util.List.class));

        // 异常不外抛，靠线路 DB 并发与看门狗漂移矫正兜底
        state.releaseGlobalConcurrent();
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

    // ---------- 全局并发漂移矫正 ----------

    @Test
    void reconcile_drift_returnsReclaimedAndTargetsDbValue()
    {
        // DB 线路并发合计=30，余量=8 → ceiling=38；脚本返回回收 10 个槽位
        when(redis.execute(any(org.springframework.data.redis.core.script.RedisScript.class),
                any(java.util.List.class), any(), any())).thenReturn(10L);

        assertThat(state.reconcileGlobalConcurrent(30, 8)).isEqualTo(10L);
        // Lua 参数：ceiling=38、target=30（矫正到权威值而非上限）
        verify(redis, times(1)).execute(any(),
                eq(Collections.singletonList("call:dispatch:global-concurrent")),
                eq("38"), eq("30"));
    }

    @Test
    void reconcile_noDrift_returnsZero()
    {
        when(redis.execute(any(org.springframework.data.redis.core.script.RedisScript.class),
                any(java.util.List.class), any(), any())).thenReturn(0L);

        assertThat(state.reconcileGlobalConcurrent(50, 8)).isZero();
    }

    @Test
    void reconcile_negativeAuthoritative_floorsToZero()
    {
        // 权威值异常为负（不应发生）：ceiling=max(0,-5+8)=3（先加余量再兜底）、target=0，
        // 矫正只能向下到 0，方向安全
        when(redis.execute(any(org.springframework.data.redis.core.script.RedisScript.class),
                any(java.util.List.class), any(), any())).thenReturn(0L);

        assertThat(state.reconcileGlobalConcurrent(-5, 8)).isZero();
        verify(redis).execute(any(),
                eq(Collections.singletonList("call:dispatch:global-concurrent")),
                eq("3"), eq("0"));
    }

    @Test
    void reconcile_redisFailure_returnsMinusOne()
    {
        when(redis.execute(any(org.springframework.data.redis.core.script.RedisScript.class),
                any(java.util.List.class), any(), any()))
                .thenThrow(new RuntimeException("connection lost"));

        // -1 表示本轮未矫正，下个周期再试
        assertThat(state.reconcileGlobalConcurrent(30, 8)).isEqualTo(-1L);
    }

    // ---------- CPS 限速 ----------

    @Test
    void acquireCps_firstCount_returnsTrueViaAtomicScript()
    {
        // Lua 原子 INCR + 首次 PEXPIRE，返回 1
        when(redis.execute(any(), any(), any())).thenReturn(1L);

        assertThat(state.acquireCps(7L, 5)).isTrue();
        verify(redis, times(1)).execute(any(),
                eq(Collections.singletonList("call:dispatch:cps:7")), eq("1000"));
        // 不再有独立的 expire 往返（消除 INCR 成功/EXPIRE 丢失导致的永久卡限）
        verify(redis, never()).expire(anyString(),
                org.mockito.ArgumentMatchers.anyLong(),
                any(java.util.concurrent.TimeUnit.class));
    }

    @Test
    void acquireCps_underLimit_returnsTrue()
    {
        when(redis.execute(any(), any(), any())).thenReturn(3L);

        assertThat(state.acquireCps(7L, 5)).isTrue();
    }

    @Test
    void acquireCps_overLimit_returnsFalse()
    {
        when(redis.execute(any(), any(), any())).thenReturn(6L);

        assertThat(state.acquireCps(7L, 5)).isFalse();
    }

    @Test
    void acquireCps_zeroLimit_bypass()
    {
        assertThat(state.acquireCps(7L, 0)).isTrue();
        verify(redis, never()).execute(any(org.springframework.data.redis.core.script.RedisScript.class),
                any(java.util.List.class), any());
    }

    @Test
    void acquireCps_redisFailure_failOpen()
    {
        when(redis.execute(any(), any(), any())).thenThrow(new RuntimeException("down"));

        // fail-open：CPS 为软限速，Redis 故障不阻断呼叫
        assertThat(state.acquireCps(7L, 5)).isTrue();
    }

    // ---------- 呼叫-线路占用 ----------

    @Test
    void trunkHolder_holdGetRelease()
    {
        state.holdTrunk("uuid-1", 7L);
        verify(valueOps).set(eq("call:dispatch:trunk:uuid-1"), eq("7"),
                eq(3600L), eq(java.util.concurrent.TimeUnit.SECONDS));

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

    @Test
    void scanTrunkHolders_returnsUuids()
    {
        // RedisCallback 在 mock 中不会真实执行，直接桩回调返回值即可
        when(redis.execute(any(RedisCallback.class)))
                .thenReturn(Arrays.asList("uuid-a", "uuid-b"));

        List<String> uuids = state.scanTrunkHolders();

        assertThat(uuids).containsExactly("uuid-a", "uuid-b");
    }

    @Test
    void scanTrunkHolders_redisFailure_returnsEmptyList()
    {
        when(redis.execute(any(RedisCallback.class))).thenThrow(new RuntimeException("down"));

        assertThat(state.scanTrunkHolders()).isEmpty();
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
