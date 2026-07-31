package ai.lawyers.web.controller.lawyers.ivr;

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
import ai.lawyers.system.domain.lawyers.ivr.AiIvrIntention;
import ai.lawyers.system.service.lawyers.ivr.IAiIvrIntentionService;

@RestController
@RequestMapping("/lawyers/ivr/intention")
public class AiIvrIntentionController extends BaseController
{
    @Autowired
    private IAiIvrIntentionService aiIvrIntentionService;

    @PreAuthorize("@ss.hasPermi('lawyers:ivr:intention:list')")
    @GetMapping("/list")
    public TableDataInfo list(AiIvrIntention aiIvrIntention)
    {
        startPage();
        List<AiIvrIntention> list = aiIvrIntentionService.selectAiIvrIntentionList(aiIvrIntention);
        return getDataTable(list);
    }

    @PreAuthorize("@ss.hasPermi('lawyers:ivr:intention:export')")
    @Log(title = "意图定义", businessType = BusinessType.EXPORT)
    @PostMapping("/export")
    public void export(HttpServletResponse response, AiIvrIntention aiIvrIntention)
    {
        List<AiIvrIntention> list = aiIvrIntentionService.selectAiIvrIntentionList(aiIvrIntention);
        ExcelUtil<AiIvrIntention> util = new ExcelUtil<AiIvrIntention>(AiIvrIntention.class);
        util.exportExcel(response, list, "意图定义数据");
    }

    @PreAuthorize("@ss.hasPermi('lawyers:ivr:intention:query')")
    @GetMapping(value = "/{intentionId}")
    public AjaxResult getInfo(@PathVariable("intentionId") Long intentionId)
    {
        return success(aiIvrIntentionService.selectAiIvrIntentionByIntentionId(intentionId));
    }

    @PreAuthorize("@ss.hasPermi('lawyers:ivr:intention:add')")
    @Log(title = "意图定义", businessType = BusinessType.INSERT)
    @PostMapping
    public AjaxResult add(@RequestBody AiIvrIntention aiIvrIntention)
    {
        aiIvrIntention.setCreateBy(getUsername());
        return toAjax(aiIvrIntentionService.insertAiIvrIntention(aiIvrIntention));
    }

    @PreAuthorize("@ss.hasPermi('lawyers:ivr:intention:edit')")
    @Log(title = "意图定义", businessType = BusinessType.UPDATE)
    @PutMapping
    public AjaxResult edit(@RequestBody AiIvrIntention aiIvrIntention)
    {
        aiIvrIntention.setUpdateBy(getUsername());
        return toAjax(aiIvrIntentionService.updateAiIvrIntention(aiIvrIntention));
    }

    @PreAuthorize("@ss.hasPermi('lawyers:ivr:intention:remove')")
    @Log(title = "意图定义", businessType = BusinessType.DELETE)
    @DeleteMapping("/{intentionIds}")
    public AjaxResult remove(@PathVariable Long[] intentionIds)
    {
        return toAjax(aiIvrIntentionService.deleteAiIvrIntentionByIntentionIds(intentionIds));
    }

    @PreAuthorize("@ss.hasPermi('lawyers:ivr:intention:list')")
    @GetMapping("/active")
    public AjaxResult getActiveIntentions()
    {
        return success(aiIvrIntentionService.selectActiveIntentions());
    }

    @PreAuthorize("@ss.hasPermi('lawyers:ivr:intention:match')")
    @PostMapping("/match")
    public AjaxResult matchIntention(@RequestBody java.util.Map<String, String> params)
    {
        String inputText = params.get("inputText");
        return success(aiIvrIntentionService.matchIntention(inputText));
    }
}
