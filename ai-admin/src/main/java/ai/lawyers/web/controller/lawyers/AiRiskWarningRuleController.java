package ai.lawyers.web.controller.lawyers;

import java.util.List;
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
import ai.lawyers.system.domain.lawyers.AiRiskWarningRule;
import ai.lawyers.system.service.lawyers.IAiRiskWarningRuleService;

@RestController
@RequestMapping("/lawyers/riskWarning/rule")
public class AiRiskWarningRuleController extends BaseController
{
    @Autowired
    private IAiRiskWarningRuleService aiRiskWarningRuleService;

    @PreAuthorize("@ss.hasPermi('lawyers:riskWarning:list')")
    @GetMapping("/list")
    public TableDataInfo list(AiRiskWarningRule aiRiskWarningRule)
    {
        startPage();
        List<AiRiskWarningRule> list = aiRiskWarningRuleService.selectAiRiskWarningRuleList(aiRiskWarningRule);
        return getDataTable(list);
    }

    @PreAuthorize("@ss.hasPermi('lawyers:riskWarning:query')")
    @GetMapping(value = "/{ruleId}")
    public AjaxResult getInfo(@PathVariable("ruleId") Long ruleId)
    {
        return success(aiRiskWarningRuleService.selectAiRiskWarningRuleByRuleId(ruleId));
    }

    @PreAuthorize("@ss.hasPermi('lawyers:riskWarning:add')")
    @Log(title = "风险预警规则", businessType = BusinessType.INSERT)
    @PostMapping
    public AjaxResult add(@RequestBody AiRiskWarningRule aiRiskWarningRule)
    {
        aiRiskWarningRule.setCreateBy(getUsername());
        return toAjax(aiRiskWarningRuleService.insertAiRiskWarningRule(aiRiskWarningRule));
    }

    @PreAuthorize("@ss.hasPermi('lawyers:riskWarning:edit')")
    @Log(title = "风险预警规则", businessType = BusinessType.UPDATE)
    @PutMapping
    public AjaxResult edit(@RequestBody AiRiskWarningRule aiRiskWarningRule)
    {
        aiRiskWarningRule.setUpdateBy(getUsername());
        return toAjax(aiRiskWarningRuleService.updateAiRiskWarningRule(aiRiskWarningRule));
    }

    @PreAuthorize("@ss.hasPermi('lawyers:riskWarning:remove')")
    @Log(title = "风险预警规则", businessType = BusinessType.DELETE)
    @DeleteMapping("/{ruleIds}")
    public AjaxResult remove(@PathVariable Long[] ruleIds)
    {
        return toAjax(aiRiskWarningRuleService.deleteAiRiskWarningRuleByRuleIds(ruleIds));
    }
}
