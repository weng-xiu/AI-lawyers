package ai.lawyers.common.utils.http;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * HTTP 工具测试：借助 JDK 内置 HttpServer 验证 GET/POST 请求收发，
 * 以及非法地址时的异常兜底行为。
 *
 * @author ai-lawyers
 */
class HttpUtilsTest
{
    private HttpServer server;

    private String baseUrl;

    @BeforeEach
    void setUp() throws IOException
    {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/get", exchange -> respond(exchange, "hello"));
        server.createContext("/post", exchange -> {
            String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            respond(exchange, "echo:" + body);
        });
        server.start();
        baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
    }

    @AfterEach
    void tearDown()
    {
        if (server != null)
        {
            server.stop(0);
        }
    }

    @Test
    void sendGet_无参请求返回响应体()
    {
        assertEquals("hello", HttpUtils.sendGet(baseUrl + "/get"));
    }

    @Test
    void sendGet_携带参数与编码返回响应体()
    {
        assertEquals("hello", HttpUtils.sendGet(baseUrl + "/get", "a=1&b=2"));
        assertEquals("hello", HttpUtils.sendGet(baseUrl + "/get", "a=1", "GBK"));
    }

    @Test
    void sendPost_默认表单与自定义内容类型()
    {
        assertEquals("echo:a=1&b=2", HttpUtils.sendPost(baseUrl + "/post", "a=1&b=2"));
        assertEquals("echo:{\"x\":1}", HttpUtils.sendPost(baseUrl + "/post", "{\"x\":1}", "application/json"));
    }

    @Test
    void 非法地址_异常被吞掉返回空串()
    {
        assertEquals("", HttpUtils.sendGet("://bad url"));
        assertEquals("", HttpUtils.sendPost("://bad url", "a=1"));
        // http 地址强转 HttpsURLConnection 失败，同样返回空串
        assertEquals("", HttpUtils.sendSSLPost(baseUrl + "/post", "a=1"));
    }

    /** 输出指定文本并关闭交换 */
    private static void respond(HttpExchange exchange, String text) throws IOException
    {
        byte[] body = text.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(200, body.length);
        try (OutputStream os = exchange.getResponseBody())
        {
            os.write(body);
        }
    }
}
