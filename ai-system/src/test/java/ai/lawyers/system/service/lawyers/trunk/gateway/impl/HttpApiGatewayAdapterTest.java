package ai.lawyers.system.service.lawyers.trunk.gateway.impl;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import ai.lawyers.system.domain.lawyers.trunk.AiCallTrunk;
import ai.lawyers.system.domain.lawyers.trunk.DialRequest;
import ai.lawyers.system.domain.lawyers.trunk.DialResult;
import ai.lawyers.system.enums.DialStatusEnum;
import ai.lawyers.system.service.lawyers.trunk.gateway.GatewayHealth;

/**
 * {@link HttpApiGatewayAdapter} 通用 HTTP 网关适配器测试。
 *
 * <p>用 JDK 自带 HttpServer 模拟 OpenAPI 端点（零新依赖），覆盖：外呼成功
 * （success/code=0/code=200 三种成功约定、网关回传 callId、请求体号码变换与
 * 可选参数）、失败（业务拒绝带 sipCode、非 JSON 响应、HTTP 5xx、连接异常）、
 * 挂断/转接、健康探测（注册状态覆盖与失败形态）、Basic/Bearer 鉴权及
 * apiBaseUrl 缺省与尾部斜杠处理。</p>
 *
 * @author ai-lawyers
 */
class HttpApiGatewayAdapterTest
{
    private HttpServer server;

    private int port;

    private final AtomicReference<String> lastMethod = new AtomicReference<>();

    private final AtomicReference<String> lastAuth = new AtomicReference<>();

    private final AtomicReference<String> lastQuery = new AtomicReference<>();

    private final AtomicReference<String> lastBody = new AtomicReference<>();

    private HttpApiGatewayAdapter adapter;

    @BeforeEach
    void setUp() throws IOException
    {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        port = server.getAddress().getPort();
        context("/ok/originate", 200, "{\"success\":true,\"callId\":\"gw-1\"}");
        context("/code0/originate", 200, "{\"code\":0}");
        context("/code200/originate", 200, "{\"code\":200}");
        context("/reject/originate", 200,
                "{\"success\":false,\"message\":\"线路不可用\",\"sipCode\":503}");
        context("/plain/originate", 200, "not-json");
        context("/error/originate", 500, "boom");
        context("/ok/hangup", 200, "{\"code\":200}");
        context("/reject/hangup", 200, "{\"success\":false}");
        context("/ok/transfer", 200, "{\"success\":true}");
        context("/reject/transfer", 200, "{\"success\":false}");
        context("/ok/status", 200, "{\"success\":true,\"state\":\"NOREG\"}");
        context("/plain/status", 200, "{\"success\":true}");
        context("/reject/status", 200, "{\"success\":false,\"message\":\"offline\"}");
        context("/auth/originate", 200, "{\"success\":true}");
        server.start();

        adapter = new HttpApiGatewayAdapter();
        ReflectionTestUtils.setField(adapter, "originatePath", "/ok/originate");
        ReflectionTestUtils.setField(adapter, "hangupPath", "/ok/hangup");
        ReflectionTestUtils.setField(adapter, "transferPath", "/ok/transfer");
        ReflectionTestUtils.setField(adapter, "statusPath", "/ok/status");
        ReflectionTestUtils.setField(adapter, "timeout", 1500);
    }

    @AfterEach
    void tearDown()
    {
        if (server != null)
        {
            server.stop(0);
        }
    }

    // ---------- 基础信息 ----------

    @Test
    void vendorIsHttpApi()
    {
        assertThat(adapter.getVendor()).isEqualTo("HTTP_API");
    }

    // ---------- 外呼 ----------

