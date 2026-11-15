package ai.lawyers.system.service.lawyers.rag.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import ai.lawyers.system.domain.lawyers.AiLegalKnowledge;
import ai.lawyers.system.domain.lawyers.AiLegalKnowledgeChunk;
import ai.lawyers.system.mapper.lawyers.AiLegalKnowledgeChunkMapper;
import ai.lawyers.system.mapper.lawyers.AiLegalKnowledgeMapper;
import ai.lawyers.system.service.lawyers.IAiModelConfigService;
import ai.lawyers.system.service.lawyers.rag.VectorIndex;
import ai.lawyers.system.service.lawyers.stat.AiModelCallLogRecorder;

/**
 * RAG 分块与向量索引服务单测：开关降级、启动加载、删除清理、全量/单条重建、
 * 中文分块与 embedding 批量回填。
 *
 * @author ai-lawyers
 */
class RagIndexServiceImplTest
{
    private RagIndexServiceImpl service;
    private AiLegalKnowledgeMapper knowledgeMapper;
    private AiLegalKnowledgeChunkMapper chunkMapper;
    private IAiModelConfigService modelConfigService;
    private VectorIndex vectorIndex;

    @BeforeEach
    void setUp()
    {
        service = new RagIndexServiceImpl();
        knowledgeMapper = mock(AiLegalKnowledgeMapper.class);
        chunkMapper = mock(AiLegalKnowledgeChunkMapper.class);
        modelConfigService = mock(IAiModelConfigService.class);
        vectorIndex = mock(VectorIndex.class);

        ReflectionTestUtils.setField(service, "knowledgeMapper", knowledgeMapper);
        ReflectionTestUtils.setField(service, "chunkMapper", chunkMapper);
        ReflectionTestUtils.setField(service, "modelConfigService", modelConfigService);
        ReflectionTestUtils.setField(service, "vectorIndex", vectorIndex);
        ReflectionTestUtils.setField(service, "ragEnabled", true);
        // 同步执行器：让异步提交的任务在断言前同步跑完
        ReflectionTestUtils.setField(service, "indexExecutor", new DirectExecutorService());
    }

    @Test
    void rebuildAllAsync_disabled_skips()
    {
        ReflectionTestUtils.setField(service, "ragEnabled", false);

        service.rebuildAllAsync();

        verify(knowledgeMapper, never()).selectIndexableKnowledge();
    }

    @Test
    void rebuildKnowledgeAsync_disabledOrNull_skips()
    {
        ReflectionTestUtils.setField(service, "ragEnabled", false);
        service.rebuildKnowledgeAsync(1L);
        ReflectionTestUtils.setField(service, "ragEnabled", true);
        service.rebuildKnowledgeAsync(null);

        verify(knowledgeMapper, never()).selectAiLegalKnowledgeById(any());
        verify(chunkMapper, never()).deleteByKnowledgeId(any());
    }

    @Test
    void onKnowledgeDeleted_nullOrEmpty_skips()
    {
        service.onKnowledgeDeleted(null);
        service.onKnowledgeDeleted(new Long[0]);

        verify(chunkMapper, never()).deleteByKnowledgeIds(any());
    }

    @Test
    void onKnowledgeDeleted_deletesChunksAndRefreshesIndex()
    {
        Long[] ids = new Long[] { 1L, 2L };
        List<AiLegalKnowledgeChunk> all = Collections.singletonList(new AiLegalKnowledgeChunk());
        when(chunkMapper.selectAllChunksForIndex()).thenReturn(all);
        when(vectorIndex.rebuild(all)).thenReturn(1);

        service.onKnowledgeDeleted(ids);

        verify(chunkMapper).deleteByKnowledgeIds(ids);
        verify(chunkMapper).selectAllChunksForIndex();
        verify(vectorIndex).rebuild(all);
    }

    @Test
    void onKnowledgeDeleted_exceptionSwallowed()
    {
        when(chunkMapper.deleteByKnowledgeIds(any())).thenThrow(new RuntimeException("表不存在"));

        service.onKnowledgeDeleted(new Long[] { 1L });

        verify(vectorIndex, never()).rebuild(any());
    }

