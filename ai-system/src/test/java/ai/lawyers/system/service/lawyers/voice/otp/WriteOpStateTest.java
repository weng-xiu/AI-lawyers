package ai.lawyers.system.service.lawyers.voice.otp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * 写操作状态枚举测试：终态判定与状态中文标签。
 *
 * @author ai-lawyers
 */
class WriteOpStateTest
{
    @Test
    void isTerminal_已执行取消超时为终态()
    {
        assertTrue(WriteOpState.EXECUTED.isTerminal());
        assertTrue(WriteOpState.REJECTED.isTerminal());
        assertTrue(WriteOpState.EXPIRED.isTerminal());
    }

    @Test
    void isTerminal_流程中的状态不是终态()
    {
        assertFalse(WriteOpState.DRAFT.isTerminal());
        assertFalse(WriteOpState.OTP_SENT.isTerminal());
        assertFalse(WriteOpState.VERIFIED.isTerminal());
        assertFalse(WriteOpState.AWAITING_CONFIRM.isTerminal());
        assertFalse(WriteOpState.CONFIRMED.isTerminal());
    }

    @Test
    void getLabel_返回中文标签()
    {
        assertEquals("草稿已建", WriteOpState.DRAFT.getLabel());
        assertEquals("核身码已发送", WriteOpState.OTP_SENT.getLabel());
        assertEquals("身份已核验", WriteOpState.VERIFIED.getLabel());
        assertEquals("等待用户确认", WriteOpState.AWAITING_CONFIRM.getLabel());
        assertEquals("用户已确认", WriteOpState.CONFIRMED.getLabel());
        assertEquals("已执行", WriteOpState.EXECUTED.getLabel());
        assertEquals("已取消", WriteOpState.REJECTED.getLabel());
        assertEquals("已超时", WriteOpState.EXPIRED.getLabel());
    }
}
