package ai.lawyers.common.annotation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.junit.jupiter.api.Test;

/**
 * 匿名访问注解测试：元注解与作用目标。
 *
 * @author ai-lawyers
 */
class AnonymousTest
{
    @Anonymous
    static class AnonymousController
    {
        @Anonymous
        void login()
        {
        }
    }

    @Test
    void 保留策略为运行时()
    {
        Retention retention = Anonymous.class.getAnnotation(Retention.class);

        assertEquals(RetentionPolicy.RUNTIME, retention.value());
    }

    @Test
    void 作用目标为方法与类型()
    {
        Target target = Anonymous.class.getAnnotation(Target.class);

        assertEquals(2, target.value().length);
        assertEquals(ElementType.METHOD, target.value()[0]);
        assertEquals(ElementType.TYPE, target.value()[1]);
    }

    @Test
    void 标注了文档注解()
    {
        assertTrue(Anonymous.class.isAnnotationPresent(Documented.class));
    }

    @Test
    void 可标注类与方法()
    {
        assertTrue(AnonymousController.class.isAnnotationPresent(Anonymous.class));
        assertTrue(AnonymousController.class.getDeclaredMethods()[0].isAnnotationPresent(Anonymous.class));
    }
}
