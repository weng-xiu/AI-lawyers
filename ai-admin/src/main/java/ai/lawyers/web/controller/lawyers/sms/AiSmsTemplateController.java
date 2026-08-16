package ai.lawyers.web.controller.lawyers.sms;

import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ai.lawyers.common.annotation.Log;
import ai.lawyers.common.core.controller.BaseController;
import ai.lawyers.common.core.domain.AjaxResult;
import ai.lawyers.common.core.page.TableDataInfo;
import ai.lawyers.common.enums.BusinessType;
import ai.lawyers.system.domain.lawyers.sms.AiSmsTemplate;
import ai.lawyers.system.mapper.lawyers.sms.AiSmsTemplateMapper;

/**
 * 短信模板Controller
 *
 * @author ai-lawyers
 */
@RestController
@RequestMapping("/lawyers/sms/template")
public class AiSmsTemplateController extends BaseController
{
    @Autowired
    private AiSmsTemplateMapper aiSmsTemplateMapper;

    @PreAuthorize("@ss.hasPermi('lawyers:smsTemplate:view')")
    @GetMapping("/list")
    public TableDataInfo list(AiSmsTemplate query)
    {
        startPage();
        return getDataTable(aiSmsTemplateMapper.selectAiSmsTemplateList(query));
    }

    @PreAuthorize("@ss.hasPermi('lawyers:smsTemplate:view')")
    @GetMapping("/{templateId}")
    public AjaxResult getInfo(@PathVariable Long templateId)
    {
        return success(aiSmsTemplateMapper.selectAiSmsTemplateByTemplateId(templateId));
    }

    @Log(title = "短信模板", businessType = BusinessType.INSERT)
    @PostMapping
    public AjaxResult add(@RequestBody AiSmsTemplate template)
    {
        template.setCreateBy(getUsername());
        return toAjax(aiSmsTemplateMapper.insertAiSmsTemplate(template));
    }

    @Log(title = "短信模板", businessType = BusinessType.UPDATE)
    @PutMapping
    public AjaxResult edit(@RequestBody AiSmsTemplate template)
    {
        template.setUpdateBy(getUsername());
        return toAjax(aiSmsTemplateMapper.updateAiSmsTemplate(template));
    }

    @Log(title = "短信模板", businessType = BusinessType.DELETE)
    @DeleteMapping("/{templateIds}")
    public AjaxResult remove(@PathVariable Long[] templateIds)
    {
        return toAjax(aiSmsTemplateMapper.deleteAiSmsTemplateByTemplateIds(templateIds));
    }

    /**
     * 全部启用模板（供设计器下拉选择）
     */
    @GetMapping("/enabled")
    public AjaxResult enabled()
    {
        AiSmsTemplate query = new AiSmsTemplate();
        query.setStatus("1");
        List<AiSmsTemplate> list = aiSmsTemplateMapper.selectAiSmsTemplateList(query);
        return success(list);
    }
}
