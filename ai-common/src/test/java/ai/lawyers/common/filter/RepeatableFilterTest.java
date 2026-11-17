package ai.lawyers.common.filter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.io.ByteArrayInputStream;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;

import org.junit.jupiter.api.Test;

import ai.lawyers.common.utils.http.HttpHelper;

/**
 * 可重复读取过滤器测试：JSON 请求包装、非 JSON 原样透传、非 HTTP 请求原样透传。
 *
 * @author ai-lawyers
 */
class RepeatableFilterTest
{
    private final RepeatableFilter filter = new RepeatableFilter();

    @Test
    void JSON请求包装后请求体可重复读取() throws Exception
    {
        AtomicReference<ServletRequest> passed = new AtomicReference<>();
        FilterChain chain = (request, response) -> passed.set(request);
        ServletRequest request = httpRequest("application/json", "{\"a\":1}");

        filter.doFilter(request, response(), chain);

        RepeatedlyRequestWrapper wrapper = (RepeatedlyRequestWrapper) passed.get();
        assertEquals("{\"a\":1}", HttpHelper.getBodyString(wrapper));
        assertEquals("{\"a\":1}", HttpHelper.getBodyString(wrapper));
    }

    @Test
    void 非JSON请求原样透传() throws Exception
    {
        AtomicReference<ServletRequest> passed = new AtomicReference<>();
        FilterChain chain = (request, response) -> passed.set(request);
        ServletRequest request = httpRequest("text/plain", "hello");

        filter.doFilter(request, response(), chain);

        assertSame(request, passed.get());
    }

    @Test
    void 非HTTP请求原样透传() throws Exception
    {
        AtomicReference<ServletRequest> passed = new AtomicReference<>();
        FilterChain chain = (request, response) -> passed.set(request);
        ServletRequest request = (ServletRequest) Proxy.newProxyInstance(getClass().getClassLoader(),
                new Class<?>[] { ServletRequest.class }, (proxy, method, args) ->
                {
                    if ("getContentType".equals(method.getName()))
                    {
                        return "application/json";
                    }
                    return defaultValue(method);
                });

        filter.doFilter(request, response(), chain);

        assertSame(request, passed.get());
    }

    @Test
    void init与destroy为空实现不影响使用() throws Exception
    {
        filter.init(null);
        filter.destroy();

        AtomicReference<ServletRequest> passed = new AtomicReference<>();
        ServletRequest request = httpRequest(null, "x");
        filter.doFilter(request, response(), (req, resp) -> passed.set(req));

        assertSame(request, passed.get());
    }

    /** 构造带 Content-Type 与请求体的 HTTP 请求代理 */
    private HttpServletRequest httpRequest(String contentType, String body)
    {
        return (HttpServletRequest) Proxy.newProxyInstance(getClass().getClassLoader(),
                new Class<?>[] { HttpServletRequest.class }, (proxy, method, args) ->
                {
                    switch (method.getName())
                    {
                        case "getContentType":
                            return contentType;
                        case "getInputStream":
                            return inputStream(body);
                        case "setCharacterEncoding":
                            return null;
                        default:
                            return defaultValue(method);
                    }
                });
    }

    /** 构造空的响应代理 */
    private ServletResponse response()
    {
        return (ServletResponse) Proxy.newProxyInstance(getClass().getClassLoader(),
                new Class<?>[] { ServletResponse.class }, (proxy, method, args) -> defaultValue(method));
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
