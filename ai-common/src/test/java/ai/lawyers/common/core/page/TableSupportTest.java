package ai.lawyers.common.core.page;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.Map;

import jakarta.servlet.http.HttpServletRequest;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * 分页参数封装测试：请求参数读取、缺省与非法值兜底、排序参数透传。
 *
 * @author ai-lawyers
 */
class TableSupportTest
{
    @AfterEach
    void 清理请求上下文()
    {
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void getPageDomain_缺省参数使用默认分页()
    {
        bindRequest(new HashMap<>());

        PageDomain pageDomain = TableSupport.getPageDomain();

        assertEquals(Integer.valueOf(1), pageDomain.getPageNum());
        assertEquals(Integer.valueOf(10), pageDomain.getPageSize());
        assertNull(pageDomain.getOrderByColumn());
        assertEquals("asc", pageDomain.getIsAsc());
        assertTrue(pageDomain.getReasonable());
    }

    @Test
    void getPageDomain_读取请求中的完整分页参数()
    {
        Map<String, String> params = new HashMap<>();
        params.put("pageNum", "2");
        params.put("pageSize", "5");
        params.put("orderByColumn", "createTime");
        params.put("isAsc", "descending");
        params.put("reasonable", "false");
        bindRequest(params);

        PageDomain pageDomain = TableSupport.getPageDomain();

        assertEquals(Integer.valueOf(2), pageDomain.getPageNum());
        assertEquals(Integer.valueOf(5), pageDomain.getPageSize());
        assertEquals("create_time desc", pageDomain.getOrderBy());
        assertEquals("desc", pageDomain.getIsAsc());
        assertFalse(pageDomain.getReasonable());
    }

    @Test
    void getPageDomain_非法值回退默认()
    {
        Map<String, String> params = new HashMap<>();
        params.put("pageNum", "");
        params.put("pageSize", "abc");
        params.put("isAsc", "");
        bindRequest(params);

        PageDomain pageDomain = TableSupport.getPageDomain();

        assertEquals(Integer.valueOf(1), pageDomain.getPageNum());
        assertEquals(Integer.valueOf(10), pageDomain.getPageSize());
        // 空排序方向不覆盖默认 asc
        assertEquals("asc", pageDomain.getIsAsc());
    }

    @Test
    void buildPageRequest_与getPageDomain等价()
    {
        Map<String, String> params = new HashMap<>();
        params.put("pageNum", "4");
        params.put("pageSize", "15");
        params.put("orderByColumn", "updateTime");
        params.put("isAsc", "ascending");
        bindRequest(params);

        PageDomain pageDomain = TableSupport.buildPageRequest();

        assertEquals(Integer.valueOf(4), pageDomain.getPageNum());
        assertEquals(Integer.valueOf(15), pageDomain.getPageSize());
        assertEquals("update_time asc", pageDomain.getOrderBy());
        assertEquals("asc", pageDomain.getIsAsc());
    }

    /** 绑定只响应 getParameter 的请求到上下文 */
    private void bindRequest(Map<String, String> params)
    {
        HttpServletRequest request = (HttpServletRequest) Proxy.newProxyInstance(
                getClass().getClassLoader(), new Class<?>[] { HttpServletRequest.class },
                (proxy, method, args) ->
                {
                    if ("getParameter".equals(method.getName()))
                    {
                        return params.get(args[0]);
                    }
                    return defaultValue(method);
                });
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
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
