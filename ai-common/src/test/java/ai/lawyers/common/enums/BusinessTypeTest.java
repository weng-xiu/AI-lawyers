package ai.lawyers.common.enums;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/**
 * 业务操作类型枚举测试。
 *
 * @author ai-lawyers
 */
class BusinessTypeTest
{
    @Test
    void 枚举项齐全()
    {
        BusinessType[] types = BusinessType.values();

        assertEquals(10, types.length);
        assertEquals(BusinessType.INSERT, BusinessType.valueOf("INSERT"));
        assertEquals(BusinessType.CLEAN, BusinessType.valueOf("CLEAN"));
    }
}