    @Test
    void rebuildAllAsync_noKnowledge_refreshesEmptyIndex()
    {
        when(knowledgeMapper.selectIndexableKnowledge()).thenReturn(Collections.emptyList());

        service.rebuildAllAsync();

        verify(chunkMapper, never()).deleteAll();
        verify(vectorIndex).rebuild(Collections.emptyList());
    }

    @Test
    void rebuildAllAsync_buildsChunksAndEmbeds()
    {
        AiLegalKnowledge k = knowledge("短小的一段正文。");
        when(knowledgeMapper.selectIndexableKnowledge()).thenReturn(Collections.singletonList(k));
        when(modelConfigService.embedTexts(anyList(), eq(AiModelCallLogRecorder.SCENE_RAG_INDEX)))
                .thenAnswer(inv -> vectors(((List<?>) inv.getArgument(0)).size()));
        when(chunkMapper.selectAllChunksForIndex()).thenReturn(Collections.emptyList());

        service.rebuildAllAsync();

        verify(chunkMapper).deleteAll();
        verify(chunkMapper).insertChunk(any());
        verify(modelConfigService).embedTexts(argThat(l -> l.size() == 1),
                eq(AiModelCallLogRecorder.SCENE_RAG_INDEX));
        // 分块未落库回填 chunkId，故此处为 null
        verify(chunkMapper).updateChunkEmbedding(isNull(), any(byte[].class), eq(2));
        verify(vectorIndex).rebuild(Collections.emptyList());
    }

    @Test
    void rebuildAllAsync_failureSwallowed()
    {
        when(knowledgeMapper.selectIndexableKnowledge()).thenThrow(new RuntimeException("db down"));

        service.rebuildAllAsync();

        verify(vectorIndex, never()).rebuild(any());
    }

    @Test
    void rebuildKnowledge_deletedKnowledge_clearsOnly()
    {
        when(knowledgeMapper.selectAiLegalKnowledgeById(9L)).thenReturn(null);
        when(chunkMapper.selectAllChunksForIndex()).thenReturn(Collections.emptyList());

        service.rebuildKnowledgeAsync(9L);

        verify(chunkMapper).deleteByKnowledgeId(9L);
        verify(chunkMapper, never()).insertChunk(any());
        verify(vectorIndex).rebuild(Collections.emptyList());
    }

    @Test
    void rebuildKnowledge_indexableChunksAndEmbeds()
    {
        AiLegalKnowledge k = knowledge("第一段正文内容。");
        when(knowledgeMapper.selectAiLegalKnowledgeById(9L)).thenReturn(k);
        when(modelConfigService.embedTexts(anyList(), anyString()))
                .thenReturn(Collections.singletonList(new float[] { 1f, 0f }));
        when(chunkMapper.selectAllChunksForIndex()).thenReturn(Collections.emptyList());

        service.rebuildKnowledgeAsync(9L);

        verify(chunkMapper).deleteByKnowledgeId(9L);
        verify(chunkMapper).insertChunk(any());
        verify(chunkMapper).updateChunkEmbedding(isNull(), any(byte[].class), eq(2));
        verify(vectorIndex).rebuild(Collections.emptyList());
    }

    @Test
    void loadIndexOnStartup_loadsChunks()
    {
        List<AiLegalKnowledgeChunk> chunks = Collections.singletonList(new AiLegalKnowledgeChunk());
        when(chunkMapper.selectAllChunksForIndex()).thenReturn(chunks);
        when(vectorIndex.rebuild(chunks)).thenReturn(1);

        service.loadIndexOnStartup();

        verify(vectorIndex).rebuild(chunks);
    }

    @Test
    void loadIndexOnStartup_exceptionSwallowed()
    {
        when(chunkMapper.selectAllChunksForIndex()).thenThrow(new RuntimeException("表未建"));

        service.loadIndexOnStartup();

        verify(vectorIndex, never()).rebuild(any());
    }

    @Test
    void indexedVectorCount_delegates()
    {
        when(vectorIndex.size()).thenReturn(7);

        assertEquals(7, service.indexedVectorCount());
    }

