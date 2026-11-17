package ai.lawyers.common.filter;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;

import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;

import org.junit.jupiter.api.Test;

import ai.lawyers.common.constant.Constants;
import ai.lawyers.common.utils.http.HttpHelper;

/**
 * 可重复读取请求包装测试：编码设置、输入流多次读取、Reader 读取、
 * 换行被拼接丢弃与 available 返回全文长度。
 *
 * @author ai-lawyers
 */
class RepeatedlyRequestWrapperTest
{
    private final Recorder recorder = new Recorder();

    @Test
    void 构造时请求与响应统一UTF8编码() throws Exception
    {
        new RepeatedlyRequestWrapper(request("{\"a\":1}"), response());

        assertEquals(Constants.UTF8, recorder.requestEncoding);
        assertEquals(Constants.UTF8, recorder.responseEncoding);
    }

    @Test
    void 输入流可重复读取() throws Exception
    {
        RepeatedlyRequestWrapper wrapper = new RepeatedlyRequestWrapper(request("{\"a\":1}"), response());

        assertEquals("{\"a\":1}", readAll(wrapper.getInputStream()));
        assertEquals("{\"a\":1}", readAll(wrapper.getInputStream()));
    }

    @Test
    void 通过HttpHelper可多次读取请求体() throws Exception
    {
        RepeatedlyRequestWrapper wrapper = new RepeatedlyRequestWrapper(request("{\"a\":1}"), response());

        assertEquals("{\"a\":1}", HttpHelper.getBodyString(wrapper));
        assertEquals("{\"a\":1}", HttpHelper.getBodyString(wrapper));
    }

    @Test
    void getReader返回完整请求体() throws Exception
    {
        RepeatedlyRequestWrapper wrapper = new RepeatedlyRequestWrapper(request("hello"), response());
        BufferedReader reader = wrapper.getReader();

        assertEquals("hello", reader.readLine());
    }

    @Test
    void 构造读取时丢弃换行() throws Exception
    {
        RepeatedlyRequestWrapper wrapper = new RepeatedlyRequestWrapper(request("a\nb"), response());

        // HttpHelper 按行读取且不追加换行符
        assertEquals("ab", HttpHelper.getBodyString(wrapper));
    }

    @Test
    void available返回请求体总长度() throws Exception
    {
        byte[] body = "{\"a\":1}".getBytes(StandardCharsets.UTF_8);
        RepeatedlyRequestWrapper wrapper = new RepeatedlyRequestWrapper(request("{\"a\":1}"), response());

        assertEquals(body.length, wrapper.getInputStream().available());
    }

    /** 读取输入流全部字节 */
    private String readAll(ServletInputStream inputStream) throws IOException
    {
        byte[] buffer = new byte[64];
        int len = inputStream.read(buffer);
        return new String(buffer, 0, Math.max(len, 0), StandardCharsets.UTF_8);
    }

    /** 构造可读取请求体的请求代理 */
    private HttpServletRequest request(String body)
    {
        return (HttpServletRequest) Proxy.newProxyInstance(getClass().getClassLoader(),
                new Class<?>[] { HttpServletRequest.class }, (proxy, method, args) ->
                {
                    switch (method.getName())
                    {
                        case "getInputStream":
                            return inputStream(body);
                        case "setCharacterEncoding":
                            recorder.requestEncoding = (String) args[0];
                            return null;
                        default:
                            return defaultValue(method);
                    }
                });
    }

    /** 构造记录编码设置的响应代理 */
    private ServletResponse response()
    {
        return (ServletResponse) Proxy.newProxyInstance(getClass().getClassLoader(),
                new Class<?>[] { ServletResponse.class }, (proxy, method, args) ->
                {
                    if ("setCharacterEncoding".equals(method.getName()))
                    {
                        recorder.responseEncoding = (String) args[0];
                    }
                    return defaultValue(method);
                });
    }

    /** 构造请求体输入流 */
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
            public int available()
            {
                return in.available();
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

    /** 记录编码设置 */
    private static final class Recorder
    {
        private String requestEncoding;

        private String responseEncoding;
    }
}
