package ai.lawyers.framework.web.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Collections;
import java.util.concurrent.ScheduledExecutorService;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.Authentication;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import ai.lawyers.common.constant.CacheConstants;
import ai.lawyers.common.core.domain.entity.SysUser;
import ai.lawyers.common.core.domain.model.LoginUser;
import ai.lawyers.common.core.redis.RedisCache;
import ai.lawyers.common.exception.user.CaptchaExpireException;
import ai.lawyers.common.exception.user.UserNotExistsException;
import ai.lawyers.common.exception.user.UserPasswordNotMatchException;
import ai.lawyers.common.utils.MessageUtils;
import ai.lawyers.common.utils.ip.IpUtils;
import ai.lawyers.common.utils.spring.SpringUtils;
import ai.lawyers.system.service.ISysConfigService;
import ai.lawyers.system.service.ISysUserService;

/**
 * 用户端登录服务测试：验证码过期、前置校验、认证成功签发 token、
 * 凭据错误异常转换。
 *
 * @author ai-lawyers
 */
class AiUserLoginServiceTest
{
    private MockedStatic<MessageUtils> messageUtilsMock;

    private MockedStatic<IpUtils> ipUtilsMock;

    private ConfigurableListableBeanFactory previousBeanFactory;

    private TokenService tokenService;

    private AuthenticationManager authenticationManager;

    private RedisCache redisCache;

    private ISysUserService userService;

    private ISysConfigService configService;

    private AiUserLoginService loginService;

    @BeforeEach
    void setUp()
    {
        // AsyncManager 静态单例构造时从 SpringUtils 取调度线程池，这里注入桩工厂避免真实线程池
        previousBeanFactory = (ConfigurableListableBeanFactory) ReflectionTestUtils.getField(SpringUtils.class, "beanFactory");
        ConfigurableListableBeanFactory beanFactory = mock(ConfigurableListableBeanFactory.class);
        when(beanFactory.getBean("scheduledExecutorService")).thenReturn(mock(ScheduledExecutorService.class));
        ReflectionTestUtils.setField(SpringUtils.class, "beanFactory", beanFactory);
        // AsyncFactory 记录登录日志时从请求上下文取 IP
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(new MockHttpServletRequest()));
        messageUtilsMock = mockStatic(MessageUtils.class);
        messageUtilsMock.when(() -> MessageUtils.message(anyString())).thenReturn("msg");
        ipUtilsMock = mockStatic(IpUtils.class);
        ipUtilsMock.when(IpUtils::getIpAddr).thenReturn("10.0.0.9");

        tokenService = mock(TokenService.class);
        authenticationManager = mock(AuthenticationManager.class);
        redisCache = mock(RedisCache.class);
        userService = mock(ISysUserService.class);
        configService = mock(ISysConfigService.class);
        loginService = new AiUserLoginService();
        ReflectionTestUtils.setField(loginService, "tokenService", tokenService);
        ReflectionTestUtils.setField(loginService, "authenticationManager", authenticationManager);
        ReflectionTestUtils.setField(loginService, "redisCache", redisCache);
        ReflectionTestUtils.setField(loginService, "userService", userService);
        ReflectionTestUtils.setField(loginService, "configService", configService);
    }

    @AfterEach
    void tearDown()
    {
        ReflectionTestUtils.setField(SpringUtils.class, "beanFactory", previousBeanFactory);
        RequestContextHolder.resetRequestAttributes();
        messageUtilsMock.close();
        ipUtilsMock.close();
    }

    @Test
    void 验证码过期抛异常()
    {
        when(configService.selectCaptchaEnabled()).thenReturn(true);
        when(redisCache.getCacheObject(CacheConstants.CAPTCHA_CODE_KEY + "u-2")).thenReturn(null);

        assertThatThrownBy(() -> loginService.validateCaptcha("zhangsan", "1234", "u-2"))
                .isInstanceOf(CaptchaExpireException.class);
    }

    @Test
    void 空用户名抛用户不存在异常()
    {
        assertThatThrownBy(() -> loginService.loginPreCheck(null, "user123"))
                .isInstanceOf(UserNotExistsException.class);
    }

    @Test
    void 用户登录成功返回token并记录登录信息()
    {
        when(configService.selectCaptchaEnabled()).thenReturn(false);
        when(configService.selectConfigByKey("sys.login.blackIPList")).thenReturn(null);
        LoginUser loginUser = new LoginUser(8L, 2L, new SysUser(), Collections.emptySet());
        Authentication authentication = mock(Authentication.class);
        when(authentication.getPrincipal()).thenReturn(loginUser);
        when(authenticationManager.authenticate(any(Authentication.class))).thenReturn(authentication);
        when(tokenService.createToken(loginUser)).thenReturn("user-token");

        String token = loginService.login("zhangsan", "user123", null, null);

        assertThat(token).isEqualTo("user-token");
        verify(userService).updateUserProfile(any(SysUser.class));
    }

    @Test
    void 凭据错误抛密码不匹配异常()
    {
        when(configService.selectCaptchaEnabled()).thenReturn(false);
        when(configService.selectConfigByKey("sys.login.blackIPList")).thenReturn(null);
        when(authenticationManager.authenticate(any(Authentication.class)))
                .thenThrow(new BadCredentialsException("bad"));

        assertThatThrownBy(() -> loginService.login("zhangsan", "user123", null, null))
                .isInstanceOf(UserPasswordNotMatchException.class);
    }

    @Test
    void 前置校验正常通过()
    {
        when(configService.selectConfigByKey("sys.login.blackIPList")).thenReturn(null);

        assertThatCode(() -> loginService.loginPreCheck("zhangsan", "user123")).doesNotThrowAnyException();
    }
}
