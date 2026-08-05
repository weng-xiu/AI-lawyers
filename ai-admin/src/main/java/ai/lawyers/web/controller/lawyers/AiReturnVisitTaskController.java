package ai.lawyers.web.controller.lawyers;

import java.util.List;
import javax.servlet.http.HttpServletResponse;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ai.lawyers.common.annotation.Log;
import ai.lawyers.common.core.controller.BaseController;
import ai.lawyers.common.core.domain.AjaxResult;
import ai.lawyers.common.core.page.TableDataInfo;
import ai.lawyers.common.enums.BusinessType;
import ai.lawyers.common.utils.poi.ExcelUtil;
import ai.lawyers.system.domain.lawyers.AiReturnVisitTask;
import ai.lawyers.system.service.lawyers.IAiReturnVisitTaskService;

/**
 * 回访任务Controller
 *
 * @author ai-lawyers
 */
@RestController
@RequestMapping("/lawyers/returnVisitTask")
public class AiReturnVisitTaskController extends BaseController
{
    @Autowired
    private IAiReturnVisitTaskService aiReturnVisitTaskService;

    @PreAuthorize("@ss.hasPermi('lawyers:returnVisitTask:list')")
    @GetMapping("/list")
    public TableDataInfo list(AiReturnVisitTask aiReturnVisitTask)
    {
        startPage();
        List<AiReturnVisitTask> list = aiReturnVisitTaskService.selectAiReturnVisitTaskList(aiReturnVisitTask);
        return getDataTable(list);
    }

    @PreAuthorize("@ss.hasPermi('lawyers:returnVisitTask:export')")
    @Log(title = "回访任务", businessType = BusinessType.EXPORT)
    @PostMapping("/export")
    public void export(HttpServletResponse response, AiReturnVisitTask aiReturnVisitTask)
    {
        List<AiReturnVisitTask> list = aiReturnVisitTaskService.selectAiReturnVisitTaskList(aiReturnVisitTask);
        ExcelUtil<AiReturnVisitTask> util = new ExcelUtil<AiReturnVisitTask>(AiReturnVisitTask.class);
        util.exportExcel(response, list, "回访任务数据");
    }

    @PreAuthorize("@ss.hasPermi('lawyers:returnVisitTask:query')")
    @GetMapping(value = "/{taskId}")
    public AjaxResult getInfo(@PathVariable("taskId") Long taskId)
    {
        return success(aiReturnVisitTaskService.selectAiReturnVisitTaskByTaskId(taskId));
    }

    @PreAuthorize("@ss.hasPermi('lawyers:returnVisitTask:add')")
    @Log(title = "回访任务", businessType = BusinessType.INSERT)
    @PostMapping
    public AjaxResult add(@RequestBody AiReturnVisitTask aiReturnVisitTask)
    {
        aiReturnVisitTask.setCreateBy(getUsername());
        if (aiReturnVisitTask.getAssignee() == null || aiReturnVisitTask.getAssignee().isEmpty()) {
            aiReturnVisitTask.setAssignee(getUsername());
        }
        if (aiReturnVisitTask.getStatus() == null || aiReturnVisitTask.getStatus().isEmpty()) {
            aiReturnVisitTask.setStatus("0");
        }
        if (aiReturnVisitTask.getPriority() == null || aiReturnVisitTask.getPriority().isEmpty()) {
            aiReturnVisitTask.setPriority("2");
        }
        return toAjax(aiReturnVisitTaskService.insertAiReturnVisitTask(aiReturnVisitTask));
    }

    @PreAuthorize("@ss.hasPermi('lawyers:returnVisitTask:edit')")
    @Log(title = "回访任务", businessType = BusinessType.UPDATE)
    @PutMapping
    public AjaxResult edit(@RequestBody AiReturnVisitTask aiReturnVisitTask)
    {
        aiReturnVisitTask.setUpdateBy(getUsername());
        return toAjax(aiReturnVisitTaskService.updateAiReturnVisitTask(aiReturnVisitTask));
    }

    @PreAuthorize("@ss.hasPermi('lawyers:returnVisitTask:remove')")
    @Log(title = "回访任务", businessType = BusinessType.DELETE)
    @DeleteMapping("/{taskIds}")
    public AjaxResult remove(@PathVariable Long[] taskIds)
    {
        return toAjax(aiReturnVisitTaskService.deleteAiReturnVisitTaskByTaskIds(taskIds));
    }

    /** 回访任务统计 */
    @PreAuthorize("@ss.hasPermi('lawyers:returnVisitTask:list')")
    @GetMapping("/stats")
    public AjaxResult getStats()
    {
        return success(aiReturnVisitTaskService.selectReturnVisitTaskStats());
    }
}
