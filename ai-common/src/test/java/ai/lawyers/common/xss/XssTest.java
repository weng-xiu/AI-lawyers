package ai.lawyers.common.xss;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import jakarta.validation.Constraint;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import org.junit.jupiter.api.Test;

/**
 * XSS 校验注解测试：元注解、校验器绑定与属性默认值。
 *
 * @author ai-lawyers
 */
class XssTest
{
    static class XssHolder
    {
        @Xss
        private String content;

        @Xss
        void check(@Xss String value)
        {
        }
    }

    @Test
    void 保留策略为运行时()
    {
        assertEquals(RetentionPolicy.RUNTIME, Xss.class.getAnnotation(Retention.class).value());
    }

    @Test
    void 作用目标包含方法与字段等()
    {
        Target target = Xss.class.getAnnotation(Target.class);

        assertEquals(4, target.value().length);
        assertEquals(ElementType.METHOD, target.value()[0]);
        assertEquals(ElementType.FIELD, target.value()[1]);
        assertEquals(ElementType.CONSTRUCTOR, target.value()[2]);
        assertEquals(ElementType.PARAMETER, target.value()[3]);
    }

    @Test
    void 绑定Xss校验器()
    {
        Constraint constraint = Xss.class.getAnnotation(Constraint.class);

        assertEquals(1, constraint.validatedBy().length);
        assertEquals(XssValidator.class, constraint.validatedBy()[0]);
    }

    @Test
    void 属性默认值() throws Exception
    {
        Field field = XssHolder.class.getDeclaredField("content");
        Xss xss = field.getAnnotation(Xss.class);

        assertEquals("不允许任何脚本运行", xss.message());
        assertEquals(0, xss.groups().length);
        assertEquals(0, xss.payload().length);
    }

    @Test
    void 可标注字段参数与方法() throws Exception
    {
        Field field = XssHolder.class.getDeclaredField("content");
        Method method = XssHolder.class.getDeclaredMethod("check", String.class);

        assertTrue(field.isAnnotationPresent(Xss.class));
        assertTrue(method.isAnnotationPresent(Xss.class));
        assertTrue(method.getParameters()[0].isAnnotationPresent(Xss.class));
    }
}
