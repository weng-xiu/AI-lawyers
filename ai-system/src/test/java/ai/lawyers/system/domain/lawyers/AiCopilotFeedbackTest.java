package ai.lawyers.system.domain.lawyers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.Date;
import org.junit.jupiter.api.Test;

/**
 * Copilot 建议采纳行为埋点实体测试：属性读写、默认值与常量定义。
 *
 * @author ai-lawyers
 */
class AiCopilotFeedbackTest
{
    @Test
    void 属性读写()
    {
        Date createTime = new Date();
        AiCopilotFeedback feedback = new AiCopilotFeedback();
        feedback.setFeedbackId(1L);
        feedback.setRecordId(2L);
        feedback.setSessionId("sess-001");
        feedback.setAgentId(3L);
        feedback.setAgentName("坐席丙");
        feedback.setDeptId(4L);
        feedback.setSuggestionType(AiCopilotFeedback.TYPE_LAW);
        feedback.setSuggestionRef("chunk-88");
        feedback.setAction(AiCopilotFeedback.ACTION_ADOPT);
        feedback.setCreateTime(createTime);

        assertEquals(1L, feedback.getFeedbackId());
        assertEquals(2L, feedback.getRecordId());
        assertEquals("sess-001", feedback.getSessionId());
        assertEquals(3L, feedback.getAgentId());
        assertEquals("坐席丙", feedback.getAgentName());
        assertEquals(4L, feedback.getDeptId());
        assertEquals("LAW", feedback.getSuggestionType());
        assertEquals("chunk-88", feedback.getSuggestionRef());
        assertEquals("ADOPT", feedback.getAction());
        assertEquals(createTime, feedback.getCreateTime());
    }

    @Test
    void 默认值为空()
    {
        AiCopilotFeedback feedback = new AiCopilotFeedback();

        assertNull(feedback.getFeedbackId());
        assertNull(feedback.getRecordId());
        assertNull(feedback.getSessionId());
        assertNull(feedback.getSuggestionType());
        assertNull(feedback.getAction());
        assertNull(feedback.getCreateTime());
    }

    @Test
    void 类型与行为常量定义()
    {
        assertEquals("ELEMENT", AiCopilotFeedback.TYPE_ELEMENT);
        assertEquals("LAW", AiCopilotFeedback.TYPE_LAW);
        assertEquals("TICKET", AiCopilotFeedback.TYPE_TICKET);
        assertEquals("ADOPT", AiCopilotFeedback.ACTION_ADOPT);
        assertEquals("MODIFY", AiCopilotFeedback.ACTION_MODIFY);
        assertEquals("IGNORE", AiCopilotFeedback.ACTION_IGNORE);
    }
}
