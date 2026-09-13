package ai.lawyers.web.controller.lawyers.collab;

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
import ai.lawyers.system.domain.lawyers.AiSlaPolicy;
import ai.lawyers.system.service.lawyers.IAiSlaPolicyService;

/**
 * 工单 SLA 策略 Controller（F9）
 *
 * @author ai-lawyers
 */
@RestController
@RequestMapping("/lawyers/sla/policy")
public class AiSlaPolicyController extends BaseController
{
    @Autowired
    private IAiSlaPolicyService slaPolicyService;

    @PreAuthorize("@ss.hasPermi('lawyers:slaPolicy:list')")
    @GetMapping("/list")
    public TableDataInfo list(AiSlaPolicy query)
    {
        startPage();
        List<AiSlaPolicy> list = slaPolicyService.selectAiSlaPolicyList(query);
        return getDataTable(list);
    }

    @PreAuthorize("@ss.hasPermi('lawyers:slaPolicy:query')")
    @GetMapping("/{policyId}")
    public AjaxResult getInfo(@PathVariable("policyId") Long policyId)
    {
        return success(slaPolicyService.selectAiSlaPolicyByPolicyId(policyId));
    }

    @PreAuthorize("@ss.hasPermi('lawyers:slaPolicy:add')")
    @Log(title = "SLA策略", businessType = BusinessType.INSERT)
    @PostMapping
    public AjaxResult add(@RequestBody AiSlaPolicy policy)
    {
        policy.setCreateBy(getUsername());
        return toAjax(slaPolicyService.insertAiSlaPolicy(policy));
    }

    @PreAuthorize("@ss.hasPermi('lawyers:slaPolicy:edit')")
    @Log(title = "SLA策略", businessType = BusinessType.UPDATE)
    @PutMapping
    public AjaxResult edit(@RequestBody AiSlaPolicy policy)
    {
        policy.setUpdateBy(getUsername());
        return toAjax(slaPolicyService.updateAiSlaPolicy(policy));
    }

    @PreAuthorize("@ss.hasPermi('lawyers:slaPolicy:remove')")
    @Log(title = "SLA策略", businessType = BusinessType.DELETE)
    @DeleteMapping("/{policyIds}")
    public AjaxResult remove(@PathVariable Long[] policyIds)
    {
        return toAjax(slaPolicyService.deleteAiSlaPolicyByPolicyIds(policyIds));
    }
}
