package ai.lawyers.system.domain.lawyers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Date;
import org.junit.jupiter.api.Test;

/**
 * 来电工单实体测试：属性读写、默认值与字符串输出。
 *
 * @author ai-lawyers
 */
class AiCallTicketTest
{
    @Test
    void 属性读写()
    {
        Date dueTime = new Date();
        AiCallTicket ticket = new AiCallTicket();
        ticket.setTicketId(1L);
        ticket.setTicketNo("TK20261118001");
        ticket.setRecordId(2L);
        ticket.setTitle("劳动关系咨询");
        ticket.setContent("咨询解除劳动合同补偿问题");
        ticket.setPriority("1");
        ticket.setDueTime(dueTime);
        ticket.setOvertimeFlag(0);
        ticket.setStatus("0");
        ticket.setAssignUserId(3L);
        ticket.setAssignUserName("张三");
        ticket.setProcessContent("已电话回复");

        assertEquals(1L, ticket.getTicketId());
        assertEquals("TK20261118001", ticket.getTicketNo());
        assertEquals(2L, ticket.getRecordId());
        assertEquals("劳动关系咨询", ticket.getTitle());
        assertEquals("咨询解除劳动合同补偿问题", ticket.getContent());
        assertEquals("1", ticket.getPriority());
        assertEquals(dueTime, ticket.getDueTime());
        assertEquals(0, ticket.getOvertimeFlag());
        assertEquals("0", ticket.getStatus());
        assertEquals(3L, ticket.getAssignUserId());
        assertEquals("张三", ticket.getAssignUserName());
        assertEquals("已电话回复", ticket.getProcessContent());
    }

    @Test
    void 外部协同字段读写()
    {
        Date transferTime = new Date();
        AiCallTicket ticket = new AiCallTicket();
        ticket.setCallerNumber("13800000000");
        ticket.setCallerNumberIndex("abc123");
        ticket.setCallerName("李四");
        ticket.setExternalType("LEGAL_AID");
        ticket.setExternalOrgId(9L);
        ticket.setExternalTicketNo("EXT-001");
        ticket.setExternalStatus("PENDING");
        ticket.setTransferTime(transferTime);
        ticket.setDirection("OUT");

        assertEquals("13800000000", ticket.getCallerNumber());
        assertEquals("abc123", ticket.getCallerNumberIndex());
        assertEquals("李四", ticket.getCallerName());
        assertEquals("LEGAL_AID", ticket.getExternalType());
        assertEquals(9L, ticket.getExternalOrgId());
        assertEquals("EXT-001", ticket.getExternalTicketNo());
        assertEquals("PENDING", ticket.getExternalStatus());
        assertEquals(transferTime, ticket.getTransferTime());
        assertEquals("OUT", ticket.getDirection());
    }

    @Test
    void 默认值为空()
    {
        AiCallTicket ticket = new AiCallTicket();

        assertNull(ticket.getTicketId());
        assertNull(ticket.getTicketNo());
        assertNull(ticket.getTitle());
        assertNull(ticket.getStatus());
        assertNull(ticket.getContentEmbedding());
        assertNull(ticket.getEmbeddingDim());
        assertNull(ticket.getTokenGroups());
        assertNull(ticket.getExternalType());
    }

    @Test
    void 字符串输出包含工单号与标题()
    {
        AiCallTicket ticket = new AiCallTicket();
        ticket.setTicketNo("TK20261118002");
        ticket.setTitle("婚姻家事咨询");

        String text = ticket.toString();
        assertTrue(text.contains("TK20261118002"));
        assertTrue(text.contains("婚姻家事咨询"));
    }
}
