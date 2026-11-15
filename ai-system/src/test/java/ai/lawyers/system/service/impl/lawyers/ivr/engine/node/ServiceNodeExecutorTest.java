package ai.lawyers.system.service.impl.lawyers.ivr.engine.node;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
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
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import ai.lawyers.system.domain.lawyers.ivr.AiIvrFlow;
import ai.lawyers.system.domain.lawyers.ivr.AiIvrNode;
import ai.lawyers.system.domain.lawyers.ivr.IvrExecuteRequest;
import ai.lawyers.system.domain.lawyers.ivr.IvrExecuteResult;
import ai.lawyers.system.domain.lawyers.ivr.IvrNodeStep;
import ai.lawyers.system.service.impl.lawyers.ivr.engine.IvrHttpClient;
import ai.lawyers.system.service.impl.lawyers.ivr.engine.IvrTemplateRenderer;

/**
 * service HTTP 调用节点单测：url 缺失、SSRF 拦截、成功响应/取值路径、
 * 请求方法归一化、模板化 headers/body 与调用失败降级。
 *
 * @author ai-lawyers
 */
@ExtendWith(MockitoExtension.class)
class ServiceNodeExecutorTest
{
    private static final String URL = "https://api.example.com/x";

    @Mock
    private IvrHttpClient httpClient;

    private ServiceNodeExecutor executor;

    @BeforeEach
    void setUp()
    {
        executor = new ServiceNodeExecutor(new IvrTemplateRenderer(), httpClient);
    }

    @Test
    void missingUrlStopsBeforeCall() throws Exception
    {
        Map<String, Object> vars = new HashMap<>();
        IvrNodeContext ctx = context(node("{}"), vars);

        AiIvrNode next = executor.execute(ctx);

        assertThat(vars).containsEntry("serviceError", "未配置请求地址");
        assertThat(ctx.getStep().getAction()).isEqualTo("SERVICE");
        assertThat(ctx.getStep().getDetail()).contains("未配置url");
        assertThat(next).isNull();
        verify(httpClient, never()).checkUrlAllowed(anyString());
    }

    @Test
    void ssrfBlockedRecordsError() throws Exception
    {
        when(httpClient.checkUrlAllowed("http://127.0.0.1/admin"))
                .thenReturn("禁止访问内网/回环/保留地址：127.0.0.1");
        Map<String, Object> vars = new HashMap<>();
        IvrNodeContext ctx = context(node("{\"url\":\"http://127.0.0.1/admin\"}"), vars);

        executor.execute(ctx);

        assertThat(vars).containsEntry("serviceError", "禁止访问内网/回环/保留地址：127.0.0.1");
        assertThat(ctx.getStep().getDetail()).contains("被安全策略拦截");
        verify(httpClient, never()).httpCall(anyString(), anyString(), anyMap(), any(), anyInt());
    }

    @Test
    void successDefaultGetWritesResultVar() throws Exception
    {
        when(httpClient.checkUrlAllowed(URL)).thenReturn(null);
        when(httpClient.httpCall(URL, "GET", new HashMap<>(), null, 10000))
                .thenReturn("{\"ok\":true}");
        Map<String, Object> vars = new HashMap<>();
        IvrNodeContext ctx = context(node("{\"url\":\"" + URL + "\"}"), vars);

        executor.execute(ctx);

        assertThat(vars).containsEntry("serviceResult", "{\"ok\":true}");
        assertThat(ctx.getStep().getDetail()).contains("HTTP调用成功：GET " + URL)
                .contains("响应11字节");
    }

    @Test
    void resultPathExtractsServiceValue() throws Exception
    {
        when(httpClient.checkUrlAllowed(URL)).thenReturn(null);
        when(httpClient.httpCall(URL, "GET", new HashMap<>(), null, 10000)).thenReturn("{\"data\":{\"id\":42}}");
        when(httpClient.jsonPath("{\"data\":{\"id\":42}}", "data.id")).thenReturn("42");
        Map<String, Object> vars = new HashMap<>();
        IvrNodeContext ctx = context(node("{\"url\":\"" + URL + "\",\"result\":\"data.id\"}"), vars);

        executor.execute(ctx);

        assertThat(vars).containsEntry("serviceValue", "42");
        assertThat(ctx.getStep().getDetail()).contains("取值路径=data.id");
    }

    @Test
    void missingResultPathValueBecomesEmptyString() throws Exception
    {
        when(httpClient.checkUrlAllowed(URL)).thenReturn(null);
        when(httpClient.httpCall(URL, "GET", new HashMap<>(), null, 10000)).thenReturn("{}");
        when(httpClient.jsonPath("{}", "missing")).thenReturn(null);
        Map<String, Object> vars = new HashMap<>();
        IvrNodeContext ctx = context(node("{\"url\":\"" + URL + "\",\"result\":\"missing\"}"), vars);

        executor.execute(ctx);

        assertThat(vars).containsEntry("serviceValue", "");
    }

