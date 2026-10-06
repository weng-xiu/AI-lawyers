package ai.lawyers.web.controller.lawyers;

import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ai.lawyers.common.core.controller.BaseController;
import ai.lawyers.common.core.domain.AjaxResult;
import ai.lawyers.system.domain.lawyers.AiTicketFlowDefinition;
import ai.lawyers.system.service.lawyers.TicketFlowService;

/**
 * P1-6：工单配置化流转 DSL——动作查询（按钮动态渲染）+ 规则管理端 CRUD。
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

    /**
     * 管理端：规则条件过滤列表。
     */
    @PreAuthorize("@ss.hasPermi('lawyers:ticketFlow:list')")
    @GetMapping("/list")
    public AjaxResult list(AiTicketFlowDefinition query)
    {
        return AjaxResult.success(ticketFlowService.listDefinitions(query));
    }

    /**
     * 管理端：按主键查询（编辑回显）。
     */
    @PreAuthorize("@ss.hasPermi('lawyers:ticketFlow:query')")
    @GetMapping("/{flowId}")
    public AjaxResult getInfo(@PathVariable("flowId") Long flowId)
    {
        return AjaxResult.success(ticketFlowService.getDefinition(flowId));
    }

    /**
     * 管理端：新增规则。
     */
    @PreAuthorize("@ss.hasPermi('lawyers:ticketFlow:add')")
    @PostMapping
    public AjaxResult add(@RequestBody AiTicketFlowDefinition rule)
    {
        return toAjax(ticketFlowService.addDefinition(rule));
    }

    /**
     * 管理端：修改规则。
     */
    @PreAuthorize("@ss.hasPermi('lawyers:ticketFlow:edit')")
    @PutMapping
    public AjaxResult edit(@RequestBody AiTicketFlowDefinition rule)
    {
        return toAjax(ticketFlowService.updateDefinition(rule));
    }
}
