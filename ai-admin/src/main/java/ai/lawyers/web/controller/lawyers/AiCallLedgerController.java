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
import org.springframework.web.bind.annotation.RestController;
import ai.lawyers.common.annotation.Log;
import ai.lawyers.common.core.controller.BaseController;
import ai.lawyers.common.core.domain.AjaxResult;
import ai.lawyers.common.core.page.TableDataInfo;
import ai.lawyers.common.enums.BusinessType;
import ai.lawyers.common.utils.poi.ExcelUtil;
import ai.lawyers.system.domain.lawyers.AiCallLedger;
import ai.lawyers.system.service.lawyers.IAiCallLedgerService;

@RestController
@RequestMapping("/lawyers/call/ledger")
public class AiCallLedgerController extends BaseController
{
    @Autowired
    private IAiCallLedgerService aiCallLedgerService;

    @PreAuthorize("@ss.hasPermi('lawyers:call:ledger:list')")
    @GetMapping("/list")
    public TableDataInfo list(AiCallLedger aiCallLedger)
    {
        startPage();
        List<AiCallLedger> list = aiCallLedgerService.selectAiCallLedgerList(aiCallLedger);
        return getDataTable(list);
    }

    @PreAuthorize("@ss.hasPermi('lawyers:call:ledger:export')")
    @Log(title = "咨询台账", businessType = BusinessType.EXPORT)
    @PostMapping("/export")
    public void export(HttpServletResponse response, AiCallLedger aiCallLedger)
    {
        List<AiCallLedger> list = aiCallLedgerService.selectAiCallLedgerList(aiCallLedger);
        ExcelUtil<AiCallLedger> util = new ExcelUtil<AiCallLedger>(AiCallLedger.class);
        util.exportExcel(response, list, "咨询台账数据");
    }

    @PreAuthorize("@ss.hasPermi('lawyers:call:ledger:query')")
    @GetMapping(value = "/{ledgerId}")
    public AjaxResult getInfo(@PathVariable("ledgerId") Long ledgerId)
    {
        return success(aiCallLedgerService.selectAiCallLedgerByLedgerId(ledgerId));
    }

    @PreAuthorize("@ss.hasPermi('lawyers:call:ledger:add')")
    @Log(title = "咨询台账", businessType = BusinessType.INSERT)
    @PostMapping
    public AjaxResult add(@RequestBody AiCallLedger aiCallLedger)
    {
        aiCallLedger.setCreateBy(getUsername());
        return toAjax(aiCallLedgerService.insertAiCallLedger(aiCallLedger));
    }

    @PreAuthorize("@ss.hasPermi('lawyers:call:ledger:edit')")
    @Log(title = "咨询台账", businessType = BusinessType.UPDATE)
    @PutMapping
    public AjaxResult edit(@RequestBody AiCallLedger aiCallLedger)
    {
        aiCallLedger.setUpdateBy(getUsername());
        return toAjax(aiCallLedgerService.updateAiCallLedger(aiCallLedger));
    }

    @PreAuthorize("@ss.hasPermi('lawyers:call:ledger:remove')")
    @Log(title = "咨询台账", businessType = BusinessType.DELETE)
    @DeleteMapping("/{ledgerIds}")
    public AjaxResult remove(@PathVariable Long[] ledgerIds)
    {
        return toAjax(aiCallLedgerService.deleteAiCallLedgerByLedgerIds(ledgerIds));
    }

    @PreAuthorize("@ss.hasPermi('lawyers:call:ledger:add')")
    @GetMapping("/generateNo")
    public AjaxResult generateLedgerNo()
    {
        String ledgerNo = aiCallLedgerService.generateLedgerNo();
        return success(ledgerNo);
    }

    /** 台账模板列表（6 类常用咨询登记模板） */
    @PreAuthorize("@ss.hasPermi('lawyers:call:ledger:list')")
    @GetMapping("/templates")
    public AjaxResult templates()
    {
        return success(aiCallLedgerService.selectLedgerTemplates());
    }

    /** 根据来电记录ID自动填充台账字段 */
    @PreAuthorize("@ss.hasPermi('lawyers:call:ledger:add')")
    @GetMapping("/autoFill/{recordId}")
    public AjaxResult autoFill(@PathVariable("recordId") Long recordId)
    {
        return success(aiCallLedgerService.autoFillByRecordId(recordId));
    }

    /** 将台账转工单：由台账生成工单并回写 ticketId */
    @PreAuthorize("@ss.hasPermi('lawyers:call:ledger:edit')")
    @Log(title = "台账转工单", businessType = BusinessType.OTHER)
    @PostMapping("/transferTicket/{ledgerId}")
    public AjaxResult transferTicket(@PathVariable("ledgerId") Long ledgerId)
    {
        Long ticketId = aiCallLedgerService.transferToTicket(ledgerId, getUsername());
        if (ticketId != null) {
            Map<String, Object> data = new java.util.HashMap<>();
            data.put("ticketId", ticketId);
            return success(data);
        }
        return error("台账转工单失败，未找到对应台账记录");
    }
}
