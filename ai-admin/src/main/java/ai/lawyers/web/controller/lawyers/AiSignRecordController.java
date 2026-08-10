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
import ai.lawyers.system.domain.lawyers.AiSignRecord;
import ai.lawyers.system.service.lawyers.IAiSignRecordService;

@RestController
@RequestMapping("/lawyers/signRecord")
public class AiSignRecordController extends BaseController
{
    @Autowired
    private IAiSignRecordService aiSignRecordService;

    @PreAuthorize("@ss.hasPermi('lawyers:signRecord:list')")
    @GetMapping("/list")
    public TableDataInfo list(AiSignRecord aiSignRecord)
    {
        startPage();
        List<AiSignRecord> list = aiSignRecordService.selectAiSignRecordList(aiSignRecord);
        return getDataTable(list);
    }

    @PreAuthorize("@ss.hasPermi('lawyers:signRecord:export')")
    @Log(title = "签署记录", businessType = BusinessType.EXPORT)
    @PostMapping("/export")
    public void export(HttpServletResponse response, AiSignRecord aiSignRecord)
    {
        List<AiSignRecord> list = aiSignRecordService.selectAiSignRecordList(aiSignRecord);
        ExcelUtil<AiSignRecord> util = new ExcelUtil<AiSignRecord>(AiSignRecord.class);
        util.exportExcel(response, list, "签署记录数据");
    }

    @PreAuthorize("@ss.hasPermi('lawyers:signRecord:query')")
    @GetMapping(value = "/{signId}")
    public AjaxResult getInfo(@PathVariable("signId") Long signId)
    {
        return success(aiSignRecordService.selectAiSignRecordBySignId(signId));
    }

    @PreAuthorize("@ss.hasPermi('lawyers:signRecord:add')")
    @Log(title = "签署记录", businessType = BusinessType.INSERT)
    @PostMapping
    public AjaxResult add(@RequestBody AiSignRecord aiSignRecord)
    {
        aiSignRecord.setCreateBy(getUsername());
        return toAjax(aiSignRecordService.insertAiSignRecord(aiSignRecord));
    }

    @PreAuthorize("@ss.hasPermi('lawyers:signRecord:edit')")
    @Log(title = "签署记录", businessType = BusinessType.UPDATE)
    @PutMapping
    public AjaxResult edit(@RequestBody AiSignRecord aiSignRecord)
    {
        aiSignRecord.setUpdateBy(getUsername());
        return toAjax(aiSignRecordService.updateAiSignRecord(aiSignRecord));
    }

    @PreAuthorize("@ss.hasPermi('lawyers:signRecord:remove')")
    @Log(title = "签署记录", businessType = BusinessType.DELETE)
    @DeleteMapping("/{signIds}")
    public AjaxResult remove(@PathVariable Long[] signIds)
    {
        return toAjax(aiSignRecordService.deleteAiSignRecordBySignIds(signIds));
    }
}
