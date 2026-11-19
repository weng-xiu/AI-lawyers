package ai.lawyers.web.controller.lawyers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import com.github.pagehelper.PageHelper;
import ai.lawyers.common.core.domain.AjaxResult;
import ai.lawyers.framework.web.service.PermissionService;
import ai.lawyers.system.domain.lawyers.AiCallRecord;
import ai.lawyers.system.domain.lawyers.AiCallTicket;
import ai.lawyers.system.domain.lawyers.AiLegalKnowledge;
import ai.lawyers.system.service.lawyers.IAiCallRecordService;
import ai.lawyers.system.service.lawyers.IAiCallTicketService;
import ai.lawyers.system.service.lawyers.IAiLegalKnowledgeService;

/**
 * 命令面板聚合搜索接口单元测试
 *
 * @author ai-lawyers
 */
@ExtendWith(MockitoExtension.class)
class AiPaletteSearchControllerTest
{
    @Mock
    private IAiCallTicketService ticketService;

    @Mock
    private IAiLegalKnowledgeService knowledgeService;

    @Mock
    private IAiCallRecordService callRecordService;

    @Mock
    private PermissionService permissionService;

    @InjectMocks
    private AiPaletteSearchController controller;

    @SuppressWarnings("unchecked")
    private Map<String, Object> dataOf(AjaxResult result)
    {
        return (Map<String, Object>) result.get(AjaxResult.DATA_TAG);
    }

    @Test
    void 空关键词直接返回空结果()
    {
        AjaxResult result = controller.palette("  ", 5);

        assertTrue(dataOf(result).isEmpty());
        verify(permissionService, never()).hasPermi(org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void 无权限时各域均跳过()
    {
        when(permissionService.hasPermi(org.mockito.ArgumentMatchers.anyString())).thenReturn(false);

        AjaxResult result = controller.palette("劳动", 5);

        assertTrue(dataOf(result).isEmpty());
    }

    @Test
    void 三个域均命中并脱敏展示()
    {
        List<AiCallTicket> tickets = new ArrayList<AiCallTicket>();
        AiCallTicket ticket = new AiCallTicket();
        ticket.setTicketId(11L);
        ticket.setTicketNo("TK-001");
        ticket.setTitle("劳动仲裁咨询");
        tickets.add(ticket);

        List<AiLegalKnowledge> knowledges = new ArrayList<AiLegalKnowledge>();
        AiLegalKnowledge knowledge = new AiLegalKnowledge();
        knowledge.setKnowledgeId(22L);
        knowledge.setTitle("劳动仲裁时效");
        knowledge.setKeywords("仲裁,时效");
        knowledges.add(knowledge);

        List<AiCallRecord> records = new ArrayList<AiCallRecord>();
        AiCallRecord record = new AiCallRecord();
        record.setRecordId(33L);
        record.setCallerNumber("13800000000");
        record.setCallTime(new Date());
        records.add(record);

        when(permissionService.hasPermi("lawyers:call:ticket:list")).thenReturn(true);
        when(permissionService.hasPermi("lawyers:knowledge:list")).thenReturn(true);
        when(permissionService.hasPermi("lawyers:call:record:list")).thenReturn(true);
        when(ticketService.selectAiCallTicketList(org.mockito.ArgumentMatchers.any(AiCallTicket.class)))
                .thenReturn(tickets);
        when(knowledgeService.selectAiLegalKnowledgeList(org.mockito.ArgumentMatchers.any(AiLegalKnowledge.class)))
                .thenReturn(knowledges);
        when(callRecordService.selectAiCallRecordList(org.mockito.ArgumentMatchers.any(AiCallRecord.class)))
                .thenReturn(records);

        try (MockedStatic<com.github.pagehelper.page.PageMethod> pageHelper =
                mockStatic(com.github.pagehelper.page.PageMethod.class))
        {
            AjaxResult result = controller.palette("劳动", 5);

            Map<String, Object> data = dataOf(result);
            assertEquals(3, data.size());
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> ticketItems = (List<Map<String, Object>>) data.get("tickets");
            assertEquals(11L, ticketItems.get(0).get("id"));
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> knowledgeItems = (List<Map<String, Object>>) data.get("knowledges");
            assertEquals(22L, knowledgeItems.get(0).get("id"));
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> recordItems = (List<Map<String, Object>>) data.get("records");
            assertEquals("138****0000", recordItems.get(0).get("title"));
            assertFalse(recordItems.get(0).get("sub").toString().isEmpty());
        }
        verify(permissionService).hasPermi(eq("lawyers:call:ticket:list"));
    }

    @Test
    void 上限被限制在1到10之间()
    {
        when(permissionService.hasPermi("lawyers:call:ticket:list")).thenReturn(true);
        when(permissionService.hasPermi("lawyers:knowledge:list")).thenReturn(false);
        when(permissionService.hasPermi("lawyers:call:record:list")).thenReturn(false);
        when(ticketService.selectAiCallTicketList(org.mockito.ArgumentMatchers.any(AiCallTicket.class)))
                .thenReturn(new ArrayList<AiCallTicket>());

        try
        {
            controller.palette("合同", 50);
            assertEquals(1, PageHelper.getLocalPage().getPageNum());
            assertEquals(10, PageHelper.getLocalPage().getPageSize());

            controller.palette("合同", 0);
            assertEquals(1, PageHelper.getLocalPage().getPageSize());
        }
        finally
        {
            PageHelper.clearPage();
        }
    }
}
