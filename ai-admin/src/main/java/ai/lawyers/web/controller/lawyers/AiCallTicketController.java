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
import ai.lawyers.system.domain.lawyers.AiCallTicket;
import ai.lawyers.system.service.lawyers.IAiCallTicketService;

@RestController
@RequestMapping("/lawyers/call/ticket")
public class AiCallTicketController extends BaseController
{
    @Autowired
    private IAiCallTicketService aiCallTicketService;

    @PreAuthorize("@ss.hasPermi('lawyers:call:ticket:list')")
    @GetMapping("/list")
    public TableDataInfo list(AiCallTicket aiCallTicket)
    {
        startPage();
        List<AiCallTicket> list = aiCallTicketService.selectAiCallTicketList(aiCallTicket);
        return getDataTable(list);
    }

    @PreAuthorize("@ss.hasPermi('lawyers:call:ticket:export')")
    @Log(title = "工单", businessType = BusinessType.EXPORT)
    @PostMapping("/export")
    public void export(HttpServletResponse response, AiCallTicket aiCallTicket)
    {
        List<AiCallTicket> list = aiCallTicketService.selectAiCallTicketList(aiCallTicket);
        ExcelUtil<AiCallTicket> util = new ExcelUtil<AiCallTicket>(AiCallTicket.class);
        util.exportExcel(response, list, "工单数据");
    }

    @PreAuthorize("@ss.hasPermi('lawyers:call:ticket:query')")
    @GetMapping(value = "/{ticketId}")
    public AjaxResult getInfo(@PathVariable("ticketId") Long ticketId)
    {
        return success(aiCallTicketService.selectAiCallTicketByTicketId(ticketId));
    }

    @PreAuthorize("@ss.hasPermi('lawyers:call:ticket:add')")
    @Log(title = "工单", businessType = BusinessType.INSERT)
    @PostMapping
    public AjaxResult add(@RequestBody AiCallTicket aiCallTicket)
    {
        aiCallTicket.setCreateBy(getUsername());
        return toAjax(aiCallTicketService.insertAiCallTicket(aiCallTicket));
    }

    @PreAuthorize("@ss.hasPermi('lawyers:call:ticket:edit')")
    @Log(title = "工单", businessType = BusinessType.UPDATE)
    @PutMapping
    public AjaxResult edit(@RequestBody AiCallTicket aiCallTicket)
    {
        aiCallTicket.setUpdateBy(getUsername());
        return toAjax(aiCallTicketService.updateAiCallTicket(aiCallTicket));
    }

    @PreAuthorize("@ss.hasPermi('lawyers:call:ticket:remove')")
    @Log(title = "工单", businessType = BusinessType.DELETE)
    @DeleteMapping("/{ticketIds}")
    public AjaxResult remove(@PathVariable Long[] ticketIds)
    {
        return toAjax(aiCallTicketService.deleteAiCallTicketByTicketIds(ticketIds));
    }

    @PreAuthorize("@ss.hasPermi('lawyers:call:ticket:list')")
    @GetMapping("/user/{assignUserId}")
    public AjaxResult getTicketsByAssignUserId(@PathVariable("assignUserId") Long assignUserId)
    {
        List<AiCallTicket> list = aiCallTicketService.selectAiCallTicketByAssignUserId(assignUserId);
        return success(list);
    }

    @PreAuthorize("@ss.hasPermi('lawyers:call:ticket:process')")
    @Log(title = "工单处理", businessType = BusinessType.UPDATE)
    @PostMapping("/process")
    public AjaxResult processTicket(@RequestBody java.util.Map<String, Object> params)
    {
        Long ticketId = Long.valueOf(params.get("ticketId").toString());
        String processContent = params.get("processContent").toString();
        Long assignUserId = params.get("assignUserId") != null ? Long.valueOf(params.get("assignUserId").toString()) : null;
        String assignUserName = params.get("assignUserName") != null ? params.get("assignUserName").toString() : null;
        int result = aiCallTicketService.updateTicketProcess(ticketId, processContent, assignUserId, assignUserName);
        if (result > 0) {
            return success("工单处理成功");
        }
        return error("工单处理失败");
    }

    @PreAuthorize("@ss.hasPermi('lawyers:call:ticket:complete')")
    @Log(title = "工单完成", businessType = BusinessType.UPDATE)
    @PostMapping("/complete")
    public AjaxResult completeTicket(@RequestBody java.util.Map<String, Object> params)
    {
        Long ticketId = Long.valueOf(params.get("ticketId").toString());
        int result = aiCallTicketService.updateTicketStatus(ticketId, "2");
        if (result > 0) {
            return success("工单已完成");
        }
        return error("工单完成失败");
    }

    @PreAuthorize("@ss.hasPermi('lawyers:call:ticket:archive')")
    @Log(title = "工单归档", businessType = BusinessType.UPDATE)
    @PostMapping("/archive")
    public AjaxResult archiveTicket(@RequestBody java.util.Map<String, Object> params)
    {
        Long ticketId = Long.valueOf(params.get("ticketId").toString());
        int result = aiCallTicketService.archiveTicket(ticketId);
        if (result > 0) {
            return success("工单已归档");
        }
        return error("工单归档失败");
    }

    @PreAuthorize("@ss.hasPermi('lawyers:call:ticket:add')")
    @GetMapping("/generateNo")
    public AjaxResult generateTicketNo()
    {
        String ticketNo = aiCallTicketService.generateTicketNo();
        return success(ticketNo);
    }

    /**
     * 查询工单流转记录（创建/处理/完成/归档）。
     */
    @PreAuthorize("@ss.hasPermi('lawyers:call:ticket:query')")
    @GetMapping("/{ticketId}/timeline")
    public AjaxResult timeline(@PathVariable("ticketId") Long ticketId)
    {
        return success(aiCallTicketService.selectTicketTimeline(ticketId));
    }
}
