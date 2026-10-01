package ai.lawyers.web.controller.lawyers;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ai.lawyers.common.annotation.Log;
import ai.lawyers.common.core.controller.BaseController;
import ai.lawyers.common.core.domain.AjaxResult;
import ai.lawyers.common.enums.BusinessType;
import ai.lawyers.system.service.lawyers.voice.robot.RobotSimulationService;

/**
 * P1-8：语音机器人上线前仿真回归入口。
 *
 * @author ai-lawyers
 */
@RestController
@RequestMapping("/lawyers/robot/simulation")
public class RobotSimulationController extends BaseController
{
    @Autowired
    private RobotSimulationService robotSimulationService;

    /**
     * 执行合成对话集仿真评测；请求体为空时跑内置默认用例集。
     */
    @PreAuthorize("@ss.hasPermi('lawyers:modelConfig:test')")
    @Log(title = "机器人仿真回归", businessType = BusinessType.OTHER)
    @PostMapping("/run")
    public AjaxResult run(@RequestBody(required = false) String casesJson)
    {
        try
        {
            return AjaxResult.success(robotSimulationService.run(casesJson));
        }
        catch (IllegalArgumentException e)
        {
            return AjaxResult.error(e.getMessage());
        }
    }
}
