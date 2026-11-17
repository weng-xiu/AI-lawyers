package ai.lawyers.system.domain.lawyers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.Date;
import org.junit.jupiter.api.Test;

/**
 * 跨渠道统一会话索引实体测试：属性读写与默认值。
 *
 * @author ai-lawyers
 */
class AiUnifiedSessionTest
{
    @Test
    void 属性读写()
    {
        Date startTime = new Date();
        Date endTime = new Date();
        Date lastHeartbeat = new Date();
        Date expireTime = new Date();
        AiUnifiedSession session = new AiUnifiedSession();
        session.setSessionId(1L);
        session.setProfileId(2L);
        session.setCallerNumber("13500000000");
        session.setChannelType("WECHAT_MP");
        session.setBizType("CHAT");
        session.setBizId("chat-001");
        session.setBizTitle("劳动仲裁咨询");
        session.setStartTime(startTime);
        session.setEndTime(endTime);
        session.setActiveFlag("1");
        session.setLastHeartbeat(lastHeartbeat);
        session.setExpireTime(expireTime);
        session.setActiveOwner("13500000000#WECHAT_MP");

        assertEquals(1L, session.getSessionId());
        assertEquals(2L, session.getProfileId());
        assertEquals("13500000000", session.getCallerNumber());
        assertEquals("WECHAT_MP", session.getChannelType());
        assertEquals("CHAT", session.getBizType());
        assertEquals("chat-001", session.getBizId());
        assertEquals("劳动仲裁咨询", session.getBizTitle());
        assertEquals(startTime, session.getStartTime());
        assertEquals(endTime, session.getEndTime());
        assertEquals("1", session.getActiveFlag());
        assertEquals(lastHeartbeat, session.getLastHeartbeat());
        assertEquals(expireTime, session.getExpireTime());
        assertEquals("13500000000#WECHAT_MP", session.getActiveOwner());
    }

    @Test
    void 默认值为空()
    {
        AiUnifiedSession session = new AiUnifiedSession();

        assertNull(session.getSessionId());
        assertNull(session.getProfileId());
        assertNull(session.getCallerNumber());
        assertNull(session.getChannelType());
        assertNull(session.getBizType());
        assertNull(session.getActiveFlag());
        assertNull(session.getActiveOwner());
    }
}
