package ai.lawyers.system.service.impl.lawyers.ivr.engine.node;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayDeque;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import ai.lawyers.system.domain.lawyers.ivr.AiIvrFlow;
import ai.lawyers.system.domain.lawyers.ivr.AiIvrNode;
import ai.lawyers.system.domain.lawyers.ivr.IvrExecuteRequest;
import ai.lawyers.system.domain.lawyers.ivr.IvrExecuteResult;
import ai.lawyers.system.domain.lawyers.ivr.IvrNodeStep;
import ai.lawyers.system.service.impl.lawyers.ivr.engine.IvrTemplateRenderer;
import ai.lawyers.system.service.lawyers.IAiModelConfigService;
import ai.lawyers.system.service.lawyers.stat.AiModelCallLogRecorder;

/**
 * sentiment 情绪分析节点单测：文本来源、模型结果归一化、
 * 模型失败关键词兜底与空文本默认中性。
 *
 * @author ai-lawyers
 */
@ExtendWith(MockitoExtension.class)
class SentimentNodeExecutorTest
{
    @Mock
    private IAiModelConfigService modelConfigService;

    private SentimentNodeExecutor executor;

    @BeforeEach
    void setUp()
    {
        executor = new SentimentNodeExecutor(new IvrTemplateRenderer(), modelConfigService);
    }

    @Test
    void writesPositiveFromModel()
    {
        when(modelConfigService.chatJson(anyString(), anyString(), eq(AiModelCallLogRecorder.SCENE_EMOTION)))
                .thenReturn("{\"sentiment\":\"positive\",\"confidence\":0.9}");
        Map<String, Object> vars = new HashMap<>();
        IvrNodeContext ctx = context(node("{\"text\":\"谢谢你们\"}"), vars);

        executor.execute(ctx);

        assertThat(vars).containsEntry("sentiment", "positive");
        assertThat(ctx.getStep().getAction()).isEqualTo("SENTIMENT");
        assertThat(ctx.getStep().getDetail()).contains("情绪分析：positive");
    }

    @Test
    void normalizesChineseNegative()
    {
        when(modelConfigService.chatJson(anyString(), anyString(), eq(AiModelCallLogRecorder.SCENE_EMOTION)))
                .thenReturn("{\"sentiment\":\"负面\"}");
        Map<String, Object> vars = new HashMap<>();
        IvrNodeContext ctx = context(node("{\"text\":\"我要投诉\"}"), vars);

        executor.execute(ctx);

        assertThat(vars).containsEntry("sentiment", "negative");
    }

    @Test
    void fallsBackToEmotionField()
    {
        when(modelConfigService.chatJson(anyString(), anyString(), eq(AiModelCallLogRecorder.SCENE_EMOTION)))
                .thenReturn("{\"emotion\":\"positive\"}");
        Map<String, Object> vars = new HashMap<>();
        IvrNodeContext ctx = context(node("{\"text\":\"很好\"}"), vars);

        executor.execute(ctx);

        assertThat(vars).containsEntry("sentiment", "positive");
    }

    @Test
    void unknownValueNormalizedToNeutral()
    {
        when(modelConfigService.chatJson(anyString(), anyString(), eq(AiModelCallLogRecorder.SCENE_EMOTION)))
                .thenReturn("{\"sentiment\":\"unknown\"}");
        Map<String, Object> vars = new HashMap<>();
        IvrNodeContext ctx = context(node("{\"text\":\"一般\"}"), vars);

        executor.execute(ctx);

        assertThat(vars).containsEntry("sentiment", "neutral");
    }

    @Test
    void modelFailureFallsBackToNegativeKeyword()
    {
        when(modelConfigService.chatJson(anyString(), anyString(), eq(AiModelCallLogRecorder.SCENE_EMOTION)))
                .thenThrow(new RuntimeException("模型不可用"));
        Map<String, Object> vars = new HashMap<>();
        IvrNodeContext ctx = context(node("{\"text\":\"服务太差了，我要投诉\"}"), vars);

        executor.execute(ctx);

        assertThat(vars).containsEntry("sentiment", "negative");
    }

    @Test
    void modelFailureFallsBackToPositiveKeyword()
    {
        when(modelConfigService.chatJson(anyString(), anyString(), eq(AiModelCallLogRecorder.SCENE_EMOTION)))
                .thenThrow(new RuntimeException("模型不可用"));
        Map<String, Object> vars = new HashMap<>();
        IvrNodeContext ctx = context(node("{\"text\":\"非常感谢你们\"}"), vars);

        executor.execute(ctx);

        assertThat(vars).containsEntry("sentiment", "positive");
    }

    @Test
    void modelFailureWithoutKeywordsIsNeutral()
    {
        when(modelConfigService.chatJson(anyString(), anyString(), eq(AiModelCallLogRecorder.SCENE_EMOTION)))
                .thenThrow(new RuntimeException("模型不可用"));
        Map<String, Object> vars = new HashMap<>();
        IvrNodeContext ctx = context(node("{\"text\":\"我咨询一下流程\"}"), vars);

        executor.execute(ctx);

        assertThat(vars).containsEntry("sentiment", "neutral");
    }

    @Test
    void emptyTextIsNeutralWithoutModelCall()
    {
        Map<String, Object> vars = new HashMap<>();
        IvrNodeContext ctx = context(node("{}"), vars);

        executor.execute(ctx);

        assertThat(vars).containsEntry("sentiment", "neutral");
        verify(modelConfigService, never())
                .chatJson(anyString(), anyString(), anyString());
    }

    @Test
    void fallsBackToLastInput()
    {
        when(modelConfigService.chatJson(anyString(), org.mockito.ArgumentMatchers.contains("上次输入"),
                eq(AiModelCallLogRecorder.SCENE_EMOTION))).thenReturn("{\"sentiment\":\"positive\"}");
        Map<String, Object> vars = new HashMap<>();
        vars.put("lastInput", "上次输入");
        IvrNodeContext ctx = context(node("{}"), vars);

        executor.execute(ctx);

        assertThat(vars).containsEntry("sentiment", "positive");
    }

    @Test
    void messageKeyUsedAsTextFallback()
    {
        when(modelConfigService.chatJson(anyString(), org.mockito.ArgumentMatchers.contains("消息文本"),
                eq(AiModelCallLogRecorder.SCENE_EMOTION))).thenReturn("{\"sentiment\":\"negative\"}");
        Map<String, Object> vars = new HashMap<>();
        IvrNodeContext ctx = context(node("{\"message\":\"消息文本\"}"), vars);

        executor.execute(ctx);

        assertThat(vars).containsEntry("sentiment", "negative");
    }

    private IvrNodeContext context(AiIvrNode node, Map<String, Object> vars)
    {
        IvrNodeContext ctx = new IvrNodeContext(new HashMap<>(), Collections.emptyList(), vars,
                new ArrayDeque<>(), new IvrExecuteRequest(), "IVR-T", new AiIvrFlow(),
                new IvrExecuteResult(), r -> null);
        ctx.setCurrent(node);
        ctx.setStep(new IvrNodeStep());
        return ctx;
    }

    private AiIvrNode node(String config)
    {
        AiIvrNode node = new AiIvrNode();
        node.setNodeId(1L);
        node.setFlowId(10L);
        node.setNodeType("sentiment");
        node.setNodeName("情绪分析");
        node.setNodeConfig(config);
        return node;
    }
}