    @Test
    void shutdown_nullExecutorSafe()
    {
        ReflectionTestUtils.setField(service, "indexExecutor", null);

        service.shutdown();
    }

    @Test
    void shutdown_shutsExecutorDown()
    {
        DirectExecutorService executor = new DirectExecutorService();
        ReflectionTestUtils.setField(service, "indexExecutor", executor);

        service.shutdown();

        assertTrue(executor.isShutdown());
    }

    @Test
    void chunkKnowledge_emptyContent_returnsEmpty()
    {
        AiLegalKnowledge k = knowledge(null);
        assertEquals(0, chunk(k).size());

        k.setContent("   ");
        assertEquals(0, chunk(k).size());
    }

    @Test
    void chunkKnowledge_singleChunk_prefixAndLawArticle()
    {
        AiLegalKnowledge k = knowledge("当事人应当依法履行义务。");
        k.setLawArticle("《民法典》第一百条");

        List<AiLegalKnowledgeChunk> chunks = chunk(k);

        assertEquals(1, chunks.size());
        AiLegalKnowledgeChunk c = chunks.get(0);
        assertEquals(Long.valueOf(9L), c.getKnowledgeId());
        assertEquals("测试法规", c.getTitle());
        assertEquals(Integer.valueOf(0), c.getChunkIndex());
        assertEquals("0", c.getStatus());
        assertEquals("rag-index", c.getCreateBy());
        assertEquals(Integer.valueOf(0), c.getEmbeddingDim());
        assertEquals(Long.valueOf(3L), c.getCategoryId());
        assertEquals("测试来源", c.getSource());
        assertTrue(c.getChunkContent().startsWith("测试法规。"));
        assertTrue(c.getChunkContent().contains("《民法典》第一百条"));
        assertTrue(c.getChunkContent().endsWith("当事人应当依法履行义务。"));
    }

    @Test
    void chunkKnowledge_noLawArticle_firstChunkHasNoArticle()
    {
        AiLegalKnowledge k = knowledge("当事人应当依法履行义务。");
        k.setLawArticle(null);

        List<AiLegalKnowledgeChunk> chunks = chunk(k);

        assertEquals(1, chunks.size());
        assertEquals("测试法规。当事人应当依法履行义务。", chunks.get(0).getChunkContent());
    }

    @Test
    void chunkKnowledge_longContent_splitsAndOnlyFirstHasLawArticle()
    {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 25; i++)
        {
            sb.append("第").append(i).append("条规定当事人依法享有权利并履行义务。");
        }
        AiLegalKnowledge k = knowledge(sb.toString());
        k.setLawArticle("《民法典》第一百条");
        assertTrue(sb.length() > 400);

        List<AiLegalKnowledgeChunk> chunks = chunk(k);

