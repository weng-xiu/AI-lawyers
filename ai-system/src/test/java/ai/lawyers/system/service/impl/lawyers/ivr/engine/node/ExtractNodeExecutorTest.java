package ai.lawyers.system.service.impl.lawyers.ivr.engine.node;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
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
 * extract 信息抽取节点单测：字段来源(object/keys 数组)、结果变量写入、
 * JSON 清洗、文本兜底、未配置跳过与调用失败降级。
 *
 * @author ai-lawyers
 */
@ExtendWith(MockitoExtension.class)
class ExtractNodeExecutorTest
{
    @Mock
    private IAiModelConfigService modelConfigService;

    private ExtractNodeExecutor executor;

    @BeforeEach
    void setUp()
    {
        executor = new ExtractNodeExecutor(new IvrTemplateRenderer(), modelConfigService);
    }

    @Test
    void extractsObjectFieldsAndWritesResultVar()
    {
        when(modelConfigService.chatJson(anyString(), anyString(), eq(AiModelCallLogRecorder.SCENE_EXTRACT)))
                .thenReturn("```json\n{\"name\":\"张三\",\"phone\":\"13800000000\"}\n```");
        Map<String, Object> vars = new HashMap<>();
        IvrNodeContext ctx = context(node("{\"text\":\"我叫张三，电话13800000000\","
                + "\"fields\":{\"name\":\"姓名\",\"phone\":\"电话\"}}"), vars);

        executor.execute(ctx);

        assertThat(vars).containsEntry("name", "张三").containsEntry("phone", "13800000000");
        assertThat(vars.get("extractResult")).isEqualTo("{\"name\":\"张三\",\"phone\":\"13800000000\"}");
        assertThat(ctx.getStep().getAction()).isEqualTo("EXTRACT");
        assertThat(ctx.getStep().getDetail()).contains("信息抽取成功，共提取2个字段");
    }

    @Test
    void supportsCustomResultVar()
    {
        when(modelConfigService.chatJson(anyString(), anyString(), eq(AiModelCallLogRecorder.SCENE_EXTRACT)))
                .thenReturn("{\"amount\":\"100\"}");
        Map<String, Object> vars = new HashMap<>();
        IvrNodeContext ctx = context(node("{\"text\":\"100元\",\"fields\":{\"amount\":\"金额\"},"
                + "\"resultVar\":\"extractAmount\"}"), vars);

        executor.execute(ctx);

        assertThat(vars).containsEntry("amount", "100");
        assertThat(vars).containsKey("extractAmount").doesNotContainKey("extractResult");
    }

    @Test
    void supportsKeysArrayWithDescription()
    {
        when(modelConfigService.chatJson(anyString(), anyString(), eq(AiModelCallLogRecorder.SCENE_EXTRACT)))
                .thenReturn("{\"city\":\"北京\"}");
        Map<String, Object> vars = new HashMap<>();
        IvrNodeContext ctx = context(node("{\"text\":\"我在北京\",\"keys\":["
                + "{\"name\":\"city\",\"desc\":\"城市\"}]}"), vars);

        executor.execute(ctx);

        assertThat(vars).containsEntry("city", "北京");
        assertThat(ctx.getStep().getDetail()).contains("共提取1个字段");
    }

    @Test
    void missingFieldBecomesEmptyString()
    {
        when(modelConfigService.chatJson(anyString(), anyString(), eq(AiModelCallLogRecorder.SCENE_EXTRACT)))
                .thenReturn("{\"name\":\"张三\"}");
        Map<String, Object> vars = new HashMap<>();
        IvrNodeContext ctx = context(node("{\"text\":\"张三\",\"fields\":{\"name\":\"姓名\",\"phone\":\"电话\"}}"), vars);

        executor.execute(ctx);

        assertThat(vars).containsEntry("name", "张三").containsEntry("phone", "");
    }

    @Test
    void nestedValueSerializedAsJson()
    {
        when(modelConfigService.chatJson(anyString(), anyString(), eq(AiModelCallLogRecorder.SCENE_EXTRACT)))
                .thenReturn("{\"detail\":{\"a\":1}}");
        Map<String, Object> vars = new HashMap<>();
        IvrNodeContext ctx = context(node("{\"text\":\"x\",\"fields\":{\"detail\":\"明细\"}}"), vars);

        executor.execute(ctx);

        assertThat(vars.get("detail")).isEqualTo("{\"a\":1}");
    }

    @Test
    void fallsBackToLastInputWhenTextMissing()
    {
        when(modelConfigService.chatJson(anyString(), anyString(), eq(AiModelCallLogRecorder.SCENE_EXTRACT)))
                .thenReturn("{}");
        Map<String, Object> vars = new HashMap<>();
        vars.put("lastInput", "来自上次输入");
        IvrNodeContext ctx = context(node("{\"fields\":{\"name\":\"姓名\"}}"), vars);

        executor.execute(ctx);

        verify(modelConfigService).chatJson(anyString(),
                org.mockito.ArgumentMatchers.contains("来自上次输入"),
                eq(AiModelCallLogRecorder.SCENE_EXTRACT));
    }

    @Test
    void messageKeyUsedAsTextFallback()
    {
        when(modelConfigService.chatJson(anyString(), anyString(), eq(AiModelCallLogRecorder.SCENE_EXTRACT)))
                .thenReturn("{}");
        Map<String, Object> vars = new HashMap<>();
        IvrNodeContext ctx = context(node("{\"message\":\"消息文本\",\"fields\":{\"name\":\"姓名\"}}"), vars);

        executor.execute(ctx);

        verify(modelConfigService).chatJson(anyString(),
                org.mockito.ArgumentMatchers.contains("消息文本"),
                eq(AiModelCallLogRecorder.SCENE_EXTRACT));
    }

    @Test
    void noFieldsConfigSkipsWithoutCallingModel()
    {
        Map<String, Object> vars = new HashMap<>();
        IvrNodeContext ctx = context(node("{\"text\":\"hello\"}"), vars);

        AiIvrNode next = executor.execute(ctx);

        assertThat(ctx.getStep().getDetail()).contains("未配置fields/keys，跳过");
        assertThat(next).isNull();
        verify(modelConfigService, org.mockito.Mockito.never())
                .chatJson(anyString(), anyString(), anyString());
    }

    @Test
    void modelFailureRecordsErrorAndContinues()
    {
        when(modelConfigService.chatJson(anyString(), anyString(), eq(AiModelCallLogRecorder.SCENE_EXTRACT)))
                .thenThrow(new RuntimeException("模型超时"));
        Map<String, Object> vars = new HashMap<>();
        IvrNodeContext ctx = context(node("{\"text\":\"x\",\"fields\":{\"name\":\"姓名\"}}"), vars);

        AiIvrNode next = executor.execute(ctx);

        assertThat(vars).containsEntry("extractError", "模型超时");
        assertThat(ctx.getStep().getDetail()).contains("信息抽取失败：模型超时");
        assertThat(next).isNull();
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
        node.setNodeType("extract");
        node.setNodeName("信息抽取");
        node.setNodeConfig(config);
        return node;
    }
}
