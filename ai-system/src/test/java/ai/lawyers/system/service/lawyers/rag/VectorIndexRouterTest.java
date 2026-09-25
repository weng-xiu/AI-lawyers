package ai.lawyers.system.service.lawyers.rag;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.util.ReflectionTestUtils;
import ai.lawyers.system.domain.lawyers.AiLegalKnowledgeChunk;

/**
 * P3-C5：VectorIndexRouter 路由与降级测试。
 *
 * <p>核心回归点：memory 模式不触 Redis；redis 模式读优先 Redis、异常当次降级内存；
 * 写侧双写且 Redis 失败不影响内存快照。</p>
 *
 * @author ai-lawyers
 */
class VectorIndexRouterTest
{
    private VectorIndexRouter router;
    private InMemoryVectorIndex memoryIndex;
    private RedisVectorIndex redisIndex;

    @BeforeEach
    void setUp()
    {
        router = new VectorIndexRouter();
        memoryIndex = mock(InMemoryVectorIndex.class);
        redisIndex = mock(RedisVectorIndex.class);
        ReflectionTestUtils.setField(router, "memoryIndex", memoryIndex);
        ReflectionTestUtils.setField(router, "redisIndex", redisIndex);
        ReflectionTestUtils.setField(router, "stringRedisTemplate", mock(StringRedisTemplate.class));
    }

    private void setMode(String mode)
    {
        ReflectionTestUtils.setField(router, "vectorStore", mode);
    }

    private AiLegalKnowledgeChunk chunk(long id)
    {
        AiLegalKnowledgeChunk c = new AiLegalKnowledgeChunk();
        c.setChunkId(id);
        c.setKnowledgeId(1L);
        c.setEmbeddingDim(4);
        c.setEmbedding(VectorUtils.toBytes(new float[]{1f, 0f, 0f, 0f}));
        return c;
    }

    // ------------------------------------------------------------------ 路由

    @Test
    void memoryMode_search_neverTouchesRedis()
    {
        setMode("memory");
        when(memoryIndex.searchNearest(any(), any(), anyInt()))
                .thenReturn(Collections.singletonList(new RagChunk(chunk(1L), 0.9d)));

        List<RagChunk> hits = router.searchNearest(new float[]{1f}, null, 5);

        assertThat(hits).hasSize(1);
        verify(redisIndex, never()).searchNearest(any(), any(), anyInt());
    }

    @Test
    void redisMode_search_prefersRedis()
    {
        setMode("redis");
        when(redisIndex.isReady()).thenReturn(true);
        when(redisIndex.searchNearest(any(), any(), anyInt()))
                .thenReturn(Collections.singletonList(new RagChunk(chunk(2L), 0.8d)));

        List<RagChunk> hits = router.searchNearest(new float[]{1f}, null, 5);

        assertThat(hits).hasSize(1);
        assertThat(hits.get(0).getChunkId()).isEqualTo(2L);
        verify(memoryIndex, never()).searchNearest(any(), any(), anyInt());
    }

    @Test
    void redisMode_redisThrows_fallsBackToMemory()
    {
        setMode("redis");
        when(redisIndex.isReady()).thenReturn(true);
        when(redisIndex.searchNearest(any(), any(), anyInt()))
                .thenThrow(new RuntimeException("ERR unknown command 'FT.SEARCH'"));
        when(memoryIndex.searchNearest(any(), any(), anyInt()))
                .thenReturn(Collections.singletonList(new RagChunk(chunk(3L), 0.7d)));

        List<RagChunk> hits = router.searchNearest(new float[]{1f}, null, 5);

        assertThat(hits).hasSize(1);
        assertThat(hits.get(0).getChunkId()).isEqualTo(3L);
    }

    @Test
    void redisMode_redisNotReady_usesMemory()
    {
        setMode("redis");
        when(redisIndex.isReady()).thenReturn(false);
        when(memoryIndex.searchNearest(any(), any(), anyInt()))
                .thenReturn(Collections.singletonList(new RagChunk(chunk(4L), 0.6d)));

        List<RagChunk> hits = router.searchNearest(new float[]{1f}, null, 5);

        assertThat(hits).hasSize(1);
        verify(redisIndex, never()).searchNearest(any(), any(), anyInt());
    }

    // ------------------------------------------------------------------ 写侧

    @Test
    void redisMode_rebuild_writesBothSides()
    {
        setMode("redis");
        List<AiLegalKnowledgeChunk> chunks = Collections.singletonList(chunk(1L));
        when(memoryIndex.rebuild(chunks)).thenReturn(1);
        when(redisIndex.rebuild(chunks)).thenReturn(1);

        int loaded = router.rebuild(chunks);

        assertThat(loaded).isEqualTo(1);
        verify(memoryIndex).rebuild(chunks);
        verify(redisIndex).rebuild(chunks);
    }

    @Test
    void redisMode_redisRebuildFails_memorySurvives()
    {
        setMode("redis");
        List<AiLegalKnowledgeChunk> chunks = Collections.singletonList(chunk(1L));
        when(memoryIndex.rebuild(chunks)).thenReturn(1);
        when(redisIndex.rebuild(chunks)).thenThrow(new RuntimeException("redis down"));

        int loaded = router.rebuild(chunks);

        // 降级返回内存载入数，不抛异常
        assertThat(loaded).isEqualTo(1);
    }

    @Test
    void memoryMode_rebuild_onlyMemory()
    {
        setMode("memory");
        List<AiLegalKnowledgeChunk> chunks = Collections.singletonList(chunk(1L));
        when(memoryIndex.rebuild(chunks)).thenReturn(1);

        int loaded = router.rebuild(chunks);

        assertThat(loaded).isEqualTo(1);
        verify(redisIndex, never()).rebuild(anyList());
    }

    // ------------------------------------------------------------------ 状态

    @Test
    void isReady_redisReady_true()
    {
        setMode("redis");
        when(redisIndex.isReady()).thenReturn(true);
        assertThat(router.isReady()).isTrue();
    }

    @Test
    void isReady_redisDown_memoryFallback()
    {
        setMode("redis");
        when(redisIndex.isReady()).thenReturn(false);
        when(memoryIndex.isReady()).thenReturn(true);
        assertThat(router.isReady()).isTrue();
    }
}
