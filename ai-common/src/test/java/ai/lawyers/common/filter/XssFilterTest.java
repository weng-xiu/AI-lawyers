package ai.lawyers.common.filter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.concurrent.atomic.AtomicReference;

import jakarta.servlet.FilterChain;
import jakarta.servlet.FilterConfig;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.junit.jupiter.api.Test;

/**
 * XSS 过滤器测试：排除链接解析、GET/DELETE 不过滤、排除路径与普通 POST 包装。
 *
 * @author ai-lawyers
 */
class XssFilterTest
{
    private final XssFilter filter = new XssFilter();

    @Test
    void init解析逗号分隔的排除链接() throws Exception
    {
        filter.init(filterConfig("a,b, /api/**"));

        assertEquals(3, filter.excludes.size());
        assertTrue(filter.excludes.contains("b"));
        assertTrue(filter.excludes.contains(" /api/**"));
    }

    @Test
    void init无排除参数时列表为空() throws Exception
    {
        filter.init(filterConfig(null));

        assertTrue(filter.excludes.isEmpty());
    }

    @Test
    void GET与DELETE请求不过滤() throws Exception
    {
        AtomicReference<ServletRequest> getPassed = new AtomicReference<>();
        HttpServletRequest getRequest = request("GET", "/api/test");
        filter.doFilter(getRequest, response(), chain(getPassed));
        assertSame(getRequest, getPassed.get());

        AtomicReference<ServletRequest> deletePassed = new AtomicReference<>();
        HttpServletRequest deleteRequest = request("DELETE", "/api/test");
        filter.doFilter(deleteRequest, response(), chain(deletePassed));
        assertSame(deleteRequest, deletePassed.get());
    }

    @Test
    void 排除链接不做XSS包装() throws Exception
    {
        filter.excludes.add("/api/**");
        AtomicReference<ServletRequest> passed = new AtomicReference<>();
        HttpServletRequest request = request("POST", "/api/test");

        filter.doFilter(request, response(), chain(passed));

        assertSame(request, passed.get());
    }

    @Test
    void 普通POST请求包装为XSS请求() throws Exception
    {
        AtomicReference<ServletRequest> passed = new AtomicReference<>();
        HttpServletRequest request = request("POST", "/system/user");

        filter.doFilter(request, response(), chain(passed));

        assertTrue(passed.get() instanceof XssHttpServletRequestWrapper);
    }

    @Test
    void 请求方法为空时不过滤() throws Exception
    {
        AtomicReference<ServletRequest> passed = new AtomicReference<>();
        HttpServletRequest request = request(null, "/api/test");

        filter.doFilter(request, response(), chain(passed));

        assertSame(request, passed.get());
    }

    /** 记录链路收到的请求 */
    private FilterChain chain(AtomicReference<ServletRequest> passed)
    {
        return (request, response) -> passed.set(request);
    }

    /** 构造带请求方法与路径的请求代理 */
    private HttpServletRequest request(String method, String servletPath)
    {
        return (HttpServletRequest) Proxy.newProxyInstance(getClass().getClassLoader(),
                new Class<?>[] { HttpServletRequest.class }, (proxy, m, args) ->
                {
                    switch (m.getName())
                    {
                        case "getMethod":
                            return method;
                        case "getServletPath":
                            return servletPath;
                        default:
                            return defaultValue(m);
                    }
                });
    }

    /** 构造响应代理 */
    private HttpServletResponse response()
    {
        return (HttpServletResponse) Proxy.newProxyInstance(getClass().getClassLoader(),
                new Class<?>[] { HttpServletResponse.class }, (proxy, m, args) -> defaultValue(m));
    }

    /** 构造过滤器配置代理 */
    private FilterConfig filterConfig(String excludes)
    {
        return (FilterConfig) Proxy.newProxyInstance(getClass().getClassLoader(),
                new Class<?>[] { FilterConfig.class }, (proxy, method, args) ->
                {
                    if ("getInitParameter".equals(method.getName()))
                    {
                        return excludes;
                    }
                    return defaultValue(method);
                });
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
