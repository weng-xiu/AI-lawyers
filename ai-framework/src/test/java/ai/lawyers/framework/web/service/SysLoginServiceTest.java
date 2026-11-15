package ai.lawyers.framework.web.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Collections;
import java.util.concurrent.ScheduledExecutorService;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
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
import ai.lawyers.common.constant.UserConstants;
import ai.lawyers.common.core.domain.entity.SysUser;
import ai.lawyers.common.core.domain.model.LoginUser;
import ai.lawyers.common.core.redis.RedisCache;
import ai.lawyers.common.exception.ServiceException;
import ai.lawyers.common.exception.user.BlackListException;
import ai.lawyers.common.exception.user.CaptchaException;
import ai.lawyers.common.exception.user.CaptchaExpireException;
import ai.lawyers.common.exception.user.UserNotExistsException;
import ai.lawyers.common.exception.user.UserPasswordNotMatchException;
import ai.lawyers.common.utils.MessageUtils;
import ai.lawyers.common.utils.ip.IpUtils;
import ai.lawyers.common.utils.spring.SpringUtils;
import ai.lawyers.system.service.ISysConfigService;
import ai.lawyers.system.service.ISysUserService;

/**
 * 管理端登录服务测试：验证码开关/过期/错误/忽略大小写、用户名密码空值与长度校验、
 * IP 黑名单、认证成功签发 token 并记录登录信息、凭据错误与其他异常转换。
 *
 * @author ai-lawyers
 */
class SysLoginServiceTest
{
    private MockedStatic<MessageUtils> messageUtilsMock;

    private MockedStatic<IpUtils> ipUtilsMock;

    private ConfigurableListableBeanFactory previousBeanFactory;

    private TokenService tokenService;

    private AuthenticationManager authenticationManager;

    private RedisCache redisCache;

    private ISysUserService userService;

    private ISysConfigService configService;

    private SysLoginService loginService;

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
        ipUtilsMock.when(IpUtils::getIpAddr).thenReturn("10.0.0.8");

        tokenService = mock(TokenService.class);
        authenticationManager = mock(AuthenticationManager.class);
        redisCache = mock(RedisCache.class);
        userService = mock(ISysUserService.class);
        configService = mock(ISysConfigService.class);
        loginService = new SysLoginService();
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
    void 验证码关闭时跳过校验()
    {
        when(configService.selectCaptchaEnabled()).thenReturn(false);

        loginService.validateCaptcha("admin", "any", "u-1");

        verify(redisCache, never()).getCacheObject(anyString());
    }

    @Test
    void 验证码缺失抛过期异常()
    {
        when(configService.selectCaptchaEnabled()).thenReturn(true);
        when(redisCache.getCacheObject(CacheConstants.CAPTCHA_CODE_KEY + "u-1")).thenReturn(null);

        assertThatThrownBy(() -> loginService.validateCaptcha("admin", "1234", "u-1"))
                .isInstanceOf(CaptchaExpireException.class);
    }

    @Test
    void 验证码错误删除缓存后抛校验异常()
    {
        when(configService.selectCaptchaEnabled()).thenReturn(true);
        when(redisCache.getCacheObject(CacheConstants.CAPTCHA_CODE_KEY + "u-1")).thenReturn("AB12");

        assertThatThrownBy(() -> loginService.validateCaptcha("admin", "9999", "u-1"))
                .isInstanceOf(CaptchaException.class);
        verify(redisCache).deleteObject(CacheConstants.CAPTCHA_CODE_KEY + "u-1");
    }

    @Test
    void 验证码正确时忽略大小写通过()
    {
        when(configService.selectCaptchaEnabled()).thenReturn(true);
        when(redisCache.getCacheObject(CacheConstants.CAPTCHA_CODE_KEY + "u-1")).thenReturn("AB12");

        assertThatCode(() -> loginService.validateCaptcha("admin", "ab12", "u-1")).doesNotThrowAnyException();
        verify(redisCache).deleteObject(CacheConstants.CAPTCHA_CODE_KEY + "u-1");
    }

