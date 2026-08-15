package ai.lawyers.web.controller.lawyers.ivr;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import ai.lawyers.common.core.controller.BaseController;
import ai.lawyers.common.core.domain.AjaxResult;
import ai.lawyers.system.domain.lawyers.ivr.IvrExecuteRequest;
import ai.lawyers.system.service.lawyers.ivr.engine.IIvrEngineService;
import ai.lawyers.system.service.lawyers.ivr.engine.IntentionRecognitionService;

/**
 * IVR 流程执行引擎 Controller（在线调试 / 运行时调用）。
 */
@RestController
@RequestMapping("/lawyers/ivr/engine")
public class IvrEngineController extends BaseController
{
    @Autowired
    private IIvrEngineService ivrEngineService;

    @Autowired
    private IntentionRecognitionService intentionRecognitionService;

    /**
     * 模拟执行一次 IVR 流程（可指定 inputs 模拟按键/语音输入，返回逐步执行轨迹）。
     */
    @PreAuthorize("@ss.hasPermi('lawyers:ivr:flow:list')")
    @PostMapping("/execute")
    public AjaxResult execute(@RequestBody IvrExecuteRequest request)
    {
        return success(ivrEngineService.executeFlow(request));
    }

    /**
     * 意图识别（界面测试用，不落日志）。
     */
    @PreAuthorize("@ss.hasPermi('lawyers:ivr:intention:list')")
    @PostMapping("/intention")
    public AjaxResult intention(@RequestBody java.util.Map<String, String> param)
    {
        String text = param == null ? null : param.get("text");
        return success(intentionRecognitionService.recognize(text));
    }
}
