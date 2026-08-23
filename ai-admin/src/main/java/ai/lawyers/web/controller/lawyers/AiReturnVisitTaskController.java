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
import ai.lawyers.system.domain.lawyers.trunk.DialRequest;
import ai.lawyers.system.domain.lawyers.trunk.DialResult;
import ai.lawyers.system.service.lawyers.IAiReturnVisitTaskService;
import ai.lawyers.system.service.lawyers.trunk.ICallDispatchService;

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

    @Autowired
    private ICallDispatchService callDispatchService;

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

    /**
     * 一键外呼：根据回访任务中的号码发起外呼。
     * 任务的 callerNumber 作为主叫显号，calleeNumber/callerNumber 作为被叫，
     * 统一交给运营商线路调度器下发。
     */
    @PreAuthorize("@ss.hasPermi('lawyers:returnVisitTask:edit')")
    @Log(title = "回访任务一键外呼", businessType = BusinessType.OTHER)
    @PutMapping("/call/{taskId}")
    public AjaxResult call(@PathVariable("taskId") Long taskId)
    {
        AiReturnVisitTask task = aiReturnVisitTaskService.selectAiReturnVisitTaskByTaskId(taskId);
        if (task == null)
        {
            return error("回访任务不存在");
        }
        String callee = task.getCallerNumber();
        if (callee == null || callee.trim().isEmpty())
        {
            return error("该回访任务无来电号码，无法外呼");
        }
        DialRequest request = new DialRequest();
        request.setCalleeNumber(callee.trim());
        request.setCallerNumber(task.getCallerName());
        request.setPriority(50);
        request.setCreateBy(getUsername());
        request.setRemark("回访任务一键外呼 taskNo=" + task.getTaskNo());
        DialResult result = callDispatchService.dialWithQueue(request);
        if (result != null && result.isSuccess())
        {
            AjaxResult ajax = AjaxResult.success("外呼已发起");
            ajax.put("data", result);
            return ajax;
        }
        String msg = result == null ? "无可用线路" : result.getMessage();
        return error("外呼发起失败：" + msg);
    }
}
