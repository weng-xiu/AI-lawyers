package ai.lawyers.system.service.lawyers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.test.util.ReflectionTestUtils;
import ai.lawyers.system.domain.lawyers.AiLegalKnowledge;
import ai.lawyers.system.mapper.lawyers.AiLegalKnowledgeMapper;
import ai.lawyers.system.service.lawyers.rag.KnowledgeQueryTextUtils;

/**
 * F8 未命中问句沉淀单测：来源哈希去重、空问句拒绝、待审草稿字段。
 *
 * @author ai-lawyers
 */
class KnowledgeDraftServiceQueryTest
{
    @Mock
    private AiLegalKnowledgeMapper knowledgeMapper;

    @Mock
    private IAiModelConfigService modelConfigService;

    private KnowledgeDraftService service;

    @BeforeEach
    void setUp()
    {
        MockitoAnnotations.openMocks(this);
        service = new KnowledgeDraftService();
        ReflectionTestUtils.setField(service, "knowledgeMapper", knowledgeMapper);
        ReflectionTestUtils.setField(service, "modelConfigService", modelConfigService);
    }

    @Test
    void blankQuestionIsRejected()
    {
        assertThrows(IllegalArgumentException.class, () -> service.draftFromQuery("  ", null));
        verify(knowledgeMapper, never()).insertAiLegalKnowledge(any());
    }

    @Test
    void duplicatedQueryHashIsRejectedBeforeModelCall()
    {
        String question = "劳动合同没有签书面合同怎么办";
        String hash = KnowledgeQueryTextUtils.hash(question);
        when(knowledgeMapper.countBySourceUrl("source://query/" + hash)).thenReturn(1);

        assertThrows(IllegalArgumentException.class, () -> service.draftFromQuery(question, hash));
        verify(modelConfigService, never()).chatJson(anyString(), anyString(), anyString());
    }

    @Test
    void generatesPendingDraftWithQuerySourceKey()
    {
        String question = "劳动合同没有签书面合同怎么办";
        String hash = KnowledgeQueryTextUtils.hash(question);
        when(knowledgeMapper.countBySourceUrl("source://query/" + hash)).thenReturn(0);
        when(modelConfigService.chatJson(anyString(), anyString(), anyString()))
                .thenReturn("{\"title\":\"未签书面劳动合同\",\"content\":\"用人单位应自用工之日起一个月内订立书面劳动合同。\","
                        + "\"keywords\":\"劳动合同,书面形式\",\"lawArticle\":\"劳动合同法第十条\"}");
        doAnswer(invocation ->
        {
            AiLegalKnowledge knowledge = invocation.getArgument(0);
            knowledge.setKnowledgeId(88L);
            return 1;
        }).when(knowledgeMapper).insertAiLegalKnowledge(any());

        Long knowledgeId = service.draftFromQuery(question, hash);

        assertEquals(88L, knowledgeId);
        ArgumentCaptor<AiLegalKnowledge> captor = ArgumentCaptor.forClass(AiLegalKnowledge.class);
        verify(knowledgeMapper).insertAiLegalKnowledge(captor.capture());
        AiLegalKnowledge saved = captor.getValue();
        assertEquals("source://query/" + hash, saved.getSourceUrl());
        assertEquals("AI沉淀-未命中问句", saved.getSource());
        assertEquals("0", saved.getAuditStatus());
        assertEquals("0", saved.getStatus());
        assertEquals("ai-draft", saved.getCreateBy());
    }
}
