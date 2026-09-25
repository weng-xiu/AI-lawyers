package ai.lawyers.system.service.impl.lawyers.summary;

import java.lang.reflect.Field;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import ai.lawyers.system.domain.lawyers.AiCallRecord;
import ai.lawyers.system.mapper.lawyers.AiCallRecordMapper;
import ai.lawyers.system.service.lawyers.IAiModelConfigService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * P3-E3 话后 AI 通话小结服务测试：幂等、CAS 抢占、无文本/模型异常容错、开关与 force 重生成。
 *
 * @author ai-lawyers
 */
class AiCallSummaryServiceImplTest
{
    private static final Long RECORD_ID = 1001L;

    private AiCallRecordMapper mapper;
    private IAiModelConfigService modelConfigService;
    private AiCallSummaryServiceImpl service;

    private AiCallRecord record;

    private static final String JSON_OK = "{\"caseBrief\":\"张某咨询公司拖欠三个月工资\","
            + "\"issues\":[\"公司能否扣发工资\",\"如何讨要\"],"
            + "\"legalOpinion\":\"根据劳动合同法第八十五条，可向劳动监察投诉或申请仲裁\","
            + "\"todos\":[\"准备劳动合同与工资流水\",\"向劳动保障监察机构投诉\"],"
            + "\"followUp\":\"三日后回访投诉受理进展\"}";

    @BeforeEach
    void setUp() throws Exception
    {
        service = new AiCallSummaryServiceImpl();
        mapper = mock(AiCallRecordMapper.class);
        modelConfigService = mock(IAiModelConfigService.class);
        setField(service, "callRecordMapper", mapper);
        setField(service, "modelConfigService", modelConfigService);
        setField(service, "summaryEnabled", true);

        record = new AiCallRecord();
        record.setRecordId(RECORD_ID);
        record.setCallerName("张某");
        record.setCategoryName("劳动纠纷");
        record.setDuration(180);
        record.setTranscript("坐席：您好，请问咨询什么问题？群众：公司三个月没发工资了……");
        record.setAiSummaryStatus("0");

        when(mapper.selectAiCallRecordByRecordId(RECORD_ID)).thenAnswer(inv -> record);
        // CAS 默认抢占成功
        when(mapper.casAiSummaryStatus(eq(RECORD_ID), anyString(), anyString())).thenReturn(1);
        // 模拟 DB 成功回写后再查状态为 2
        when(mapper.updateAiSummarySuccess(any())).thenAnswer(inv ->
        {
            record.setAiSummaryStatus("2");
            record.setAiSummary(inv.getArgument(0, AiCallRecord.class).getAiSummary());
            return 1;
        });
        when(mapper.updateAiSummaryFail(eq(RECORD_ID), anyString())).thenAnswer(inv ->
        {
            record.setAiSummaryStatus("3");
            record.setAiSummaryFailReason(inv.getArgument(1));
            return 1;
        });
    }

    private void setField(Object obj, String name, Object value) throws Exception
    {
        Field f = obj.getClass().getDeclaredField(name);
        f.setAccessible(true);
        f.set(obj, value);
    }

    @Test
    void generateSummary_withTranscript_buildsFiveSectionTextAndDone()
    {
        when(modelConfigService.chatJson(anyString(), anyString())).thenReturn(JSON_OK);

        AiCallRecord out = service.generateSummary(RECORD_ID, false);

        assertThat(out.getAiSummaryStatus()).isEqualTo("2");
        assertThat(out.getAiSummary())
                .contains("【案情摘要】").contains("张某咨询公司拖欠三个月工资")
                .contains("【争议焦点】").contains("公司能否扣发工资")
                .contains("【法律意见】").contains("劳动合同法第八十五条")
                .contains("【待办事项】").contains("1.准备劳动合同与工资流水").contains("2.向劳动保障监察机构投诉")
                .contains("【回访建议】").contains("三日后回访");
        verify(mapper).updateAiSummarySuccess(any());
        verify(mapper, never()).updateAiSummaryFail(eq(RECORD_ID), anyString());
    }

    @Test
    void generateSummary_noText_marksFailWithoutCallingModel()
    {
        record.setTranscript(null);
        record.setContent(null);
        record.setAnswer(null);

        AiCallRecord out = service.generateSummary(RECORD_ID, false);

        assertThat(out.getAiSummaryStatus()).isEqualTo("3");
        assertThat(out.getAiSummaryFailReason()).contains("无可用通话文本");
        verify(modelConfigService, never()).chatJson(anyString(), anyString());
        verify(mapper, never()).casAiSummaryStatus(eq(RECORD_ID), anyString(), anyString());
        verify(mapper).updateAiSummaryFail(eq(RECORD_ID), anyString());
    }

