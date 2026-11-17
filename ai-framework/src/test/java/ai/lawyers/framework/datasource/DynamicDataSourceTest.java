package ai.lawyers.framework.datasource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;

import java.util.Collections;

import javax.sql.DataSource;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import ai.lawyers.common.enums.DataSourceType;

/**
 * 动态数据源测试：路由 key 取当前线程上下文，无上下文时为默认数据源。
 *
 * @author ai-lawyers
 */
class DynamicDataSourceTest
{
    /** 暴露 determineCurrentLookupKey 便于断言 */
    static class ExposedDataSource extends DynamicDataSource
    {
        ExposedDataSource(DataSource defaultTargetDataSource)
        {
            super(defaultTargetDataSource, Collections.emptyMap());
        }

        Object lookupKey()
        {
            return determineCurrentLookupKey();
        }
    }

    private final ExposedDataSource dataSource = new ExposedDataSource(mock(DataSource.class));

    @AfterEach
    void clear()
    {
        DynamicDataSourceContextHolder.clearDataSourceType();
    }

    @Test
    void determineCurrentLookupKey_无上下文时返回null()
    {
        assertNull(dataSource.lookupKey());
    }

    @Test
    void determineCurrentLookupKey_返回当前线程指定数据源()
    {
        DynamicDataSourceContextHolder.setDataSourceType(DataSourceType.SLAVE.name());

        assertEquals(DataSourceType.SLAVE.name(), dataSource.lookupKey());
    }

    @Test
    void determineCurrentLookupKey_清理后回到默认数据源()
    {
        DynamicDataSourceContextHolder.setDataSourceType(DataSourceType.SLAVE.name());
        DynamicDataSourceContextHolder.clearDataSourceType();

        assertNull(dataSource.lookupKey());
    }
}
