package ai.lawyers.web.controller.lawyers.skill;

import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import ai.lawyers.common.annotation.Log;
import ai.lawyers.common.core.controller.BaseController;
import ai.lawyers.common.core.domain.AjaxResult;
import ai.lawyers.common.core.page.TableDataInfo;
import ai.lawyers.common.enums.BusinessType;
import ai.lawyers.system.domain.lawyers.skill.AiCallQueue;
import ai.lawyers.system.domain.lawyers.skill.DispatchContext;
import ai.lawyers.system.domain.lawyers.skill.DispatchResult;
import ai.lawyers.system.mapper.lawyers.skill.AiCallQueueMapper;
import ai.lawyers.system.service.lawyers.skill.IAgentDispatchService;

/**
 * 排队监控与智能分配Controller
 *
 * @author ai-lawyers
 */
@RestController
@RequestMapping("/lawyers/skill/queue")
public class AgentDispatchController extends BaseController
{
    @Autowired
    private IAgentDispatchService agentDispatchService;

    @Autowired
    private AiCallQueueMapper aiCallQueueMapper;

    /**
     * 排队流水列表（含历史）
     */
    @PreAuthorize("@ss.hasPermi('lawyers:queue:query')")
    @GetMapping("/list")
    public TableDataInfo list(AiCallQueue query)
    {
        startPage();
        return getDataTable(aiCallQueueMapper.selectAiCallQueueList(query));
    }

    /**
     * 当前排队中列表（实时监控，不分页）
     */
    @PreAuthorize("@ss.hasPermi('lawyers:queue:query')")
    @GetMapping("/queuing")
    public AjaxResult queuing(@RequestParam(required = false) Long groupId)
    {
        return success(agentDispatchService.listQueuing(groupId));
    }

    /**
     * 调试用：触发一次分配
     */
    @PreAuthorize("@ss.hasPermi('lawyers:queue:assign')")
    @Log(title = "智能分配", businessType = BusinessType.OTHER)
    @PostMapping("/dispatch")
    public AjaxResult dispatch(@RequestParam Long groupId, @RequestBody(required = false) DispatchContext ctx)
    {
        if (ctx == null)
        {
            ctx = new DispatchContext();
        }
        DispatchResult result = agentDispatchService.dispatch(groupId, ctx);
        return success(result);
    }

    /**
     * 手动分配排队通话给指定坐席
     */
    @PreAuthorize("@ss.hasPermi('lawyers:queue:assign')")
    @Log(title = "排队手动分配", businessType = BusinessType.UPDATE)
    @PostMapping("/assign/{queueId}/{agentId}")
    public AjaxResult assignManually(@PathVariable Long queueId, @PathVariable Long agentId)
    {
        DispatchResult result = agentDispatchService.assignManually(queueId, agentId);
        return result.isSuccess() ? success(result) : AjaxResult.error(result.getMessage());
    }

    /**
     * 踢除排队
     */
    @PreAuthorize("@ss.hasPermi('lawyers:queue:kick')")
    @Log(title = "踢除排队", businessType = BusinessType.DELETE)
    @PostMapping("/kick/{queueId}")
    public AjaxResult kick(@PathVariable Long queueId, @RequestParam(required = false) String reason)
    {
        return toAjax(agentDispatchService.kickQueue(queueId, reason));
    }
}