    @Test
    void 用户名为空抛用户不存在异常()
    {
        assertThatThrownBy(() -> loginService.loginPreCheck("", "admin123"))
                .isInstanceOf(UserNotExistsException.class);
    }

    @Test
    void 密码为空抛用户不存在异常()
    {
        assertThatThrownBy(() -> loginService.loginPreCheck("admin", null))
                .isInstanceOf(UserNotExistsException.class);
    }

    @Test
    void 密码长度越界抛密码不匹配异常()
    {
        String tooShort = "a".repeat(UserConstants.PASSWORD_MIN_LENGTH - 1);

        assertThatThrownBy(() -> loginService.loginPreCheck("admin", tooShort))
                .isInstanceOf(UserPasswordNotMatchException.class);
    }

    @Test
    void 用户名长度越界抛密码不匹配异常()
    {
        String tooShort = "a".repeat(UserConstants.USERNAME_MIN_LENGTH - 1);

        assertThatThrownBy(() -> loginService.loginPreCheck(tooShort, "admin123"))
                .isInstanceOf(UserPasswordNotMatchException.class);
    }

    @Test
    void IP黑名单命中抛黑名单异常()
    {
        when(configService.selectConfigByKey("sys.login.blackIPList")).thenReturn("10.0.0.8");
        ipUtilsMock.when(() -> IpUtils.isMatchedIp(anyString(), anyString())).thenReturn(true);

        assertThatThrownBy(() -> loginService.loginPreCheck("admin", "admin123"))
                .isInstanceOf(BlackListException.class);
    }

    @Test
    void 前置校验通过不抛异常()
    {
        when(configService.selectConfigByKey("sys.login.blackIPList")).thenReturn(null);

        assertThatCode(() -> loginService.loginPreCheck("admin", "admin123")).doesNotThrowAnyException();
    }

    @Test
    void 登录成功签发token并记录登录信息()
    {
        when(configService.selectCaptchaEnabled()).thenReturn(false);
        when(configService.selectConfigByKey("sys.login.blackIPList")).thenReturn(null);
        LoginUser loginUser = new LoginUser(5L, 1L, new SysUser(), Collections.emptySet());
        Authentication authentication = mock(Authentication.class);
        when(authentication.getPrincipal()).thenReturn(loginUser);
        when(authenticationManager.authenticate(any(Authentication.class))).thenReturn(authentication);
        when(tokenService.createToken(loginUser)).thenReturn("token-1");

        String token = loginService.login("admin", "admin123", null, null);

        assertThat(token).isEqualTo("token-1");
        ArgumentCaptor<SysUser> captor = ArgumentCaptor.forClass(SysUser.class);
        verify(userService).updateUserProfile(captor.capture());
        assertThat(captor.getValue().getUserId()).isEqualTo(5L);
        assertThat(captor.getValue().getLoginIp()).isEqualTo("10.0.0.8");
        assertThat(captor.getValue().getLoginDate()).isNotNull();
    }

    @Test
    void 凭据错误抛密码不匹配异常()
    {
        when(configService.selectCaptchaEnabled()).thenReturn(false);
        when(configService.selectConfigByKey("sys.login.blackIPList")).thenReturn(null);
        when(authenticationManager.authenticate(any(Authentication.class)))
                .thenThrow(new BadCredentialsException("bad credentials"));

        assertThatThrownBy(() -> loginService.login("admin", "admin123", null, null))
                .isInstanceOf(UserPasswordNotMatchException.class);
    }

    @Test
    void 其他认证异常转服务异常()
    {
        when(configService.selectCaptchaEnabled()).thenReturn(false);
        when(configService.selectConfigByKey("sys.login.blackIPList")).thenReturn(null);
        when(authenticationManager.authenticate(any(Authentication.class)))
                .thenThrow(new RuntimeException("账号锁定"));

        assertThatThrownBy(() -> loginService.login("admin", "admin123", null, null))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("账号锁定");
    }
}
