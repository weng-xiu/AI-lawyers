package ai.lawyers.web.controller.lawyers.ivr;

import java.util.List;
import javax.servlet.http.HttpServletResponse;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
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
import ai.lawyers.system.domain.lawyers.ivr.AiIvrExecutionLog;
import ai.lawyers.system.service.lawyers.ivr.IAiIvrExecutionLogService;

@RestController
@RequestMapping("/lawyers/ivr/executionLog")
public class AiIvrExecutionLogController extends BaseController
{
    @Autowired
    private IAiIvrExecutionLogService aiIvrExecutionLogService;

    @PreAuthorize("@ss.hasPermi('lawyers:ivr:executionLog:list')")
    @GetMapping("/list")
    public TableDataInfo list(AiIvrExecutionLog aiIvrExecutionLog)
    {
        startPage();
        List<AiIvrExecutionLog> list = aiIvrExecutionLogService.selectAiIvrExecutionLogList(aiIvrExecutionLog);
        return getDataTable(list);
    }

    @PreAuthorize("@ss.hasPermi('lawyers:ivr:executionLog:export')")
    @Log(title = "IVR执行日志", businessType = BusinessType.EXPORT)
    @PostMapping("/export")
    public void export(HttpServletResponse response, AiIvrExecutionLog aiIvrExecutionLog)
    {
        List<AiIvrExecutionLog> list = aiIvrExecutionLogService.selectAiIvrExecutionLogList(aiIvrExecutionLog);
        ExcelUtil<AiIvrExecutionLog> util = new ExcelUtil<AiIvrExecutionLog>(AiIvrExecutionLog.class);
        util.exportExcel(response, list, "IVR执行日志数据");
    }

    @PreAuthorize("@ss.hasPermi('lawyers:ivr:executionLog:query')")
    @GetMapping(value = "/{execId}")
    public AjaxResult getInfo(@PathVariable("execId") Long execId)
    {
        return success(aiIvrExecutionLogService.selectAiIvrExecutionLogByExecId(execId));
    }

    @PreAuthorize("@ss.hasPermi('lawyers:ivr:executionLog:query')")
    @GetMapping(value = "/session/{sessionId}")
    public AjaxResult getBySessionId(@PathVariable("sessionId") String sessionId)
    {
        return success(aiIvrExecutionLogService.selectAiIvrExecutionLogBySessionId(sessionId));
    }

    @PreAuthorize("@ss.hasPermi('lawyers:ivr:executionLog:add')")
    @Log(title = "IVR执行日志", businessType = BusinessType.INSERT)
    @PostMapping
    public AjaxResult add(@RequestBody AiIvrExecutionLog aiIvrExecutionLog)
    {
        aiIvrExecutionLog.setCreateBy(getUsername());
        return toAjax(aiIvrExecutionLogService.insertAiIvrExecutionLog(aiIvrExecutionLog));
    }

    @PreAuthorize("@ss.hasPermi('lawyers:ivr:executionLog:edit')")
    @Log(title = "IVR执行日志", businessType = BusinessType.UPDATE)
    @PutMapping
    public AjaxResult edit(@RequestBody AiIvrExecutionLog aiIvrExecutionLog)
    {
        aiIvrExecutionLog.setUpdateBy(getUsername());
        return toAjax(aiIvrExecutionLogService.updateAiIvrExecutionLog(aiIvrExecutionLog));
    }
}
