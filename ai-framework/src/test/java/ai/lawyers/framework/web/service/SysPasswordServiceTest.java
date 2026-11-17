package ai.lawyers.framework.web.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.Authentication;
import org.springframework.test.util.ReflectionTestUtils;

import ai.lawyers.common.constant.CacheConstants;
import ai.lawyers.common.core.domain.entity.SysUser;
import ai.lawyers.common.core.redis.RedisCache;
import ai.lawyers.common.exception.user.UserPasswordNotMatchException;
import ai.lawyers.common.exception.user.UserPasswordRetryLimitExceedException;
import ai.lawyers.common.utils.SecurityUtils;
import ai.lawyers.framework.security.context.AuthenticationContextHolder;

/**
 * 登录密码服务测试：密码校验、错误次数累加、锁定阈值与成功清缓存。
 *
 * @author ai-lawyers
 */
class SysPasswordServiceTest
{
    private static final String USERNAME = "admin";

    private static final String CACHE_KEY = CacheConstants.PWD_ERR_CNT_KEY + USERNAME;

    private SysPasswordService passwordService;

    private RedisCache redisCache;

    @BeforeEach
    void setUp()
    {
        passwordService = new SysPasswordService();
        redisCache = mock(RedisCache.class);
        ReflectionTestUtils.setField(passwordService, "redisCache", redisCache);
        ReflectionTestUtils.setField(passwordService, "maxRetryCount", 5);
        ReflectionTestUtils.setField(passwordService, "lockTime", 10);

        Authentication authentication = mock(Authentication.class);
        when(authentication.getName()).thenReturn(USERNAME);
        when(authentication.getCredentials()).thenReturn("admin123");
        AuthenticationContextHolder.setContext(authentication);
    }

    @AfterEach
    void tearDown()
    {
        AuthenticationContextHolder.clearContext();
    }

    @Test
    void validate_密码正确时清除错误次数()
    {
        SysUser user = user("admin123");
        when(redisCache.getCacheObject(CACHE_KEY)).thenReturn(2);
        when(redisCache.hasKey(CACHE_KEY)).thenReturn(true);

        passwordService.validate(user);

        verify(redisCache).deleteObject(CACHE_KEY);
    }

    @Test
    void validate_密码错误时累加次数并抛出异常()
    {
        SysUser user = user("other-password");
        when(redisCache.getCacheObject(CACHE_KEY)).thenReturn(2);

        assertThrows(UserPasswordNotMatchException.class, () -> passwordService.validate(user));

        verify(redisCache).setCacheObject(CACHE_KEY, 3, 10, TimeUnit.MINUTES);
    }

    @Test
    void validate_达到最大重试次数时锁定()
    {
        SysUser user = user("admin123");
        when(redisCache.getCacheObject(CACHE_KEY)).thenReturn(5);

        assertThrows(UserPasswordRetryLimitExceedException.class, () -> passwordService.validate(user));

        verify(redisCache, never()).setCacheObject(anyString(), any(), any(), any());
    }

    @Test
    void clearLoginRecordCache_无缓存时不删除()
    {
        when(redisCache.hasKey(CACHE_KEY)).thenReturn(false);

        passwordService.clearLoginRecordCache(USERNAME);

        verify(redisCache, never()).deleteObject(CACHE_KEY);
    }

    @Test
    void matches_明文与密文比对()
    {
        SysUser user = user("admin123");

        assertTrue(passwordService.matches(user, "admin123"));
        assertFalse(passwordService.matches(user, "wrong"));
    }

    /** 构造使用 BCrypt 密文的用户 */
    private SysUser user(String rawPassword)
    {
        SysUser user = new SysUser();
        user.setUserName(USERNAME);
        user.setPassword(SecurityUtils.encryptPassword(rawPassword));
        return user;
    }
}
