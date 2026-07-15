package ai.lawyers.web.controller.lawyers;

import java.util.List;
import javax.servlet.http.HttpServletResponse;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
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
import ai.lawyers.system.domain.lawyers.AiCallTransfer;
import ai.lawyers.system.service.lawyers.IAiCallTransferService;

@RestController
@RequestMapping("/lawyers/call/transfer")
public class AiCallTransferController extends BaseController
{
    @Autowired
    private IAiCallTransferService aiCallTransferService;

    @PreAuthorize("@ss.hasPermi('lawyers:call:transfer:list')")
    @GetMapping("/list")
    public TableDataInfo list(AiCallTransfer aiCallTransfer)
    {
        startPage();
        List<AiCallTransfer> list = aiCallTransferService.selectAiCallTransferList(aiCallTransfer);
        return getDataTable(list);
    }

    @PreAuthorize("@ss.hasPermi('lawyers:call:transfer:query')")
    @GetMapping(value = "/{transferId}")
    public AjaxResult getInfo(@PathVariable("transferId") Long transferId)
    {
        return success(aiCallTransferService.selectAiCallTransferByTransferId(transferId));
    }

    @PreAuthorize("@ss.hasPermi('lawyers:call:transfer:add')")
    @Log(title = "转接记录", businessType = BusinessType.INSERT)
    @PostMapping
    public AjaxResult add(@RequestBody AiCallTransfer aiCallTransfer)
    {
        aiCallTransfer.setCreateBy(getUsername());
        return toAjax(aiCallTransferService.insertAiCallTransfer(aiCallTransfer));
    }

    @PreAuthorize("@ss.hasPermi('lawyers:call:transfer:remove')")
    @Log(title = "转接记录", businessType = BusinessType.DELETE)
    @DeleteMapping("/{transferIds}")
    public AjaxResult remove(@PathVariable Long[] transferIds)
    {
        return toAjax(aiCallTransferService.deleteAiCallTransferByTransferIds(transferIds));
    }

    @PreAuthorize("@ss.hasPermi('lawyers:call:transfer:transfer')")
    @Log(title = "咨询转接", businessType = BusinessType.UPDATE)
    @PostMapping("/doTransfer")
    public AjaxResult doTransfer(@RequestBody java.util.Map<String, Object> params)
    {
        Long recordId = Long.valueOf(params.get("recordId").toString());
        Long fromAgentId = Long.valueOf(params.get("fromAgentId").toString());
        String fromAgentName = params.get("fromAgentName").toString();
        Long toAgentId = Long.valueOf(params.get("toAgentId").toString());
        String toAgentName = params.get("toAgentName").toString();
        String reason = params.get("reason") != null ? params.get("reason").toString() : "";
        int result = aiCallTransferService.transferCall(recordId, fromAgentId, fromAgentName, toAgentId, toAgentName, reason);
        if (result > 0) {
            return success("转接成功");
        }
        return error("转接失败");
    }

    @PreAuthorize("@ss.hasPermi('lawyers:call:transfer:list')")
    @GetMapping("/record/{recordId}")
    public AjaxResult getTransfersByRecordId(@PathVariable("recordId") Long recordId)
    {
        List<AiCallTransfer> list = aiCallTransferService.selectAiCallTransferByRecordId(recordId);
        return success(list);
    }
}
