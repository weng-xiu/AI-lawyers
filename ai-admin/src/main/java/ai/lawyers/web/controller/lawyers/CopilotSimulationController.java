package ai.lawyers.web.controller.lawyers;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import ai.lawyers.common.annotation.Log;
import ai.lawyers.common.core.controller.BaseController;
import ai.lawyers.common.core.domain.AjaxResult;
import ai.lawyers.common.enums.BusinessType;
import ai.lawyers.system.service.lawyers.voice.copilot.CopilotSimulationManager;

/**
 * G3 深化（V2.79）：Copilot 坐席辅助上线前仿真回归入口。
 *
 * <p>异步任务模式：提交即返回 taskId，后台执行批量评测，按 taskId 轮询进度与报告。</p>
 *
 * @author ai-lawyers
 */
@RestController
@RequestMapping("/lawyers/copilot/simulation")
public class CopilotSimulationController extends BaseController
{
    @Autowired
    private CopilotSimulationManager copilotSimulationManager;

    /**
     * 提交 Copilot 评测任务；请求体为空时跑内置默认用例集。
     */
    @PreAuthorize("@ss.hasPermi('lawyers:modelConfig:test')")
    @Log(title = "Copilot仿真回归", businessType = BusinessType.OTHER)
    @PostMapping("/submit")
    public AjaxResult submit(@RequestBody(required = false) String casesJson)
    {
        String taskId = copilotSimulationManager.submit(casesJson);
        return AjaxResult.success("Copilot 评测任务已提交").put("taskId", taskId);
    }

    /**
     * 查询评测任务进度；完成后 data.report 为完整回归报告。
     */
    @PreAuthorize("@ss.hasPermi('lawyers:modelConfig:test')")
    @GetMapping("/progress")
    public AjaxResult progress(@RequestParam("taskId") String taskId)
    {
        return success(copilotSimulationManager.getTask(taskId));
    }
}
