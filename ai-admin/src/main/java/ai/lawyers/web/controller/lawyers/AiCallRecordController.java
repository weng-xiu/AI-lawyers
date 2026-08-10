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
import ai.lawyers.system.domain.lawyers.AiCallRecord;
import ai.lawyers.system.service.lawyers.IAiCallRecordService;

@RestController
@RequestMapping("/lawyers/call/record")
public class AiCallRecordController extends BaseController
{
    @Autowired
    private IAiCallRecordService aiCallRecordService;

    @PreAuthorize("@ss.hasPermi('lawyers:call:record:list')")
    @GetMapping("/list")
    public TableDataInfo list(AiCallRecord aiCallRecord)
    {
        startPage();
        List<AiCallRecord> list = aiCallRecordService.selectAiCallRecordList(aiCallRecord);
        return getDataTable(list);
    }

    @PreAuthorize("@ss.hasPermi('lawyers:call:record:export')")
    @Log(title = "来电记录", businessType = BusinessType.EXPORT)
    @PostMapping("/export")
    public void export(HttpServletResponse response, AiCallRecord aiCallRecord)
    {
        List<AiCallRecord> list = aiCallRecordService.selectAiCallRecordList(aiCallRecord);
        ExcelUtil<AiCallRecord> util = new ExcelUtil<AiCallRecord>(AiCallRecord.class);
        util.exportExcel(response, list, "来电记录数据");
    }

    @PreAuthorize("@ss.hasPermi('lawyers:call:record:query')")
    @GetMapping(value = "/{recordId}")
    public AjaxResult getInfo(@PathVariable("recordId") Long recordId)
    {
        return success(aiCallRecordService.selectAiCallRecordByRecordId(recordId));
    }

    @PreAuthorize("@ss.hasPermi('lawyers:call:record:add')")
    @Log(title = "来电记录", businessType = BusinessType.INSERT)
    @PostMapping
    public AjaxResult add(@RequestBody AiCallRecord aiCallRecord)
    {
        aiCallRecord.setCreateBy(getUsername());
        return toAjax(aiCallRecordService.insertAiCallRecord(aiCallRecord));
    }

    @PreAuthorize("@ss.hasPermi('lawyers:call:record:edit')")
    @Log(title = "来电记录", businessType = BusinessType.UPDATE)
    @PutMapping
    public AjaxResult edit(@RequestBody AiCallRecord aiCallRecord)
    {
        aiCallRecord.setUpdateBy(getUsername());
        return toAjax(aiCallRecordService.updateAiCallRecord(aiCallRecord));
    }

    @PreAuthorize("@ss.hasPermi('lawyers:call:record:remove')")
    @Log(title = "来电记录", businessType = BusinessType.DELETE)
    @DeleteMapping("/{recordIds}")
    public AjaxResult remove(@PathVariable Long[] recordIds)
    {
        return toAjax(aiCallRecordService.deleteAiCallRecordByRecordIds(recordIds));
    }

    @PreAuthorize("@ss.hasPermi('lawyers:call:record:list')")
    @GetMapping("/agent/{agentId}")
    public AjaxResult getRecordsByAgentId(@PathVariable("agentId") Long agentId)
    {
        List<AiCallRecord> list = aiCallRecordService.selectAiCallRecordByAgentId(agentId);
        return success(list);
    }

    @PreAuthorize("@ss.hasPermi('lawyers:call:record:statistics')")
    @GetMapping("/statistics")
    public AjaxResult getStatistics()
    {
        java.util.Map<String, Object> statistics = aiCallRecordService.getCallStatistics();
        return success(statistics);
    }

    @PreAuthorize("@ss.hasPermi('lawyers:call:record:statistics')")
    @GetMapping("/statistics/agent")
    public AjaxResult getStatisticsByAgent()
    {
        List<java.util.Map<String, Object>> data = aiCallRecordService.getCallStatisticsByAgent();
        return success(data);
    }

    @PreAuthorize("@ss.hasPermi('lawyers:call:record:statistics')")
    @GetMapping("/statistics/category")
    public AjaxResult getStatisticsByCategory()
    {
        List<java.util.Map<String, Object>> data = aiCallRecordService.getCallStatisticsByCategory();
        return success(data);
    }

    @PreAuthorize("@ss.hasPermi('lawyers:call:record:statistics')")
    @GetMapping("/statistics/date")
    public AjaxResult getStatisticsByDate(Integer days)
    {
        List<java.util.Map<String, Object>> data = aiCallRecordService.getCallStatisticsByDate(days);
        return success(data);
    }

    /** 工作台首页汇总 */
    @GetMapping("/workbench")
    public AjaxResult getWorkbenchSummary()
    {
        return success(aiCallRecordService.getWorkbenchSummary());
    }
}
