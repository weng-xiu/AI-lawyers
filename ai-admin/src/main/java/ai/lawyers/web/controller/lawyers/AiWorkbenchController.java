package ai.lawyers.web.controller.lawyers;

import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import ai.lawyers.common.core.controller.BaseController;
import ai.lawyers.common.core.domain.AjaxResult;
import ai.lawyers.system.service.lawyers.IWorkbenchService;

/**
 * 统一工作台聚合接口
 */
@RestController
@RequestMapping("/lawyers/workbench")
public class AiWorkbenchController extends BaseController
{
    @Autowired
    private IWorkbenchService workbenchService;

    /** 工作台统计卡：今日通话/服务时长/满意度/在线时长 */
    @PreAuthorize("@ss.hasPermi('lawyers:workbench:index')")
    @GetMapping("/stats")
    public AjaxResult stats()
    {
        return success(workbenchService.getWorkbenchStats(getUserId()));
    }

    /** 我的待办 */
    @PreAuthorize("@ss.hasPermi('lawyers:workbench:index')")
    @GetMapping("/todos")
    public AjaxResult todos()
    {
        return success(workbenchService.getWorkbenchTodos(getUserId()));
    }

    /** 最近通话记录 */
    @PreAuthorize("@ss.hasPermi('lawyers:workbench:index')")
    @GetMapping("/recentCalls")
    public AjaxResult recentCalls(@RequestParam(value = "limit", required = false) Integer limit)
    {
        List<?> list = workbenchService.getWorkbenchRecentCalls(limit);
        return success(list);
    }

    /** 已发布公告 */
    @PreAuthorize("@ss.hasPermi('lawyers:workbench:index')")
    @GetMapping("/notices")
    public AjaxResult notices(@RequestParam(value = "limit", required = false) Integer limit)
    {
        return success(workbenchService.getWorkbenchNotices(limit));
    }
}
