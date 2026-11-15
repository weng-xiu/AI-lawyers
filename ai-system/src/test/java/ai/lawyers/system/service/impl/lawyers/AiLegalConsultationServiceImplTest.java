package ai.lawyers.system.service.impl.lawyers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.test.util.ReflectionTestUtils;
import ai.lawyers.system.domain.lawyers.AiLegalConsultation;
import ai.lawyers.system.domain.lawyers.AiLegalKnowledge;
import ai.lawyers.system.mapper.lawyers.AiLegalConsultationMapper;
import ai.lawyers.system.service.lawyers.IAiLegalKnowledgeService;
import ai.lawyers.system.service.lawyers.IAiModelConfigService;
import ai.lawyers.system.service.lawyers.stat.AiModelCallLogRecorder;

/**
 * 法律咨询服务单测。
 *
 * <p>覆盖咨询记录增删改查、AI 应答的知识检索与次数累加、模型空结果与异常的兜底话术，
 * 以及统计数据的空值处理与按日期统计的天数默认值。</p>
 *
 * @author ai-lawyers
 */
class AiLegalConsultationServiceImplTest
{
    private AiLegalConsultationServiceImpl service;

    @Mock private AiLegalConsultationMapper aiLegalConsultationMapper;
    @Mock private IAiLegalKnowledgeService aiLegalKnowledgeService;
    @Mock private IAiModelConfigService aiModelConfigService;

    @BeforeEach
    void setUp()
    {
        MockitoAnnotations.openMocks(this);
        service = new AiLegalConsultationServiceImpl();
        ReflectionTestUtils.setField(service, "aiLegalConsultationMapper", aiLegalConsultationMapper);
        ReflectionTestUtils.setField(service, "aiLegalKnowledgeService", aiLegalKnowledgeService);
        ReflectionTestUtils.setField(service, "aiModelConfigService", aiModelConfigService);
    }

    private static AiLegalKnowledge knowledge(Long id, String title, String content)
    {
        AiLegalKnowledge knowledge = new AiLegalKnowledge();
        knowledge.setKnowledgeId(id);
        knowledge.setTitle(title);
        knowledge.setContent(content);
        return knowledge;
    }

    @Test
    void selectByConsultationId_delegates()
    {
        AiLegalConsultation expected = new AiLegalConsultation();
        when(aiLegalConsultationMapper.selectAiLegalConsultationById(1L)).thenReturn(expected);

        assertSame(expected, service.selectAiLegalConsultationByConsultationId(1L));
    }

    @Test
    void selectList_delegates()
    {
        AiLegalConsultation query = new AiLegalConsultation();
        List<AiLegalConsultation> list = Collections.singletonList(new AiLegalConsultation());
        when(aiLegalConsultationMapper.selectAiLegalConsultationList(query)).thenReturn(list);

        assertSame(list, service.selectAiLegalConsultationList(query));
    }

    @Test
    void selectByUserId_delegates()
    {
        List<AiLegalConsultation> list = Collections.singletonList(new AiLegalConsultation());
        when(aiLegalConsultationMapper.selectAiLegalConsultationByUserId(1L)).thenReturn(list);

        assertSame(list, service.selectAiLegalConsultationByUserId(1L));
    }

    @Test
    void insert_fillsCreateTime()
    {
        AiLegalConsultation entity = new AiLegalConsultation();
        when(aiLegalConsultationMapper.insertAiLegalConsultation(entity)).thenReturn(1);

        assertEquals(1, service.insertAiLegalConsultation(entity));
        assertTrue(entity.getCreateTime() != null);
    }

    @Test
    void update_fillsUpdateTime()
    {
        AiLegalConsultation entity = new AiLegalConsultation();
        when(aiLegalConsultationMapper.updateAiLegalConsultation(entity)).thenReturn(1);

        assertEquals(1, service.updateAiLegalConsultation(entity));
        assertTrue(entity.getUpdateTime() != null);
    }

    @Test
    void deleteByIds_delegates()
    {
        Long[] ids = new Long[] { 1L, 2L };
        when(aiLegalConsultationMapper.deleteAiLegalConsultationByIds(ids)).thenReturn(2);

        assertEquals(2, service.deleteAiLegalConsultationByConsultationIds(ids));
    }

    @Test
    void deleteById_delegates()
    {
        when(aiLegalConsultationMapper.deleteAiLegalConsultationById(1L)).thenReturn(1);

        assertEquals(1, service.deleteAiLegalConsultationByConsultationId(1L));
    }

    @Test
    void generateLegalAnswer_withKnowledge_buildsContextAndIncreasesViews()
    {
        List<AiLegalKnowledge> knowledgeList = Arrays.asList(
            knowledge(1L, "劳动法", "内容1"),
            knowledge(2L, "劳动合同法", "内容2"),
            knowledge(3L, "司法解释", "内容3"),
            knowledge(4L, "不应被使用", "内容4"));
        when(aiLegalKnowledgeService.searchAiLegalKnowledge("拖欠工资")).thenReturn(knowledgeList);
        when(aiModelConfigService.callAiModel(eq("拖欠工资"), anyString(),
            eq(AiModelCallLogRecorder.SCENE_CONSULTATION))).thenReturn("AI回答");

        assertEquals("AI回答", service.generateLegalAnswer("拖欠工资", 1L));

        verify(aiLegalKnowledgeService, times(3)).increaseViewCount(anyLong());
        verify(aiLegalKnowledgeService, never()).increaseViewCount(4L);
    }