        assertTrue(chunks.size() >= 2);
        assertEquals(Integer.valueOf(0), chunks.get(0).getChunkIndex());
        assertEquals(Integer.valueOf(1), chunks.get(1).getChunkIndex());
        assertTrue(chunks.get(0).getChunkContent().contains("《民法典》第一百条"));
        assertFalse(chunks.get(1).getChunkContent().contains("《民法典》第一百条"));
        assertTrue(chunks.get(1).getChunkContent().startsWith("测试法规。"));
    }

    @Test
    void splitSentences_keepsPunctuationAndTail()
    {
        @SuppressWarnings("unchecked")
        List<String> sentences = (List<String>) ReflectionTestUtils.invokeMethod(service, "splitSentences",
                "甲。乙！丙？丁；戊!己?庚;辛\n壬");

        assertNotNull(sentences);
        assertEquals(9, sentences.size());
        assertEquals("甲。", sentences.get(0));
        assertEquals("壬", sentences.get(8));

        @SuppressWarnings("unchecked")
        List<String> tailOnly = (List<String>) ReflectionTestUtils.invokeMethod(service, "splitSentences",
                "无标点结尾");
        assertEquals(Collections.singletonList("无标点结尾"), tailOnly);
    }

    @Test
    void embedChunks_batchesBy16()
    {
        List<AiLegalKnowledgeChunk> chunks = new ArrayList<>();
        for (int i = 0; i < 17; i++)
        {
            AiLegalKnowledgeChunk c = new AiLegalKnowledgeChunk();
            c.setChunkId((long) i + 1);
            c.setChunkContent("分块正文" + i);
            chunks.add(c);
        }
        when(modelConfigService.embedTexts(anyList(), eq(AiModelCallLogRecorder.SCENE_RAG_INDEX)))
                .thenAnswer(inv -> vectors(((List<?>) inv.getArgument(0)).size()));

        ReflectionTestUtils.invokeMethod(service, "embedChunks", chunks);

        verify(modelConfigService, times(2)).embedTexts(anyList(),
                eq(AiModelCallLogRecorder.SCENE_RAG_INDEX));
        verify(modelConfigService).embedTexts(argThat(l -> l.size() == 16),
                eq(AiModelCallLogRecorder.SCENE_RAG_INDEX));
        verify(modelConfigService).embedTexts(argThat(l -> l.size() == 1),
                eq(AiModelCallLogRecorder.SCENE_RAG_INDEX));
        verify(chunkMapper, times(17)).updateChunkEmbedding(any(), any(byte[].class), eq(2));
        assertEquals(Integer.valueOf(2), chunks.get(0).getEmbeddingDim());
        assertEquals(8, chunks.get(0).getEmbedding().length);
    }

    @Test
    void embedChunks_failureSwallowed()
    {
        List<AiLegalKnowledgeChunk> chunks = Collections.singletonList(new AiLegalKnowledgeChunk());
        chunks.get(0).setChunkId(1L);
        chunks.get(0).setChunkContent("正文");
        when(modelConfigService.embedTexts(anyList(), anyString()))
                .thenThrow(new RuntimeException("模型不可用"));

        ReflectionTestUtils.invokeMethod(service, "embedChunks", chunks);

        verify(chunkMapper, never()).updateChunkEmbedding(any(), any(), org.mockito.ArgumentMatchers.anyInt());
    }

    @Test
    void embedChunks_emptyNoop()
    {
        ReflectionTestUtils.invokeMethod(service, "embedChunks", (Object) null);
        ReflectionTestUtils.invokeMethod(service, "embedChunks", Collections.emptyList());

        verify(modelConfigService, never()).embedTexts(anyList(), anyString());
    }

    @SuppressWarnings("unchecked")
    private List<AiLegalKnowledgeChunk> chunk(AiLegalKnowledge k)
    {
        return (List<AiLegalKnowledgeChunk>) ReflectionTestUtils.invokeMethod(service, "chunkKnowledge", k);
    }

    private AiLegalKnowledge knowledge(String content)
    {
        AiLegalKnowledge k = new AiLegalKnowledge();
        k.setKnowledgeId(9L);
        k.setTitle("测试法规");
        k.setContent(content);
        k.setCategoryId(3L);
        k.setLawArticle("《民法典》第一百条");
        k.setSource("测试来源");
        k.setStatus("0");
        k.setAuditStatus("1");
        return k;
    }

    private List<float[]> vectors(int size)
    {
        List<float[]> result = new ArrayList<>(size);
        for (int i = 0; i < size; i++)
        {
            result.add(new float[] { 1f, 0f });
        }
        return result;
    }

    /** 同步执行器：submit 即当前线程执行，保证断言时序确定 */
    private static class DirectExecutorService extends AbstractExecutorService
    {
        private volatile boolean shutdown;

        @Override
        public void shutdown()
        {
            shutdown = true;
        }

        @Override
        public List<Runnable> shutdownNow()
        {
            shutdown = true;
            return Collections.emptyList();
        }

        @Override
        public boolean isShutdown()
        {
            return shutdown;
        }

        @Override
        public boolean isTerminated()
        {
            return shutdown;
        }

        @Override
        public boolean awaitTermination(long timeout, TimeUnit unit)
        {
            return true;
        }

        @Override
        public void execute(Runnable command)
        {
            command.run();
        }
    }
}
