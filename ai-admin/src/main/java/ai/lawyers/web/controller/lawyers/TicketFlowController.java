package ai.lawyers.web.controller.lawyers;

import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ai.lawyers.common.core.controller.BaseController;
import ai.lawyers.common.core.domain.AjaxResult;
import ai.lawyers.system.domain.lawyers.AiTicketFlowDefinition;
import ai.lawyers.system.service.lawyers.TicketFlowService;

/**
 * P1-6：工单配置化流转 DSL 查询入口——前端按当前工单状态动态渲染操作按钮。
 *
 * @author ai-lawyers
 */
@RestController
@RequestMapping("/lawyers/ticket-flow")
public class TicketFlowController extends BaseController
{
    @Autowired
    private TicketFlowService ticketFlowService;

    /**
     * 查询某流程在指定状态下当前用户可执行的动作。
     */
    @PreAuthorize("@ss.hasPermi('lawyers:call:ticket:list')")
    @GetMapping("/actions/{flowCode}/{status}")
    public AjaxResult actions(@PathVariable("flowCode") String flowCode,
                              @PathVariable("status") String status)
    {
        List<AiTicketFlowDefinition> actions =
                ticketFlowService.listAllowedActions(flowCode, status,
                        ticketFlowService.currentRoleKeys());
        return AjaxResult.success(actions);
    }
}
