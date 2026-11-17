package ai.lawyers.framework.security.context;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * 权限上下文测试：基于请求作用域存取权限串。
 *
 * @author ai-lawyers
 */
class PermissionContextHolderTest
{
    @AfterEach
    void tearDown()
    {
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void setContext_读取到请求作用域中的权限()
    {
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(new MockHttpServletRequest()));

        PermissionContextHolder.setContext("system:user:list");

        assertEquals("system:user:list", PermissionContextHolder.getContext());
    }

    @Test
    void getContext_未设置时返回空()
    {
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(new MockHttpServletRequest()));

        assertNull(PermissionContextHolder.getContext());
    }
}
