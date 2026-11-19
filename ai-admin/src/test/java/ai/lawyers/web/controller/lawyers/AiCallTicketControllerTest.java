package ai.lawyers.web.controller.lawyers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import ai.lawyers.common.core.domain.AjaxResult;
import ai.lawyers.common.core.domain.entity.SysUser;
import ai.lawyers.common.core.domain.model.LoginUser;
import ai.lawyers.common.core.page.TableDataInfo;
import ai.lawyers.common.utils.SecurityUtils;
import ai.lawyers.system.domain.lawyers.AiCallTicket;
import ai.lawyers.system.service.lawyers.IAiCallTicketService;

/**
 * 工单接口单元测试
 *
 * @author ai-lawyers
 */
@ExtendWith(MockitoExtension.class)
class AiCallTicketControllerTest
{
    @Mock
    private IAiCallTicketService aiCallTicketService;

    @InjectMocks
    private AiCallTicketController controller;

    @AfterEach
    void tearDown()
    {
        RequestContextHolder.resetRequestAttributes();
    }

    private LoginUser loginUser(String username)
    {
        SysUser user = new SysUser();
        user.setUserName(username);
        return new LoginUser(1L, 2L, user, null);
    }

    private AiCallTicket ticket(Long ticketId)
    {
        AiCallTicket ticket = new AiCallTicket();
        ticket.setTicketId(ticketId);
        ticket.setTicketNo("T-" + ticketId);
        ticket.setTitle("合同咨询");
        return ticket;
    }

    @Test
    void 工单列表分页返回()
    {
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(new MockHttpServletRequest()));
        AiCallTicket query = new AiCallTicket();
        List<AiCallTicket> list = new ArrayList<AiCallTicket>();
        list.add(ticket(5L));
        when(aiCallTicketService.selectAiCallTicketList(query)).thenReturn(list);

        TableDataInfo table = controller.list(query);

