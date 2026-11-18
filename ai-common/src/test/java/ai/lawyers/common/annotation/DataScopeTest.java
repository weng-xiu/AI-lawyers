package ai.lawyers.common.annotation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.Method;
import org.junit.jupiter.api.Test;

/**
 * 数据权限注解测试：元注解、作用目标与属性默认值。
 *
 * @author ai-lawyers
 */
class DataScopeTest
{
    static class QueryService
    {
        @DataScope
        void defaultScope()
        {
        }

        @DataScope(deptAlias = "d", userAlias = "u", permission = "system:user:list")
        void customScope()
        {
        }
    }

    @Test
    void 保留策略为运行时且仅作用于方法()
    {
        assertEquals(RetentionPolicy.RUNTIME, DataScope.class.getAnnotation(Retention.class).value());
        assertEquals(ElementType.METHOD, DataScope.class.getAnnotation(Target.class).value()[0]);
        assertTrue(DataScope.class.isAnnotationPresent(Documented.class));
    }

    @Test
    void 属性默认值为空字符串() throws Exception
    {
        DataScope scope = defaultScope();

        assertEquals("", scope.deptAlias());
        assertEquals("", scope.userAlias());
        assertEquals("", scope.permission());
    }

    @Test
    void 属性可自定义() throws Exception
    {
        Method method = QueryService.class.getDeclaredMethod("customScope");
        DataScope scope = method.getAnnotation(DataScope.class);

        assertEquals("d", scope.deptAlias());
        assertEquals("u", scope.userAlias());
        assertEquals("system:user:list", scope.permission());
    }

    private DataScope defaultScope() throws Exception
    {
        Method method = QueryService.class.getDeclaredMethod("defaultScope");
        return method.getAnnotation(DataScope.class);
    }
}
