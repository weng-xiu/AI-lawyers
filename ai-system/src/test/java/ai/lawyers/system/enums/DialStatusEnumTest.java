package ai.lawyers.system.enums;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * 拨号状态枚举测试：终态判定、线路故障判定与状态描述。
 *
 * @author ai-lawyers
 */
class DialStatusEnumTest
{
    @Test
    void isFinal_终态返回true()
    {
        assertTrue(DialStatusEnum.isFinal(DialStatusEnum.HANGUP.getCode()));
        assertTrue(DialStatusEnum.isFinal(DialStatusEnum.FAILED.getCode()));
        assertTrue(DialStatusEnum.isFinal(DialStatusEnum.TIMEOUT.getCode()));
        assertTrue(DialStatusEnum.isFinal(DialStatusEnum.REJECTED.getCode()));
        assertTrue(DialStatusEnum.isFinal(DialStatusEnum.BUSY.getCode()));
        assertTrue(DialStatusEnum.isFinal(DialStatusEnum.INVALID_NUMBER.getCode()));
    }

    @Test
    void isFinal_过程状态与空值返回false()
    {
        assertFalse(DialStatusEnum.isFinal(DialStatusEnum.QUEUING.getCode()));
        assertFalse(DialStatusEnum.isFinal(DialStatusEnum.DIALING.getCode()));
        assertFalse(DialStatusEnum.isFinal(DialStatusEnum.RINGING.getCode()));
        assertFalse(DialStatusEnum.isFinal(DialStatusEnum.ANSWERED.getCode()));
        assertFalse(DialStatusEnum.isFinal(null));
        assertFalse(DialStatusEnum.isFinal("unknown"));
    }

    @Test
    void isTrunkFault_仅失败与超时判定为线路故障()
    {
        assertTrue(DialStatusEnum.isTrunkFault(DialStatusEnum.FAILED.getCode()));
        assertTrue(DialStatusEnum.isTrunkFault(DialStatusEnum.TIMEOUT.getCode()));
        assertFalse(DialStatusEnum.isTrunkFault(DialStatusEnum.BUSY.getCode()));
        assertFalse(DialStatusEnum.isTrunkFault(DialStatusEnum.REJECTED.getCode()));
        assertFalse(DialStatusEnum.isTrunkFault(null));
    }

    @Test
    void infoOf_返回状态描述未知兜底()
    {
        assertEquals("排队中", DialStatusEnum.infoOf("0"));
        assertEquals("已接通", DialStatusEnum.infoOf("3"));
        assertEquals("空号", DialStatusEnum.infoOf("9"));
        assertEquals("未知", DialStatusEnum.infoOf("99"));
        assertEquals("未知", DialStatusEnum.infoOf(null));
    }
}
