package ai.lawyers.common.annotation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ai.lawyers.common.enums.DataSourceType;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.junit.jupiter.api.Test;

/**
 * 多数据源切换注解测试：元注解、作用目标与默认数据源。
 *
 * @author ai-lawyers
 */
class DataSourceTest
{
    @DataSource
    static class MasterService
    {
    }

    @DataSource(DataSourceType.SLAVE)
    static class SlaveService
    {
    }

    @Test
    void 保留策略为运行时()
    {
        assertEquals(RetentionPolicy.RUNTIME, DataSource.class.getAnnotation(Retention.class).value());
    }

    @Test
    void 作用目标为方法与类型且可继承()
    {
        Target target = DataSource.class.getAnnotation(Target.class);

        assertEquals(2, target.value().length);
        assertEquals(ElementType.METHOD, target.value()[0]);
        assertEquals(ElementType.TYPE, target.value()[1]);
        assertTrue(DataSource.class.isAnnotationPresent(Documented.class));
        assertTrue(DataSource.class.isAnnotationPresent(Inherited.class));
    }

    @Test
    void 默认数据源为主库()
    {
        assertEquals(DataSourceType.MASTER, MasterService.class.getAnnotation(DataSource.class).value());
    }

    @Test
    void 数据源可显式指定()
    {
        assertEquals(DataSourceType.SLAVE, SlaveService.class.getAnnotation(DataSource.class).value());
    }
}
