package ai.lawyers.system.service.lawyers.voice.copilot;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

/**
 * Copilot 相似工单推荐项测试：展示字段透传与内容片段为空。
 *
 * @author ai-lawyers
 */
class CopilotTicketTest
{
    @Test
    void 构造函数保存工单展示字段()
    {
        CopilotTicket ticket = new CopilotTicket(1L, "T20260101", "劳动合同争议", "1", "申请人主张经济补偿……");

        assertEquals(1L, ticket.getTicketId());
        assertEquals("T20260101", ticket.getTicketNo());
        assertEquals("劳动合同争议", ticket.getTitle());
        assertEquals("1", ticket.getStatus());
        assertEquals("申请人主张经济补偿……", ticket.getContentSnippet());
    }

    @Test
    void 允许内容片段为空()
    {
        CopilotTicket ticket = new CopilotTicket(2L, "T2", "标题", "0", null);

        assertNull(ticket.getContentSnippet());
        assertEquals("0", ticket.getStatus());
    }
}
