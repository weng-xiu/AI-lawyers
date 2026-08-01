package ai.lawyers.web.controller.lawyers.outbound;

import java.util.List;
import java.util.Map;
import javax.servlet.http.HttpServletResponse;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
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
import ai.lawyers.system.domain.lawyers.outbound.AiOutboundResult;
import ai.lawyers.system.service.lawyers.outbound.IAiOutboundResultService;

@RestController
@RequestMapping("/lawyers/outbound/result")
public class AiOutboundResultController extends BaseController
{
    @Autowired
    private IAiOutboundResultService aiOutboundResultService;

    @PreAuthorize("@ss.hasPermi('lawyers:outbound:result:list')")
    @GetMapping("/list")
    public TableDataInfo list(AiOutboundResult aiOutboundResult)
    {
        startPage();
        List<AiOutboundResult> list = aiOutboundResultService.selectAiOutboundResultList(aiOutboundResult);
        return getDataTable(list);
    }

    @PreAuthorize("@ss.hasPermi('lawyers:outbound:result:list')")
    @GetMapping("/task/{taskId}")
    public AjaxResult getByTaskId(@PathVariable("taskId") Long taskId)
    {
        return success(aiOutboundResultService.selectAiOutboundResultByTaskId(taskId));
    }

    @PreAuthorize("@ss.hasPermi('lawyers:outbound:result:query')")
    @GetMapping("/callee/{calleeId}")
    public AjaxResult getByCalleeId(@PathVariable("calleeId") Long calleeId)
    {
        return success(aiOutboundResultService.selectAiOutboundResultByCalleeId(calleeId));
    }

    @PreAuthorize("@ss.hasPermi('lawyers:outbound:result:list')")
    @GetMapping("/statistics/{taskId}")
    public AjaxResult getTaskStatistics(@PathVariable("taskId") Long taskId)
    {
        return success(aiOutboundResultService.getTaskResultStatistics(taskId));
    }

    @PreAuthorize("@ss.hasPermi('lawyers:outbound:result:export')")
    @Log(title = "外呼结果", businessType = BusinessType.EXPORT)
    @PostMapping("/export")
    public void export(HttpServletResponse response, AiOutboundResult aiOutboundResult)
    {
        List<AiOutboundResult> list = aiOutboundResultService.selectAiOutboundResultList(aiOutboundResult);
        ExcelUtil<AiOutboundResult> util = new ExcelUtil<AiOutboundResult>(AiOutboundResult.class);
        util.exportExcel(response, list, "外呼结果数据");
    }

    @PreAuthorize("@ss.hasPermi('lawyers:outbound:result:query')")
    @GetMapping(value = "/{resultId}")
    public AjaxResult getInfo(@PathVariable("resultId") Long resultId)
    {
        return success(aiOutboundResultService.selectAiOutboundResultByResultId(resultId));
    }

    @PreAuthorize("@ss.hasPermi('lawyers:outbound:result:add')")
    @Log(title = "外呼结果", businessType = BusinessType.INSERT)
    @PostMapping
    public AjaxResult add(@RequestBody AiOutboundResult aiOutboundResult)
    {
        aiOutboundResult.setCreateBy(getUsername());
        return toAjax(aiOutboundResultService.insertAiOutboundResult(aiOutboundResult));
    }
}
