package ai.lawyers.system.service.impl.lawyers.ivr.engine.node;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayDeque;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import ai.lawyers.system.domain.lawyers.ivr.AiIvrEdge;
import ai.lawyers.system.domain.lawyers.ivr.AiIvrFlow;
import ai.lawyers.system.domain.lawyers.ivr.AiIvrNode;
import ai.lawyers.system.domain.lawyers.ivr.IvrExecuteRequest;
import ai.lawyers.system.domain.lawyers.ivr.IvrExecuteResult;
import ai.lawyers.system.domain.lawyers.ivr.IvrNodeStep;
import ai.lawyers.system.domain.lawyers.sms.SmsResult;
import ai.lawyers.system.service.impl.lawyers.ivr.engine.IvrTemplateRenderer;
import ai.lawyers.system.service.lawyers.sms.ISmsService;

/**
 * sms 短信通知节点单测：模板校验、收件号码来源、参数渲染、
 * 成功/失败/异常结果写入与流程不中断。
 *
 * @author ai-lawyers
 */
@ExtendWith(MockitoExtension.class)
class SmsNodeExecutorTest
{
    @Mock
    private ISmsService smsService;

    private SmsNodeExecutor executor;

    @BeforeEach
    void setUp()
    {
        executor = new SmsNodeExecutor(new IvrTemplateRenderer(), smsService);
    }

    @Test
    void missingTemplateIdFailsWithoutSending()
    {
        Map<String, Object> vars = new HashMap<>();
        IvrNodeContext ctx = context(node("{}"), vars, request("13800000000"));

        AiIvrNode next = executor.execute(ctx);

        assertThat(vars).containsEntry("smsStatus", "fail")
                .containsEntry("smsError", "未配置短信模板");
        assertThat(ctx.getStep().getAction()).isEqualTo("SMS");
        assertThat(ctx.getStep().getDetail()).contains("未配置templateId");
        assertThat(next).isNull();
        verify(smsService, never()).send(anyString(), any(), anyMap(), any(), any());
    }

    @Test
    void sendsToCallerNumberVariableWithRenderedParams()
    {
        when(smsService.send(eq("13800000000"), eq(5L), anyMap(), eq("IVR-T"), isNull()))
                .thenReturn(SmsResult.success("msg-1", "【律所】您好张三"));
        Map<String, Object> vars = new HashMap<>();
        vars.put("callerNumber", "13800000000");
        vars.put("name", "张三");
        IvrNodeContext ctx = context(node("{\"templateId\":5,"
                + "\"params\":{\"name\":\"${name}\",\"code\":\"A1\"}}"), vars, request(null));

        executor.execute(ctx);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, String>> paramsCaptor = ArgumentCaptor.forClass(Map.class);
        verify(smsService).send(eq("13800000000"), eq(5L), paramsCaptor.capture(), eq("IVR-T"), isNull());
        assertThat(paramsCaptor.getValue()).containsEntry("name", "张三").containsEntry("code", "A1");
        assertThat(vars).containsEntry("smsStatus", "success")
                .containsEntry("smsMsgId", "msg-1")
                .containsEntry("smsContent", "【律所】您好张三")
                .containsEntry("smsError", "");
        assertThat(ctx.getStep().getDetail()).contains("发送短信至 13800000000").contains("成功（msg-1）");
    }

    @Test
    void customPhoneVarResolvedFromVariables()
    {
        when(smsService.send(eq("13900000000"), eq(5L), anyMap(), eq("IVR-T"), isNull()))
                .thenReturn(SmsResult.success("m", "c"));
        Map<String, Object> vars = new HashMap<>();
        vars.put("phone", "13900000000");
        IvrNodeContext ctx = context(node("{\"templateId\":5,\"phoneVar\":\"phone\"}"), vars, request(null));

        executor.execute(ctx);

        verify(smsService).send(eq("13900000000"), eq(5L), anyMap(), eq("IVR-T"), isNull());
    }

    @Test
    void unresolvedPhoneVarTreatedAsLiteralNumber()
    {
        when(smsService.send(eq("13911112222"), eq(5L), anyMap(), eq("IVR-T"), isNull()))
                .thenReturn(SmsResult.success("m", "c"));
        Map<String, Object> vars = new HashMap<>();
        IvrNodeContext ctx = context(node("{\"templateId\":5,\"phoneVar\":\"13911112222\"}"), vars, request(null));

        executor.execute(ctx);

        verify(smsService).send(eq("13911112222"), eq(5L), anyMap(), eq("IVR-T"), isNull());
    }

    @Test
    void emptyPhoneFallsBackToRequestCallerNumber()
    {
        when(smsService.send(eq("13700000000"), eq(5L), anyMap(), eq("IVR-T"), isNull()))
                .thenReturn(SmsResult.success("m", "c"));
        Map<String, Object> vars = new HashMap<>();
        vars.put("phone", "");
        IvrNodeContext ctx = context(node("{\"templateId\":5,\"phoneVar\":\"phone\"}"), vars,
                request("13700000000"));

        executor.execute(ctx);

        verify(smsService).send(eq("13700000000"), eq(5L), anyMap(), eq("IVR-T"), isNull());
    }

