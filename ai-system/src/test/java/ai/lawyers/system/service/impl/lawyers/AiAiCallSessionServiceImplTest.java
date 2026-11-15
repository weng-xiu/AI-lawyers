package ai.lawyers.system.service.impl.lawyers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.test.util.ReflectionTestUtils;
import ai.lawyers.system.domain.lawyers.AiAiCallSession;
import ai.lawyers.system.domain.lawyers.AiLegalKnowledge;
import ai.lawyers.system.mapper.lawyers.AiAiCallSessionMapper;
import ai.lawyers.system.service.lawyers.IAiLegalKnowledgeService;

/**
 * AI 律师辅助会话服务单测。
 *
 * <p>覆盖辅助会话的增删改查、接听后启动会话、意图识别与法条推荐、知识库异常降级，
 * 以及小结结束时的状态流转。</p>
 *
 * @author ai-lawyers
 */
class AiAiCallSessionServiceImplTest
{
    private AiAiCallSessionServiceImpl service;

    @Mock private AiAiCallSessionMapper aiAiCallSessionMapper;
    @Mock private IAiLegalKnowledgeService aiLegalKnowledgeService;

    @BeforeEach
    void setUp()
    {
        MockitoAnnotations.openMocks(this);
        service = new AiAiCallSessionServiceImpl();
        ReflectionTestUtils.setField(service, "aiAiCallSessionMapper", aiAiCallSessionMapper);
        ReflectionTestUtils.setField(service, "aiLegalKnowledgeService", aiLegalKnowledgeService);
    }

    private static AiLegalKnowledge knowledge(String title)
    {
        AiLegalKnowledge knowledge = new AiLegalKnowledge();
        knowledge.setTitle(title);
        return knowledge;
    }

    @Test
    void selectAiAiCallSessionBySessionId_delegates()
    {
        AiAiCallSession expected = new AiAiCallSession();
        when(aiAiCallSessionMapper.selectAiAiCallSessionBySessionId(1L)).thenReturn(expected);

        assertSame(expected, service.selectAiAiCallSessionBySessionId(1L));
    }

    @Test
    void selectAiAiCallSessionByRecordId_delegates()
    {
        AiAiCallSession expected = new AiAiCallSession();
        when(aiAiCallSessionMapper.selectAiAiCallSessionByRecordId(1L)).thenReturn(expected);

        assertSame(expected, service.selectAiAiCallSessionByRecordId(1L));
    }

    @Test
    void selectAiAiCallSessionList_delegates()
    {
        AiAiCallSession query = new AiAiCallSession();
        List<AiAiCallSession> list = Collections.singletonList(new AiAiCallSession());
        when(aiAiCallSessionMapper.selectAiAiCallSessionList(query)).thenReturn(list);

        assertSame(list, service.selectAiAiCallSessionList(query));
    }

    @Test
    void insertAiAiCallSession_delegates()
    {
        AiAiCallSession session = new AiAiCallSession();
        when(aiAiCallSessionMapper.insertAiAiCallSession(session)).thenReturn(1);

        assertEquals(1, service.insertAiAiCallSession(session));
    }

    @Test
    void updateAiAiCallSession_delegates()
    {
        AiAiCallSession session = new AiAiCallSession();
        when(aiAiCallSessionMapper.updateAiAiCallSession(session)).thenReturn(1);

        assertEquals(1, service.updateAiAiCallSession(session));
    }

    @Test
    void deleteAiAiCallSessionBySessionId_delegates()
    {
        when(aiAiCallSessionMapper.deleteAiAiCallSessionBySessionId(1L)).thenReturn(1);

        assertEquals(1, service.deleteAiAiCallSessionBySessionId(1L));
    }

    @Test
    void deleteAiAiCallSessionBySessionIds_delegates()
    {
        Long[] ids = new Long[] { 1L, 2L };
        when(aiAiCallSessionMapper.deleteAiAiCallSessionBySessionIds(ids)).thenReturn(2);

        assertEquals(2, service.deleteAiAiCallSessionBySessionIds(ids));
    }

    @Test
    void startAssistSession_fillsInitialFields()
    {
        AiAiCallSession session = service.startAssistSession(1L, 2L, "138", "张三");

        assertEquals(1L, session.getRecordId());
        assertEquals(2L, session.getAgentId());
        assertEquals("138", session.getCallerPhone());
        assertEquals("张三", session.getCallerName());
        assertEquals("0", session.getSessionStatus());
        assertEquals("AI-Assist", session.getCreateBy());
        assertNotNull(session.getStartTime());
        verify(aiAiCallSessionMapper).insertAiAiCallSession(session);
    }

