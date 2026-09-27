package ai.lawyers.system.service.lawyers.voice.copilot;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.test.util.ReflectionTestUtils;
import ai.lawyers.system.domain.lawyers.AiCopilotFeedback;
import ai.lawyers.system.mapper.lawyers.AiCopilotFeedbackMapper;

/**
 * F4：{@link AiCopilotFeedbackServiceImpl} 单元测试。
 *
 * <p>覆盖枚举校验、服务端补坐席（无登录上下文保留原值）、纯数字 sessionId 解析、
 * mapper 异常吞掉、采纳率计算与按坐席映射。</p>
 *
 * @author ai-lawyers
 */
class AiCopilotFeedbackServiceImplTest
{
    private AiCopilotFeedbackMapper mapper;

    private AiCopilotFeedbackServiceImpl service;

    @BeforeEach
    void setUp()
    {
        mapper = Mockito.mock(AiCopilotFeedbackMapper.class);
        service = new AiCopilotFeedbackServiceImpl();
        ReflectionTestUtils.setField(service, "feedbackMapper", mapper);
    }

    private AiCopilotFeedback feedback(String type, String action)
    {
        AiCopilotFeedback fb = new AiCopilotFeedback();
        fb.setRecordId(1000L);
        fb.setSuggestionType(type);
        fb.setSuggestionRef("element");
        fb.setAction(action);
        return fb;
    }

    // ---------- record：校验 ----------

    @Test
    void record_null_noop()
    {
        service.record(null);
        verifyNoInteractions(mapper);
    }

    @Test
    void record_invalidType_dropped()
    {
        service.record(feedback("WEATHER", AiCopilotFeedback.ACTION_ADOPT));
        verify(mapper, never()).insertFeedback(any(AiCopilotFeedback.class));
    }

    @Test
    void record_invalidAction_dropped()
    {
        service.record(feedback(AiCopilotFeedback.TYPE_LAW, "DELETE"));
        verify(mapper, never()).insertFeedback(any(AiCopilotFeedback.class));
    }

    @Test
    void record_valid_insertWithCreateTime()
    {
        service.record(feedback(AiCopilotFeedback.TYPE_ELEMENT,
                AiCopilotFeedback.ACTION_ADOPT));

        ArgumentCaptor<AiCopilotFeedback> cap =
                ArgumentCaptor.forClass(AiCopilotFeedback.class);
        verify(mapper).insertFeedback(cap.capture());
        AiCopilotFeedback saved = cap.getValue();
        assertThat(saved.getSuggestionType()).isEqualTo("ELEMENT");
        assertThat(saved.getAction()).isEqualTo("ADOPT");
        assertThat(saved.getCreateTime()).isNotNull();
    }

    @Test
    void record_allThreeTypeActionCombinationsAccepted()
    {
        service.record(feedback(AiCopilotFeedback.TYPE_LAW, AiCopilotFeedback.ACTION_MODIFY));
        service.record(feedback(AiCopilotFeedback.TYPE_TICKET, AiCopilotFeedback.ACTION_IGNORE));
        verify(mapper, Mockito.times(2)).insertFeedback(any(AiCopilotFeedback.class));
    }

    // ---------- record：recordId 兜底 ----------

    @Test
    void record_recordIdNullNumericSessionId_parsed()
    {
        AiCopilotFeedback fb = feedback(AiCopilotFeedback.TYPE_LAW,
                AiCopilotFeedback.ACTION_ADOPT);
        fb.setRecordId(null);
        fb.setSessionId("2000");

        service.record(fb);

        ArgumentCaptor<AiCopilotFeedback> cap =
                ArgumentCaptor.forClass(AiCopilotFeedback.class);
        verify(mapper).insertFeedback(cap.capture());
        assertThat(cap.getValue().getRecordId()).isEqualTo(2000L);
    }

    @Test
    void record_nonNumericSessionId_recordIdStaysNull()
    {
        AiCopilotFeedback fb = feedback(AiCopilotFeedback.TYPE_LAW,
                AiCopilotFeedback.ACTION_ADOPT);
        fb.setRecordId(null);
        fb.setSessionId("sess-uuid-1");

        service.record(fb);

        ArgumentCaptor<AiCopilotFeedback> cap =
                ArgumentCaptor.forClass(AiCopilotFeedback.class);
        verify(mapper).insertFeedback(cap.capture());
        assertThat(cap.getValue().getRecordId()).isNull();
    }

