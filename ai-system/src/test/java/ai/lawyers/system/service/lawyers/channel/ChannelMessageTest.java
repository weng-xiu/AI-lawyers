package ai.lawyers.system.service.lawyers.channel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.Date;

import org.junit.jupiter.api.Test;

/**
 * 跨渠道归一化消息测试：字段透传与可空字段。
 *
 * @author ai-lawyers
 */
class ChannelMessageTest
{
    @Test
    void 初始状态_字段均为空()
    {
        ChannelMessage message = new ChannelMessage();

        assertNull(message.getChannelType());
        assertNull(message.getChannelUid());
        assertNull(message.getMsgType());
        assertNull(message.getMsgId());
        assertNull(message.getCreateTimeSec());
        assertNull(message.getReceivedTime());
    }

    @Test
    void setter_文本消息字段透传()
    {
        ChannelMessage message = new ChannelMessage();
        Date received = new Date(1700000000000L);

        message.setChannelType("WECHAT_MP");
        message.setChannelUid("openid-123");
        message.setChannelNickname("张三");
        message.setMsgType("TEXT");
        message.setContent("劳动仲裁怎么申请");
        message.setMsgId("msg-1");
        message.setCreateTimeSec(1700000000L);
        message.setReceivedTime(received);
        message.setRawPayload("{\"content\":\"劳动仲裁怎么申请\"}");

        assertEquals("WECHAT_MP", message.getChannelType());
        assertEquals("openid-123", message.getChannelUid());
        assertEquals("张三", message.getChannelNickname());
        assertEquals("TEXT", message.getMsgType());
        assertEquals("劳动仲裁怎么申请", message.getContent());
        assertEquals("msg-1", message.getMsgId());
        assertEquals(Long.valueOf(1700000000L), message.getCreateTimeSec());
        assertEquals(received, message.getReceivedTime());
        assertEquals("{\"content\":\"劳动仲裁怎么申请\"}", message.getRawPayload());
    }

    @Test
    void 事件消息_内容承载事件key且消息ID可为空()
    {
        ChannelMessage message = new ChannelMessage();
        message.setChannelType("WECHAT_MINI");
        message.setMsgType("EVENT");
        message.setContent("subscribe");

        assertEquals("EVENT", message.getMsgType());
        assertEquals("subscribe", message.getContent());
        assertNull(message.getMsgId());
    }
}
