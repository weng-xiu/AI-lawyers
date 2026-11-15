package ai.lawyers.system.service.impl.lawyers.ivr.engine.node;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import ai.lawyers.system.domain.lawyers.ivr.AiIvrEdge;
import ai.lawyers.system.domain.lawyers.ivr.AiIvrNode;
import ai.lawyers.system.domain.lawyers.ivr.IvrExecuteRequest;
import ai.lawyers.system.domain.lawyers.ivr.IvrExecuteResult;
import ai.lawyers.system.domain.lawyers.ivr.IvrNodeStep;
import ai.lawyers.system.service.impl.lawyers.ivr.engine.IvrTemplateRenderer;
import ai.lawyers.system.service.lawyers.voice.VoiceEngineManager;
import ai.lawyers.system.service.lawyers.voice.VoiceModelEnum;

/**
 * B4：{@link SayNodeExecutor} 语音播报节点执行器测试。
 *
 * <p>覆盖 text/play/节点名称三级文本回退、模板渲染结果写入 lastSay、默认纯文本
 * 播报、配置 voiceEngine 时的 TTS 合成（格式/采样率/tts 参数透传、空音频不记录）
 * 以及后续节点路由。</p>
 *
 * @author ai-lawyers
 */
class SayNodeExecutorTest
{
    private IvrTemplateRenderer templateRenderer;

    private VoiceEngineManager voiceEngineManager;

    private SayNodeExecutor executor;

    @BeforeEach
    void setUp()
    {
        templateRenderer = mock(IvrTemplateRenderer.class);
        voiceEngineManager = mock(VoiceEngineManager.class);
        executor = new SayNodeExecutor(templateRenderer, voiceEngineManager);
        // 默认原样返回模板，便于断言渲染前后的文本
        when(templateRenderer.render(any(), any())).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void supportsSayNode()
    {
        assertThat(executor.supportedTypes()).containsExactly(SayNodeExecutor.NODE_TYPE);
    }

    @Test
    void playsConfiguredText()
    {
        IvrNodeContext ctx = context(node("{\"text\":\"您好，欢迎致电\"}"));

        AiIvrNode next = executor.execute(ctx);

        assertThat(next).isSameAs(ctx.getNodeMap().get(2L));
        assertThat(ctx.getVariables()).containsEntry("lastSay", "您好，欢迎致电");
        assertThat(ctx.getStep().getAction()).isEqualTo("PLAY");
        assertThat(ctx.getStep().getDetail()).isEqualTo("语音播报：您好，欢迎致电");
        assertThat(ctx.getResult().getSteps()).containsExactly(ctx.getStep());
        verifyNoInteractions(voiceEngineManager);
    }

    @Test
    void fallsBackToPlayField()
    {
        IvrNodeContext ctx = context(node("{\"play\":\"请稍候\"}"));

        executor.execute(ctx);

        assertThat(ctx.getVariables()).containsEntry("lastSay", "请稍候");
    }

    @Test
    void fallsBackToNodeName()
    {
        AiIvrNode node = node("{}");
        node.setNodeName("默认欢迎语");
        IvrNodeContext ctx = context(node);

        executor.execute(ctx);

        assertThat(ctx.getVariables()).containsEntry("lastSay", "默认欢迎语");
    }

    @Test
    void rendersTemplateVariables()
    {
        when(templateRenderer.render(eq("您好，${name}"), any())).thenReturn("您好，张三");
        IvrNodeContext ctx = context(node("{\"text\":\"您好，${name}\"}"));

        executor.execute(ctx);

        assertThat(ctx.getVariables()).containsEntry("lastSay", "您好，张三");
        assertThat(ctx.getStep().getDetail()).isEqualTo("语音播报：您好，张三");
    }

    @Test
    void synthesizesAudioWhenEngineConfigured()
    {
        when(voiceEngineManager.synthesize(eq(VoiceModelEnum.ALI), eq("欢迎"), eq("mp3"), eq(16000), anyMap()))
                .thenReturn(new byte[12]);
        IvrNodeContext ctx = context(node(
                "{\"text\":\"欢迎\",\"voiceEngine\":\"ali\",\"format\":\"mp3\",\"sampleRate\":16000,"
                + "\"tts\":{\"voice\":\"v1\",\"speed\":\"1.2\"}}"));

        executor.execute(ctx);

        assertThat(ctx.getVariables()).containsEntry("lastSayAudioLength", 12);
        assertThat(ctx.getStep().getDetail()).isEqualTo("语音播报：欢迎（TTS已合成12字节音频）");

        ArgumentCaptor<Map> options = ArgumentCaptor.forClass(Map.class);
        verify(voiceEngineManager).synthesize(eq(VoiceModelEnum.ALI), eq("欢迎"), eq("mp3"), eq(16000),
                options.capture());
        assertThat(options.getValue()).containsEntry("voice", "v1").containsEntry("speed", "1.2");
    }

    @Test
    void emptyAudioKeepsTextOnly()
    {
        when(voiceEngineManager.synthesize(any(), anyString(), anyString(), anyInt(), anyMap()))
                .thenReturn(new byte[0]);
        IvrNodeContext ctx = context(node("{\"text\":\"欢迎\",\"voiceEngine\":\"mock\"}"));

        executor.execute(ctx);

        assertThat(ctx.getVariables()).doesNotContainKey("lastSayAudioLength");
        assertThat(ctx.getStep().getDetail()).isEqualTo("语音播报：欢迎");
    }

    @Test
    void blankEngineSkipsSynthesis()
    {
        IvrNodeContext ctx = context(node("{\"text\":\"欢迎\",\"voiceEngine\":\" \"}"));

        executor.execute(ctx);

        verifyNoInteractions(voiceEngineManager);
    }

    @Test
    void blankTextAndNoNameResultsInEmptySay()
    {
        AiIvrNode node = node("{}");
        IvrNodeContext ctx = context(node);

        executor.execute(ctx);

        assertThat(ctx.getVariables()).containsEntry("lastSay", "");
        assertThat(ctx.getStep().getDetail()).isEqualTo("语音播报：");
    }

    private IvrNodeContext context(AiIvrNode current)
    {
        Map<Long, AiIvrNode> nodeMap = new HashMap<>();
        nodeMap.put(1L, current);
        nodeMap.put(2L, node("say", "{}"));
        List<AiIvrEdge> edges = new ArrayList<>();
        edges.add(edge(1L, 2L));
        IvrNodeStep step = new IvrNodeStep(1L, current.getNodeType(), "播报", null, null);
        IvrNodeContext ctx = new IvrNodeContext(nodeMap, edges, new HashMap<>(), new ArrayDeque<>(),
                new IvrExecuteRequest(), "sess-1", null, new IvrExecuteResult(), req -> null);
        ctx.setCurrent(current);
        ctx.setStep(step);
        return ctx;
    }

    private static AiIvrNode node(String config)
    {
        return node("say", config);
    }

    private static AiIvrNode node(String type, String config)
    {
        AiIvrNode node = new AiIvrNode();
        node.setNodeId(1L);
        node.setNodeType(type);
        node.setNodeConfig(config);
        node.setNodeName("");
        return node;
    }

    private static AiIvrEdge edge(Long source, Long target)
    {
        AiIvrEdge edge = new AiIvrEdge();
        edge.setSourceNodeId(source);
        edge.setTargetNodeId(target);
        edge.setSortOrder(0);
        return edge;
    }
}
