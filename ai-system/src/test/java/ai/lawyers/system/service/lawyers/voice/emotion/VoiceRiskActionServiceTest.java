package ai.lawyers.system.service.lawyers.voice.emotion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import ai.lawyers.system.domain.lawyers.AiRiskWarning;
import ai.lawyers.system.domain.lawyers.skill.AiCallQueue;
import ai.lawyers.system.mapper.lawyers.skill.AiCallQueueMapper;
import ai.lawyers.system.service.lawyers.IAiRiskWarningService;
import ai.lawyers.system.service.lawyers.metrics.HotlineMetrics;

/**
 * {@link VoiceRiskActionService} P3-E4 测试：
 * 预警落库字段、队列仅升不降提优、无排队行/非数字会话兜底、
 * 依赖异常不外抛、开关关闭零动作、指标计数。
 *
 * @author ai-lawyers
 */
class VoiceRiskActionServiceTest
{
    private IAiRiskWarningService riskWarningService;

    private AiCallQueueMapper queueMapper;

    private HotlineMetrics metrics;

    private VoiceRiskActionService service;

    @BeforeEach
    void setUp() throws Exception
    {
        riskWarningService = mock(IAiRiskWarningService.class);
        queueMapper = mock(AiCallQueueMapper.class);
        metrics = mock(HotlineMetrics.class);
        service = new VoiceRiskActionService();
        inject("riskWarningService", riskWarningService);
        inject("queueMapper", queueMapper);
        inject("metrics", metrics);
        // 脱离 Spring 时 @Value 不生效，反射写入默认配置值
        inject("urgentPriority", 100);
        inject("negativePriority", 50);
        service.setEnabled(true);
        // insert 模拟回填自增预警ID
        doAnswer(inv -> {
            inv.getArgument(0, AiRiskWarning.class).setWarningId(55L);
            return 1;
        }).when(riskWarningService).insertAiRiskWarning(any(AiRiskWarning.class));
    }

    private void inject(String field, Object value) throws Exception
    {
        Field f = VoiceRiskActionService.class.getDeclaredField(field);
        f.setAccessible(true);
        f.set(service, value);
    }

    private AiCallQueue queueRow(long queueId, Integer priority)
    {
        AiCallQueue q = new AiCallQueue();
        q.setQueueId(queueId);
        q.setPriority(priority);
        return q;
    }

    @Test
    void urgent_warningLevel1AndQueueBoost100()
    {
        when(queueMapper.selectAiCallQueueList(any())).thenReturn(java.util.Arrays.asList(queueRow(9L, 0)));

        Long warningId = service.handleEmotion(EmotionIntentResult.EMOTION_URGENT, "URGENT",
                "我不想活了", java.util.Arrays.asList("不想活"), "1000");

        assertThat(warningId).isEqualTo(55L);

        ArgumentCaptor<AiRiskWarning> cap = ArgumentCaptor.forClass(AiRiskWarning.class);
        verify(riskWarningService).insertAiRiskWarning(cap.capture());
        AiRiskWarning w = cap.getValue();
        assertThat(w.getWarningType()).isEqualTo("情绪异常");
        assertThat(w.getWarningLevel()).isEqualTo("1");
        assertThat(w.getSourceType()).isEqualTo("通话");
        assertThat(w.getSourceId()).isEqualTo(1000L);
        assertThat(w.getContent()).contains("不想活").contains("紧急");

        ArgumentCaptor<AiCallQueue> qcap = ArgumentCaptor.forClass(AiCallQueue.class);
        verify(queueMapper).updateAiCallQueue(qcap.capture());
        assertThat(qcap.getValue().getQueueId()).isEqualTo(9L);
        assertThat(qcap.getValue().getPriority()).isEqualTo(100);

        verify(metrics).incrementVoiceEmotion("urgent");
    }