        assertEquals(200, table.getCode());
        assertSame(list, table.getRows());
    }

    @Test
    void 导出工单Excel()
    {
        when(aiCallTicketService.selectAiCallTicketList(any(AiCallTicket.class)))
                .thenReturn(List.of(ticket(5L)));
        MockHttpServletResponse response = new MockHttpServletResponse();

        controller.export(response, new AiCallTicket());

        assertTrue(response.getContentType().contains("spreadsheetml"));
        assertTrue(response.getContentAsByteArray().length > 0);
    }

    @Test
    void 工单详情与按处理人查询()
    {
        AiCallTicket ticket = ticket(5L);
        List<AiCallTicket> list = List.of(ticket);
        when(aiCallTicketService.selectAiCallTicketByTicketId(5L)).thenReturn(ticket);
        when(aiCallTicketService.selectAiCallTicketByAssignUserId(3L)).thenReturn(list);

        assertSame(ticket, controller.getInfo(5L).get(AjaxResult.DATA_TAG));
        assertSame(list, controller.getTicketsByAssignUserId(3L).get(AjaxResult.DATA_TAG));
    }

    @Test
    void 新增工单返回新工单或失败提示()
    {
        AiCallTicket success = ticket(5L);
        AiCallTicket failed = ticket(6L);
        when(aiCallTicketService.insertAiCallTicket(success)).thenReturn(1);
        when(aiCallTicketService.insertAiCallTicket(failed)).thenReturn(0);

        try (MockedStatic<SecurityUtils> mocked = mockStatic(SecurityUtils.class))
        {
            mocked.when(SecurityUtils::getLoginUser).thenReturn(loginUser("agent"));
            AjaxResult ok = controller.add(success);
            assertEquals(200, ok.get(AjaxResult.CODE_TAG));
            assertSame(success, ok.get(AjaxResult.DATA_TAG));

            AjaxResult error = controller.add(failed);
            assertEquals(500, error.get(AjaxResult.CODE_TAG));
            assertEquals("工单创建失败", error.get(AjaxResult.MSG_TAG));
        }
        assertEquals("agent", success.getCreateBy());
    }

    @Test
    void 修改与删除工单()
    {
        AiCallTicket ticket = ticket(5L);
        Long[] ids = new Long[] { 5L };
        when(aiCallTicketService.updateAiCallTicket(ticket)).thenReturn(1);
        when(aiCallTicketService.deleteAiCallTicketByTicketIds(ids)).thenReturn(1);

        try (MockedStatic<SecurityUtils> mocked = mockStatic(SecurityUtils.class))
        {
            mocked.when(SecurityUtils::getLoginUser).thenReturn(loginUser("agent"));
            assertEquals(200, controller.edit(ticket).get(AjaxResult.CODE_TAG));
        }
        assertEquals("agent", ticket.getUpdateBy());
        assertEquals(200, controller.remove(ids).get(AjaxResult.CODE_TAG));
    }

    @Test
    void 工单处理成功与失败()
    {
        Map<String, Object> params = new HashMap<String, Object>();
        params.put("ticketId", 9);
        params.put("processContent", "已电话回复");
        params.put("assignUserId", 3);
        params.put("assignUserName", "张律师");
        when(aiCallTicketService.updateTicketProcess(9L, "已电话回复", 3L, "张律师")).thenReturn(1);
        when(aiCallTicketService.updateTicketProcess(10L, "无进展", null, null)).thenReturn(0);

        assertEquals("工单处理成功", controller.processTicket(params).get(AjaxResult.MSG_TAG));

        Map<String, Object> failParams = new HashMap<String, Object>();
        failParams.put("ticketId", 10);
        failParams.put("processContent", "无进展");
        AjaxResult failed = controller.processTicket(failParams);
        assertEquals(500, failed.get(AjaxResult.CODE_TAG));
        assertEquals("工单处理失败", failed.get(AjaxResult.MSG_TAG));
    }

    @Test
    void 工单处理非法参数返回错误()
    {
        Map<String, Object> params = new HashMap<String, Object>();
        params.put("ticketId", "abc");
        params.put("processContent", "内容");

        AjaxResult result = controller.processTicket(params);

        assertEquals(500, result.get(AjaxResult.CODE_TAG));
        assertTrue(String.valueOf(result.get(AjaxResult.MSG_TAG)).contains("abc"));
    }

    @Test
    void 工单完成与归档()
    {
        Map<String, Object> complete = new LinkedHashMap<String, Object>();
        complete.put("ticketId", 9);
        when(aiCallTicketService.updateTicketStatus(9L, "2")).thenReturn(1);
        when(aiCallTicketService.updateTicketStatus(10L, "2")).thenReturn(0);
        when(aiCallTicketService.archiveTicket(11L)).thenReturn(1);

        assertEquals("工单已完成", controller.completeTicket(complete).get(AjaxResult.MSG_TAG));

        Map<String, Object> completeFail = new LinkedHashMap<String, Object>();
        completeFail.put("ticketId", 10);
        assertEquals("工单完成失败", controller.completeTicket(completeFail).get(AjaxResult.MSG_TAG));

        Map<String, Object> archive = new LinkedHashMap<String, Object>();
        archive.put("ticketId", 11);
        assertEquals("工单已归档", controller.archiveTicket(archive).get(AjaxResult.MSG_TAG));

        Map<String, Object> archiveFail = new LinkedHashMap<String, Object>();
        archiveFail.put("ticketId", "bad");
        assertEquals(500, controller.archiveTicket(archiveFail).get(AjaxResult.CODE_TAG));
    }

    @Test
    void 生成工单号与流转记录()
    {
        List<Map<String, Object>> timeline = new ArrayList<Map<String, Object>>();
        when(aiCallTicketService.generateTicketNo()).thenReturn("T20261120001");
        when(aiCallTicketService.selectTicketTimeline(9L)).thenReturn(timeline);

        assertEquals("T20261120001", controller.generateTicketNo().get(AjaxResult.MSG_TAG));
        assertSame(timeline, controller.timeline(9L).get(AjaxResult.DATA_TAG));
        verify(aiCallTicketService).selectTicketTimeline(9L);
    }
}
