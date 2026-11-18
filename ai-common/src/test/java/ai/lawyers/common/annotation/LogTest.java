package ai.lawyers.common.annotation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ai.lawyers.common.enums.BusinessType;
import ai.lawyers.common.enums.OperatorType;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.Method;
import org.junit.jupiter.api.Test;

/**
 * 操作日志注解测试：元注解、作用目标与属性默认值。
 *
 * @author ai-lawyers
 */
class LogTest
{
    static class LogService
    {
        @Log
        void defaultLog()
        {
        }

        @Log(title = "用户管理", businessType = BusinessType.INSERT, operatorType = OperatorType.MOBILE,
                isSaveRequestData = false, isSaveResponseData = false, excludeParamNames = { "password" })
        void customLog()
        {
        }
    }

    @Test
    void 保留策略为运行时且作用于参数与方法()
    {
        assertEquals(RetentionPolicy.RUNTIME, Log.class.getAnnotation(Retention.class).value());
        Target target = Log.class.getAnnotation(Target.class);

        assertEquals(2, target.value().length);
        assertEquals(ElementType.PARAMETER, target.value()[0]);
        assertEquals(ElementType.METHOD, target.value()[1]);
        assertTrue(Log.class.isAnnotationPresent(Documented.class));
    }

    @Test
    void 属性默认值() throws Exception
    {
        Method method = LogService.class.getDeclaredMethod("defaultLog");
        Log log = method.getAnnotation(Log.class);

        assertEquals("", log.title());
        assertEquals(BusinessType.OTHER, log.businessType());
        assertEquals(OperatorType.MANAGE, log.operatorType());
        assertTrue(log.isSaveRequestData());
        assertTrue(log.isSaveResponseData());
        assertEquals(0, log.excludeParamNames().length);
    }

    @Test
    void 属性可自定义() throws Exception
    {
        Method method = LogService.class.getDeclaredMethod("customLog");
        Log log = method.getAnnotation(Log.class);

        assertEquals("用户管理", log.title());
        assertEquals(BusinessType.INSERT, log.businessType());
        assertEquals(OperatorType.MOBILE, log.operatorType());
        assertFalse(log.isSaveRequestData());
        assertFalse(log.isSaveResponseData());
        assertEquals(1, log.excludeParamNames().length);
        assertEquals("password", log.excludeParamNames()[0]);
    }
}
