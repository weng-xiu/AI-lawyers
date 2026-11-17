package ai.lawyers.system.domain.lawyers.agent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Date;
import org.junit.jupiter.api.Test;

/**
 * AI智能体对话消息实体测试：属性读写、默认值与字符串输出。
 *
 * @author ai-lawyers
 */
class AiAgentMessageTest
{
    @Test
    void 属性读写()
    {
        Date createTime = new Date();
        AiAgentMessage message = new AiAgentMessage();
        message.setMessageId(1L);
        message.setSessionId("sess-001");
        message.setAgentId(2L);
        message.setFlowId(3L);
        message.setNodeId(4L);
        message.setRecordId(5L);
        message.setCallerNumber("13300000000");
        message.setRole("assistant");
        message.setContent("您好，请问有什么可以帮您");
        message.setHandoff("1");
        message.setReason("客户要求人工");
        message.setKnowledgeRefs("11,12");
        message.setTurnNo(2);
        message.setCreateTime(createTime);

        assertEquals(1L, message.getMessageId());
        assertEquals("sess-001", message.getSessionId());
        assertEquals(2L, message.getAgentId());
        assertEquals(3L, message.getFlowId());
        assertEquals(4L, message.getNodeId());
        assertEquals(5L, message.getRecordId());
        assertEquals("13300000000", message.getCallerNumber());
        assertEquals("assistant", message.getRole());
        assertEquals("您好，请问有什么可以帮您", message.getContent());
        assertEquals("1", message.getHandoff());
        assertEquals("客户要求人工", message.getReason());
        assertEquals("11,12", message.getKnowledgeRefs());
        assertEquals(2, message.getTurnNo());
        assertEquals(createTime, message.getCreateTime());
    }

    @Test
    void 默认值为空()
    {
        AiAgentMessage message = new AiAgentMessage();

        assertNull(message.getMessageId());
        assertNull(message.getSessionId());
        assertNull(message.getRole());
        assertNull(message.getContent());
        assertNull(message.getHandoff());
        assertNull(message.getTurnNo());
        assertNull(message.getCreateTime());
    }

    @Test
    void 字符串输出包含角色与内容()
    {
        AiAgentMessage message = new AiAgentMessage();
        message.setRole("user");
        message.setContent("我想咨询离婚程序");

        String text = message.toString();
        assertTrue(text.contains("user"));
        assertTrue(text.contains("我想咨询离婚程序"));
    }
}
