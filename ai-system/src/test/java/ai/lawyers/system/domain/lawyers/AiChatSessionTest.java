package ai.lawyers.system.domain.lawyers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Date;
import org.junit.jupiter.api.Test;

/**
 * 图文会话实体测试：属性读写、默认值与字符串输出。
 *
 * @author ai-lawyers
 */
class AiChatSessionTest
{
    @Test
    void 属性读写()
    {
        Date lastMessageTime = new Date();
        AiChatSession session = new AiChatSession();
        session.setSessionId(1L);
        session.setSessionNo("CHAT20261118001");
        session.setCustomerName("李四");
        session.setCustomerPhone("13900000000");
        session.setCustomerAvatar("https://example.com/avatar.png");
        session.setUserId(8L);
        session.setAssignee("王律师");
        session.setChannel("1");
        session.setStatus("0");
        session.setLastMessage("请问劳动仲裁需要准备哪些材料？");
        session.setLastMessageTime(lastMessageTime);
        session.setUnreadCount(3);

        assertEquals(1L, session.getSessionId());
        assertEquals("CHAT20261118001", session.getSessionNo());
        assertEquals("李四", session.getCustomerName());
        assertEquals("13900000000", session.getCustomerPhone());
        assertEquals("https://example.com/avatar.png", session.getCustomerAvatar());
        assertEquals(8L, session.getUserId());
        assertEquals("王律师", session.getAssignee());
        assertEquals("1", session.getChannel());
        assertEquals("0", session.getStatus());
        assertEquals("请问劳动仲裁需要准备哪些材料？", session.getLastMessage());
        assertEquals(lastMessageTime, session.getLastMessageTime());
        assertEquals(3, session.getUnreadCount());
    }

    @Test
    void 默认值为空()
    {
        AiChatSession session = new AiChatSession();

        assertNull(session.getSessionId());
        assertNull(session.getSessionNo());
        assertNull(session.getCustomerName());
        assertNull(session.getCustomerPhone());
        assertNull(session.getUserId());
        assertNull(session.getAssignee());
        assertNull(session.getChannel());
        assertNull(session.getStatus());
        assertNull(session.getLastMessage());
        assertNull(session.getUnreadCount());
    }

    @Test
    void 字符串输出包含会话编号与客户姓名()
    {
        AiChatSession session = new AiChatSession();
        session.setSessionNo("CHAT001");
        session.setCustomerName("赵六");

        String text = session.toString();
        assertTrue(text.contains("CHAT001"));
        assertTrue(text.contains("赵六"));
    }
}
