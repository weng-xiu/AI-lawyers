package ai.lawyers.common.enums;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/**
 * 数据源类型枚举测试。
 *
 * @author ai-lawyers
 */
class DataSourceTypeTest
{
    @Test
    void 枚举项齐全()
    {
        assertEquals(2, DataSourceType.values().length);
        assertEquals(DataSourceType.MASTER, DataSourceType.valueOf("MASTER"));
        assertEquals(DataSourceType.SLAVE, DataSourceType.valueOf("SLAVE"));
    }
}
