package ai.lawyers.web.controller.lawyers.collab;

import java.util.List;
import javax.servlet.http.HttpServletResponse;
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
import ai.lawyers.common.utils.poi.ExcelUtil;
import ai.lawyers.system.domain.lawyers.AiExternalOrg;
import ai.lawyers.system.service.lawyers.IAiExternalOrgService;

/**
 * 协同外部机构台账 Controller（F3）
 *
 * @author ai-lawyers
 */
@RestController
@RequestMapping("/lawyers/external/org")
public class AiExternalOrgController extends BaseController
{
    @Autowired
    private IAiExternalOrgService externalOrgService;

    @PreAuthorize("@ss.hasPermi('lawyers:externalOrg:list')")
    @GetMapping("/list")
    public TableDataInfo list(AiExternalOrg query)
    {
        startPage();
        List<AiExternalOrg> list = externalOrgService.selectAiExternalOrgList(query);
        return getDataTable(list);
    }

    @PreAuthorize("@ss.hasPermi('lawyers:externalOrg:export')")
    @Log(title = "协同机构台账", businessType = BusinessType.EXPORT)
    @PostMapping("/export")
    public void export(HttpServletResponse response, AiExternalOrg query)
    {
        List<AiExternalOrg> list = externalOrgService.selectAiExternalOrgList(query);
        ExcelUtil<AiExternalOrg> util = new ExcelUtil<AiExternalOrg>(AiExternalOrg.class);
        util.exportExcel(response, list, "协同机构台账");
    }

    @PreAuthorize("@ss.hasPermi('lawyers:externalOrg:query')")
    @GetMapping("/{orgId}")
    public AjaxResult getInfo(@PathVariable("orgId") Long orgId)
    {
        return success(externalOrgService.selectAiExternalOrgByOrgId(orgId));
    }

    @PreAuthorize("@ss.hasPermi('lawyers:externalOrg:add')")
    @Log(title = "协同机构台账", businessType = BusinessType.INSERT)
    @PostMapping
    public AjaxResult add(@RequestBody AiExternalOrg org)
    {
        org.setCreateBy(getUsername());
        return toAjax(externalOrgService.insertAiExternalOrg(org));
    }

    @PreAuthorize("@ss.hasPermi('lawyers:externalOrg:edit')")
    @Log(title = "协同机构台账", businessType = BusinessType.UPDATE)
    @PutMapping
    public AjaxResult edit(@RequestBody AiExternalOrg org)
    {
        org.setUpdateBy(getUsername());
        return toAjax(externalOrgService.updateAiExternalOrg(org));
    }

    @PreAuthorize("@ss.hasPermi('lawyers:externalOrg:remove')")
    @Log(title = "协同机构台账", businessType = BusinessType.DELETE)
    @DeleteMapping("/{orgIds}")
    public AjaxResult remove(@PathVariable Long[] orgIds)
    {
        return toAjax(externalOrgService.deleteAiExternalOrgByOrgIds(orgIds));
    }
}
