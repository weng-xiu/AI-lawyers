package ai.lawyers.system.service.lawyers.rag;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

/**
 * P3-E1：rerank HTTP 联调测试——用 JDK 自带 HttpServer 模拟 rerank 端点，零新依赖。
 *
 * <p>覆盖：① 正常请求（请求体含 model/query/top_n/documents、Bearer 鉴权头透传、
 * 重排结果生效）；② 服务端 500 时 testRerank 返回 success=false 且带 HTTP 状态；
 * ③ 未启用/未配置 URL 时直接失败提示；④ 真实 search() 链路 rerank 失败退回 RRF 序
 * （由协议/异常路径保证，此处于 500 场景验证 testRerank 不抛异常）。</p>
 *
 * @author ai-lawyers
 */
class RagRerankHttpTest
{
    private HttpServer server;
    private int port;
    private final AtomicReference<String> lastAuthHeader = new AtomicReference<>();
    private final AtomicReference<String> lastRequestBody = new AtomicReference<>();

    @BeforeEach
    void setUp() throws IOException
    {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        port = server.getAddress().getPort();
        // /ok：Cohere 风格 results（劳动相关文档排前）
        server.createContext("/ok", exchange -> handle(exchange, 200,
                "{\"results\":["
                + "{\"index\":0,\"relevance_score\":0.91},"
                + "{\"index\":1,\"relevance_score\":0.05}]}"));
        // /err：500
        server.createContext("/err", exchange -> handle(exchange, 500, "{\"error\":\"internal\"}"));
        // /weird：HTTP 200 但协议不符（无 results）
        server.createContext("/weird", exchange -> handle(exchange, 200, "{\"hello\":\"world\"}"));
        server.start();
    }

    @AfterEach
    void tearDown()
    {
        if (server != null)
        {
            server.stop(0);
        }
    }

    private void handle(HttpExchange exchange, int status, String body) throws IOException
    {
        lastAuthHeader.set(exchange.getRequestHeaders().getFirst("Authorization"));
        byte[] req = readAll(exchange);
        lastRequestBody.set(new String(req, StandardCharsets.UTF_8));
        byte[] resp = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json; charset=utf-8");
        exchange.sendResponseHeaders(status, resp.length);
        try (OutputStream os = exchange.getResponseBody())
        {
            os.write(resp);
        }
    }

    private static byte[] readAll(HttpExchange exchange) throws IOException
    {
        java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream();
        byte[] buf = new byte[1024];
        int n;
        while ((n = exchange.getRequestBody().read(buf)) != -1)
        {
            bos.write(buf, 0, n);
        }
        return bos.toByteArray();
    }

    private RagSearchService newService(String url, String apiKey)
    {
        RagSearchService svc = new RagSearchService();
        svc.init();
        ReflectionTestUtils.setField(svc, "rerankEnabled", true);
        ReflectionTestUtils.setField(svc, "rerankUrl", url);
        ReflectionTestUtils.setField(svc, "rerankModel", "bge-reranker-base");
        ReflectionTestUtils.setField(svc, "rerankApiKey", apiKey == null ? "" : apiKey);
        ReflectionTestUtils.setField(svc, "topK", 3);
        ReflectionTestUtils.setField(svc, "chunkMaxLen", 500);
        return svc;
    }

    @Test
    @SuppressWarnings("unchecked")
    void testRerank_ok_requestContractAndRanking()
    {
        RagSearchService svc = newService("http://127.0.0.1:" + port + "/ok", "sk-test-123");

        Map<String, Object> result = svc.testRerank();

        assertThat(result.get("success")).isEqualTo(true);
        assertThat(result.get("httpStatus")).isEqualTo(200);
        assertThat((long) result.get("latencyMs")).isGreaterThanOrEqualTo(0L);
        // 重排结果：劳动文档第一，天气第二
        List<String> ranking = (List<String>) result.get("ranking");
        assertThat(ranking).hasSize(2);
        assertThat(ranking.get(0)).contains("0.9100").contains("劳动合同");
        // 请求体契约
        String req = lastRequestBody.get();
        assertThat(req).contains("\"model\":\"bge-reranker-base\"")
                .contains("\"query\":\"劳动合同必须签书面合同吗\"")
                .contains("\"top_n\":2")
                .contains("\"documents\":[");
        // 鉴权头透传
        assertThat(lastAuthHeader.get()).isEqualTo("Bearer sk-test-123");
    }

    @Test
    @SuppressWarnings("unchecked")
    void testRerank_noApiKey_noAuthHeader()
    {
        RagSearchService svc = newService("http://127.0.0.1:" + port + "/ok", "");

        Map<String, Object> result = svc.testRerank();

        assertThat(result.get("success")).isEqualTo(true);
        assertThat(lastAuthHeader.get()).isNull();
    }

    @Test
    void testRerank_http500_failureWithStatus()
    {
        RagSearchService svc = newService("http://127.0.0.1:" + port + "/err", null);

        Map<String, Object> result = svc.testRerank();

        assertThat(result.get("success")).isEqualTo(false);
        assertThat(result.get("httpStatus")).isEqualTo(500);
        assertThat(String.valueOf(result.get("error"))).contains("HTTP 500");
    }

    @Test
    void testRerank_unexpectedProtocol_failureWithRawHint()
    {
        RagSearchService svc = newService("http://127.0.0.1:" + port + "/weird", null);

        Map<String, Object> result = svc.testRerank();

        assertThat(result.get("success")).isEqualTo(false);
        assertThat(String.valueOf(result.get("error"))).contains("协议");
        assertThat(String.valueOf(result.get("raw"))).contains("hello");
    }

    @Test
    void testRerank_disabled_immediateFailure()
    {
        RagSearchService svc = new RagSearchService();
        ReflectionTestUtils.setField(svc, "rerankEnabled", false);
        ReflectionTestUtils.setField(svc, "rerankUrl", "");

        Map<String, Object> result = svc.testRerank();

        assertThat(result.get("success")).isEqualTo(false);
        assertThat(String.valueOf(result.get("error"))).contains("未启用");
    }

    @Test
    void testRerank_connectionRefused_failureNoThrow()
    {
        // 指向一个未监听端口：必须吞异常返回 failure，绝不向调用方抛
        RagSearchService svc = newService("http://127.0.0.1:1/rerank", null);

        Map<String, Object> result = svc.testRerank();

        assertThat(result.get("success")).isEqualTo(false);
        assertThat(String.valueOf(result.get("error"))).isNotBlank();
    }
}
