package ai.lawyers.framework.aspectj;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.reflect.MethodSignature;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import ai.lawyers.common.annotation.DataSource;
import ai.lawyers.common.enums.DataSourceType;
import ai.lawyers.framework.datasource.DynamicDataSourceContextHolder;

/**
 * 多数据源切面测试：方法/类注解优先级、上下文切换与清理、异常透传。
 *
 * @author ai-lawyers
 */
class DataSourceAspectTest
{
    private final DataSourceAspect aspect = new DataSourceAspect();

    /** 类上标注从库的示例服务 */
    @DataSource(DataSourceType.SLAVE)
    static class SlaveService
    {
        public void read()
        {
        }
    }

    /** 方法上标注从库的示例服务 */
    static class MasterService
    {
        @DataSource(DataSourceType.SLAVE)
        public void write()
        {
        }

        public void plain()
        {
        }
    }

    @AfterEach
    void clear()
    {
        DynamicDataSourceContextHolder.clearDataSourceType();
    }

    @Test
    void around_方法注解切换数据源并在结束后清理() throws Throwable
    {
        ProceedingJoinPoint point = pointFor(MasterService.class, "write", null);
        when(point.proceed()).thenReturn("ok");

        Object result = aspect.around(point);

        assertEquals("ok", result);
        // 切面执行完毕后必须清理，避免数据源串用
        assertNull(DynamicDataSourceContextHolder.getDataSourceType());
    }

    @Test
    void around_方法无注解时回退到类注解() throws Throwable
    {
        ProceedingJoinPoint point = pointFor(SlaveService.class, "read", null);
        // proceed 执行期间应已切换为从库
        when(point.proceed()).thenAnswer(invocation -> {
            assertEquals(DataSourceType.SLAVE.name(), DynamicDataSourceContextHolder.getDataSourceType());
            return "slave";
        });

        assertEquals("slave", aspect.around(point));
        assertNull(DynamicDataSourceContextHolder.getDataSourceType());
    }

    @Test
    void around_无注解时不切换数据源() throws Throwable
    {
        ProceedingJoinPoint point = pointFor(MasterService.class, "plain", null);
        when(point.proceed()).thenReturn("master");

        assertEquals("master", aspect.around(point));
        assertNull(DynamicDataSourceContextHolder.getDataSourceType());
    }

    @Test
    void around_业务异常透传且仍清理上下文() throws Throwable
    {
        ProceedingJoinPoint point = pointFor(MasterService.class, "write", null);
        IllegalStateException error = new IllegalStateException("boom");
        when(point.proceed()).thenThrow(error);

        IllegalStateException actual = assertThrows(IllegalStateException.class, () -> aspect.around(point));

        assertSame(error, actual);
        assertNull(DynamicDataSourceContextHolder.getDataSourceType());
    }

    @Test
    void getDataSource_方法注解优先于类注解() throws Exception
    {
        ProceedingJoinPoint point = pointFor(MasterService.class, "write", null);

        DataSource dataSource = aspect.getDataSource(point);

        assertEquals(DataSourceType.SLAVE, dataSource.value());
    }

    /** 构造返回指定方法签名的切点 */
    private ProceedingJoinPoint pointFor(Class<?> type, String methodName, Class<?>[] parameterTypes) throws Exception
    {
        MethodSignature signature = mock(MethodSignature.class);
        when(signature.getMethod()).thenReturn(type.getMethod(methodName, parameterTypes));
        when(signature.getDeclaringType()).thenReturn(type);
        ProceedingJoinPoint point = mock(ProceedingJoinPoint.class);
        when(point.getSignature()).thenReturn(signature);
        return point;
    }
}
