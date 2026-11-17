package ai.lawyers.common.enums;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/**
 * 限流类型枚举测试。
 *
 * @author ai-lawyers
 */
class LimitTypeTest
{
    @Test
    void 枚举项齐全()
    {
        assertEquals(2, LimitType.values().length);
        assertEquals(LimitType.DEFAULT, LimitType.valueOf("DEFAULT"));
        assertEquals(LimitType.IP, LimitType.valueOf("IP"));
    }
}
