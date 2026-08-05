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
import ai.lawyers.system.domain.lawyers.AiCallback;
import ai.lawyers.system.service.lawyers.IAiCallbackService;

/**
 * 客户回访Controller
 *
 * @author ai-lawyers
 */
@RestController
@RequestMapping("/lawyers/callback")
public class AiCallbackController extends BaseController
{
    @Autowired
    private IAiCallbackService aiCallbackService;

    @PreAuthorize("@ss.hasPermi('lawyers:callback:list')")
    @GetMapping("/list")
    public TableDataInfo list(AiCallback aiCallback)
    {
        startPage();
        List<AiCallback> list = aiCallbackService.selectAiCallbackList(aiCallback);
        return getDataTable(list);
    }

    @PreAuthorize("@ss.hasPermi('lawyers:callback:export')")
    @Log(title = "客户回访", businessType = BusinessType.EXPORT)
    @PostMapping("/export")
    public void export(HttpServletResponse response, AiCallback aiCallback)
    {
        List<AiCallback> list = aiCallbackService.selectAiCallbackList(aiCallback);
        ExcelUtil<AiCallback> util = new ExcelUtil<AiCallback>(AiCallback.class);
        util.exportExcel(response, list, "客户回访数据");
    }

    @PreAuthorize("@ss.hasPermi('lawyers:callback:query')")
    @GetMapping(value = "/{callbackId}")
    public AjaxResult getInfo(@PathVariable("callbackId") Long callbackId)
    {
        return success(aiCallbackService.selectAiCallbackByCallbackId(callbackId));
    }

    @PreAuthorize("@ss.hasPermi('lawyers:callback:add')")
    @Log(title = "客户回访", businessType = BusinessType.INSERT)
    @PostMapping
    public AjaxResult add(@RequestBody AiCallback aiCallback)
    {
        aiCallback.setCreateBy(getUsername());
        if (aiCallback.getVisitBy() == null || aiCallback.getVisitBy().isEmpty()) {
            aiCallback.setVisitBy(getUsername());
        }
        return toAjax(aiCallbackService.insertAiCallback(aiCallback));
    }

    @PreAuthorize("@ss.hasPermi('lawyers:callback:edit')")
    @Log(title = "客户回访", businessType = BusinessType.UPDATE)
    @PutMapping
    public AjaxResult edit(@RequestBody AiCallback aiCallback)
    {
        aiCallback.setUpdateBy(getUsername());
        return toAjax(aiCallbackService.updateAiCallback(aiCallback));
    }

    @PreAuthorize("@ss.hasPermi('lawyers:callback:remove')")
    @Log(title = "客户回访", businessType = BusinessType.DELETE)
    @DeleteMapping("/{callbackIds}")
    public AjaxResult remove(@PathVariable Long[] callbackIds)
    {
        return toAjax(aiCallbackService.deleteAiCallbackByCallbackIds(callbackIds));
    }

    /** 回访统计 */
    @PreAuthorize("@ss.hasPermi('lawyers:callback:list')")
    @GetMapping("/stats")
    public AjaxResult getStats()
    {
        return success(aiCallbackService.selectCallbackStats());
    }

    /** 满意度趋势 */
    @PreAuthorize("@ss.hasPermi('lawyers:callback:list')")
    @GetMapping("/satisfactionTrend")
    public AjaxResult getSatisfactionTrend(Integer days)
    {
        return success(aiCallbackService.selectSatisfactionTrend(days));
    }
}
