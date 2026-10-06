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
import ai.lawyers.system.service.lawyers.voice.robot.RobotSimulationManager;

/**
 * P1-8：语音机器人上线前仿真回归入口（纳入统一平台，异步任务模式）。
 *
 * <p>提交即返回 taskId，后台执行批量评测，按 taskId 轮询进度与报告。</p>
 *
 * @author ai-lawyers
 */
@RestController
@RequestMapping("/lawyers/robot/simulation")
public class RobotSimulationController extends BaseController
{
    @Autowired
    private RobotSimulationManager robotSimulationManager;

    /**
     * 提交机器人评测任务；请求体为空时跑内置默认用例集。
     */
    @PreAuthorize("@ss.hasPermi('lawyers:modelConfig:test')")
    @Log(title = "机器人仿真回归", businessType = BusinessType.OTHER)
    @PostMapping("/submit")
    public AjaxResult submit(@RequestBody(required = false) String casesJson)
    {
        String taskId = robotSimulationManager.submit(casesJson);
        return AjaxResult.success("机器人评测任务已提交").put("taskId", taskId);
    }

    /**
     * 查询评测任务进度；完成后 data.report 为完整回归报告。
     */
    @PreAuthorize("@ss.hasPermi('lawyers:modelConfig:test')")
    @GetMapping("/progress")
    public AjaxResult progress(@RequestParam("taskId") String taskId)
    {
        return success(robotSimulationManager.getTask(taskId));
    }
}
