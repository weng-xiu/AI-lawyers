package ai.lawyers.common.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.Map;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * 客户端工具测试：参数读取、请求/响应获取、Ajax 判断、内容渲染与 URL 编解码。
 *
 * @author ai-lawyers
 */
class ServletUtilsTest
{
    @AfterEach
    void 清理请求上下文()
    {
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void getParameter_读取字符串与默认值()
    {
        Map<String, String> params = new HashMap<>();
        params.put("pageNum", "2");
        params.put("keyword", "合同");
        params.put("enabled", "true");
        bindRequest(params);

        assertEquals("合同", ServletUtils.getParameter("keyword"));
        assertEquals("默认值", ServletUtils.getParameter("missing", "默认值"));
        assertEquals(Integer.valueOf(2), ServletUtils.getParameterToInt("pageNum"));
        assertEquals(Integer.valueOf(5), ServletUtils.getParameterToInt("missing", 5));
        assertEquals(Boolean.TRUE, ServletUtils.getParameterToBool("enabled"));
        assertEquals(Boolean.FALSE, ServletUtils.getParameterToBool("missing", Boolean.FALSE));
    }

    @Test
    void getParams与getParamMap_汇总请求参数()
    {
        Map<String, String[]> paramMap = new HashMap<>();
        paramMap.put("ids", new String[] { "1", "2" });
        paramMap.put("name", new String[] { "张三" });
        HttpServletRequest request = proxyRequest(new HashMap<>(), new HashMap<>(), "/test", paramMap);

        Map<String, String[]> params = ServletUtils.getParams(request);
        assertEquals(2, params.size());
        assertThrows(UnsupportedOperationException.class, () -> params.put("x", new String[0]));

        Map<String, String> result = ServletUtils.getParamMap(request);
        assertEquals("1,2", result.get("ids"));
        assertEquals("张三", result.get("name"));
    }

    @Test
    void getRequest与getResponse_来自当前请求上下文()
    {
        Map<String, String> params = new HashMap<>();
        HttpServletRequest request = proxyRequest(params, new HashMap<>(), "/test", new HashMap<>());
        HttpServletResponse response = proxyResponse(new Recorder());
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request, response));

        assertSame(request, ServletUtils.getRequest());
        assertSame(response, ServletUtils.getResponse());
        assertSame(request, ServletUtils.getRequestAttributes().getRequest());
        assertNotNull(ServletUtils.getSession());
    }

    @Test
    void renderString_写入JSON响应内容()
    {
        Recorder recorder = new Recorder();
        HttpServletResponse response = proxyResponse(recorder);

        ServletUtils.renderString(response, "{\"code\":200}");

        assertEquals(200, recorder.status);
        assertEquals("application/json", recorder.contentType);
        assertEquals("utf-8", recorder.characterEncoding);
        assertEquals("{\"code\":200}", recorder.body.toString());
    }

    @Test
    void isAjaxRequest_识别各类异步请求()
    {
        assertTrue(ServletUtils.isAjaxRequest(requestWith("application/json", null, "/api/list", null)));
        assertTrue(ServletUtils.isAjaxRequest(requestWith(null, "XMLHttpRequest", "/api/list", null)));
        // URI 判断使用完整等值比较，路径后缀不会命中
        assertFalse(ServletUtils.isAjaxRequest(requestWith(null, null, "/api/list.json", null)));
        assertTrue(ServletUtils.isAjaxRequest(requestWith(null, null, ".json", null)));
        assertTrue(ServletUtils.isAjaxRequest(requestWith(null, null, "/api/list", "json")));
        assertFalse(ServletUtils.isAjaxRequest(requestWith(null, null, "/api/list", null)));
    }

    @Test
    void urlEncode与urlDecode_UTF8往返()
    {
        String encoded = ServletUtils.urlEncode("中文 空格");

        assertEquals("%E4%B8%AD%E6%96%87+%E7%A9%BA%E6%A0%BC", encoded);
        assertEquals("中文 空格", ServletUtils.urlDecode(encoded));
    }

    /** 绑定只响应 getParameter 的请求到上下文 */
    private void bindRequest(Map<String, String> params)
    {
        HttpServletRequest request = proxyRequest(params, new HashMap<>(), "/test", new HashMap<>());
        RequestContextHolder.setRequestAttributes(
                new ServletRequestAttributes(request, proxyResponse(new Recorder())));
    }

    /** 构造用于 Ajax 判断的请求 */
    private HttpServletRequest requestWith(String accept, String requestedWith, String uri, String ajaxParam)
    {
        Map<String, String> headers = new HashMap<>();
        if (accept != null)
        {
            headers.put("accept", accept);
        }
        if (requestedWith != null)
        {
            headers.put("X-Requested-With", requestedWith);
        }
        Map<String, String> params = new HashMap<>();
        if (ajaxParam != null)
        {
            params.put("__ajax", ajaxParam);
        }
        return proxyRequest(params, headers, uri, new HashMap<>());
    }

    /** 构造请求代理，仅实现测试用到的方法 */
    private HttpServletRequest proxyRequest(Map<String, String> params, Map<String, String> headers, String uri,
            Map<String, String[]> paramMap)
    {
        HttpSession session = (HttpSession) Proxy.newProxyInstance(getClass().getClassLoader(),
                new Class<?>[] { HttpSession.class }, (proxy, method, args) -> defaultValue(method));
        return (HttpServletRequest) Proxy.newProxyInstance(getClass().getClassLoader(),
                new Class<?>[] { HttpServletRequest.class }, (proxy, method, args) ->
                {
                    switch (method.getName())
                    {
                        case "getParameter":
                            return params.get(args[0]);
                        case "getParameterMap":
                            return paramMap;
                        case "getHeader":
                            return headers.get(args[0]);
                        case "getRequestURI":
                            return uri;
                        case "getSession":
                            return session;
                        default:
                            return defaultValue(method);
                    }
                });
    }

    /** 构造响应代理，记录渲染相关调用 */
    private HttpServletResponse proxyResponse(Recorder recorder)
    {
        return (HttpServletResponse) Proxy.newProxyInstance(getClass().getClassLoader(),
                new Class<?>[] { HttpServletResponse.class }, recorder);
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

    /** 记录响应写入内容与响应头设置 */
    private static final class Recorder implements InvocationHandler
    {
        private int status;

        private String contentType;

        private String characterEncoding;

        private final StringWriter body = new StringWriter();

        @Override
        public Object invoke(Object proxy, Method method, Object[] args)
        {
            switch (method.getName())
            {
                case "setStatus":
                    status = (int) args[0];
                    return null;
                case "setContentType":
                    contentType = (String) args[0];
                    return null;
                case "setCharacterEncoding":
                    characterEncoding = (String) args[0];
                    return null;
                case "getWriter":
                    return new PrintWriter(body);
                default:
                    return defaultValue(method);
            }
        }
    }
}
