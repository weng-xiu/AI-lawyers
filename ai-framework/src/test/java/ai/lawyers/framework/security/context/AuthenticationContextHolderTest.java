package ai.lawyers.framework.security.context;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;

/**
 * 身份验证上下文测试：ThreadLocal 存取与清理。
 *
 * @author ai-lawyers
 */
class AuthenticationContextHolderTest
{
    @AfterEach
    void tearDown()
    {
        AuthenticationContextHolder.clearContext();
    }

    @Test
    void 初始状态_无认证信息()
    {
        assertNull(AuthenticationContextHolder.getContext());
    }

    @Test
    void setContext_读取到同一认证对象()
    {
        Authentication authentication = new UsernamePasswordAuthenticationToken("admin", "pwd");
        AuthenticationContextHolder.setContext(authentication);

        assertSame(authentication, AuthenticationContextHolder.getContext());
        assertEquals("admin", AuthenticationContextHolder.getContext().getPrincipal());
    }

    @Test
    void clearContext_清理后为空()
    {
        AuthenticationContextHolder.setContext(new UsernamePasswordAuthenticationToken("admin", "pwd"));

        AuthenticationContextHolder.clearContext();

        assertNull(AuthenticationContextHolder.getContext());
    }
}