    @Test
    void originateSuccessUsesGatewayCallId()
    {
        DialRequest request = new DialRequest("13800138000", null);
        request.setCallerNumber("13900139000");
        request.setRingTimeout(30);
        request.setEnableRecord(false);
        request.setAnswerAction("IVR");
        request.setAgentExtension("8001");
        request.setIvrFlowId(12L);
        request.setRecordId(21L);

        DialResult result = adapter.originate(trunk(), request);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getCallUuid()).isEqualTo("gw-1");
        assertThat(result.getDialStatus()).isEqualTo(DialStatusEnum.DIALING.getCode());
        assertThat(lastMethod.get()).isEqualTo("POST");
        assertThat(lastBody.get())
                .contains("\"trunkCode\":\"TRUNK-A\"")
                .contains("\"callee\":\"13800138000\"")
                .contains("\"caller\":\"13900139000\"")
                .contains("\"timeout\":30")
                .contains("\"record\":false")
                .contains("\"answerAction\":\"IVR\"")
                .contains("\"agentExtension\":\"8001\"")
                .contains("\"ivrFlowId\":12")
                .contains("\"recordId\":21");
    }

    @Test
    void originateAcceptsCodeZeroAndTwoHundred()
    {
        ReflectionTestUtils.setField(adapter, "originatePath", "/code0/originate");
        DialResult zero = adapter.originate(trunk(), new DialRequest("13800138000", null));
        assertThat(zero.isSuccess()).isTrue();
        assertThat(zero.getCallUuid()).hasSize(36);

        ReflectionTestUtils.setField(adapter, "originatePath", "/code200/originate");
        DialResult twoHundred = adapter.originate(trunk(), new DialRequest("13800138000", null));
        assertThat(twoHundred.isSuccess()).isTrue();
        assertThat(twoHundred.getCallUuid()).hasSize(36);
    }

    @Test
    void originateUsesDefaultsAndOmitsOptionalFields()
    {
        DialRequest request = new DialRequest("13800138000", null);

        DialResult result = adapter.originate(trunk(), request);

        assertThat(result.isSuccess()).isTrue();
        assertThat(lastBody.get())
                .contains("\"timeout\":45")
                .contains("\"record\":true")
                .contains("\"answerAction\":\"BRIDGE_AGENT\"")
                .contains("\"caller\":\"057100000000\"")
                .doesNotContain("agentExtension")
                .doesNotContain("ivrFlowId")
                .doesNotContain("recordId");
    }

    @Test
    void originateAppliesTrunkNumberTransform()
    {
        AiCallTrunk trunk = trunk();
        trunk.setStripDigits(1);
        trunk.setAddPrefix("9");
        trunk.setCallerPrefix("0");

        adapter.originate(trunk, new DialRequest("13800138000", null));

        assertThat(lastBody.get()).contains("\"callee\":\"093800138000\"");
    }

    @Test
    void originateReportsGatewayRejection()
    {
        ReflectionTestUtils.setField(adapter, "originatePath", "/reject/originate");

        DialResult result = adapter.originate(trunk(), new DialRequest("13800138000", null));

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getErrorCode()).isEqualTo("GATEWAY_REJECT");
        assertThat(result.getMessage()).isEqualTo("线路不可用");
        assertThat(result.getSipCode()).isEqualTo(503);
        assertThat(result.getDialStatus()).isEqualTo(DialStatusEnum.FAILED.getCode());
    }

    @Test
    void originateFallsBackToRawBodyWhenNotJson()
    {
        ReflectionTestUtils.setField(adapter, "originatePath", "/plain/originate");

        DialResult result = adapter.originate(trunk(), new DialRequest("13800138000", null));

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getErrorCode()).isEqualTo("GATEWAY_REJECT");
        assertThat(result.getMessage()).isEqualTo("not-json");
        assertThat(result.getSipCode()).isNull();
    }

    @Test
    void originateReadsErrorStreamOnHttpError()
    {
        ReflectionTestUtils.setField(adapter, "originatePath", "/error/originate");

        DialResult result = adapter.originate(trunk(), new DialRequest("13800138000", null));

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getErrorCode()).isEqualTo("GATEWAY_REJECT");
        assertThat(result.getMessage()).isEqualTo("boom");
    }

    @Test
    void originateWrapsConnectionFailure()
    {
        AiCallTrunk trunk = trunk();
        trunk.setApiBaseUrl("http://127.0.0.1:1");
        ReflectionTestUtils.setField(adapter, "timeout", 500);

        DialResult result = adapter.originate(trunk, new DialRequest("13800138000", null));

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getErrorCode()).isEqualTo("GATEWAY_ERROR");
        assertThat(result.getDialStatus()).isEqualTo(DialStatusEnum.FAILED.getCode());
    }

    @Test
    void originateBuildsBaseUrlFromGatewayHostWhenApiBaseUrlMissing()
    {
        AiCallTrunk trunk = trunk();
        trunk.setApiBaseUrl(null);
        trunk.setGatewayHost("127.0.0.1");
        trunk.setGatewayPort(port);

        DialResult result = adapter.originate(trunk, new DialRequest("13800138000", null));

        assertThat(result.isSuccess()).isTrue();
    }

    @Test
    void originateTrimsTrailingSlashInBaseUrl()
    {
        AiCallTrunk trunk = trunk();
        trunk.setApiBaseUrl("http://127.0.0.1:" + port + "/");

        DialResult result = adapter.originate(trunk, new DialRequest("13800138000", null));

        assertThat(result.isSuccess()).isTrue();
    }

    // ---------- 挂断与转接 ----------

    @Test
    void hangupReturnsGatewayResult()
    {
        assertThat(adapter.hangup(trunk(), "gw-1")).isTrue();
        assertThat(lastMethod.get()).isEqualTo("POST");
        assertThat(lastBody.get()).contains("\"callId\":\"gw-1\"");

        ReflectionTestUtils.setField(adapter, "hangupPath", "/reject/hangup");
        assertThat(adapter.hangup(trunk(), "gw-1")).isFalse();
    }

    @Test
    void hangupReturnsFalseOnConnectionFailure()
    {
        AiCallTrunk trunk = trunk();
        trunk.setApiBaseUrl("http://127.0.0.1:1");
        ReflectionTestUtils.setField(adapter, "timeout", 500);

        assertThat(adapter.hangup(trunk, "gw-1")).isFalse();
    }

    @Test
    void bridgeToAgentReturnsGatewayResult()
    {
        assertThat(adapter.bridgeToAgent(trunk(), "gw-1", "8001")).isTrue();
        assertThat(lastBody.get())
                .contains("\"callId\":\"gw-1\"")
                .contains("\"target\":\"8001\"");

        ReflectionTestUtils.setField(adapter, "transferPath", "/reject/transfer");
        assertThat(adapter.bridgeToAgent(trunk(), "gw-1", "8001")).isFalse();
    }

    @Test
    void bridgeToAgentReturnsFalseOnConnectionFailure()
    {
        AiCallTrunk trunk = trunk();
        trunk.setApiBaseUrl("http://127.0.0.1:1");
        ReflectionTestUtils.setField(adapter, "timeout", 500);

        assertThat(adapter.bridgeToAgent(trunk, "gw-1", "8001")).isFalse();
    }

    // ---------- 健康探测 ----------

    @Test
    void checkHealthReturnsRegisterStateFromGateway()
    {
        GatewayHealth health = adapter.checkHealth(trunk());

        assertThat(health.isReachable()).isTrue();
        assertThat(health.getRegisterState()).isEqualTo("NOREG");
        assertThat(health.getLatencyMs()).isGreaterThanOrEqualTo(0L);
        assertThat(lastMethod.get()).isEqualTo("GET");
        assertThat(lastQuery.get()).isEqualTo("trunkCode=TRUNK-A");
    }

    @Test
    void checkHealthKeepsDefaultRegisterStateWhenAbsent()
    {
        ReflectionTestUtils.setField(adapter, "statusPath", "/plain/status");

        GatewayHealth health = adapter.checkHealth(trunk());

        assertThat(health.isReachable()).isTrue();
        assertThat(health.getRegisterState()).isEqualTo("REGED");
    }

    @Test
    void checkHealthReportsDownWhenGatewayRejects()
    {
        ReflectionTestUtils.setField(adapter, "statusPath", "/reject/status");

        GatewayHealth health = adapter.checkHealth(trunk());

        assertThat(health.isReachable()).isFalse();
        assertThat(health.getMessage()).startsWith("状态接口返回异常");
    }

    @Test
    void checkHealthReportsDownOnConnectionFailure()
    {
        AiCallTrunk trunk = trunk();
        trunk.setApiBaseUrl("http://127.0.0.1:1");
        ReflectionTestUtils.setField(adapter, "timeout", 500);

        GatewayHealth health = adapter.checkHealth(trunk);

        assertThat(health.isReachable()).isFalse();
        assertThat(health.getMessage()).isNotNull();
    }

    // ---------- 鉴权 ----------

    @Test
    void appliesBasicAndBearerAuth()
    {
        ReflectionTestUtils.setField(adapter, "originatePath", "/auth/originate");
        AiCallTrunk trunk = trunk();
        trunk.setAuthUser("u");
        trunk.setAuthPassword("p");

        adapter.originate(trunk, new DialRequest("13800138000", null));
        assertThat(lastAuth.get()).isEqualTo("Basic dTpw");

        trunk.setAuthUser(" ");
        trunk.setAuthPassword("token");
        adapter.originate(trunk, new DialRequest("13800138000", null));
        assertThat(lastAuth.get()).isEqualTo("Bearer token");

        trunk.setAuthPassword(null);
        adapter.originate(trunk, new DialRequest("13800138000", null));
        assertThat(lastAuth.get()).isNull();
    }

    private void context(String path, int status, String body)
    {
        server.createContext(path, exchange ->
        {
            lastMethod.set(exchange.getRequestMethod());
            lastAuth.set(exchange.getRequestHeaders().getFirst("Authorization"));
            lastQuery.set(exchange.getRequestURI().getQuery());
            lastBody.set(new String(readAll(exchange), StandardCharsets.UTF_8));
            byte[] resp = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json; charset=utf-8");
            exchange.sendResponseHeaders(status, resp.length == 0 ? -1 : resp.length);
            try (OutputStream os = exchange.getResponseBody())
            {
                os.write(resp);
            }
        });
    }

    private static byte[] readAll(HttpExchange exchange) throws IOException
    {
        try (InputStream in = exchange.getRequestBody(); ByteArrayOutputStream out = new ByteArrayOutputStream())
        {
            byte[] buf = new byte[1024];
            int len;
            while ((len = in.read(buf)) != -1)
            {
                out.write(buf, 0, len);
            }
            return out.toByteArray();
        }
    }

    private AiCallTrunk trunk()
    {
        AiCallTrunk trunk = new AiCallTrunk();
        trunk.setTrunkCode("TRUNK-A");
        trunk.setApiBaseUrl("http://127.0.0.1:" + port);
        trunk.setCallerDisplay("057100000000");
        return trunk;
    }
}
