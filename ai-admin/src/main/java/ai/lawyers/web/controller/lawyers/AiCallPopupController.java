package ai.lawyers.web.controller.lawyers;

import java.util.Map;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import ai.lawyers.common.annotation.Log;
import ai.lawyers.common.core.controller.BaseController;
import ai.lawyers.common.core.domain.AjaxResult;
import ai.lawyers.common.enums.BusinessType;
import ai.lawyers.system.domain.lawyers.AiCallerProfile;
import ai.lawyers.system.service.lawyers.ICallPopupService;

/**
 * 来电弹屏
 */
@RestController
@RequestMapping("/lawyers/call/popup")
public class AiCallPopupController extends BaseController
{
    @Autowired
    private ICallPopupService callPopupService;

    /** 来电人档案 + 智能来电分析 */
    @PreAuthorize("@ss.hasPermi('lawyers:call:popup:query')")
    @GetMapping("/profile/{callerNumber}")
    public AjaxResult profile(@PathVariable("callerNumber") String callerNumber)
    {
        return success(callPopupService.getPopupProfile(callerNumber));
    }

    /** 历史通话 */
    @PreAuthorize("@ss.hasPermi('lawyers:call:popup:query')")
    @GetMapping("/history/{callerNumber}")
    public AjaxResult history(@PathVariable("callerNumber") String callerNumber,
                              @RequestParam(value = "limit", required = false) Integer limit)
    {
        return success(callPopupService.getPopupHistory(callerNumber, limit));
    }

    /** 历史工单 */
    @PreAuthorize("@ss.hasPermi('lawyers:call:popup:query')")
    @GetMapping("/tickets/{callerNumber}")
    public AjaxResult tickets(@PathVariable("callerNumber") String callerNumber)
    {
        return success(callPopupService.getPopupTickets(callerNumber));
    }

    /** 来电轨迹 */
    @PreAuthorize("@ss.hasPermi('lawyers:call:popup:query')")
    @GetMapping("/track/{callerNumber}")
    public AjaxResult track(@PathVariable("callerNumber") String callerNumber)
    {
        return success(callPopupService.getPopupTrack(callerNumber));
    }

    /** 更新来电人档案（编辑信息/AI分析） */
    @PreAuthorize("@ss.hasPermi('lawyers:call:popup:edit')")
    @Log(title = "来电人档案", businessType = BusinessType.UPDATE)
    @PutMapping("/profile")
    public AjaxResult editProfile(@RequestBody AiCallerProfile aiCallerProfile)
    {
        aiCallerProfile.setUpdateBy(getUsername());
        return toAjax(callPopupService.updateAiCallerProfile(aiCallerProfile));
    }
}