    @Test
    void generateLegalAnswer_emptyKnowledge_passesEmptyContext()
    {
        when(aiLegalKnowledgeService.searchAiLegalKnowledge("问题")).thenReturn(Collections.emptyList());
        when(aiModelConfigService.callAiModel(eq("问题"), eq(""),
            eq(AiModelCallLogRecorder.SCENE_CONSULTATION))).thenReturn("AI回答");

        assertEquals("AI回答", service.generateLegalAnswer("问题", null));
    }

    @Test
    void generateLegalAnswer_emptyModelAnswer_returnsFallback()
    {
        when(aiLegalKnowledgeService.searchAiLegalKnowledge("问题")).thenReturn(null);
        when(aiModelConfigService.callAiModel(anyString(), anyString(), anyString())).thenReturn("");

        assertEquals("很抱歉，我暂时无法回答您的问题。建议您咨询专业律师获取更准确的法律建议。",
            service.generateLegalAnswer("问题", null));
    }

    @Test
    void generateLegalAnswer_exception_returnsErrorText()
    {
        when(aiLegalKnowledgeService.searchAiLegalKnowledge(anyString()))
            .thenThrow(new IllegalStateException("模型超时"));

        String result = service.generateLegalAnswer("问题", null);

        assertTrue(result.startsWith("生成回答时发生错误：模型超时"));
    }

    @Test
    void getConsultationStatistics_nullCounts_usesZeros()
    {
        when(aiLegalConsultationMapper.countTotalConsultations()).thenReturn(null);
        when(aiLegalConsultationMapper.countTodayConsultations()).thenReturn(null);
        when(aiLegalConsultationMapper.countWeekConsultations()).thenReturn(null);
        when(aiLegalConsultationMapper.countMonthConsultations()).thenReturn(null);
        when(aiLegalConsultationMapper.countConsultationsByStatus(anyString())).thenReturn(null);
        when(aiLegalConsultationMapper.getAverageRating()).thenReturn(null);

        Map<String, Object> stats = service.getConsultationStatistics();

        assertEquals(0L, stats.get("totalCount"));
        assertEquals(0L, stats.get("todayCount"));
        assertEquals(0L, stats.get("weekCount"));
        assertEquals(0L, stats.get("monthCount"));
        assertEquals(0L, stats.get("completedCount"));
        assertEquals(0L, stats.get("processingCount"));
        assertEquals(0L, stats.get("failedCount"));
        assertEquals(0.0, stats.get("avgRating"));
    }

    @Test
    void getConsultationStatistics_returnsValues()
    {
        when(aiLegalConsultationMapper.countTotalConsultations()).thenReturn(10L);
        when(aiLegalConsultationMapper.countTodayConsultations()).thenReturn(1L);
        when(aiLegalConsultationMapper.countWeekConsultations()).thenReturn(3L);
        when(aiLegalConsultationMapper.countMonthConsultations()).thenReturn(8L);
        when(aiLegalConsultationMapper.countConsultationsByStatus("1")).thenReturn(5L);
        when(aiLegalConsultationMapper.countConsultationsByStatus("0")).thenReturn(3L);
        when(aiLegalConsultationMapper.countConsultationsByStatus("2")).thenReturn(2L);
        when(aiLegalConsultationMapper.getAverageRating()).thenReturn(4.5);

        Map<String, Object> stats = service.getConsultationStatistics();

        assertEquals(10L, stats.get("totalCount"));
        assertEquals(1L, stats.get("todayCount"));
        assertEquals(3L, stats.get("weekCount"));
        assertEquals(8L, stats.get("monthCount"));
        assertEquals(5L, stats.get("completedCount"));
        assertEquals(3L, stats.get("processingCount"));
        assertEquals(2L, stats.get("failedCount"));
        assertEquals(4.5, stats.get("avgRating"));
    }

    @Test
    void getConsultationByCategory_delegates()
    {
        List<Map<String, Object>> list = Collections.singletonList(Collections.emptyMap());
        when(aiLegalConsultationMapper.countConsultationsByCategory()).thenReturn(list);

        assertSame(list, service.getConsultationByCategory());
    }

    @Test
    void getConsultationByDate_defaultsToSevenDays()
    {
        List<Map<String, Object>> list = Collections.singletonList(Collections.emptyMap());
        when(aiLegalConsultationMapper.countConsultationsByDate(7)).thenReturn(list);

        assertSame(list, service.getConsultationByDate(null));
        assertSame(list, service.getConsultationByDate(0));
    }

    @Test
    void getConsultationByDate_passesThroughDays()
    {
        List<Map<String, Object>> list = Collections.singletonList(Collections.emptyMap());
        when(aiLegalConsultationMapper.countConsultationsByDate(30)).thenReturn(list);

        assertSame(list, service.getConsultationByDate(30));
    }

    @Test
    void getConsultationChannelStats_delegates()
    {
        Map<String, Object> stats = Collections.emptyMap();
        when(aiLegalConsultationMapper.selectConsultationChannelStats()).thenReturn(stats);

        assertSame(stats, service.getConsultationChannelStats());
    }
}
