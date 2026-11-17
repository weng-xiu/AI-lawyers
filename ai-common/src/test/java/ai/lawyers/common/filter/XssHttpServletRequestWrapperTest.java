package ai.lawyers.common.filter;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;

import org.junit.jupiter.api.Test;

/**
 * XSS 请求包装测试：参数值清洗去空格、JSON 请求体标签清理、非 JSON 原样透传、
 * Content-Type 判定与输入流状态。
 *
 * @author ai-lawyers
 */
class XssHttpServletRequestWrapperTest
{
    @Test
    void 参数值清洗并去除前后空格()
    {
        Map<String, String[]> params = new HashMap<>();
        params.put("name", new String[] { " <script>alert(1)</script> ", "  张三  " });
        XssHttpServletRequestWrapper wrapper = new XssHttpServletRequestWrapper(request(params, null, "{}"));

        assertArrayEquals(new String[] { "alert(1)", "张三" }, wrapper.getParameterValues("name"));
    }

    @Test
    void 参数不存在时返回null()
    {
        XssHttpServletRequestWrapper wrapper = new XssHttpServletRequestWrapper(
                request(new HashMap<>(), null, "{}"));

        assertNull(wrapper.getParameterValues("missing"));
    }

    @Test
    void JSON请求体清理HTML标签() throws Exception
    {
        String body = "{\"name\":\"<script>alert(1)</script>\"}";
        XssHttpServletRequestWrapper wrapper = new XssHttpServletRequestWrapper(
                request(new HashMap<>(), "application/json;charset=UTF-8", body));

        assertTrue(wrapper.isJsonRequest());
        assertEquals("{\"name\":\"alert(1)\"}", readAll(wrapper.getInputStream()));
    }

    @Test
    void 非JSON请求体原样透传() throws Exception
    {
        String body = "<script>alert(1)</script>";
        XssHttpServletRequestWrapper wrapper = new XssHttpServletRequestWrapper(
                request(new HashMap<>(), "text/plain", body));

        assertFalse(wrapper.isJsonRequest());
        assertEquals(body, readAll(wrapper.getInputStream()));
    }

    @Test
    void JSON请求体为空时返回空流() throws Exception
    {
        XssHttpServletRequestWrapper wrapper = new XssHttpServletRequestWrapper(
                request(new HashMap<>(), "application/json", ""));

        assertEquals("", readAll(wrapper.getInputStream()));
    }

    @Test
    void isJsonRequest识别ContentType()
    {
        assertTrue(wrapperWithContentType("application/json").isJsonRequest());
        assertTrue(wrapperWithContentType("Application/JSON;charset=UTF-8").isJsonRequest());
        assertFalse(wrapperWithContentType("text/plain").isJsonRequest());
        assertFalse(wrapperWithContentType(null).isJsonRequest());
    }

    @Test
    void JSON过滤后输入流状态与长度() throws Exception
    {
        String body = "{\"name\":\"<script>alert(1)</script>\"}";
        XssHttpServletRequestWrapper wrapper = new XssHttpServletRequestWrapper(
                request(new HashMap<>(), "application/json", body));
        ServletInputStream inputStream = wrapper.getInputStream();
        int available = inputStream.available();
        String content = readAll(inputStream);

        assertTrue(inputStream.isFinished());
        assertTrue(inputStream.isReady());
        assertFalse(content.contains("<script"));
        assertEquals(content.getBytes(StandardCharsets.UTF_8).length, available);
    }

    /** 构造指定 Content-Type 的包装 */
    private XssHttpServletRequestWrapper wrapperWithContentType(String contentType)
    {
        return new XssHttpServletRequestWrapper(request(new HashMap<>(), contentType, "{}"));
    }

    /** 读取输入流全部内容 */
    private String readAll(InputStream inputStream) throws IOException
    {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[64];
        int len;
        while ((len = inputStream.read(buffer)) != -1)
        {
            out.write(buffer, 0, len);
        }
        return new String(out.toByteArray(), StandardCharsets.UTF_8);
    }

    /** 构造请求代理：参数、Content-Type 与请求体 */
    private HttpServletRequest request(Map<String, String[]> params, String contentType, String body)
    {
        return (HttpServletRequest) Proxy.newProxyInstance(getClass().getClassLoader(),
                new Class<?>[] { HttpServletRequest.class }, (proxy, method, args) ->
                {
                    switch (method.getName())
                    {
                        case "getParameterValues":
                            return params.get(args[0]);
                        case "getHeader":
                            return "Content-Type".equalsIgnoreCase((String) args[0]) ? contentType : null;
                        case "getInputStream":
                            return inputStream(body);
                        default:
                            return defaultValue(method);
                    }
                });
    }

    /** 每次调用返回新的请求体输入流 */
    private ServletInputStream inputStream(String body)
    {
        ByteArrayInputStream in = new ByteArrayInputStream(body.getBytes(StandardCharsets.UTF_8));
        return new ServletInputStream()
        {
            @Override
            public int read()
            {
                return in.read();
            }

            @Override
            public boolean isFinished()
            {
                return false;
            }

            @Override
            public boolean isReady()
            {
                return false;
            }

            @Override
            public void setReadListener(ReadListener readListener)
            {
            }
        };
    }

    /** 返回类型对应的默认值 */
    private static Object defaultValue(Method method)
    {
        Class<?> type = method.getReturnType();
        if (type == boolean.class)
        {
            return false;
        }
        if (type == int.class)
        {
            return 0;
        }
        if (type == long.class)
        {
            return 0L;
        }
        return null;
    }
}
