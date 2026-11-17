package ai.lawyers.system.domain.lawyers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Date;
import org.junit.jupiter.api.Test;

/**
 * 聊天消息实体测试：属性读写、默认值与字符串输出。
 *
 * @author ai-lawyers
 */
class AiChatMessageTest
{
    @Test
    void 属性读写()
    {
        Date sendTime = new Date();
        AiChatMessage message = new AiChatMessage();
        message.setMessageId(1L);
        message.setSessionId(2L);
        message.setSenderType("1");
        message.setSenderName("客户甲");
        message.setContent("请问如何申请劳动仲裁");
        message.setMsgType("1");
        message.setAttachmentUrl("/upload/chat/a.png");
        message.setIsRead("0");
        message.setSendTime(sendTime);

        assertEquals(1L, message.getMessageId());
        assertEquals(2L, message.getSessionId());
        assertEquals("1", message.getSenderType());
        assertEquals("客户甲", message.getSenderName());
        assertEquals("请问如何申请劳动仲裁", message.getContent());
        assertEquals("1", message.getMsgType());
        assertEquals("/upload/chat/a.png", message.getAttachmentUrl());
        assertEquals("0", message.getIsRead());
        assertEquals(sendTime, message.getSendTime());
    }

    @Test
    void 默认值为空()
    {
        AiChatMessage message = new AiChatMessage();

        assertNull(message.getMessageId());
        assertNull(message.getSessionId());
        assertNull(message.getSenderType());
        assertNull(message.getContent());
        assertNull(message.getIsRead());
        assertNull(message.getSendTime());
    }

    @Test
    void 字符串输出包含发送人与内容()
    {
        AiChatMessage message = new AiChatMessage();
        message.setSenderName("客服乙");
        message.setContent("已为您转接值班律师");

        String text = message.toString();
        assertTrue(text.contains("客服乙"));
        assertTrue(text.contains("已为您转接值班律师"));
    }
}
