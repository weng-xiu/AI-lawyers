package ai.lawyers.framework.aspectj;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.lang.reflect.Method;

import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.reflect.MethodSignature;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import ai.lawyers.common.annotation.RateLimiter;
import ai.lawyers.common.enums.LimitType;
import ai.lawyers.common.exception.ServiceException;

/**
 * 限流切面测试：未超限放行、超限拒绝、Redis 异常包装与限流 key 拼接。
 *
 * @author ai-lawyers
 */
class RateLimiterAspectTest
{
    private final RateLimiterAspect aspect = new RateLimiterAspect();

    private RedisTemplate<Object, Object> redisTemplate;

    private RedisScript<Long> limitScript;

    private JoinPoint point;

    private RateLimiter rateLimiter;

    /** 被限流的示例方法 */
    static class SampleService
    {
        public void handle()
        {
        }
    }

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() throws Exception
    {
        redisTemplate = mock(RedisTemplate.class);
        limitScript = mock(RedisScript.class);
        aspect.setRedisTemplate1(redisTemplate);
        aspect.setLimitScript(limitScript);

        MethodSignature signature = mock(MethodSignature.class);
        Method method = SampleService.class.getMethod("handle");
        when(signature.getMethod()).thenReturn(method);
        point = mock(JoinPoint.class);
        when(point.getSignature()).thenReturn(signature);

        rateLimiter = mock(RateLimiter.class);
        when(rateLimiter.key()).thenReturn("rate:");
        when(rateLimiter.time()).thenReturn(10);
        when(rateLimiter.count()).thenReturn(3);
        when(rateLimiter.limitType()).thenReturn(LimitType.DEFAULT);
    }

    @AfterEach
    void tearDown()
    {
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void doBefore_未超限放行() throws Throwable
    {
        givenLimitResult(1L);

        aspect.doBefore(point, rateLimiter);
    }

    @Test
    void doBefore_计数为空时拒绝() throws Throwable
    {
        givenLimitResult(null);

        ServiceException error = assertThrows(ServiceException.class, () -> aspect.doBefore(point, rateLimiter));
        assertEquals("访问过于频繁，请稍候再试", error.getMessage());
    }

    @Test
    void doBefore_超过阈值时拒绝() throws Throwable
    {
        givenLimitResult(4L);

        ServiceException error = assertThrows(ServiceException.class, () -> aspect.doBefore(point, rateLimiter));
        assertEquals("访问过于频繁，请稍候再试", error.getMessage());
    }

    @Test
    void doBefore_Redis异常包装为运行时异常() throws Throwable
    {
        when(redisTemplate.execute(any(RedisScript.class), anyList(), any(), any()))
                .thenThrow(new IllegalStateException("redis down"));

        RuntimeException error = assertThrows(RuntimeException.class, () -> aspect.doBefore(point, rateLimiter));
        assertEquals("服务器限流异常，请稍候再试", error.getMessage());
    }

    @Test
    void getCombineKey_默认类型拼接限流前缀与类方法名()
    {
        String key = aspect.getCombineKey(rateLimiter, point);

        assertEquals("rate:" + SampleService.class.getName() + "-handle", key);
    }

    @Test
    void getCombineKey_IP类型包含客户端地址()
    {
        when(rateLimiter.limitType()).thenReturn(LimitType.IP);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("x-forwarded-for", "1.2.3.4");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

        String key = aspect.getCombineKey(rateLimiter, point);

        assertTrue(key.startsWith("rate:1.2.3.4-"), key);
        assertTrue(key.endsWith(SampleService.class.getName() + "-handle"), key);
    }

    @SuppressWarnings("unchecked")
    private void givenLimitResult(Long value)
    {
        when(redisTemplate.execute(any(RedisScript.class), anyList(), any(), any())).thenReturn(value);
    }
}
