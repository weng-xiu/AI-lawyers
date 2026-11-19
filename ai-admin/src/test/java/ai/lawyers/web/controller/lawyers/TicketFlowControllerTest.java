package ai.lawyers.web.controller.lawyers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import ai.lawyers.common.core.domain.AjaxResult;
import ai.lawyers.system.domain.lawyers.AiTicketFlowDefinition;
import ai.lawyers.system.domain.lawyers.AiTicketFlowLayout;
import ai.lawyers.system.service.lawyers.TicketFlowService;

/**
 * 工单配置化流转接口单元测试
 *
 * @author ai-lawyers
 */
@ExtendWith(MockitoExtension.class)
class TicketFlowControllerTest
{
    @Mock
    private TicketFlowService ticketFlowService;

    @InjectMocks
    private TicketFlowController controller;

    @Test
    void 动作查询携带当前角色()
    {
        List<String> roles = new ArrayList<String>();
        roles.add("admin");
        List<AiTicketFlowDefinition> actions = new ArrayList<AiTicketFlowDefinition>();
        when(ticketFlowService.currentRoleKeys()).thenReturn(roles);
        when(ticketFlowService.listAllowedActions("HOTLINE", "1", roles)).thenReturn(actions);

        AjaxResult result = controller.actions("HOTLINE", "1");

        assertSame(actions, result.get(AjaxResult.DATA_TAG));
        verify(ticketFlowService).listAllowedActions("HOTLINE", "1", roles);
    }

    @Test
    void 发布前校验透传结果()
    {
        Map<String, Object> report = new HashMap<String, Object>();
        report.put("valid", true);
        when(ticketFlowService.validateFlow("HOTLINE")).thenReturn(report);

        AjaxResult result = controller.validate("HOTLINE");

        assertSame(report, result.get(AjaxResult.DATA_TAG));
    }

    @Test
    void 画布布局查询与保存()
    {
        List<AiTicketFlowLayout> layouts = new ArrayList<AiTicketFlowLayout>();
        when(ticketFlowService.listLayout("HOTLINE")).thenReturn(layouts);
        when(ticketFlowService.saveLayout("HOTLINE", layouts)).thenReturn(2);

        assertSame(layouts, controller.getLayout("HOTLINE").get(AjaxResult.DATA_TAG));
        AjaxResult saved = controller.saveLayout("HOTLINE", layouts);

        assertEquals(200, saved.get(AjaxResult.CODE_TAG));
        verify(ticketFlowService).saveLayout("HOTLINE", layouts);
    }

    @Test
    void 规则列表与详情透传服务结果()
    {
        AiTicketFlowDefinition query = new AiTicketFlowDefinition();
        List<AiTicketFlowDefinition> definitions = new ArrayList<AiTicketFlowDefinition>();
        AiTicketFlowDefinition definition = new AiTicketFlowDefinition();
        when(ticketFlowService.listDefinitions(query)).thenReturn(definitions);
        when(ticketFlowService.getDefinition(5L)).thenReturn(definition);

        assertSame(definitions, controller.list(query).get(AjaxResult.DATA_TAG));
        assertSame(definition, controller.getInfo(5L).get(AjaxResult.DATA_TAG));
    }

    @Test
    void 新增规则返回影响行数()
    {
        AiTicketFlowDefinition rule = new AiTicketFlowDefinition();
        when(ticketFlowService.addDefinition(rule)).thenReturn(1);

        AjaxResult result = controller.add(rule);

        assertEquals(200, result.get(AjaxResult.CODE_TAG));
        verify(ticketFlowService).addDefinition(rule);
    }

    @Test
    void 修改规则失败返回错误码()
    {
        AiTicketFlowDefinition rule = new AiTicketFlowDefinition();
        when(ticketFlowService.updateDefinition(rule)).thenReturn(0);

        AjaxResult result = controller.edit(rule);

        assertEquals(500, result.get(AjaxResult.CODE_TAG));
        verify(ticketFlowService).updateDefinition(rule);
    }
}
