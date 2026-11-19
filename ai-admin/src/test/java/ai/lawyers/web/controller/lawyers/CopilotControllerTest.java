package ai.lawyers.web.controller.lawyers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import ai.lawyers.common.core.domain.AjaxResult;
import ai.lawyers.system.domain.lawyers.AiCallTicket;
import ai.lawyers.system.domain.lawyers.AiCopilotFeedback;
import ai.lawyers.system.domain.lawyers.AiLegalKnowledgeChunk;
import ai.lawyers.system.mapper.lawyers.AiLegalKnowledgeChunkMapper;
import ai.lawyers.system.service.lawyers.IAiCallTicketService;
import ai.lawyers.system.service.lawyers.voice.copilot.IAiCopilotFeedbackService;

/**
 * 坐席 Copilot 深化接口单元测试
 *
 * @author ai-lawyers
 */
@ExtendWith(MockitoExtension.class)
class CopilotControllerTest
{
    @Mock
    private IAiCopilotFeedbackService feedbackService;

    @Mock
    private AiLegalKnowledgeChunkMapper chunkMapper;

    @Mock
    private IAiCallTicketService ticketService;

    @InjectMocks
    private CopilotController controller;

    private AiCallTicket ticket(Long ticketId, String status, String assignUserName, String processContent)
    {
        AiCallTicket ticket = new AiCallTicket();
        ticket.setTicketId(ticketId);
        ticket.setTicketNo("T-" + ticketId);
        ticket.setTitle("合同咨询");
        ticket.setStatus(status);
        ticket.setAssignUserName(assignUserName);
        ticket.setProcessContent(processContent);
        ticket.setCreateTime(new Date(1700000000000L));
        ticket.setProcessTime(new Date(1700000100000L));
        return ticket;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> queryData(Long ticketId)
    {
        return (Map<String, Object>) controller.queryTicket(ticketId).get(AjaxResult.DATA_TAG);
    }

    @Test
    void 法条溯源详情校验与透传()
    {
        assertEquals("chunkId 不能为空", controller.chunk(null).get(AjaxResult.MSG_TAG));

        when(chunkMapper.selectChunkById(3L)).thenReturn(null);
        assertEquals("法条内容不存在或已下线", controller.chunk(3L).get(AjaxResult.MSG_TAG));

        AiLegalKnowledgeChunk chunk = new AiLegalKnowledgeChunk();
        chunk.setChunkId(4L);
        when(chunkMapper.selectChunkById(4L)).thenReturn(chunk);
        assertSame(chunk, controller.chunk(4L).get(AjaxResult.DATA_TAG));
    }

    @Test
    void 建议行为埋点恒成功()
    {
        AiCopilotFeedback feedback = new AiCopilotFeedback();

        AjaxResult result = controller.feedback(feedback);

        assertEquals(200, result.get(AjaxResult.CODE_TAG));
        verify(feedbackService).record(feedback);
    }

    @Test
    void 采纳率统计默认回溯三十天与按坐席维度()
    {
        Map<String, Object> total = new LinkedHashMap<String, Object>();
        List<Map<String, Object>> byAgent = new ArrayList<Map<String, Object>>();
        when(feedbackService.adoptionTotal(any(Date.class), any(Date.class))).thenReturn(total);
        when(feedbackService.adoptionByAgent(any(Date.class), any(Date.class))).thenReturn(byAgent);

        assertSame(total, controller.adoption(null, null, null).get(AjaxResult.DATA_TAG));
        assertSame(byAgent, controller.adoption("AGENT", null, null).get(AjaxResult.DATA_TAG));

        ArgumentCaptor<Date> begin = ArgumentCaptor.forClass(Date.class);
        ArgumentCaptor<Date> end = ArgumentCaptor.forClass(Date.class);
        verify(feedbackService).adoptionTotal(begin.capture(), end.capture());
        assertEquals(30L * 24 * 3600 * 1000, end.getValue().getTime() - begin.getValue().getTime());
    }

    @Test
    void 采纳率统计透传显式区间()
    {
        Date begin = new Date(1700000000000L);
        Date end = new Date(1700086400000L);
        Map<String, Object> total = new LinkedHashMap<String, Object>();
        when(feedbackService.adoptionTotal(begin, end)).thenReturn(total);

        assertSame(total, controller.adoption("total", begin, end).get(AjaxResult.DATA_TAG));
        verify(feedbackService).adoptionTotal(begin, end);
    }

    @Test
    void 确认建单校验空标题与空内容()
    {
        assertEquals("工单标题与内容不能为空", controller.createTicket(null).get(AjaxResult.MSG_TAG));
        assertEquals("工单标题与内容不能为空", controller.createTicket(new AiCallTicket()).get(AjaxResult.MSG_TAG));

        AiCallTicket blankTitle = new AiCallTicket();
        blankTitle.setTitle("  ");
        blankTitle.setContent("内容");
        assertEquals("工单标题与内容不能为空", controller.createTicket(blankTitle).get(AjaxResult.MSG_TAG));

        AiCallTicket blankContent = new AiCallTicket();
        blankContent.setTitle("标题");
        blankContent.setContent("");
        assertEquals("工单标题与内容不能为空", controller.createTicket(blankContent).get(AjaxResult.MSG_TAG));
    }

    @Test
    void 确认建单只接收白名单字段()
    {
        AiCallTicket draft = new AiCallTicket();
        draft.setTitle(" 咨询合同纠纷 ");
        draft.setContent(" 请协助处理 ");
        draft.setRecordId(88L);
        draft.setPriority(null);
        draft.setStatus("9");
        draft.setAssignUserName("伪造处理人");

        AjaxResult result = controller.createTicket(draft);

        assertEquals(200, result.get(AjaxResult.CODE_TAG));
        ArgumentCaptor<AiCallTicket> captor = ArgumentCaptor.forClass(AiCallTicket.class);
        verify(ticketService).insertAiCallTicket(captor.capture());
        AiCallTicket saved = captor.getValue();
        assertEquals("咨询合同纠纷", saved.getTitle());
        assertEquals("请协助处理", saved.getContent());
        assertEquals("2", saved.getPriority());
        assertEquals(88L, saved.getRecordId());
        assertEquals("0", saved.getStatus());
        assertNull(saved.getAssignUserName());
        assertSame(saved, result.get(AjaxResult.DATA_TAG));
    }

    @Test
    void 确认建单保留指定优先级()
    {
        AiCallTicket draft = new AiCallTicket();
        draft.setTitle("标题");
        draft.setContent("内容");
        draft.setPriority("1");

        controller.createTicket(draft);

        ArgumentCaptor<AiCallTicket> captor = ArgumentCaptor.forClass(AiCallTicket.class);
        verify(ticketService).insertAiCallTicket(captor.capture());
        assertEquals("1", captor.getValue().getPriority());
    }

    @Test
    void 查询工单进度参数与不存在提示()
    {
        assertEquals("ticketId 不能为空", controller.queryTicket(null).get(AjaxResult.MSG_TAG));

        when(ticketService.selectAiCallTicketByTicketId(7L)).thenReturn(null);
        assertEquals("未查询到该工单", controller.queryTicket(7L).get(AjaxResult.MSG_TAG));
    }

    @Test
    void 查询工单进度话术分支()
    {
        when(ticketService.selectAiCallTicketByTicketId(11L)).thenReturn(ticket(11L, null, null, null));
        when(ticketService.selectAiCallTicketByTicketId(12L)).thenReturn(ticket(12L, "1", "张律师", "已受理"));
        when(ticketService.selectAiCallTicketByTicketId(13L)).thenReturn(ticket(13L, "2", null, null));
        when(ticketService.selectAiCallTicketByTicketId(14L)).thenReturn(ticket(14L, "3", null, null));

        Map<String, Object> pending = queryData(11L);
        assertEquals("待处理", pending.get("statusName"));
        assertEquals("T-11", pending.get("ticketNo"));
        assertEquals("合同咨询", pending.get("title"));
        String pendingSpeech = String.valueOf(pending.get("speech"));
        assertTrue(pendingSpeech.startsWith("您工单号为T-11的咨询，当前状态是待处理"));
        assertTrue(pendingSpeech.endsWith("请您耐心等待，我们会尽快处理"));

        String processingSpeech = String.valueOf(queryData(12L).get("speech"));
        assertTrue(processingSpeech.contains("当前状态是处理中"));
        assertTrue(processingSpeech.contains("由张律师负责跟进"));
        assertTrue(processingSpeech.contains("最新办理情况是：已受理"));
        assertTrue(processingSpeech.endsWith("请您耐心等待，我们会尽快处理"));

        Map<String, Object> finished = queryData(13L);
        assertEquals("已完成", finished.get("statusName"));
        assertTrue(String.valueOf(finished.get("speech")).endsWith("已办理完结，请您留意后续反馈"));

        assertEquals("已归档", queryData(14L).get("statusName"));
    }
}
