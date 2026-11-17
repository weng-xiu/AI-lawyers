package ai.lawyers.framework.security.filter;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Collections;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

import ai.lawyers.common.core.domain.entity.SysUser;
import ai.lawyers.common.core.domain.model.LoginUser;
import ai.lawyers.common.utils.SecurityUtils;
import ai.lawyers.framework.web.service.TokenService;

/**
 * JWT 认证过滤器测试：无 token 放行、有效 token 写入安全上下文、已认证时不重复校验。
 *
 * @author ai-lawyers
 */
class JwtAuthenticationTokenFilterTest
{
    private JwtAuthenticationTokenFilter filter;

    private TokenService tokenService;

    @BeforeEach
    void setUp()
    {
        filter = new JwtAuthenticationTokenFilter();
        tokenService = mock(TokenService.class);
        ReflectionTestUtils.setField(filter, "tokenService", tokenService);
    }

    @AfterEach
    void tearDown()
    {
        SecurityContextHolder.clearContext();
    }

    @Test
    void doFilterInternal_无登录用户时直接放行() throws Exception
    {
        when(tokenService.getLoginUser(any())).thenReturn(null);
        AtomicBoolean chained = new AtomicBoolean(false);

        filter.doFilterInternal(new MockHttpServletRequest(), new MockHttpServletResponse(),
                (request, response) -> chained.set(true));

        assertTrue(chained.get());
        assertNull(SecurityUtils.getAuthentication());
    }

    @Test
    void doFilterInternal_有效token写入安全上下文() throws Exception
    {
        LoginUser loginUser = loginUser();
        when(tokenService.getLoginUser(any())).thenReturn(loginUser);
        AtomicBoolean chained = new AtomicBoolean(false);

        filter.doFilterInternal(new MockHttpServletRequest(), new MockHttpServletResponse(),
                (request, response) -> chained.set(true));

        Authentication authentication = SecurityUtils.getAuthentication();
        assertNotNull(authentication);
        assertSame(loginUser, authentication.getPrincipal());
        verify(tokenService).verifyToken(loginUser);
        assertTrue(chained.get());
    }

    @Test
    void doFilterInternal_已认证时不再重复校验() throws Exception
    {
        LoginUser loginUser = loginUser();
        when(tokenService.getLoginUser(any())).thenReturn(loginUser);
        UsernamePasswordAuthenticationToken existing = new UsernamePasswordAuthenticationToken("existing", null);
        SecurityContextHolder.getContext().setAuthentication(existing);

        filter.doFilterInternal(new MockHttpServletRequest(), new MockHttpServletResponse(),
                (request, response) -> {
                });

        assertSame(existing, SecurityUtils.getAuthentication());
        verify(tokenService, never()).verifyToken(any());
    }

    /** 构造带用户名的登录用户 */
    private LoginUser loginUser()
    {
        SysUser user = new SysUser();
        user.setUserName("lawyer");
        LoginUser loginUser = new LoginUser(user, Collections.emptySet());
        loginUser.setToken("token-1");
        return loginUser;
    }
}
