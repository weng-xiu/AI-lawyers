package ai.lawyers.system.service.impl.lawyers.ivr.engine.node;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayDeque;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import ai.lawyers.system.domain.lawyers.AiHotspotSuppress;
import ai.lawyers.system.domain.lawyers.ivr.AiIvrFlow;
import ai.lawyers.system.domain.lawyers.ivr.AiIvrNode;
import ai.lawyers.system.domain.lawyers.ivr.IntentionMatchResult;
import ai.lawyers.system.domain.lawyers.ivr.IvrExecuteRequest;
import ai.lawyers.system.domain.lawyers.ivr.IvrExecuteResult;
import ai.lawyers.system.domain.lawyers.ivr.IvrNodeStep;
import ai.lawyers.system.service.impl.lawyers.ivr.engine.IvrTemplateRenderer;
import ai.lawyers.system.service.lawyers.IAiHotspotSuppressService;
import ai.lawyers.system.service.lawyers.ivr.engine.IntentionRecognitionService;

/**
 * intention 意图识别节点单测：文本来源优先级、命中/未命中结果写入、
 * 高频置底 REJECT 拦截与 PRIORITY 降权、开关与话路方向。
 *
 * @author ai-lawyers
 */
@ExtendWith(MockitoExtension.class)
class IntentionNodeExecutorTest
{
    @Mock
    private IntentionRecognitionService intentionRecognitionService;

    @Mock
    private IAiHotspotSuppressService hotspotSuppressService;

    private IntentionNodeExecutor executor;

    @BeforeEach
    void setUp()
    {
        executor = new IntentionNodeExecutor(new IvrTemplateRenderer(),
                intentionRecognitionService, hotspotSuppressService, true);
    }

    @Test
    void matchedIntentionWritesVariablesAndResult()
    {
        when(hotspotSuppressService.matchKeyword(any(), any(), any(), any()))
                .thenReturn(null);
        when(intentionRecognitionService.recognize(anyString(), isNull(), eq("IVR-T"), eq(10L), eq(1L)))
                .thenReturn(match("CONTRACT", "合同纠纷", "REGEX", 5L, "民事"));
        Map<String, Object> vars = new HashMap<>();
        IvrNodeContext ctx = context(node("{\"question\":\"我要起诉\"}"), vars, request());

        AiIvrNode next = executor.execute(ctx);

        assertThat(vars).containsEntry("matchedIntention", "CONTRACT")
                .containsEntry("matchedIntentionName", "合同纠纷")
                .containsEntry("matchedCategoryId", 5L)
                .containsEntry("matchedCategoryName", "民事");
        assertThat(ctx.getResult().getMatchedIntention()).isEqualTo("CONTRACT");
        assertThat(ctx.getResult().getMatchMethod()).isEqualTo("REGEX");
        assertThat(ctx.getStep().getAction()).isEqualTo("INTENTION");
        assertThat(ctx.getStep().getDetail()).contains("REGEX").contains("合同纠纷").contains("CONTRACT");
        assertThat(next).isNull();
    }

    @Test
    void unmatchedIntentionWritesEmptyVariables()
    {
        when(hotspotSuppressService.matchKeyword(any(), any(), any(), any()))
                .thenReturn(null);
        when(intentionRecognitionService.recognize(anyString(), isNull(), eq("IVR-T"), eq(10L), eq(1L)))
                .thenReturn(IntentionMatchResult.none("未命中"));
        Map<String, Object> vars = new HashMap<>();
        IvrNodeContext ctx = context(node("{\"question\":\"随便\"}"), vars, request());

        executor.execute(ctx);

        assertThat(vars).containsEntry("matchedIntention", "").containsEntry("matchedIntentionName", "");
        assertThat(ctx.getResult().getMatchedIntention()).isNull();
        assertThat(ctx.getStep().getDetail()).contains("未命中");
    }

    @Test
    void nullMatchTreatedAsUnmatched()
    {
        when(hotspotSuppressService.matchKeyword(any(), any(), any(), any()))
                .thenReturn(null);
        when(intentionRecognitionService.recognize(anyString(), isNull(), eq("IVR-T"), eq(10L), eq(1L)))
                .thenReturn(null);
        Map<String, Object> vars = new HashMap<>();
        IvrNodeContext ctx = context(node("{\"question\":\"你好\"}"), vars, request());

        executor.execute(ctx);

        assertThat(vars).containsEntry("matchedIntention", "");
        assertThat(ctx.getStep().getDetail()).contains("未命中");
    }

    @Test
    void rendersQuestionTemplate()
    {
        when(hotspotSuppressService.matchKeyword(eq("我想咨询劳动仲裁"), any(), any(), any()))
                .thenReturn(null);
        when(intentionRecognitionService.recognize(eq("我想咨询劳动仲裁"), isNull(), eq("IVR-T"), eq(10L), eq(1L)))
                .thenReturn(IntentionMatchResult.none("未命中"));
        Map<String, Object> vars = new HashMap<>();
        vars.put("topic", "劳动仲裁");
        IvrNodeContext ctx = context(node("{\"question\":\"我想咨询${topic}\"}"), vars, request());

        executor.execute(ctx);

        verify(intentionRecognitionService)
                .recognize(eq("我想咨询劳动仲裁"), isNull(), eq("IVR-T"), eq(10L), eq(1L));
    }