    @Test
    void providerFailureRecorded()
    {
        when(smsService.send(anyString(), eq(5L), anyMap(), anyString(), any()))
                .thenReturn(SmsResult.fail("余额不足"));
        Map<String, Object> vars = new HashMap<>();
        IvrNodeContext ctx = context(node("{\"templateId\":5}"), vars, request("13800000000"));

        executor.execute(ctx);

        assertThat(vars).containsEntry("smsStatus", "fail").containsEntry("smsError", "余额不足");
        assertThat(ctx.getStep().getDetail()).contains("失败（余额不足）");
    }

    @Test
    void sendExceptionRecordedWithoutBreakingFlow()
    {
        when(smsService.send(anyString(), eq(5L), anyMap(), anyString(), any()))
                .thenThrow(new RuntimeException("网关异常"));
        Map<String, Object> vars = new HashMap<>();
        IvrNodeContext ctx = context(node("{\"templateId\":5}"), vars, request("13800000000"));

        AiIvrNode next = executor.execute(ctx);

        assertThat(vars).containsEntry("smsStatus", "fail").containsEntry("smsError", "网关异常");
        assertThat(ctx.getStep().getDetail()).contains("短信发送异常：网关异常");
        assertThat(next).isNull();
    }

    @Test
    void textualParamsParsedAsJson()
    {
        when(smsService.send(anyString(), eq(5L), anyMap(), anyString(), any()))
                .thenReturn(SmsResult.success("m", "c"));
        Map<String, Object> vars = new HashMap<>();
        vars.put("name", "李四");
        IvrNodeContext ctx = context(node("{\"templateId\":5,"
                + "\"params\":\"{\\\"name\\\":\\\"${name}\\\"}\"}"), vars, request("13800000000"));

        executor.execute(ctx);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, String>> paramsCaptor = ArgumentCaptor.forClass(Map.class);
        verify(smsService).send(anyString(), eq(5L), paramsCaptor.capture(), anyString(), any());
        assertThat(paramsCaptor.getValue()).containsEntry("name", "李四");
    }

    @Test
    void invalidTextualParamsIgnored()
    {
        when(smsService.send(anyString(), eq(5L), anyMap(), anyString(), any()))
                .thenReturn(SmsResult.success("m", "c"));
        Map<String, Object> vars = new HashMap<>();
        IvrNodeContext ctx = context(node("{\"templateId\":5,\"params\":\"not-json\"}"), vars,
                request("13800000000"));

        executor.execute(ctx);

        verify(smsService).send(anyString(), eq(5L), eq(new HashMap<>()), anyString(), any());
    }

    @Test
    void continuesToNextNodeByDefaultEdge()
    {
        when(smsService.send(anyString(), eq(5L), anyMap(), anyString(), any()))
                .thenReturn(SmsResult.success("m", "c"));
        Map<Long, AiIvrNode> nodeMap = new HashMap<>();
        nodeMap.put(2L, node("{}", 2L));
        AiIvrEdge edge = new AiIvrEdge();
        edge.setEdgeId(1L);
        edge.setSourceNodeId(1L);
        edge.setTargetNodeId(2L);
        edge.setSortOrder(1);
        Map<String, Object> vars = new HashMap<>();
        IvrNodeContext ctx = context(node("{\"templateId\":5}"), vars, request("13800000000"),
                Collections.singletonList(edge), nodeMap);

        AiIvrNode next = executor.execute(ctx);

        assertThat(next.getNodeId()).isEqualTo(2L);
    }

    private IvrExecuteRequest request(String callerNumber)
    {
        IvrExecuteRequest request = new IvrExecuteRequest();
        request.setCallerNumber(callerNumber);
        return request;
    }

    private IvrNodeContext context(AiIvrNode node, Map<String, Object> vars, IvrExecuteRequest request)
    {
        return context(node, vars, request, Collections.emptyList(), new HashMap<>());
    }

    private IvrNodeContext context(AiIvrNode node, Map<String, Object> vars, IvrExecuteRequest request,
                                   List<AiIvrEdge> edges, Map<Long, AiIvrNode> nodeMap)
    {
        IvrNodeContext ctx = new IvrNodeContext(nodeMap, edges, vars, new ArrayDeque<>(),
                request, "IVR-T", new AiIvrFlow(), new IvrExecuteResult(), r -> null);
        ctx.setCurrent(node);
        ctx.setStep(new IvrNodeStep());
        return ctx;
    }

    private AiIvrNode node(String config)
    {
        return node(config, 1L);
    }

    private AiIvrNode node(String config, Long nodeId)
    {
        AiIvrNode node = new AiIvrNode();
        node.setNodeId(nodeId);
        node.setFlowId(10L);
        node.setNodeType("sms");
        node.setNodeName("短信通知");
        node.setNodeConfig(config);
        return node;
    }
}