    @Test
    void httpFailureRecordsError() throws Exception
    {
        when(httpClient.checkUrlAllowed(URL)).thenReturn(null);
        when(httpClient.httpCall(URL, "GET", new HashMap<>(), null, 10000))
                .thenThrow(new IllegalStateException("HTTP 500：服务器错误"));
        Map<String, Object> vars = new HashMap<>();
        IvrNodeContext ctx = context(node("{\"url\":\"" + URL + "\"}"), vars);

        AiIvrNode next = executor.execute(ctx);

        assertThat(vars).containsEntry("serviceError", "HTTP 500：服务器错误");
        assertThat(ctx.getStep().getDetail()).contains("HTTP调用失败").contains("HTTP 500");
        assertThat(next).isNull();
    }

    @Test
    void postRendersBodyAndHeaders() throws Exception
    {
        String url = "https://api.example.com/post";
        when(httpClient.checkUrlAllowed(url)).thenReturn(null);
        when(httpClient.httpCall(eq(url), eq("POST"), anyMap(), eq("{\"a\":\"值\"}"), eq(3000)))
                .thenReturn("ok");
        Map<String, Object> vars = new HashMap<>();
        vars.put("v", "值");
        vars.put("token", "tok");
        IvrNodeContext ctx = context(node("{\"url\":\"" + url + "\",\"method\":\"post\","
                + "\"timeoutMs\":3000,\"headers\":{\"X-Token\":\"${token}\"},"
                + "\"body\":{\"a\":\"${v}\"}}"), vars);

        executor.execute(ctx);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, String>> headersCaptor = ArgumentCaptor.forClass(Map.class);
        verify(httpClient).httpCall(eq(url), eq("POST"), headersCaptor.capture(),
                eq("{\"a\":\"值\"}"), eq(3000));
        assertThat(headersCaptor.getValue()).containsEntry("X-Token", "tok");
    }

    @Test
    void textualHeadersParsedAsJson() throws Exception
    {
        when(httpClient.checkUrlAllowed(URL)).thenReturn(null);
        when(httpClient.httpCall(eq(URL), eq("GET"), anyMap(), isNull(), eq(10000))).thenReturn("ok");
        Map<String, Object> vars = new HashMap<>();
        vars.put("token", "tok");
        IvrNodeContext ctx = context(node("{\"url\":\"" + URL + "\","
                + "\"headers\":\"{\\\"A\\\":\\\"${token}\\\"}\"}"), vars);

        executor.execute(ctx);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, String>> headersCaptor = ArgumentCaptor.forClass(Map.class);
        verify(httpClient).httpCall(eq(URL), eq("GET"), headersCaptor.capture(), isNull(), eq(10000));
        assertThat(headersCaptor.getValue()).containsEntry("A", "tok");
    }

    @Test
    void invalidTextualHeadersIgnored() throws Exception
    {
        when(httpClient.checkUrlAllowed(URL)).thenReturn(null);
        when(httpClient.httpCall(eq(URL), eq("GET"), anyMap(), isNull(), eq(10000))).thenReturn("ok");
        Map<String, Object> vars = new HashMap<>();
        IvrNodeContext ctx = context(node("{\"url\":\"" + URL + "\",\"headers\":\"not-json\"}"), vars);

        executor.execute(ctx);

        verify(httpClient).httpCall(eq(URL), eq("GET"), eq(new HashMap<>()), isNull(), eq(10000));
    }

    @Test
    void customResultVarUsed() throws Exception
    {
        when(httpClient.checkUrlAllowed(URL)).thenReturn(null);
        when(httpClient.httpCall(URL, "GET", new HashMap<>(), null, 10000)).thenReturn("data");
        Map<String, Object> vars = new HashMap<>();
        IvrNodeContext ctx = context(node("{\"url\":\"" + URL + "\",\"resultVar\":\"httpBody\"}"), vars);

        executor.execute(ctx);

        assertThat(vars).containsEntry("httpBody", "data")
                .doesNotContainKey("serviceResult");
    }

    @Test
    void emptyMethodDefaultsToGet() throws Exception
    {
        when(httpClient.checkUrlAllowed(URL)).thenReturn(null);
        when(httpClient.httpCall(URL, "GET", new HashMap<>(), null, 10000)).thenReturn("ok");
        Map<String, Object> vars = new HashMap<>();
        IvrNodeContext ctx = context(node("{\"url\":\"" + URL + "\",\"method\":\"\"}"), vars);

        executor.execute(ctx);

        verify(httpClient).httpCall(URL, "GET", new HashMap<>(), null, 10000);
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
        node.setNodeType("service");
        node.setNodeName("HTTP调用");
        node.setNodeConfig(config);
        return node;
    }
}