    @Test
    void fallsBackToInputQueueWhenQuestionMissing()
    {
        when(hotspotSuppressService.matchKeyword(any(), any(), any(), any()))
                .thenReturn(null);
        when(intentionRecognitionService.recognize(eq("队列输入"), isNull(), eq("IVR-T"), eq(10L), eq(1L)))
                .thenReturn(IntentionMatchResult.none("未命中"));
        Map<String, Object> vars = new HashMap<>();
        vars.put("lastInput", "上次输入");
        IvrNodeContext ctx = context(node("{}"), vars, request(), "队列输入");

        executor.execute(ctx);

        verify(intentionRecognitionService)
                .recognize(eq("队列输入"), isNull(), eq("IVR-T"), eq(10L), eq(1L));
    }

    @Test
    void fallsBackToLastInputWhenQueueEmpty()
    {
        when(hotspotSuppressService.matchKeyword(any(), any(), any(), any()))
                .thenReturn(null);
        when(intentionRecognitionService.recognize(eq("上次输入"), isNull(), eq("IVR-T"), eq(10L), eq(1L)))
                .thenReturn(IntentionMatchResult.none("未命中"));
        Map<String, Object> vars = new HashMap<>();
        vars.put("lastInput", "上次输入");
        IvrNodeContext ctx = context(node("{}"), vars, request());

        executor.execute(ctx);

        verify(intentionRecognitionService)
                .recognize(eq("上次输入"), isNull(), eq("IVR-T"), eq(10L), eq(1L));
    }

    @Test
    void rejectKeywordTerminatesFlow()
    {
        when(hotspotSuppressService.matchKeyword(any(), any(), any(), any()))
                .thenReturn(suppress("骚扰拦截", "REJECT", null));
        Map<String, Object> vars = new HashMap<>();
        IvrNodeContext ctx = context(node("{\"question\":\"推销\"}"), vars, request());

        AiIvrNode next = executor.execute(ctx);

        assertThat(next).isNull();
        assertThat(vars).containsEntry("__terminated", "hangup");
        assertThat(ctx.getStep().getAction()).isEqualTo("HANGUP");
        assertThat(ctx.getStep().getDetail()).contains("骚扰拦截").contains("拦截挂断");
        assertThat(ctx.getResult().getSteps()).hasSize(1);
        verify(intentionRecognitionService, never())
                .recognize(anyString(), any(), anyString(), anyLong(), anyLong());
    }

    @Test
    void priorityKeywordWritesDispatchPriorityAndContinues()
    {
        when(hotspotSuppressService.matchKeyword(any(), any(), any(), any()))
                .thenReturn(suppress("高频来电", "PRIORITY", -50));
        when(intentionRecognitionService.recognize(anyString(), isNull(), eq("IVR-T"), eq(10L), eq(1L)))
                .thenReturn(IntentionMatchResult.none("未命中"));
        Map<String, Object> vars = new HashMap<>();
        IvrNodeContext ctx = context(node("{\"question\":\"你好\"}"), vars, request());

        executor.execute(ctx);

        assertThat(vars).containsEntry("dispatchPriority", -50);
        assertThat(ctx.getStep().getAction()).isEqualTo("INTENTION");
        assertThat(vars).doesNotContainKey("__terminated");
    }

    @Test
    void priorityKeywordWithoutLevelUsesDefaultMinus100()
    {
        when(hotspotSuppressService.matchKeyword(any(), any(), any(), any()))
                .thenReturn(suppress("高频来电", "PRIORITY", null));
        when(intentionRecognitionService.recognize(anyString(), isNull(), eq("IVR-T"), eq(10L), eq(1L)))
                .thenReturn(IntentionMatchResult.none("未命中"));
        Map<String, Object> vars = new HashMap<>();
        IvrNodeContext ctx = context(node("{\"question\":\"你好\"}"), vars, request());

        executor.execute(ctx);

        assertThat(vars).containsEntry("dispatchPriority", -100);
    }

    @Test
    void hotspotDisabledSkipsSuppressCheck()
    {
        executor = new IntentionNodeExecutor(new IvrTemplateRenderer(),
                intentionRecognitionService, hotspotSuppressService, false);
        when(intentionRecognitionService.recognize(anyString(), isNull(), eq("IVR-T"), eq(10L), eq(1L)))
                .thenReturn(IntentionMatchResult.none("未命中"));
        Map<String, Object> vars = new HashMap<>();
        IvrNodeContext ctx = context(node("{\"question\":\"你好\"}"), vars, request());

        executor.execute(ctx);

        verify(hotspotSuppressService, never())
                .matchKeyword(any(), any(), any(), any());
    }

