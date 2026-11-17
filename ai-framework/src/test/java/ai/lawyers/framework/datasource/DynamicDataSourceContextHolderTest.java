package ai.lawyers.framework.datasource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * 动态数据源上下文测试：设置、读取与清理。
 *
 * @author ai-lawyers
 */
class DynamicDataSourceContextHolderTest
{
    @AfterEach
    void tearDown()
    {
        DynamicDataSourceContextHolder.clearDataSourceType();
    }

    @Test
    void 初始状态_无数据源类型()
    {
        assertNull(DynamicDataSourceContextHolder.getDataSourceType());
    }

    @Test
    void setDataSourceType_读取到最近一次设置()
    {
        DynamicDataSourceContextHolder.setDataSourceType("MASTER");
        assertEquals("MASTER", DynamicDataSourceContextHolder.getDataSourceType());

        DynamicDataSourceContextHolder.setDataSourceType("SLAVE");
        assertEquals("SLAVE", DynamicDataSourceContextHolder.getDataSourceType());
    }

    @Test
    void clearDataSourceType_清理后为空()
    {
        DynamicDataSourceContextHolder.setDataSourceType("MASTER");

        DynamicDataSourceContextHolder.clearDataSourceType();

        assertNull(DynamicDataSourceContextHolder.getDataSourceType());
    }
}
