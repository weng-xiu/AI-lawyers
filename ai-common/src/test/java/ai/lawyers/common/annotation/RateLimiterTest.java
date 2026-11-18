package ai.lawyers.common.annotation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ai.lawyers.common.constant.CacheConstants;
import ai.lawyers.common.enums.LimitType;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.Method;
import org.junit.jupiter.api.Test;

/**
 * 限流注解测试：元注解、属性默认值与自定义值。
 *
 * @author ai-lawyers
 */
class RateLimiterTest
{
    static class LimitService
    {
        @RateLimiter
        void defaultLimit()
        {
        }

        @RateLimiter(key = "login:", time = 30, count = 5, limitType = LimitType.IP)
        void customLimit()
        {
        }
    }

    @Test
    void 保留策略为运行时且仅作用于方法()
    {
        assertEquals(RetentionPolicy.RUNTIME, RateLimiter.class.getAnnotation(Retention.class).value());
        assertEquals(ElementType.METHOD, RateLimiter.class.getAnnotation(Target.class).value()[0]);
        assertTrue(RateLimiter.class.isAnnotationPresent(Documented.class));
    }

    @Test
    void 属性默认值() throws Exception
    {
        Method method = LimitService.class.getDeclaredMethod("defaultLimit");
        RateLimiter limiter = method.getAnnotation(RateLimiter.class);

        assertEquals(CacheConstants.RATE_LIMIT_KEY, limiter.key());
        assertEquals(60, limiter.time());
        assertEquals(100, limiter.count());
        assertEquals(LimitType.DEFAULT, limiter.limitType());
    }

    @Test
    void 属性可自定义() throws Exception
    {
        Method method = LimitService.class.getDeclaredMethod("customLimit");
        RateLimiter limiter = method.getAnnotation(RateLimiter.class);

        assertEquals("login:", limiter.key());
        assertEquals(30, limiter.time());
        assertEquals(5, limiter.count());
        assertEquals(LimitType.IP, limiter.limitType());
    }
}