    @Test
    void outboundCallUsesCalleeNumberDirection()
    {
        when(hotspotSuppressService.matchKeyword(any(), any(), any(), any()))
                .thenReturn(null);
        when(intentionRecognitionService.recognize(anyString(), isNull(), eq("IVR-T"), eq(10L), eq(1L)))
                .thenReturn(IntentionMatchResult.none("未命中"));
        IvrExecuteRequest request = request();
        request.setCallerNumber("10086");
        request.setCalleeNumber("13800000000");
        Map<String, Object> vars = new HashMap<>();
        IvrNodeContext ctx = context(node("{\"question\":\"你好\"}"), vars, request);

        executor.execute(ctx);

        verify(hotspotSuppressService).matchKeyword("你好", "10086", "IVR-T", "OUTBOUND");
    }

    @Test
    void inboundCallUsesInboundDirection()
    {
        when(hotspotSuppressService.matchKeyword(any(), any(), any(), any()))
                .thenReturn(null);
        when(intentionRecognitionService.recognize(anyString(), isNull(), eq("IVR-T"), eq(10L), eq(1L)))
                .thenReturn(IntentionMatchResult.none("未命中"));
        IvrExecuteRequest request = request();
        request.setCallerNumber("13800000000");
        Map<String, Object> vars = new HashMap<>();
        IvrNodeContext ctx = context(node("{\"question\":\"你好\"}"), vars, request);

        executor.execute(ctx);

        verify(hotspotSuppressService).matchKeyword("你好", "13800000000", "IVR-T", "INBOUND");
    }

    @Test
    void emptyTextSkipsSuppressCheckButStillRecognizes()
    {
        when(intentionRecognitionService.recognize(eq(""), isNull(), eq("IVR-T"), eq(10L), eq(1L)))
                .thenReturn(IntentionMatchResult.none("未命中"));
        Map<String, Object> vars = new HashMap<>();
        IvrNodeContext ctx = context(node("{}"), vars, request());

        executor.execute(ctx);

        verify(hotspotSuppressService, never())
                .matchKeyword(any(), any(), any(), any());
        verify(intentionRecognitionService)
                .recognize(eq(""), isNull(), eq("IVR-T"), eq(10L), eq(1L));
    }

    @Test
    void matchedWithoutCategoryDoesNotWriteCategoryVariables()
    {
        when(hotspotSuppressService.matchKeyword(any(), any(), any(), any()))
                .thenReturn(null);
        when(intentionRecognitionService.recognize(anyString(), isNull(), eq("IVR-T"), eq(10L), eq(1L)))
                .thenReturn(match("OTHER", "其他", "AI", null, null));
        Map<String, Object> vars = new HashMap<>();
        IvrNodeContext ctx = context(node("{\"question\":\"你好\"}"), vars, request());

        executor.execute(ctx);

        assertThat(vars).containsEntry("matchedIntention", "OTHER");
        assertThat(vars).doesNotContainKey("matchedCategoryId")
                .doesNotContainKey("matchedCategoryName");
    }

    private IvrExecuteRequest request()
    {
        IvrExecuteRequest request = new IvrExecuteRequest();
        request.setRecordId(null);
        return request;
    }

    private IvrNodeContext context(AiIvrNode node, Map<String, Object> vars,
                                   IvrExecuteRequest request, String... inputs)
    {
        Deque<String> queue = new ArrayDeque<>();
        for (String input : inputs)
        {
            queue.add(input);
        }
        AiIvrFlow flow = new AiIvrFlow();
        flow.setFlowId(10L);
        IvrNodeContext ctx = new IvrNodeContext(new HashMap<>(), Collections.emptyList(), vars, queue,
                request, "IVR-T", flow, new IvrExecuteResult(), r -> null);
        ctx.setCurrent(node);
        ctx.setStep(new IvrNodeStep());
        return ctx;
    }

    private AiIvrNode node(String config)
    {
        AiIvrNode node = new AiIvrNode();
        node.setNodeId(1L);
        node.setFlowId(10L);
        node.setNodeType("intention");
        node.setNodeName("意图识别");
        node.setNodeConfig(config);
        return node;
    }

    private IntentionMatchResult match(String code, String name, String method,
                                       Long categoryId, String categoryName)
    {
        IntentionMatchResult result = new IntentionMatchResult();
        result.setMatched(true);
        result.setIntentionCode(code);
        result.setIntentionName(name);
        result.setMatchMethod(method);
        result.setCategoryId(categoryId);
        result.setCategoryName(categoryName);
        return result;
    }

    private AiHotspotSuppress suppress(String ruleName, String action, Integer priority)
    {
        AiHotspotSuppress suppress = new AiHotspotSuppress();
        suppress.setRuleName(ruleName);
        suppress.setAction(action);
        suppress.setPriorityLevel(priority);
        return suppress;
    }
}
