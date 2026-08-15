package ai.lawyers.web.controller.lawyers.outbound;

import java.util.Map;
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
import ai.lawyers.common.annotation.Anonymous;
import ai.lawyers.common.core.controller.BaseController;
import ai.lawyers.common.core.domain.AjaxResult;
import ai.lawyers.common.core.page.TableDataInfo;
import ai.lawyers.common.enums.BusinessType;
import ai.lawyers.common.utils.poi.ExcelUtil;
import ai.lawyers.system.domain.lawyers.outbound.AiOutboundTask;
import ai.lawyers.system.service.lawyers.outbound.IAiOutboundTaskService;
import ai.lawyers.system.service.lawyers.outbound.IOutboundExecutionService;

@RestController
@RequestMapping("/lawyers/outbound/task")
public class AiOutboundTaskController extends BaseController
{
    @Autowired
    private IAiOutboundTaskService aiOutboundTaskService;

    @Autowired
    private IOutboundExecutionService outboundExecutionService;

    @PreAuthorize("@ss.hasPermi('lawyers:outbound:task:list')")
    @GetMapping("/list")
    public TableDataInfo list(AiOutboundTask aiOutboundTask)
    {
        startPage();
        List<AiOutboundTask> list = aiOutboundTaskService.selectAiOutboundTaskList(aiOutboundTask);
        return getDataTable(list);
    }

    @PreAuthorize("@ss.hasPermi('lawyers:outbound:task:export')")
    @Log(title = "外呼任务", businessType = BusinessType.EXPORT)
    @PostMapping("/export")
    public void export(HttpServletResponse response, AiOutboundTask aiOutboundTask)
    {
        List<AiOutboundTask> list = aiOutboundTaskService.selectAiOutboundTaskList(aiOutboundTask);
        ExcelUtil<AiOutboundTask> util = new ExcelUtil<AiOutboundTask>(AiOutboundTask.class);
        util.exportExcel(response, list, "外呼任务数据");
    }

    @PreAuthorize("@ss.hasPermi('lawyers:outbound:task:query')")
    @GetMapping(value = "/{taskId}")
    public AjaxResult getInfo(@PathVariable("taskId") Long taskId)
    {
        return success(aiOutboundTaskService.selectAiOutboundTaskByTaskId(taskId));
    }

    @PreAuthorize("@ss.hasPermi('lawyers:outbound:task:add')")
    @Log(title = "外呼任务", businessType = BusinessType.INSERT)
    @PostMapping
    public AjaxResult add(@RequestBody AiOutboundTask aiOutboundTask)
    {
        aiOutboundTask.setCreateBy(getUsername());
        return toAjax(aiOutboundTaskService.insertAiOutboundTask(aiOutboundTask));
    }

    @PreAuthorize("@ss.hasPermi('lawyers:outbound:task:edit')")
    @Log(title = "外呼任务", businessType = BusinessType.UPDATE)
    @PutMapping
    public AjaxResult edit(@RequestBody AiOutboundTask aiOutboundTask)
    {
        aiOutboundTask.setUpdateBy(getUsername());
        return toAjax(aiOutboundTaskService.updateAiOutboundTask(aiOutboundTask));
    }

    @PreAuthorize("@ss.hasPermi('lawyers:outbound:task:remove')")
    @Log(title = "外呼任务", businessType = BusinessType.DELETE)
    @DeleteMapping("/{taskIds}")
    public AjaxResult remove(@PathVariable Long[] taskIds)
    {
        return toAjax(aiOutboundTaskService.deleteAiOutboundTaskByTaskIds(taskIds));
    }

    @PreAuthorize("@ss.hasPermi('lawyers:outbound:task:query')")
    @GetMapping("/generateNo")
    public AjaxResult generateTaskNo()
    {
        return success(aiOutboundTaskService.generateTaskNo());
    }

    @PreAuthorize("@ss.hasPermi('lawyers:outbound:task:start')")
    @Log(title = "外呼任务启动", businessType = BusinessType.UPDATE)
    @PostMapping("/start/{taskId}")
    public AjaxResult startTask(@PathVariable Long taskId)
    {
        return toAjax(aiOutboundTaskService.startTask(taskId));
    }

    @PreAuthorize("@ss.hasPermi('lawyers:outbound:task:pause')")
    @Log(title = "外呼任务暂停", businessType = BusinessType.UPDATE)
    @PostMapping("/pause/{taskId}")
    public AjaxResult pauseTask(@PathVariable Long taskId)
    {
        return toAjax(aiOutboundTaskService.pauseTask(taskId));
    }

    @PreAuthorize("@ss.hasPermi('lawyers:outbound:task:stop')")
    @Log(title = "外呼任务停止", businessType = BusinessType.UPDATE)
    @PostMapping("/stop/{taskId}")
    public AjaxResult stopTask(@PathVariable Long taskId)
    {
        return toAjax(aiOutboundTaskService.stopTask(taskId));
    }

    /**
     * 立即执行外呼任务（执行一批号码，最大并发数为任务配置）。
     */
    @PreAuthorize("@ss.hasPermi('lawyers:outbound:task:start')")
    @Log(title = "外呼任务执行", businessType = BusinessType.OTHER)
    @PostMapping("/execute/{taskId}")
    public AjaxResult executeTask(@PathVariable Long taskId)
    {
        int processed = outboundExecutionService.executeTask(taskId);
        return success(processed);
    }

    /**
     * 网关事件回调（外呼话单最终化）。
     * 无需登录鉴权，请在网关侧通过 IP 白名单或签名保证安全。
     *
     * 请求体示例：
     * { "callUuid":"xxx", "event":"ANSWERED" } / { "callUuid":"xxx", "event":"HANGUP", "talkDuration":35 }
     */
    @Anonymous
    @PostMapping("/event")
    public AjaxResult event(@RequestBody Map<String, Object> param)
    {
        Object uuid = param.get("callUuid");
        Object event = param.get("event");
        if (uuid == null || event == null)
        {
            return AjaxResult.error("callUuid 与 event 不能为空");
        }
        outboundExecutionService.onCallEvent(uuid.toString(), event.toString(), param);
        return success();
    }
}
