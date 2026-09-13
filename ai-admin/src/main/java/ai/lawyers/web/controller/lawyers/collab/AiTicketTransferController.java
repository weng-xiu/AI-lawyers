package ai.lawyers.web.controller.lawyers.collab;

import java.util.List;
import javax.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ai.lawyers.common.annotation.Log;
import ai.lawyers.common.core.controller.BaseController;
import ai.lawyers.common.core.domain.AjaxResult;
import ai.lawyers.common.core.page.TableDataInfo;
import ai.lawyers.common.enums.BusinessType;
import ai.lawyers.common.utils.poi.ExcelUtil;
import ai.lawyers.system.domain.lawyers.AiTicketTransfer;
import ai.lawyers.system.service.lawyers.IAiTicketTransferService;

/**
 * 工单跨域转办 Controller（F3，坐席端）
 *
 * @author ai-lawyers
 */
@RestController
@RequestMapping("/lawyers/ticket/transfer")
public class AiTicketTransferController extends BaseController
{
    @Autowired
    private IAiTicketTransferService ticketTransferService;

    /**
     * 转办流水列表。
     */
    @PreAuthorize("@ss.hasPermi('lawyers:ticketTransfer:list')")
    @GetMapping("/list")
    public TableDataInfo list(AiTicketTransfer query)
    {
        startPage();
        List<AiTicketTransfer> list = ticketTransferService.selectTransferList(query);
        return getDataTable(list);
    }

    /**
     * 转办流水导出（报文留痕字段不导出，仅导出 Excel 注解列）。
     */
    @PreAuthorize("@ss.hasPermi('lawyers:ticketTransfer:export')")
    @Log(title = "转办协同", businessType = BusinessType.EXPORT)
    @PostMapping("/export")
    public void export(HttpServletResponse response, AiTicketTransfer query)
    {
        List<AiTicketTransfer> list = ticketTransferService.selectTransferList(query);
        ExcelUtil<AiTicketTransfer> util = new ExcelUtil<AiTicketTransfer>(AiTicketTransfer.class);
        util.exportExcel(response, list, "转办协同数据");
    }

    /**
     * 转办流水详情（含报文留痕，供排障审计）。
     */
    @PreAuthorize("@ss.hasPermi('lawyers:ticketTransfer:query')")
    @GetMapping("/{transferId}")
    public AjaxResult getInfo(@PathVariable("transferId") Long transferId)
    {
        return success(ticketTransferService.selectTransferById(transferId));
    }

    /**
     * 发起转办。
     * 请求体：{ "ticketId":1, "orgId":2, "remark":"最小必要备注" }
     */
    @PreAuthorize("@ss.hasPermi('lawyers:ticketTransfer:add')")
    @Log(title = "发起工单转办", businessType = BusinessType.INSERT)
    @PostMapping
    public AjaxResult transfer(@RequestBody AiTicketTransfer body)
    {
        AiTicketTransfer transfer = ticketTransferService.transferOut(
                body.getTicketId(), body.getOrgId(), body.getRemark(), getUsername());
        return AjaxResult.success("转办已发起", transfer);
    }

    /**
     * 手动重试失败推送。
     */
    @PreAuthorize("@ss.hasPermi('lawyers:ticketTransfer:retry')")
    @Log(title = "转办重新推送", businessType = BusinessType.UPDATE)
    @PostMapping("/retry/{transferId}")
    public AjaxResult retry(@PathVariable("transferId") Long transferId)
    {
        ticketTransferService.retryTransfer(transferId);
        return success("重新推送完成");
    }
}
