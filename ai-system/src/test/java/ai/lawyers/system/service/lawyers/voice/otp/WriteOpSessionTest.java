package ai.lawyers.system.service.lawyers.voice.otp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import org.junit.jupiter.api.Test;

/**
 * 写操作确认会话测试：初始草稿状态、活跃时间、核身次数与可变字段。
 *
 * @author ai-lawyers
 */
class WriteOpSessionTest
{
    @Test
    void 构造后为草稿状态且保留入参()
    {
        WriteOpSession session = session(1000L);

        assertEquals("op-1", session.getOpId());
        assertSame(WriteOpAction.SEND_SMS, session.getAction());
        assertEquals("session-1", session.getSessionId());
        assertEquals(Long.valueOf(9L), session.getRecordId());
        assertEquals("13800000000", session.getCallerNumber());
        assertEquals("{\"text\":\"hi\"}", session.getPayloadJson());
        assertEquals(1000L, session.getCreatedAt());
        // 新建会话处于草稿状态，且活跃时间等于创建时间
        assertSame(WriteOpState.DRAFT, session.getState());
        assertEquals(1000L, session.getLastActiveAt());
        assertNull(session.getSummarySpeech());
        assertEquals(0, session.getIdentityAttempts());
    }

    @Test
    void touch_更新最近活跃时间()
    {
        WriteOpSession session = session(1000L);

        session.touch(9000L);

        assertEquals(9000L, session.getLastActiveAt());
        assertEquals(1000L, session.getCreatedAt());
    }

    @Test
    void incrementIdentityAttempts_累计核身失败次数()
    {
        WriteOpSession session = session(1000L);

        session.incrementIdentityAttempts();
        session.incrementIdentityAttempts();

        assertEquals(2, session.getIdentityAttempts());
    }

    @Test
    void setState与setSummarySpeech_可变更确认流程字段()
    {
        WriteOpSession session = session(1000L);

        session.setState(WriteOpState.OTP_SENT);
        session.setSummarySpeech("即将为您发送短信，请按1确认");

        assertSame(WriteOpState.OTP_SENT, session.getState());
        assertEquals("即将为您发送短信，请按1确认", session.getSummarySpeech());
    }

    /** 构造示例写操作会话 */
    private WriteOpSession session(long createdAt)
    {
        return new WriteOpSession("op-1", WriteOpAction.SEND_SMS, "session-1", 9L,
                "13800000000", "{\"text\":\"hi\"}", createdAt);
    }
}
