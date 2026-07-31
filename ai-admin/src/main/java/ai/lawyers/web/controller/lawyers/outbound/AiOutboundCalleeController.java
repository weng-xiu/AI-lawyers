package ai.lawyers.web.controller.lawyers.outbound;

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
import ai.lawyers.system.domain.lawyers.outbound.AiOutboundCallee;
import ai.lawyers.system.service.lawyers.outbound.IAiOutboundCalleeService;

@RestController
@RequestMapping("/lawyers/outbound/callee")
public class AiOutboundCalleeController extends BaseController
{
    @Autowired
    private IAiOutboundCalleeService aiOutboundCalleeService;

    @PreAuthorize("@ss.hasPermi('lawyers:outbound:callee:list')")
    @GetMapping("/list")
    public TableDataInfo list(AiOutboundCallee aiOutboundCallee)
    {
        startPage();
        List<AiOutboundCallee> list = aiOutboundCalleeService.selectAiOutboundCalleeList(aiOutboundCallee);
        return getDataTable(list);
    }

    @PreAuthorize("@ss.hasPermi('lawyers:outbound:callee:export')")
    @Log(title = "外呼号码", businessType = BusinessType.EXPORT)
    @PostMapping("/export")
    public void export(HttpServletResponse response, AiOutboundCallee aiOutboundCallee)
    {
        List<AiOutboundCallee> list = aiOutboundCalleeService.selectAiOutboundCalleeList(aiOutboundCallee);
        ExcelUtil<AiOutboundCallee> util = new ExcelUtil<AiOutboundCallee>(AiOutboundCallee.class);
        util.exportExcel(response, list, "外呼号码数据");
    }

    @PreAuthorize("@ss.hasPermi('lawyers:outbound:callee:query')")
    @GetMapping(value = "/{calleeId}")
    public AjaxResult getInfo(@PathVariable("calleeId") Long calleeId)
    {
        return success(aiOutboundCalleeService.selectAiOutboundCalleeByCalleeId(calleeId));
    }

    @PreAuthorize("@ss.hasPermi('lawyers:outbound:callee:query')")
    @GetMapping(value = "/task/{taskId}")
    public AjaxResult getCalleesByTaskId(@PathVariable("taskId") Long taskId)
    {
        return success(aiOutboundCalleeService.selectAiOutboundCalleeByTaskId(taskId));
    }

    @PreAuthorize("@ss.hasPermi('lawyers:outbound:callee:add')")
    @Log(title = "外呼号码", businessType = BusinessType.INSERT)
    @PostMapping
    public AjaxResult add(@RequestBody AiOutboundCallee aiOutboundCallee)
    {
        aiOutboundCallee.setCreateBy(getUsername());
        return toAjax(aiOutboundCalleeService.insertAiOutboundCallee(aiOutboundCallee));
    }

    @PreAuthorize("@ss.hasPermi('lawyers:outbound:callee:edit')")
    @Log(title = "外呼号码", businessType = BusinessType.UPDATE)
    @PutMapping
    public AjaxResult edit(@RequestBody AiOutboundCallee aiOutboundCallee)
    {
        aiOutboundCallee.setUpdateBy(getUsername());
        return toAjax(aiOutboundCalleeService.updateAiOutboundCallee(aiOutboundCallee));
    }

    @PreAuthorize("@ss.hasPermi('lawyers:outbound:callee:remove')")
    @Log(title = "外呼号码", businessType = BusinessType.DELETE)
    @DeleteMapping("/{calleeIds}")
    public AjaxResult remove(@PathVariable Long[] calleeIds)
    {
        return toAjax(aiOutboundCalleeService.deleteAiOutboundCalleeByCalleeIds(calleeIds));
    }

    @PreAuthorize("@ss.hasPermi('lawyers:outbound:callee:remove')")
    @Log(title = "外呼号码", businessType = BusinessType.DELETE)
    @DeleteMapping("/task/{taskId}")
    public AjaxResult removeByTaskId(@PathVariable Long taskId)
    {
        return toAjax(aiOutboundCalleeService.deleteAiOutboundCalleeByTaskId(taskId));
    }

    @PreAuthorize("@ss.hasPermi('lawyers:outbound:callee:add')")
    @Log(title = "批量导入外呼号码", businessType = BusinessType.INSERT)
    @PostMapping("/batch/{taskId}")
    public AjaxResult batchAdd(@PathVariable Long taskId, @RequestBody List<AiOutboundCallee> callees)
    {
        return toAjax(aiOutboundCalleeService.batchInsertCallees(taskId, callees));
    }
}
