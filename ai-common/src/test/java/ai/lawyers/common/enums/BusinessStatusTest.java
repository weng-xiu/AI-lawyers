package ai.lawyers.common.enums;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/**
 * 操作状态枚举测试。
 *
 * @author ai-lawyers
 */
class BusinessStatusTest
{
    @Test
    void 枚举项齐全()
    {
        assertEquals(2, BusinessStatus.values().length);
        assertEquals(BusinessStatus.SUCCESS, BusinessStatus.valueOf("SUCCESS"));
        assertEquals(BusinessStatus.FAIL, BusinessStatus.valueOf("FAIL"));
    }
}
