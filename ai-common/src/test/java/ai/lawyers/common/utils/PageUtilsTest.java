package ai.lawyers.common.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.Map;

import jakarta.servlet.http.HttpServletRequest;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import com.github.pagehelper.Page;
import com.github.pagehelper.PageHelper;

/**
 * 分页工具测试：从请求参数构建分页并清理线程变量。
 *
 * @author ai-lawyers
 */
class PageUtilsTest
{
    @AfterEach
    void 清理分页与请求上下文()
    {
        PageHelper.clearPage();
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void startPage_读取请求分页参数()
    {
        bindRequest("2", "20", "createTime", "descending", "false");

        PageUtils.startPage();

        Page<?> page = PageHelper.getLocalPage();
        assertEquals(2, page.getPageNum());
        assertEquals(20, page.getPageSize());
        assertEquals("create_time desc", page.getOrderBy());
        assertEquals(Boolean.FALSE, page.getReasonable());
    }

    @Test
    void startPage_未传参数时使用默认值()
    {
        bindRequest(null, null, null, null, null);

        PageUtils.startPage();

        Page<?> page = PageHelper.getLocalPage();
        assertEquals(1, page.getPageNum());
        assertEquals(10, page.getPageSize());
        assertEquals("", page.getOrderBy());
        assertEquals(Boolean.TRUE, page.getReasonable());
    }

    @Test
    void startPage_排序方向为ascending时转换为asc()
    {
        bindRequest("1", "10", "userName", "ascending", null);

        PageUtils.startPage();

        assertEquals("user_name asc", PageHelper.getLocalPage().getOrderBy());
    }

    @Test
    void clearPage_清除线程变量()
    {
        bindRequest("1", "10", null, null, null);
        PageUtils.startPage();

        PageUtils.clearPage();

        assertNull(PageHelper.getLocalPage());
    }

    /** 绑定只响应 getParameter 的请求到上下文 */
    private void bindRequest(String pageNum, String pageSize, String orderByColumn, String isAsc, String reasonable)
    {
        Map<String, String> params = new HashMap<>();
        putIfPresent(params, "pageNum", pageNum);
        putIfPresent(params, "pageSize", pageSize);
        putIfPresent(params, "orderByColumn", orderByColumn);
        putIfPresent(params, "isAsc", isAsc);
        putIfPresent(params, "reasonable", reasonable);

        HttpServletRequest request = (HttpServletRequest) Proxy.newProxyInstance(getClass().getClassLoader(),
                new Class<?>[] { HttpServletRequest.class }, (proxy, method, args) ->
                        "getParameter".equals(method.getName()) ? params.get(args[0]) : null);
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
    }

    private void putIfPresent(Map<String, String> params, String key, String value)
    {
        if (value != null)
        {
            params.put(key, value);
        }
    }
}
