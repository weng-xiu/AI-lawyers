package ai.lawyers.web.controller.lawyers.ivr;

import java.util.List;
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
import ai.lawyers.system.domain.lawyers.ivr.AiIvrIntentionLog;
import ai.lawyers.system.service.lawyers.ivr.IAiIvrIntentionLogService;

@RestController
@RequestMapping("/lawyers/ivr/intentionLog")
public class AiIvrIntentionLogController extends BaseController
{
    @Autowired
    private IAiIvrIntentionLogService aiIvrIntentionLogService;

    @PreAuthorize("@ss.hasPermi('lawyers:ivr:intentionLog:list')")
    @GetMapping("/list")
    public TableDataInfo list(AiIvrIntentionLog aiIvrIntentionLog)
    {
        startPage();
        List<AiIvrIntentionLog> list = aiIvrIntentionLogService.selectAiIvrIntentionLogList(aiIvrIntentionLog);
        return getDataTable(list);
    }

    @PreAuthorize("@ss.hasPermi('lawyers:ivr:intentionLog:list')")
    @GetMapping("/record/{recordId}")
    public AjaxResult getByRecordId(@PathVariable("recordId") Long recordId)
    {
        return success(aiIvrIntentionLogService.selectAiIvrIntentionLogByRecordId(recordId));
    }

    @PreAuthorize("@ss.hasPermi('lawyers:ivr:intentionLog:export')")
    @Log(title = "意图识别日志", businessType = BusinessType.EXPORT)
    @PostMapping("/export")
    public void export(HttpServletResponse response, AiIvrIntentionLog aiIvrIntentionLog)
    {
        List<AiIvrIntentionLog> list = aiIvrIntentionLogService.selectAiIvrIntentionLogList(aiIvrIntentionLog);
        ExcelUtil<AiIvrIntentionLog> util = new ExcelUtil<AiIvrIntentionLog>(AiIvrIntentionLog.class);
        util.exportExcel(response, list, "意图识别日志数据");
    }

    @PreAuthorize("@ss.hasPermi('lawyers:ivr:intentionLog:query')")
    @GetMapping(value = "/{logId}")
    public AjaxResult getInfo(@PathVariable("logId") Long logId)
    {
        return success(aiIvrIntentionLogService.selectAiIvrIntentionLogByLogId(logId));
    }

    @PreAuthorize("@ss.hasPermi('lawyers:ivr:intentionLog:add')")
    @Log(title = "意图识别日志", businessType = BusinessType.INSERT)
    @PostMapping
    public AjaxResult add(@RequestBody AiIvrIntentionLog aiIvrIntentionLog)
    {
        aiIvrIntentionLog.setCreateBy(getUsername());
        return toAjax(aiIvrIntentionLogService.insertAiIvrIntentionLog(aiIvrIntentionLog));
    }
}