    @Test
    void negative_warningLevel2AndQueueBoost50()
    {
        when(queueMapper.selectAiCallQueueList(any())).thenReturn(java.util.Arrays.asList(queueRow(9L, 0)));

        Long warningId = service.handleEmotion(EmotionIntentResult.EMOTION_NEGATIVE, "COMPLAINT",
                "我要投诉", java.util.Arrays.asList("投诉"), "1000");

        assertThat(warningId).isEqualTo(55L);
        ArgumentCaptor<AiRiskWarning> cap = ArgumentCaptor.forClass(AiRiskWarning.class);
        verify(riskWarningService).insertAiRiskWarning(cap.capture());
        assertThat(cap.getValue().getWarningLevel()).isEqualTo("2");
        assertThat(cap.getValue().getContent()).contains("负面");
        ArgumentCaptor<AiCallQueue> qcap = ArgumentCaptor.forClass(AiCallQueue.class);
        verify(queueMapper).updateAiCallQueue(qcap.capture());
        assertThat(qcap.getValue().getPriority()).isEqualTo(50);
        verify(metrics).incrementVoiceEmotion("negative");
    }

    @Test
    void noQueuingRow_warningInsertedNoQueueUpdate()
    {
        when(queueMapper.selectAiCallQueueList(any())).thenReturn(Collections.emptyList());

        Long warningId = service.handleEmotion(EmotionIntentResult.EMOTION_URGENT, null,
                "救命", java.util.Arrays.asList("救命"), "1000");

        assertThat(warningId).isEqualTo(55L);
        verify(queueMapper, never()).updateAiCallQueue(any());
    }

    @Test
    void queuePriorityAlreadyHigher_noUpdate()
    {
        // 已提优至 100 的 urgent 再次命中（同级别），仅升不降
        when(queueMapper.selectAiCallQueueList(any())).thenReturn(java.util.Arrays.asList(queueRow(9L, 100)));

        service.handleEmotion(EmotionIntentResult.EMOTION_URGENT, "URGENT",
                "不想活", java.util.Arrays.asList("不想活"), "1000");

        verify(queueMapper, never()).updateAiCallQueue(any());
    }

    @Test
    void nonNumericSessionId_fallbackLookupBySessionId()
    {
        // recordId 解析不出来时，按会话ID查排队行
        when(queueMapper.selectQueuingBySessionId("ivr-uuid-1")).thenReturn(queueRow(12L, 0));

        service.handleEmotion(EmotionIntentResult.EMOTION_NEGATIVE, null,
                "太过分了", java.util.Arrays.asList("太过分"), "ivr-uuid-1");

        ArgumentCaptor<AiRiskWarning> cap = ArgumentCaptor.forClass(AiRiskWarning.class);
        verify(riskWarningService).insertAiRiskWarning(cap.capture());
        assertThat(cap.getValue().getSourceId()).isNull();
        ArgumentCaptor<AiCallQueue> qcap = ArgumentCaptor.forClass(AiCallQueue.class);
        verify(queueMapper).updateAiCallQueue(qcap.capture());
        assertThat(qcap.getValue().getQueueId()).isEqualTo(12L);
        assertThat(qcap.getValue().getPriority()).isEqualTo(50);
    }

    @Test
    void disabled_noActionAtAll()
    {
        service.setEnabled(false);

        Long warningId = service.handleEmotion(EmotionIntentResult.EMOTION_URGENT, null,
                "救命", java.util.Arrays.asList("救命"), "1000");

        assertThat(warningId).isNull();
        verify(riskWarningService, never()).insertAiRiskWarning(any());
        verify(queueMapper, never()).updateAiCallQueue(any());
        verify(metrics, never()).incrementVoiceEmotion(any());
    }

    @Test
    void unknownLevel_noAction()
    {
        Long warningId = service.handleEmotion("neutral", null, "你好", Collections.emptyList(), "1000");
        assertThat(warningId).isNull();
        verify(riskWarningService, never()).insertAiRiskWarning(any());
    }

    @Test
    void dependenciesThrow_bestEffortNeverThrows()
    {
        // 预警落库抛异常被吞；队列提优继续、指标继续
        when(riskWarningService.insertAiRiskWarning(any())).thenThrow(new RuntimeException("db down"));
        when(queueMapper.selectAiCallQueueList(any())).thenReturn(java.util.Arrays.asList(queueRow(9L, 0)));

        Long warningId = service.handleEmotion(EmotionIntentResult.EMOTION_URGENT, null,
                "救命", java.util.Arrays.asList("救命"), "1000");

        assertThat(warningId).isNull();
        verify(queueMapper).updateAiCallQueue(any());
        verify(metrics).incrementVoiceEmotion("urgent");
    }
}
