package ai.lawyers.web.controller.lawyers;

import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ai.lawyers.common.annotation.Log;
import ai.lawyers.common.core.controller.BaseController;
import ai.lawyers.common.core.domain.AjaxResult;
import ai.lawyers.common.core.page.TableDataInfo;
import ai.lawyers.common.enums.BusinessType;
import ai.lawyers.common.utils.SecurityUtils;
import ai.lawyers.system.domain.lawyers.AiHotspotSuppress;
import ai.lawyers.system.domain.lawyers.AiHotspotSuppressLog;
import ai.lawyers.system.service.lawyers.IAiHotspotSuppressService;

/**
 * 高频置底管理 Controller（P3-D1）
 *
 * @author ai-lawyers
 */
@RestController
@RequestMapping("/lawyers/hotspot")
public class AiHotspotSuppressController extends BaseController
{
    @Autowired
    private IAiHotspotSuppressService hotspotSuppressService;

    /** 规则列表 */
    @PreAuthorize("@ss.hasPermi('lawyers:hotspot:list')")
    @GetMapping("/list")
    public TableDataInfo list(AiHotspotSuppress query)
    {
        startPage();
        List<AiHotspotSuppress> list = hotspotSuppressService.selectSuppressList(query);
        return getDataTable(list);
    }

    /** 命中处置日志列表 */
    @PreAuthorize("@ss.hasPermi('lawyers:hotspot:list')")
    @GetMapping("/log/list")
    public TableDataInfo logList(AiHotspotSuppressLog query)
    {
        startPage();
        List<AiHotspotSuppressLog> list = hotspotSuppressService.selectLogList(query);
        return getDataTable(list);
    }

    /** 规则详情 */
    @PreAuthorize("@ss.hasPermi('lawyers:hotspot:query')")
    @GetMapping("/{suppressId}")
    public AjaxResult getInfo(@PathVariable("suppressId") Long suppressId)
    {
        return success(hotspotSuppressService.selectSuppressById(suppressId));
    }

    /** 新增规则 */
    @PreAuthorize("@ss.hasPermi('lawyers:hotspot:add')")
    @Log(title = "高频置底规则", businessType = BusinessType.INSERT)
    @PostMapping
    public AjaxResult add(@RequestBody AiHotspotSuppress suppress)
    {
        suppress.setCreateBy(SecurityUtils.getUsername());
        return toAjax(hotspotSuppressService.insertSuppress(suppress));
    }

    /** 修改规则 */
    @PreAuthorize("@ss.hasPermi('lawyers:hotspot:edit')")
    @Log(title = "高频置底规则", businessType = BusinessType.UPDATE)
    @PutMapping
    public AjaxResult edit(@RequestBody AiHotspotSuppress suppress)
    {
        suppress.setUpdateBy(SecurityUtils.getUsername());
        return toAjax(hotspotSuppressService.updateSuppress(suppress));
    }

    /** 删除规则 */
    @PreAuthorize("@ss.hasPermi('lawyers:hotspot:remove')")
    @Log(title = "高频置底规则", businessType = BusinessType.DELETE)
    @DeleteMapping("/{suppressIds}")
    public AjaxResult remove(@PathVariable Long[] suppressIds)
    {
        return toAjax(hotspotSuppressService.deleteSuppressByIds(suppressIds));
    }
}
