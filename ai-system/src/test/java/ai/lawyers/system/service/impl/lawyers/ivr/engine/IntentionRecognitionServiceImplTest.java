package ai.lawyers.system.service.impl.lawyers.ivr.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.test.util.ReflectionTestUtils;
import ai.lawyers.system.domain.lawyers.AiConsultationCategory;
import ai.lawyers.system.domain.lawyers.ivr.AiIvrIntention;
import ai.lawyers.system.domain.lawyers.ivr.AiIvrIntentionLog;
import ai.lawyers.system.domain.lawyers.ivr.IntentionMatchResult;
import ai.lawyers.system.service.lawyers.IAiConsultationCategoryService;
import ai.lawyers.system.service.lawyers.IAiModelConfigService;
import ai.lawyers.system.service.lawyers.ivr.IAiIvrIntentionLogService;
import ai.lawyers.system.service.lawyers.ivr.IAiIvrIntentionService;

/**
 * 意图识别 Service 单测。
 *
 * <p>覆盖空文本、正则优先与优先级排序、非法正则容错、AI 兜底（无模型/空响应/异常）、
 * 意图到咨询分类的映射评分，以及识别日志写入与失败隔离。</p>
 *
 * @author ai-lawyers
 */
class IntentionRecognitionServiceImplTest
{
    private IntentionRecognitionServiceImpl service;

    @Mock private IAiIvrIntentionService intentionService;
    @Mock private IAiIvrIntentionLogService intentionLogService;
    @Mock private IAiModelConfigService modelConfigService;
    @Mock private IAiConsultationCategoryService categoryService;

    @BeforeEach
    void setUp()
    {
        MockitoAnnotations.openMocks(this);
        service = new IntentionRecognitionServiceImpl();
        ReflectionTestUtils.setField(service, "intentionService", intentionService);
        ReflectionTestUtils.setField(service, "intentionLogService", intentionLogService);
        ReflectionTestUtils.setField(service, "modelConfigService", modelConfigService);
        ReflectionTestUtils.setField(service, "categoryService", categoryService);
    }

    @Test
    void recognize_emptyText_returnsNone()
    {
        IntentionMatchResult result = service.recognize("");

        assertFalse(result.isMatched());
        assertEquals("输入文本为空", result.getMessage());
    }

    @Test
    void recognize_highestPriorityRegexWins()
    {
        AiIvrIntention low = intention("LOW", "退款", 5);
        AiIvrIntention high = intention("HIGH", "退费", 10);
        when(intentionService.selectActiveIntentions()).thenReturn(Arrays.asList(low, high));

        IntentionMatchResult result = service.recognize("我要退款和退费");

        assertTrue(result.isMatched());
        assertEquals("HIGH", result.getIntentionCode());
        assertEquals(new BigDecimal("1.0000"), result.getConfidence());
        assertEquals("1", result.getMatchMethod());
    }

    @Test
    void recognize_invalidRegex_skipsThenFallsThrough()
    {
        AiIvrIntention broken = intention("BROKEN", "[", 10);
        when(intentionService.selectActiveIntentions()).thenReturn(Collections.singletonList(broken));

        IntentionMatchResult result = service.recognize("任意内容");

        assertFalse(result.isMatched());
        assertEquals("未匹配到任何意图", result.getMessage());
        verify(modelConfigService, never()).callAiModel(anyString(), anyString(), anyString());
    }

    @Test
    void recognize_noRegexHit_noModelConfigured_returnsNone()
    {
        AiIvrIntention intention = intention("CODE1", "退款", 1);
        when(intentionService.selectActiveIntentions()).thenReturn(Collections.singletonList(intention));
        when(modelConfigService.getDefaultAiModelConfig()).thenReturn(null);

        assertFalse(service.recognize("我要咨询").isMatched());
        verify(modelConfigService, never()).callAiModel(anyString(), anyString(), anyString());
    }

    @Test
    void recognize_modelFallback_matchesReturnedCode()
    {
        AiIvrIntention first = intention("CODE1", "退款", 1);
        AiIvrIntention second = intention("CODE2", "投诉", 2);
        when(intentionService.selectActiveIntentions()).thenReturn(Arrays.asList(first, second));
        when(modelConfigService.getDefaultAiModelConfig()).thenReturn(new ai.lawyers.system.domain.lawyers.AiModelConfig());
        when(modelConfigService.callAiModel(anyString(), anyString(), anyString())).thenReturn("CODE2");

        IntentionMatchResult result = service.recognize("服务态度不好");

        assertTrue(result.isMatched());
        assertEquals("CODE2", result.getIntentionCode());
        assertEquals(new BigDecimal("0.6000"), result.getConfidence());
        assertEquals("2", result.getMatchMethod());
    }

    @Test
    void recognize_modelReturnsUnknownOrEmpty_returnsNone()
    {
        AiIvrIntention intention = intention("CODE1", "退款", 1);
        when(intentionService.selectActiveIntentions()).thenReturn(Collections.singletonList(intention));
        when(modelConfigService.getDefaultAiModelConfig()).thenReturn(new ai.lawyers.system.domain.lawyers.AiModelConfig());
        when(modelConfigService.callAiModel(anyString(), anyString(), anyString())).thenReturn("UNKNOWN");
        assertFalse(service.recognize("随便问问").isMatched());

        when(modelConfigService.callAiModel(anyString(), anyString(), anyString())).thenReturn("");
        assertFalse(service.recognize("随便问问").isMatched());
    }

