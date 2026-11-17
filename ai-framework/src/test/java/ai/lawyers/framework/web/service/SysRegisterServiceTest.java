package ai.lawyers.framework.web.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.concurrent.ScheduledExecutorService;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import ai.lawyers.common.constant.CacheConstants;
import ai.lawyers.common.core.domain.entity.SysUser;
import ai.lawyers.common.core.domain.model.RegisterBody;
import ai.lawyers.common.core.redis.RedisCache;
import ai.lawyers.common.exception.user.CaptchaException;
import ai.lawyers.common.exception.user.CaptchaExpireException;
import ai.lawyers.common.utils.MessageUtils;
import ai.lawyers.common.utils.SecurityUtils;
import ai.lawyers.common.utils.spring.SpringUtils;
import ai.lawyers.system.service.ISysConfigService;
import ai.lawyers.system.service.ISysUserService;

/**
 * 注册服务测试：参数校验、验证码校验、重复用户名、注册成功与失败提示。
 *
 * @author ai-lawyers
 */
class SysRegisterServiceTest
{
    private SysRegisterService registerService;

    private ISysUserService userService;

    private ISysConfigService configService;

    private RedisCache redisCache;

    private ConfigurableListableBeanFactory previousBeanFactory;

    private MockedStatic<MessageUtils> messageUtilsMock;

    @BeforeEach
    void setUp()
    {
        registerService = new SysRegisterService();
        userService = mock(ISysUserService.class);
        configService = mock(ISysConfigService.class);
        redisCache = mock(RedisCache.class);
        ReflectionTestUtils.setField(registerService, "userService", userService);
        ReflectionTestUtils.setField(registerService, "configService", configService);
        ReflectionTestUtils.setField(registerService, "redisCache", redisCache);

        previousBeanFactory = (ConfigurableListableBeanFactory) ReflectionTestUtils
                .getField(SpringUtils.class, "beanFactory");
        ConfigurableListableBeanFactory beanFactory = mock(ConfigurableListableBeanFactory.class);
        when(beanFactory.getBean("scheduledExecutorService")).thenReturn(mock(ScheduledExecutorService.class));
        ReflectionTestUtils.setField(SpringUtils.class, "beanFactory", beanFactory);
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(new MockHttpServletRequest()));

        messageUtilsMock = mockStatic(MessageUtils.class);
        messageUtilsMock.when(() -> MessageUtils.message("user.register.success")).thenReturn("注册成功");
    }

    @AfterEach
    void tearDown()
    {
        messageUtilsMock.close();
        ReflectionTestUtils.setField(SpringUtils.class, "beanFactory", previousBeanFactory);
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void register_用户名为空返回提示()
    {
        when(configService.selectCaptchaEnabled()).thenReturn(false);
        RegisterBody body = body("", "admin123");

        assertEquals("用户名不能为空", registerService.register(body));
    }

    @Test
    void register_密码长度不足返回提示()
    {
        when(configService.selectCaptchaEnabled()).thenReturn(false);
        RegisterBody body = body("lawyer", "123");

        assertEquals("密码长度必须在5到20个字符之间", registerService.register(body));
    }

    @Test
    void register_用户名重复返回提示()
    {
        when(configService.selectCaptchaEnabled()).thenReturn(false);
        when(userService.checkUserNameUnique(any(SysUser.class))).thenReturn(false);
        RegisterBody body = body("lawyer", "admin123");

        assertEquals("保存用户'lawyer'失败，注册账号已存在", registerService.register(body));
    }

    @Test
    void register_注册失败返回提示()
    {
        when(configService.selectCaptchaEnabled()).thenReturn(false);
        when(userService.checkUserNameUnique(any(SysUser.class))).thenReturn(true);
        when(userService.registerUser(any(SysUser.class))).thenReturn(false);
        RegisterBody body = body("lawyer", "admin123");

        assertEquals("注册失败,请联系系统管理人员", registerService.register(body));
    }

    @Test
    void register_注册成功补齐默认字段并记录日志()
    {
        when(configService.selectCaptchaEnabled()).thenReturn(false);
        when(userService.checkUserNameUnique(any(SysUser.class))).thenReturn(true);
        when(userService.registerUser(any(SysUser.class))).thenReturn(true);
        RegisterBody body = body("lawyer", "admin123");

        assertEquals("", registerService.register(body));

        ArgumentCaptor<SysUser> captor = ArgumentCaptor.forClass(SysUser.class);
        verify(userService).registerUser(captor.capture());
        SysUser saved = captor.getValue();
        assertEquals("lawyer", saved.getNickName());
        assertEquals("01", saved.getUserType());
        assertNotNull(saved.getPwdUpdateDate());
        assertTrue(SecurityUtils.matchesPassword("admin123", saved.getPassword()));
    }

    @Test
    void register_验证码过期抛异常()
    {
        when(configService.selectCaptchaEnabled()).thenReturn(true);
        when(redisCache.getCacheObject(CacheConstants.CAPTCHA_CODE_KEY + "uuid-1")).thenReturn(null);
        RegisterBody body = body("lawyer", "admin123");
        body.setCode("1234");
        body.setUuid("uuid-1");

        assertThrows(CaptchaExpireException.class, () -> registerService.register(body));
        verify(redisCache).deleteObject(CacheConstants.CAPTCHA_CODE_KEY + "uuid-1");
    }

    @Test
    void register_验证码错误抛异常()
    {
        when(configService.selectCaptchaEnabled()).thenReturn(true);
        when(redisCache.getCacheObject(anyString())).thenReturn("abcd");
        RegisterBody body = body("lawyer", "admin123");
        body.setCode("1234");
        body.setUuid("uuid-2");

        assertThrows(CaptchaException.class, () -> registerService.register(body));
    }

    @Test
    void register_验证码忽略大小写()
    {
        when(configService.selectCaptchaEnabled()).thenReturn(true);
        when(redisCache.getCacheObject(anyString())).thenReturn("AbCd");
        when(userService.checkUserNameUnique(any(SysUser.class))).thenReturn(false);
        RegisterBody body = body("lawyer", "admin123");
        body.setCode("abcd");
        body.setUuid("uuid-3");

        // 已验证码后才可能走到用户名重复分支
        assertEquals("保存用户'lawyer'失败，注册账号已存在", registerService.register(body));
    }

    /** 构造注册请求体 */
    private RegisterBody body(String username, String password)
    {
        RegisterBody body = new RegisterBody();
        body.setUsername(username);
        body.setPassword(password);
        return body;
    }
}
