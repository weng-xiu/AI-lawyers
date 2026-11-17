package ai.lawyers.system.enums;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * 线路健康状态枚举测试：是否可参与选路与状态描述。
 *
 * @author ai-lawyers
 */
class TrunkHealthEnumTest
{
    @Test
    void isSelectable_正常亚健康未知可参与选路()
    {
        assertTrue(TrunkHealthEnum.isSelectable(TrunkHealthEnum.UNKNOWN.getCode()));
        assertTrue(TrunkHealthEnum.isSelectable(TrunkHealthEnum.NORMAL.getCode()));
        assertTrue(TrunkHealthEnum.isSelectable(TrunkHealthEnum.DEGRADED.getCode()));
    }

    @Test
    void isSelectable_故障与熔断不可参与选路()
    {
        assertFalse(TrunkHealthEnum.isSelectable(TrunkHealthEnum.FAULT.getCode()));
        assertFalse(TrunkHealthEnum.isSelectable(TrunkHealthEnum.CIRCUIT_OPEN.getCode()));
        assertFalse(TrunkHealthEnum.isSelectable(null));
        assertFalse(TrunkHealthEnum.isSelectable("9"));
    }

    @Test
    void infoOf_返回状态描述未知兜底()
    {
        assertEquals("未知", TrunkHealthEnum.infoOf("0"));
        assertEquals("正常", TrunkHealthEnum.infoOf("1"));
        assertEquals("亚健康", TrunkHealthEnum.infoOf("2"));
        assertEquals("故障", TrunkHealthEnum.infoOf("3"));
        assertEquals("熔断", TrunkHealthEnum.infoOf("4"));
        assertEquals("未知", TrunkHealthEnum.infoOf("8"));
    }
}
