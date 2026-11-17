package ai.lawyers.framework.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * 用户端安全配置测试：密码编码器与认证管理器装配。
 *
 * @author ai-lawyers
 */
class AiUserSecurityConfigTest
{
    @Test
    void bCryptPasswordEncoder_可加密并校验密码()
    {
        AiUserSecurityConfig config = new AiUserSecurityConfig();

        BCryptPasswordEncoder encoder = config.bCryptPasswordEncoder();
        String encoded = encoder.encode("123456");

        assertNotNull(encoder);
        assertTrue(encoder.matches("123456", encoded));
    }

    @Test
    void userAuthenticationManager_使用用户认证服务与密码编码器()
    {
        AiUserSecurityConfig config = new AiUserSecurityConfig();
        UserDetailsService userDetailsService = mock(UserDetailsService.class);
        ReflectionTestUtils.setField(config, "userDetailsService", userDetailsService);

        AuthenticationManager manager = config.userAuthenticationManager();

        assertInstanceOf(ProviderManager.class, manager);
        ProviderManager providerManager = (ProviderManager) manager;
        assertEquals(1, providerManager.getProviders().size());
        DaoAuthenticationProvider provider = (DaoAuthenticationProvider) providerManager.getProviders().get(0);
        assertSame(userDetailsService, ReflectionTestUtils.getField(provider, "userDetailsService"));
        assertInstanceOf(BCryptPasswordEncoder.class, ReflectionTestUtils.getField(provider, "passwordEncoder"));
    }
}
