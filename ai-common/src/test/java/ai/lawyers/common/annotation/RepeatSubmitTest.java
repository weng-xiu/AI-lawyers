package ai.lawyers.common.annotation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.Method;
import org.junit.jupiter.api.Test;

/**
 * 防重复提交注解测试：元注解与属性默认值。
 *
 * @author ai-lawyers
 */
class RepeatSubmitTest
{
    static class SubmitService
    {
        @RepeatSubmit
        void defaultSubmit()
        {
        }

        @RepeatSubmit(interval = 1000, message = "请勿重复提交")
        void customSubmit()
        {
        }
    }

    @Test
    void 保留策略为运行时且仅作用于方法()
    {
        assertEquals(RetentionPolicy.RUNTIME, RepeatSubmit.class.getAnnotation(Retention.class).value());
        assertEquals(ElementType.METHOD, RepeatSubmit.class.getAnnotation(Target.class).value()[0]);
        assertTrue(RepeatSubmit.class.isAnnotationPresent(Documented.class));
        assertTrue(RepeatSubmit.class.isAnnotationPresent(Inherited.class));
    }

    @Test
    void 属性默认值() throws Exception
    {
        Method method = SubmitService.class.getDeclaredMethod("defaultSubmit");
        RepeatSubmit repeatSubmit = method.getAnnotation(RepeatSubmit.class);

        assertEquals(5000, repeatSubmit.interval());
        assertEquals("不允许重复提交，请稍候再试", repeatSubmit.message());
    }

    @Test
    void 属性可自定义() throws Exception
    {
        Method method = SubmitService.class.getDeclaredMethod("customSubmit");
        RepeatSubmit repeatSubmit = method.getAnnotation(RepeatSubmit.class);

        assertEquals(1000, repeatSubmit.interval());
        assertEquals("请勿重复提交", repeatSubmit.message());
    }
}
