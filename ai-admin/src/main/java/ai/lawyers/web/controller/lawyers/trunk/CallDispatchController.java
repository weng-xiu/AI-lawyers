package ai.lawyers.web.controller.lawyers.trunk;

import java.util.List;
import java.util.Map;
import javax.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ai.lawyers.common.annotation.Anonymous;
import ai.lawyers.common.annotation.Log;
import ai.lawyers.common.core.controller.BaseController;
import ai.lawyers.common.core.domain.AjaxResult;
import ai.lawyers.common.core.page.TableDataInfo;
import ai.lawyers.common.enums.BusinessType;
import ai.lawyers.common.utils.SecurityUtils;
import ai.lawyers.common.utils.poi.ExcelUtil;
import ai.lawyers.system.domain.lawyers.trunk.AiCallDialLog;
import ai.lawyers.system.domain.lawyers.trunk.DialRequest;
import ai.lawyers.system.domain.lawyers.trunk.DialResult;
import ai.lawyers.system.mapper.lawyers.trunk.AiCallDialLogMapper;
import ai.lawyers.system.service.lawyers.trunk.ICallDispatchService;

/**
 * 外呼调度 Controller
 *
 * 提供统一外呼入口、挂断、网关事件回调与拨号日志查询。
 */
@RestController
@RequestMapping("/lawyers/call")
public class CallDispatchController extends BaseController
{
    @Autowired
    private ICallDispatchService callDispatchService;

    @Autowired
    private AiCallDialLogMapper dialLogMapper;

    /**
     * 发起外呼（自动识别运营商并选路，线路满载时进入排队）
     */
    @PreAuthorize("@ss.hasPermi('lawyers:call:dial')")
    @Log(title = "外呼", businessType = BusinessType.OTHER)
    @PostMapping("/dial")
    public AjaxResult dial(@RequestBody DialRequest request)
    {
        request.setCreateBy(SecurityUtils.getUsername());
        DialResult result = callDispatchService.dialWithQueue(request);
        if (!result.isSuccess())
        {
            return AjaxResult.error(result.getMessage(), result);
        }
        return AjaxResult.success("外呼已发起", result);
    }

    /**
     * 发起外呼（同步，无可用线路立即失败，不排队）
     */
    @PreAuthorize("@ss.hasPermi('lawyers:call:dial')")
    @Log(title = "外呼-直呼", businessType = BusinessType.OTHER)
    @PostMapping("/dialDirect")
    public AjaxResult dialDirect(@RequestBody DialRequest request)
    {
        request.setCreateBy(SecurityUtils.getUsername());
        DialResult result = callDispatchService.dial(request);
        if (!result.isSuccess())
        {
            return AjaxResult.error(result.getMessage(), result);
        }
        return AjaxResult.success("外呼已发起", result);
    }

    /**
     * 挂断
     */
    @PreAuthorize("@ss.hasPermi('lawyers:call:dial')")
    @Log(title = "外呼-挂断", businessType = BusinessType.OTHER)
    @PostMapping("/hangup")
    public AjaxResult hangup(@RequestBody Map<String, Object> param)
    {
        Object uuid = param.get("callUuid");
        if (uuid == null)
        {
            return AjaxResult.error("callUuid 不能为空");
        }
        return callDispatchService.hangup(uuid.toString())
                ? success("已挂断") : AjaxResult.error("挂断失败");
    }

    /**
     * 网关事件回调（由 FreeSWITCH/Asterisk/CTI 中间件推送）
     *
     * 无需登录鉴权，请在网关侧通过 IP 白名单或签名保证安全。
     *
     * 请求体示例：
     * { "callUuid":"xxx", "event":"ANSWERED", "talkDuration":35, "hangupCause":"NORMAL_CLEARING" }
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
        callDispatchService.onCallEvent(uuid.toString(), event.toString(), param);
        return success();
    }

    /**
     * 调度器实时状态
     */
    @PreAuthorize("@ss.hasPermi('lawyers:call:dial')")
    @GetMapping("/dispatchStatus")
    public AjaxResult dispatchStatus()
    {
        AjaxResult result = AjaxResult.success();
        result.put("queueSize", callDispatchService.getQueueSize());
        result.put("globalConcurrent", callDispatchService.getGlobalConcurrent());
        return result;
    }

    // ---------------------------------------------------------------- 拨号日志

    @PreAuthorize("@ss.hasPermi('lawyers:diallog:list')")
    @GetMapping("/dialLog/list")
    public TableDataInfo dialLogList(AiCallDialLog aiCallDialLog)
    {
        startPage();
        List<AiCallDialLog> list = dialLogMapper.selectAiCallDialLogList(aiCallDialLog);
        return getDataTable(list);
    }

    @PreAuthorize("@ss.hasPermi('lawyers:diallog:list')")
    @Log(title = "拨号日志", businessType = BusinessType.EXPORT)
    @PostMapping("/dialLog/export")
    public void dialLogExport(HttpServletResponse response, AiCallDialLog aiCallDialLog)
    {
        List<AiCallDialLog> list = dialLogMapper.selectAiCallDialLogList(aiCallDialLog);
        ExcelUtil<AiCallDialLog> util = new ExcelUtil<AiCallDialLog>(AiCallDialLog.class);
        util.exportExcel(response, list, "拨号日志数据");
    }
}