    @Test
    void analyzeAndRecommend_missingSession_returnsNull()
    {
        when(aiAiCallSessionMapper.selectAiAiCallSessionBySessionId(1L)).thenReturn(null);

        assertNull(service.analyzeAndRecommend(1L, "内容"));
        verify(aiAiCallSessionMapper, never()).updateAiAiCallSession(any());
    }

    @Test
    void analyzeAndRecommend_recognizesIntentAndRecommendsLaws()
    {
        AiAiCallSession session = new AiAiCallSession();
        when(aiAiCallSessionMapper.selectAiAiCallSessionBySessionId(1L)).thenReturn(session);
        when(aiLegalKnowledgeService.searchAiLegalKnowledge("劳动纠纷"))
            .thenReturn(Arrays.asList(knowledge("劳动法"), knowledge("劳动合同法")));

        AiAiCallSession result = service.analyzeAndRecommend(1L, "公司拖欠工资怎么办");

        assertEquals("劳动纠纷", result.getIntentCategory());
        assertEquals("2", result.getSessionStatus());
        assertTrue(result.getRecommendLaws().contains("劳动法"));
        assertTrue(result.getRecommendScripts().contains("劳动纠纷"));
        verify(aiAiCallSessionMapper, times(2)).updateAiAiCallSession(session);
    }

    @Test
    void analyzeAndRecommend_unknownIntent_usesGeneralAndEmptyContentFallback()
    {
        AiAiCallSession session = new AiAiCallSession();
        when(aiAiCallSessionMapper.selectAiAiCallSessionBySessionId(1L)).thenReturn(session);
        when(aiLegalKnowledgeService.searchAiLegalKnowledge("普通咨询"))
            .thenReturn(Collections.emptyList());

        AiAiCallSession result = service.analyzeAndRecommend(1L, "你好");

        assertEquals("普通咨询", result.getIntentCategory());
        assertEquals("[]", result.getRecommendLaws());
    }

    @Test
    void analyzeAndRecommend_knowledgeServiceThrows_degradesToEmptyLaws()
    {
        AiAiCallSession session = new AiAiCallSession();
        when(aiAiCallSessionMapper.selectAiAiCallSessionBySessionId(1L)).thenReturn(session);
        when(aiLegalKnowledgeService.searchAiLegalKnowledge(any()))
            .thenThrow(new IllegalStateException("kb down"));

        AiAiCallSession result = service.analyzeAndRecommend(1L, "离婚纠纷");

        assertEquals("婚姻家庭", result.getIntentCategory());
        assertEquals("[]", result.getRecommendLaws());
    }

    @Test
    void summarizeAndEnd_missingSession_returnsNull()
    {
        when(aiAiCallSessionMapper.selectAiAiCallSessionBySessionId(1L)).thenReturn(null);

        assertNull(service.summarizeAndEnd(1L, "内容"));
    }

    @Test
    void summarizeAndEnd_recommendedSession_buildsSummaryAndEnds()
    {
        AiAiCallSession session = new AiAiCallSession();
        session.setSessionStatus("2");
        session.setIntentCategory("劳动纠纷");
        when(aiAiCallSessionMapper.selectAiAiCallSessionBySessionId(1L)).thenReturn(session);

        AiAiCallSession result = service.summarizeAndEnd(1L, "公司拖欠工资");

        assertEquals("4", result.getSessionStatus());
        assertNotNull(result.getEndTime());
        assertTrue(result.getCallSummary().contains("劳动纠纷"));
        assertTrue(result.getCallSummary().contains("公司拖欠工资"));
        verify(aiAiCallSessionMapper, times(2)).updateAiAiCallSession(session);
    }

    @Test
    void summarizeAndEnd_initialSession_analyzesFirst()
    {
        AiAiCallSession initial = new AiAiCallSession();
        initial.setSessionStatus("0");
        AiAiCallSession analyzed = new AiAiCallSession();
        analyzed.setSessionStatus("2");
        analyzed.setIntentCategory("普通咨询");
        when(aiAiCallSessionMapper.selectAiAiCallSessionBySessionId(1L))
            .thenReturn(initial, analyzed);
        when(aiLegalKnowledgeService.searchAiLegalKnowledge("普通咨询"))
            .thenReturn(Collections.emptyList());

        AiAiCallSession result = service.summarizeAndEnd(1L, "");

        assertEquals("4", result.getSessionStatus());
        assertTrue(result.getCallSummary().contains("（无文本记录）"));
        // 分析两次更新 + 小结/结束两次更新
        verify(aiAiCallSessionMapper, times(4)).updateAiAiCallSession(any(AiAiCallSession.class));
    }
}
