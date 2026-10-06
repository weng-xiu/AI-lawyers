package ai.lawyers.system.service.lawyers.rag;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import ai.lawyers.system.domain.lawyers.AiLegalKnowledge;
import ai.lawyers.system.mapper.lawyers.AiLegalKnowledgeChunkMapper;
import ai.lawyers.system.mapper.lawyers.AiLegalKnowledgeMapper;
import ai.lawyers.system.service.lawyers.IAiModelConfigService;
import ai.lawyers.system.service.lawyers.rag.impl.RagIndexServiceImpl;

/**
 * P1-10：RAG 单条增量重建的 valid_status 过滤回归——
 * 法规已修订/已失效（valid_status=2/3）时仅清理旧分块，绝不重新切分入库，
 * 防止失效法规被重新索引污染检索。
 *
 * @author ai-lawyers
 */
class RagIndexValidStatusTest
{
    private static final Long KID = 9L;

    private RagIndexServiceImpl service;
    private AiLegalKnowledgeMapper knowledgeMapper;
    private AiLegalKnowledgeChunkMapper chunkMapper;
    private VectorIndex vectorIndex;

    @BeforeEach
    void setUp()
    {
        service = new RagIndexServiceImpl();
        knowledgeMapper = mock(AiLegalKnowledgeMapper.class);
        chunkMapper = mock(AiLegalKnowledgeChunkMapper.class);
        vectorIndex = mock(VectorIndex.class);
        IAiModelConfigService modelConfigService = mock(IAiModelConfigService.class);

        ReflectionTestUtils.setField(service, "knowledgeMapper", knowledgeMapper);
        ReflectionTestUtils.setField(service, "chunkMapper", chunkMapper);
        ReflectionTestUtils.setField(service, "vectorIndex", vectorIndex);
        ReflectionTestUtils.setField(service, "modelConfigService", modelConfigService);
        ReflectionTestUtils.setField(service, "ragEnabled", true);

        when(chunkMapper.selectAllChunksForIndex()).thenReturn(Collections.emptyList());
        when(vectorIndex.rebuild(any())).thenReturn(0);
    }

    private void invokeInternal() throws Exception
    {
        java.lang.reflect.Method m = RagIndexServiceImpl.class.getDeclaredMethod(
                "rebuildKnowledgeInternal", Long.class);
        m.setAccessible(true);
        m.invoke(service, KID);
    }

    private AiLegalKnowledge indexableKnowledge(String validStatus)
    {
        AiLegalKnowledge k = new AiLegalKnowledge();
        k.setKnowledgeId(KID);
        k.setTitle("测试法规");
        k.setContent("这是一段足够长的法规正文内容，用于产生分块。第一条规定了当事人的权利与义务，应当依法履行。");
        k.setStatus("0");
        k.setAuditStatus("1");
        k.setValidStatus(validStatus);
        return k;
    }

    @Test
    void rebuild_expiredLaw_onlyClearsChunksNoInsert() throws Exception
    {
        when(knowledgeMapper.selectAiLegalKnowledgeById(KID))
                .thenReturn(indexableKnowledge("3"));

        invokeInternal();

        // 旧分块被清理、索引刷新，但不会重新切分入库
        verify(chunkMapper).deleteByKnowledgeId(KID);
        verify(chunkMapper, never()).insertChunk(any());
    }

    @Test
    void rebuild_revisedLaw_onlyClearsChunksNoInsert() throws Exception
    {
        when(knowledgeMapper.selectAiLegalKnowledgeById(KID))
                .thenReturn(indexableKnowledge("2"));

        invokeInternal();

        verify(chunkMapper).deleteByKnowledgeId(KID);
        verify(chunkMapper, never()).insertChunk(any());
    }

    @Test
    void rebuild_validLaw_rechunksAndInserts() throws Exception
    {
        when(knowledgeMapper.selectAiLegalKnowledgeById(KID))
                .thenReturn(indexableKnowledge("1"));

        invokeInternal();

        // 现行有效：重新切分并写入分块
        verify(chunkMapper).deleteByKnowledgeId(KID);
        verify(chunkMapper, org.mockito.Mockito.atLeastOnce()).insertChunk(any());
    }
}
