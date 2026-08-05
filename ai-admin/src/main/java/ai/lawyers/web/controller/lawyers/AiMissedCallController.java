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
import ai.lawyers.system.domain.lawyers.AiMissedCall;
import ai.lawyers.system.service.lawyers.IAiMissedCallService;

/**
 * 未接来电Controller
 *
 * @author ai-lawyers
 */
@RestController
@RequestMapping("/lawyers/call/missed")
public class AiMissedCallController extends BaseController
{
    @Autowired
    private IAiMissedCallService aiMissedCallService;

    @PreAuthorize("@ss.hasPermi('lawyers:call:missed:list')")
    @GetMapping("/list")
    public TableDataInfo list(AiMissedCall aiMissedCall)
    {
        startPage();
        List<AiMissedCall> list = aiMissedCallService.selectAiMissedCallList(aiMissedCall);
        return getDataTable(list);
    }

    @PreAuthorize("@ss.hasPermi('lawyers:call:missed:export')")
    @Log(title = "未接来电", businessType = BusinessType.EXPORT)
    @PostMapping("/export")
    public void export(HttpServletResponse response, AiMissedCall aiMissedCall)
    {
        List<AiMissedCall> list = aiMissedCallService.selectAiMissedCallList(aiMissedCall);
        ExcelUtil<AiMissedCall> util = new ExcelUtil<AiMissedCall>(AiMissedCall.class);
        util.exportExcel(response, list, "未接来电数据");
    }

    @PreAuthorize("@ss.hasPermi('lawyers:call:missed:query')")
    @GetMapping(value = "/{missedCallId}")
    public AjaxResult getInfo(@PathVariable("missedCallId") Long missedCallId)
    {
        return success(aiMissedCallService.selectAiMissedCallByMissedCallId(missedCallId));
    }

    @PreAuthorize("@ss.hasPermi('lawyers:call:missed:add')")
    @Log(title = "未接来电", businessType = BusinessType.INSERT)
    @PostMapping
    public AjaxResult add(@RequestBody AiMissedCall aiMissedCall)
    {
        aiMissedCall.setCreateBy(getUsername());
        return toAjax(aiMissedCallService.insertAiMissedCall(aiMissedCall));
    }

    @PreAuthorize("@ss.hasPermi('lawyers:call:missed:edit')")
    @Log(title = "未接来电", businessType = BusinessType.UPDATE)
    @PutMapping
    public AjaxResult edit(@RequestBody AiMissedCall aiMissedCall)
    {
        aiMissedCall.setUpdateBy(getUsername());
        return toAjax(aiMissedCallService.updateAiMissedCall(aiMissedCall));
    }

    @PreAuthorize("@ss.hasPermi('lawyers:call:missed:remove')")
    @Log(title = "未接来电", businessType = BusinessType.DELETE)
    @DeleteMapping("/{missedCallIds}")
    public AjaxResult remove(@PathVariable Long[] missedCallIds)
    {
        return toAjax(aiMissedCallService.deleteAiMissedCallByMissedCallIds(missedCallIds));
    }

    /** 未接来电统计：今日未接、本周未接、已回拨、回拨率 */
    @PreAuthorize("@ss.hasPermi('lawyers:call:missed:list')")
    @GetMapping("/stats")
    public AjaxResult getStats()
    {
        return success(aiMissedCallService.selectMissedCallStats());
    }

    /** 回拨标记 */
    @PreAuthorize("@ss.hasPermi('lawyers:call:missed:callback')")
    @Log(title = "未接来电回拨", businessType = BusinessType.UPDATE)
    @PutMapping("/callback/{missedCallId}")
    public AjaxResult callback(@PathVariable("missedCallId") Long missedCallId)
    {
        AiMissedCall update = new AiMissedCall();
        update.setMissedCallId(missedCallId);
        update.setStatus("1");
        update.setCallbackBy(getUsername());
        update.setCallbackTime(new java.util.Date());
        update.setUpdateBy(getUsername());
        return toAjax(aiMissedCallService.updateAiMissedCall(update));
    }
}
