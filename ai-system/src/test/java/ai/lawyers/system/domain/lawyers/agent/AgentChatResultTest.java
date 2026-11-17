package ai.lawyers.system.domain.lawyers.agent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * 智能体对话结果测试：默认值、三参构造器与扩展字段。
 *
 * @author ai-lawyers
 */
class AgentChatResultTest
{
    @Test
    void 默认构造_字段为初始值()
    {
        AgentChatResult result = new AgentChatResult();

        assertNull(result.getReply());
        assertFalse(result.isHandoff());
        assertNull(result.getReason());
        assertNull(result.getCategoryId());
        assertNull(result.getKnowledgeRefs());
        assertEquals(0, result.getTurnNo());
    }

    @Test
    void 三参构造器()
    {
        AgentChatResult result = new AgentChatResult("您好，请描述您的问题", true, "超出知识范围");

        assertEquals("您好，请描述您的问题", result.getReply());
        assertTrue(result.isHandoff());
        assertEquals("超出知识范围", result.getReason());
    }

    @Test
    void 扩展字段读写()
    {
        AgentChatResult result = new AgentChatResult();
        result.setReply("回复");
        result.setHandoff(true);
        result.setReason("原因");
        result.setCategoryId(8L);
        result.setKnowledgeRefs("1,2,3");
        result.setTurnNo(5);

        assertEquals("回复", result.getReply());
        assertTrue(result.isHandoff());
        assertEquals("原因", result.getReason());
        assertEquals(8L, result.getCategoryId());
        assertEquals("1,2,3", result.getKnowledgeRefs());
        assertEquals(5, result.getTurnNo());
    }
}
