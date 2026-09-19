package ai.lawyers.web.controller.lawyers;

import java.util.List;
import java.util.Map;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import ai.lawyers.common.annotation.Log;
import ai.lawyers.common.core.controller.BaseController;
import ai.lawyers.common.core.domain.AjaxResult;
import ai.lawyers.common.core.page.TableDataInfo;
import ai.lawyers.common.enums.BusinessType;
import ai.lawyers.common.utils.poi.ExcelUtil;
import ai.lawyers.system.domain.lawyers.AiRiskWarning;
import ai.lawyers.system.domain.lawyers.AiTicketTransfer;
import ai.lawyers.system.service.lawyers.IAiRiskWarningService;

@RestController
@RequestMapping("/lawyers/riskWarning")
public class AiRiskWarningController extends BaseController
{
    @Autowired
    private IAiRiskWarningService aiRiskWarningService;

    @PreAuthorize("@ss.hasPermi('lawyers:riskWarning:list')")
    @GetMapping("/list")
    public TableDataInfo list(AiRiskWarning aiRiskWarning)
    {
        startPage();
        List<AiRiskWarning> list = aiRiskWarningService.selectAiRiskWarningList(aiRiskWarning);
        return getDataTable(list);
    }

    @PreAuthorize("@ss.hasPermi('lawyers:riskWarning:export')")
    @Log(title = "风险预警记录", businessType = BusinessType.EXPORT)
    @PostMapping("/export")
    public void export(HttpServletResponse response, AiRiskWarning aiRiskWarning)
    {
        List<AiRiskWarning> list = aiRiskWarningService.selectAiRiskWarningList(aiRiskWarning);
        ExcelUtil<AiRiskWarning> util = new ExcelUtil<AiRiskWarning>(AiRiskWarning.class);
        util.exportExcel(response, list, "风险预警记录数据");
    }

    @PreAuthorize("@ss.hasPermi('lawyers:riskWarning:query')")
    @GetMapping(value = "/{warningId}")
    public AjaxResult getInfo(@PathVariable("warningId") Long warningId)
    {
        return success(aiRiskWarningService.selectAiRiskWarningByWarningId(warningId));
    }

    @PreAuthorize("@ss.hasPermi('lawyers:riskWarning:add')")
    @Log(title = "风险预警记录", businessType = BusinessType.INSERT)
    @PostMapping
    public AjaxResult add(@RequestBody AiRiskWarning aiRiskWarning)
    {
        return toAjax(aiRiskWarningService.insertAiRiskWarning(aiRiskWarning));
    }

    @PreAuthorize("@ss.hasPermi('lawyers:riskWarning:edit')")
    @Log(title = "风险预警记录", businessType = BusinessType.UPDATE)
    @PutMapping
    public AjaxResult edit(@RequestBody AiRiskWarning aiRiskWarning)
    {
        return toAjax(aiRiskWarningService.updateAiRiskWarning(aiRiskWarning));
    }

    @PreAuthorize("@ss.hasPermi('lawyers:riskWarning:remove')")
    @Log(title = "风险预警记录", businessType = BusinessType.DELETE)
    @DeleteMapping("/{warningIds}")
    public AjaxResult remove(@PathVariable Long[] warningIds)
    {
        return toAjax(aiRiskWarningService.deleteAiRiskWarningByWarningIds(warningIds));
    }

    /**
     * F3 风险联动：按建议条线查询可转办的启用机构（仅公开字段）。
     */
    @PreAuthorize("@ss.hasPermi('lawyers:riskWarning:query')")
    @GetMapping("/transfer/orgs")
    public AjaxResult transferOrgs(@RequestParam(value = "externalType", required = false) String externalType)
    {
        return success(aiRiskWarningService.selectTransferOrgs(externalType));
    }

    /**
     * F3 风险联动：高风险预警一键确认转办（自动建单+复用转办流水）。
     * 请求体：{ "warningId":1, "orgId":2, "remark":"最小必要备注" }
     */
    @PreAuthorize("@ss.hasPermi('lawyers:ticketTransfer:add')")
    @Log(title = "风险预警一键转办", businessType = BusinessType.INSERT)
    @PostMapping("/transfer")
    public AjaxResult transfer(@RequestBody Map<String, Object> body)
    {
        Long warningId = parseLong(body.get("warningId"));
        Long orgId = parseLong(body.get("orgId"));
        String remark = body.get("remark") == null ? null : String.valueOf(body.get("remark"));
        AiTicketTransfer transfer = aiRiskWarningService.transferByWarning(warningId, orgId, remark, getUsername());
        return AjaxResult.success("转办已发起", transfer);
    }

    private Long parseLong(Object value)
    {
        if (value == null)
        {
            return null;
        }
        String text = String.valueOf(value).trim();
        return text.isEmpty() ? null : Long.valueOf(text);
    }
}
