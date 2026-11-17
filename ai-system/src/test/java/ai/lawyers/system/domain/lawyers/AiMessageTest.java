package ai.lawyers.system.domain.lawyers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Date;
import org.junit.jupiter.api.Test;

/**
 * 站内信消息实体测试：属性读写、默认值与字符串输出。
 *
 * @author ai-lawyers
 */
class AiMessageTest
{
    @Test
    void 属性读写()
    {
        Date readTime = new Date();
        AiMessage message = new AiMessage();
        message.setMessageId(1L);
        message.setReceiverUserId(2L);
        message.setMsgType("3");
        message.setTitle("风险预警");
        message.setContent("检测到高风险来电");
        message.setBizType("warning");
        message.setBizId(3L);
        message.setIsRead("1");
        message.setReadTime(readTime);
        message.setSender("system");
        message.setPriority("1");

        assertEquals(1L, message.getMessageId());
        assertEquals(2L, message.getReceiverUserId());
        assertEquals("3", message.getMsgType());
        assertEquals("风险预警", message.getTitle());
        assertEquals("检测到高风险来电", message.getContent());
        assertEquals("warning", message.getBizType());
        assertEquals(3L, message.getBizId());
        assertEquals("1", message.getIsRead());
        assertEquals(readTime, message.getReadTime());
        assertEquals("system", message.getSender());
        assertEquals("1", message.getPriority());
    }

    @Test
    void 默认值为空()
    {
        AiMessage message = new AiMessage();

        assertNull(message.getMessageId());
        assertNull(message.getReceiverUserId());
        assertNull(message.getTitle());
        assertNull(message.getBizType());
        assertNull(message.getIsRead());
        assertNull(message.getPriority());
    }

    @Test
    void 字符串输出包含标题与内容()
    {
        AiMessage message = new AiMessage();
        message.setTitle("工单分配");
        message.setContent("您有一条新工单待处理");

        String text = message.toString();
        assertTrue(text.contains("工单分配"));
        assertTrue(text.contains("您有一条新工单待处理"));
    }
}
