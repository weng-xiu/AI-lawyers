package ai.lawyers.common.utils.http;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;

import jakarta.servlet.ServletInputStream;
import jakarta.servlet.ServletRequest;

import org.junit.jupiter.api.Test;

/**
 * HTTP 请求体读取测试：UTF-8 读取、多行拼接、空体、读取异常兜底空串。
 *
 * @author ai-lawyers
 */
class HttpHelperTest
{
    @Test
    void 读取单行请求体()
    {
        assertEquals("{\"name\":\"张三\"}", HttpHelper.getBodyString(request("{\"name\":\"张三\"}")));
    }

    @Test
    void 多行请求体拼接且不保留换行()
    {
        assertEquals("ab", HttpHelper.getBodyString(request("a\nb")));
    }

    @Test
    void 空请求体返回空串()
    {
        assertEquals("", HttpHelper.getBodyString(request("")));
    }

    @Test
    void 读取异常时返回空串()
    {
        ServletRequest request = (ServletRequest) Proxy.newProxyInstance(getClass().getClassLoader(),
                new Class<?>[] { ServletRequest.class }, (proxy, method, args) ->
                {
                    if ("getInputStream".equals(method.getName()))
                    {
                        throw new IOException("连接中断");
                    }
                    return null;
                });

        assertEquals("", HttpHelper.getBodyString(request));
    }

    /** 构造返回指定请求体的请求代理 */
    private ServletRequest request(String body)
    {
        return (ServletRequest) Proxy.newProxyInstance(getClass().getClassLoader(),
                new Class<?>[] { ServletRequest.class }, (proxy, method, args) ->
                {
                    if ("getInputStream".equals(method.getName()))
                    {
                        return inputStream(body);
                    }
                    return null;
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
            public void setReadListener(jakarta.servlet.ReadListener readListener)
            {
            }
        };
    }
}
