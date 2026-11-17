package ai.lawyers.common.enums;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/**
 * 操作人类别枚举测试。
 *
 * @author ai-lawyers
 */
class OperatorTypeTest
{
    @Test
    void 枚举项齐全()
    {
        assertEquals(3, OperatorType.values().length);
        assertEquals(OperatorType.MANAGE, OperatorType.valueOf("MANAGE"));
        assertEquals(OperatorType.MOBILE, OperatorType.valueOf("MOBILE"));
    }
}