    @Test
    void generateSummary_alreadyDoneWithoutForce_isIdempotent()
    {
        record.setAiSummaryStatus("2");
        record.setAiSummary("【案情摘要】历史小结");

        AiCallRecord out = service.generateSummary(RECORD_ID, false);

        assertThat(out.getAiSummary()).isEqualTo("【案情摘要】历史小结");
        verify(modelConfigService, never()).chatJson(anyString(), anyString());
        verify(mapper, never()).casAiSummaryStatus(eq(RECORD_ID), anyString(), anyString());
        verify(mapper, never()).updateAiSummarySuccess(any());
    }

    @Test
    void generateSummary_alreadyDoneWithForce_regenerates()
    {
        record.setAiSummaryStatus("2");
        record.setAiSummary("旧小结");
        when(modelConfigService.chatJson(anyString(), anyString())).thenReturn(JSON_OK);

        AiCallRecord out = service.generateSummary(RECORD_ID, true);

        assertThat(out.getAiSummaryStatus()).isEqualTo("2");
        assertThat(out.getAiSummary()).contains("【案情摘要】");
        verify(mapper).casAiSummaryStatus(RECORD_ID, "2", "1");
        verify(modelConfigService).chatJson(anyString(), anyString());
    }

    @Test
    void generateSummary_running_skipsWithoutCas()
    {
        record.setAiSummaryStatus("1");

        service.generateSummary(RECORD_ID, false);

        verify(modelConfigService, never()).chatJson(anyString(), anyString());
        verify(mapper, never()).casAiSummaryStatus(eq(RECORD_ID), anyString(), anyString());
    }

    @Test
    void generateSummary_casLost_doesNotCallModel()
    {
        when(mapper.casAiSummaryStatus(eq(RECORD_ID), eq("0"), eq("1"))).thenReturn(0);

        service.generateSummary(RECORD_ID, false);

        verify(modelConfigService, never()).chatJson(anyString(), anyString());
        verify(mapper, never()).updateAiSummarySuccess(any());
    }

    @Test
    void generateSummary_modelThrows_marksFailAndDoesNotPropagate()
    {
        when(modelConfigService.chatJson(anyString(), anyString()))
                .thenThrow(new RuntimeException("模型服务繁忙"));

        AiCallRecord out = service.generateSummary(RECORD_ID, false);

        assertThat(out.getAiSummaryStatus()).isEqualTo("3");
        assertThat(out.getAiSummaryFailReason()).contains("模型生成失败").contains("模型服务繁忙");
        verify(mapper).updateAiSummaryFail(eq(RECORD_ID), anyString());
        verify(mapper, never()).updateAiSummarySuccess(any());
    }

    @Test
    void generateSummary_switchDisabled_doesNothing() throws Exception
    {
        setField(service, "summaryEnabled", false);

        AiCallRecord out = service.generateSummary(RECORD_ID, false);

        assertThat(out).isSameAs(record);
        verify(modelConfigService, never()).chatJson(anyString(), anyString());
        verify(mapper, never()).casAiSummaryStatus(any(), anyString(), anyString());
        verify(mapper, never()).updateAiSummarySuccess(any());
    }

    @Test
    void generateSummary_recordMissing_returnsNull()
    {
        when(mapper.selectAiCallRecordByRecordId(999L)).thenReturn(null);
        assertThat(service.generateSummary(999L, false)).isNull();
    }

    @Test
    void generateSummary_fallbackToManualContentWhenNoTranscript()
    {
        record.setTranscript(null);
        record.setContent("咨询老板拖欠工资怎么办");
        record.setAnswer("建议申请劳动仲裁");
        when(modelConfigService.chatJson(anyString(), anyString())).thenReturn(JSON_OK);

        AiCallRecord out = service.generateSummary(RECORD_ID, false);

        assertThat(out.getAiSummaryStatus()).isEqualTo("2");
        ArgumentCaptor<String> userCaptor = ArgumentCaptor.forClass(String.class);
        verify(modelConfigService).chatJson(anyString(), userCaptor.capture());
        assertThat(userCaptor.getValue()).contains("群众咨询：咨询老板拖欠工资怎么办")
                .contains("坐席解答：建议申请劳动仲裁");
    }
}