    @Test
    void record_existingRecordId_notOverriddenBySessionId()
    {
        AiCopilotFeedback fb = feedback(AiCopilotFeedback.TYPE_LAW,
                AiCopilotFeedback.ACTION_ADOPT);
        fb.setRecordId(5L);
        fb.setSessionId("2000");

        service.record(fb);

        ArgumentCaptor<AiCopilotFeedback> cap =
                ArgumentCaptor.forClass(AiCopilotFeedback.class);
        verify(mapper).insertFeedback(cap.capture());
        assertThat(cap.getValue().getRecordId()).isEqualTo(5L);
    }

    @Test
    void record_mapperThrows_noPropagation()
    {
        when(mapper.insertFeedback(any(AiCopilotFeedback.class)))
                .thenThrow(new RuntimeException("DB down"));
        service.record(feedback(AiCopilotFeedback.TYPE_ELEMENT,
                AiCopilotFeedback.ACTION_ADOPT));
    }

    // ---------- 采纳率统计 ----------

    @Test
    void adoptionTotal_nullRow_zeroCountsAndRate()
    {
        when(mapper.selectAdoptionTotal(any(), any())).thenReturn(null);
        Map<String, Object> m = service.adoptionTotal(null, null);
        assertThat(m.get("total")).isEqualTo(0L);
        assertThat(m.get("adoptCount")).isEqualTo(0L);
        assertThat(m.get("modifyCount")).isEqualTo(0L);
        assertThat(m.get("ignoreCount")).isEqualTo(0L);
        assertThat((Double) m.get("adoptRate")).isEqualTo(0d);
    }

    @Test
    void adoptionTotal_counts_adoptRateComputed()
    {
        // MyBatis SUM/COUNT 常返回 BigDecimal/Long，两种口径都应兼容
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("total", new BigDecimal("10"));
        row.put("adoptCount", new BigDecimal("7"));
        row.put("modifyCount", 2L);
        row.put("ignoreCount", 1L);
        when(mapper.selectAdoptionTotal(any(), any())).thenReturn(row);

        Map<String, Object> m = service.adoptionTotal(null, null);
        assertThat(m.get("total")).isEqualTo(10L);
        assertThat(m.get("adoptCount")).isEqualTo(7L);
        // 7/10 = 70.00%
        assertThat((Double) m.get("adoptRate")).isEqualTo(70d);
    }

    @Test
    void adoptionTotal_zeroTotal_rateStaysZero()
    {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("total", 0L);
        when(mapper.selectAdoptionTotal(any(), any())).thenReturn(row);

        Map<String, Object> m = service.adoptionTotal(null, null);
        assertThat((Double) m.get("adoptRate")).isEqualTo(0d);
    }

    @Test
    void adoptionByAgent_eachRowMappedWithRate()
    {
        Map<String, Object> a1 = new LinkedHashMap<>();
        a1.put("agentId", 1L);
        a1.put("agentName", "张三");
        a1.put("deptId", 10L);
        a1.put("total", 4L);
        a1.put("adoptCount", 3L);
        Map<String, Object> a2 = new LinkedHashMap<>();
        a2.put("agentId", 2L);
        a2.put("agentName", "李四");
        a2.put("deptId", 10L);
        a2.put("total", 5L);
        a2.put("adoptCount", 1L);
        when(mapper.selectAdoptionByAgent(any(), any())).thenReturn(Arrays.asList(a1, a2));

        List<Map<String, Object>> rows = service.adoptionByAgent(null, null);
        assertThat(rows).hasSize(2);
        assertThat(rows.get(0).get("agentName")).isEqualTo("张三");
        assertThat((Double) rows.get(0).get("adoptRate")).isEqualTo(75d);
        assertThat((Double) rows.get(1).get("adoptRate")).isEqualTo(20d);
    }

    @Test
    void adoptionByAgent_nullResult_returnsEmptyList()
    {
        when(mapper.selectAdoptionByAgent(any(), any())).thenReturn(null);
        assertThat(service.adoptionByAgent(null, null)).isEmpty();
    }
}
