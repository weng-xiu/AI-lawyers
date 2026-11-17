package ai.lawyers.framework.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import jakarta.servlet.ServletContext;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * 服务地址配置测试：域名拼接与上下文路径处理。
 *
 * @author ai-lawyers
 */
class ServerConfigTest
{
    @AfterEach
    void 清理请求上下文()
    {
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void 包含上下文路径时拼接域名()
    {
        HttpServletRequest request = mock(HttpServletRequest.class);
        ServletContext servletContext = mock(ServletContext.class);
        when(request.getRequestURL()).thenReturn(new StringBuffer("http://localhost:8080/prod/api/test"));
        when(request.getRequestURI()).thenReturn("/prod/api/test");
        when(request.getServletContext()).thenReturn(servletContext);
        when(servletContext.getContextPath()).thenReturn("/prod");

        assertEquals("http://localhost:8080/prod", ServerConfig.getDomain(request));
    }

    @Test
    void 根上下文时仅保留协议与端口()
    {
        HttpServletRequest request = mock(HttpServletRequest.class);
        ServletContext servletContext = mock(ServletContext.class);
        when(request.getRequestURL()).thenReturn(new StringBuffer("http://localhost:8080/api/test"));
        when(request.getRequestURI()).thenReturn("/api/test");
        when(request.getServletContext()).thenReturn(servletContext);
        when(servletContext.getContextPath()).thenReturn("");

        assertEquals("http://localhost:8080", ServerConfig.getDomain(request));
    }

    @Test
    void 从当前请求获取服务地址()
    {
        HttpServletRequest request = mock(HttpServletRequest.class);
        ServletContext servletContext = mock(ServletContext.class);
        when(request.getRequestURL()).thenReturn(new StringBuffer("https://lawyers.example.com/app/system/user"));
        when(request.getRequestURI()).thenReturn("/app/system/user");
        when(request.getServletContext()).thenReturn(servletContext);
        when(servletContext.getContextPath()).thenReturn("/app");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

        assertEquals("https://lawyers.example.com/app", new ServerConfig().getUrl());
    }
}