    @Test
    void recognize_modelThrows_returnsNone()
    {
        AiIvrIntention intention = intention("CODE1", "退款", 1);
        when(intentionService.selectActiveIntentions()).thenReturn(Collections.singletonList(intention));
        when(modelConfigService.getDefaultAiModelConfig())
                .thenThrow(new RuntimeException("config missing"));
        intention.setModelId(5L);
        when(modelConfigService.callAiModel(anyString(), anyString(), anyString()))
                .thenThrow(new RuntimeException("model down"));

        assertFalse(service.recognize("随便问问").isMatched());
    }

    @Test
    void recognize_intentionWithModelId_callsModelEvenWithoutDefaultConfig()
    {
        AiIvrIntention intention = intention("CODE1", "退款", 1);
        intention.setModelId(5L);
        when(intentionService.selectActiveIntentions()).thenReturn(Collections.singletonList(intention));
        when(modelConfigService.getDefaultAiModelConfig()).thenReturn(null);
        when(modelConfigService.callAiModel(anyString(), anyString(), anyString())).thenReturn("CODE1");

        assertTrue(service.recognize("我要退款").isMatched());
    }

    @Test
    void recognize_mapsExactCategoryOverPartial()
    {
        AiIvrIntention intention = intention("LABOR", "劳动争议", 1);
        intention.setIntentionName("劳动争议");
        AiConsultationCategory partial = category(9L, "劳动");
        AiConsultationCategory exact = category(10L, "劳动争议");
        when(intentionService.selectActiveIntentions()).thenReturn(Collections.singletonList(intention));
        when(categoryService.selectAiConsultationCategoryList(any()))
                .thenReturn(Arrays.asList(partial, exact));

        IntentionMatchResult result = service.recognize("劳动争议");

        assertEquals(10L, result.getCategoryId());
        assertEquals("劳动争议", result.getCategoryName());
    }

    @Test
    void recognize_categoryServiceFailure_keepsMatchWithoutCategory()
    {
        AiIvrIntention intention = intention("LABOR", "劳动争议", 1);
        intention.setIntentionName("劳动争议");
        when(intentionService.selectActiveIntentions()).thenReturn(Collections.singletonList(intention));
        when(categoryService.selectAiConsultationCategoryList(any()))
                .thenThrow(new RuntimeException("category down"));

        IntentionMatchResult result = service.recognize("劳动争议");

        assertTrue(result.isMatched());
        assertNull(result.getCategoryId());
    }

    @Test
    void recognize_withContext_writesTruncatedLog()
    {
        AiIvrIntention intention = intention("CODE1", "退", 1);
        when(intentionService.selectActiveIntentions()).thenReturn(Collections.singletonList(intention));
        StringBuilder text = new StringBuilder();
        for (int i = 0; i < 1200; i++)
        {
            text.append("退");
        }

        service.recognize(text.toString(), 7L, "session-1", 2L, 3L);

        ArgumentCaptor<AiIvrIntentionLog> captor = ArgumentCaptor.forClass(AiIvrIntentionLog.class);
        verify(intentionLogService).insertAiIvrIntentionLog(captor.capture());
        AiIvrIntentionLog log = captor.getValue();
        assertEquals(7L, log.getRecordId());
        assertEquals("session-1", log.getSessionId());
        assertEquals(2L, log.getFlowId());
        assertEquals(3L, log.getNodeId());
        assertEquals(1000, log.getInputText().length());
        assertEquals("CODE1", log.getMatchedIntention());
        assertEquals("CODE1名称", log.getMatchedIntentionName());
        assertEquals(new BigDecimal("1.0000"), log.getConfidence());
        assertEquals("1", log.getMatchMethod());
    }

    @Test
    void recognize_unmatched_writesMethodZeroLog()
    {
        IntentionMatchResult result = service.recognize("", 8L, null, null, null);

        assertFalse(result.isMatched());
        ArgumentCaptor<AiIvrIntentionLog> captor = ArgumentCaptor.forClass(AiIvrIntentionLog.class);
        verify(intentionLogService).insertAiIvrIntentionLog(captor.capture());
        assertEquals("0", captor.getValue().getMatchMethod());
        assertNull(captor.getValue().getMatchedIntention());
    }

    @Test
    void recognize_logInsertFailure_isIsolated()
    {
        AiIvrIntention intention = intention("CODE1", "退款", 1);
        when(intentionService.selectActiveIntentions()).thenReturn(Collections.singletonList(intention));
        doThrow(new RuntimeException("log down"))
                .when(intentionLogService).insertAiIvrIntentionLog(any());

        assertTrue(service.recognize("退款", 1L, null, null, null).isMatched());
    }

    private AiIvrIntention intention(String code, String regex, Integer priority)
    {
        AiIvrIntention intention = new AiIvrIntention();
        intention.setIntentionId(1L);
        intention.setIntentionCode(code);
        intention.setIntentionName(code + "名称");
        intention.setRegexPattern(regex);
        intention.setPriority(priority);
        intention.setStatus("0");
        return intention;
    }

    private AiConsultationCategory category(Long id, String name)
    {
        AiConsultationCategory category = new AiConsultationCategory();
        category.setCategoryId(id);
        category.setCategoryName(name);
        return category;
    }
}
