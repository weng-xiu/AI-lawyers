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
import ai.lawyers.system.service.lawyers.voice.asr.AsrQualitySimulationManager;

/**
 * N2（语雀 7.4 S1）：ASR 转写质量仿真评测入口（纳入统一平台，异步任务模式）。
 *
 * <p>提交即返回 taskId，后台基于金标准集计算平均字错率（CER），按 taskId 轮询进度与报告。
 * 权限复用仿真回归通用标识 lawyers:modelConfig:test，与 Copilot / 机器人评测一致。</p>
 *
 * @author ai-lawyers
 */
@RestController
@RequestMapping("/lawyers/asr/simulation")
public class AsrSimulationController extends BaseController
{
    @Autowired
    private AsrQualitySimulationManager asrQualitySimulationManager;

    /**
     * 提交 ASR 转写质量评测任务；请求体为空时跑内置默认金标准集。
     */
    @PreAuthorize("@ss.hasPermi('lawyers:modelConfig:test')")
    @Log(title = "ASR转写质量评测", businessType = BusinessType.OTHER)
    @PostMapping("/submit")
    public AjaxResult submit(@RequestBody(required = false) String samplesJson)
    {
        String taskId = asrQualitySimulationManager.submit(samplesJson);
        return AjaxResult.success("ASR 转写质量评测任务已提交").put("taskId", taskId);
    }

    /**
     * 查询评测任务进度；完成后 data.report 为完整 CER 评测报告。
     */
    @PreAuthorize("@ss.hasPermi('lawyers:modelConfig:test')")
    @GetMapping("/progress")
    public AjaxResult progress(@RequestParam("taskId") String taskId)
    {
        return success(asrQualitySimulationManager.getTask(taskId));
    }
}
