package ai.lawyers.admin.controller.lawyers;

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
import ai.lawyers.common.enums.BusinessType;
import ai.lawyers.system.domain.lawyers.AiSystemPrompt;
import ai.lawyers.system.service.lawyers.IAiSystemPromptService;
import ai.lawyers.common.utils.poi.ExcelUtil;
import ai.lawyers.common.core.page.TableDataInfo;

/**
 * 系统提示词配置Controller
 * 
 * @author ai-lawyers
 * @date 2025-07-15
 */
@RestController
@RequestMapping("/lawyers/systemPrompt")
public class AiSystemPromptController extends BaseController
{
    @Autowired
    private IAiSystemPromptService aiSystemPromptService;

    /**
     * 查询系统提示词配置列表
     */
    @PreAuthorize("@ss.hasPermi('lawyers:systemPrompt:list')")
    @GetMapping("/list")
    public TableDataInfo list(AiSystemPrompt aiSystemPrompt)
    {
        startPage();
        List<AiSystemPrompt> list = aiSystemPromptService.selectAiSystemPromptList(aiSystemPrompt);
        return getDataTable(list);
    }

    /**
     * 导出系统提示词配置列表
     */
    @PreAuthorize("@ss.hasPermi('lawyers:systemPrompt:export')")
    @Log(title = "系统提示词配置", businessType = BusinessType.EXPORT)
    @PostMapping("/export")
    public void export(HttpServletResponse response, AiSystemPrompt aiSystemPrompt)
    {
        List<AiSystemPrompt> list = aiSystemPromptService.selectAiSystemPromptList(aiSystemPrompt);
        ExcelUtil<AiSystemPrompt> util = new ExcelUtil<AiSystemPrompt>(AiSystemPrompt.class);
        util.exportExcel(response, list, "系统提示词配置数据");
    }

    /**
     * 获取系统提示词配置详细信息
     */
    @PreAuthorize("@ss.hasPermi('lawyers:systemPrompt:query')")
    @GetMapping(value = "/{promptId}")
    public AjaxResult getInfo(@PathVariable("promptId") Long promptId)
    {
        return success(aiSystemPromptService.selectAiSystemPromptByPromptId(promptId));
    }

    /**
     * 新增系统提示词配置
     */
    @PreAuthorize("@ss.hasPermi('lawyers:systemPrompt:add')")
    @Log(title = "系统提示词配置", businessType = BusinessType.INSERT)
    @PostMapping
    public AjaxResult add(@RequestBody AiSystemPrompt aiSystemPrompt)
    {
        return toAjax(aiSystemPromptService.insertAiSystemPrompt(aiSystemPrompt));
    }

    /**
     * 修改系统提示词配置
     */
    @PreAuthorize("@ss.hasPermi('lawyers:systemPrompt:edit')")
    @Log(title = "系统提示词配置", businessType = BusinessType.UPDATE)
    @PutMapping
    public AjaxResult edit(@RequestBody AiSystemPrompt aiSystemPrompt)
    {
        return toAjax(aiSystemPromptService.updateAiSystemPrompt(aiSystemPrompt));
    }

    /**
     * 删除系统提示词配置
     */
    @PreAuthorize("@ss.hasPermi('lawyers:systemPrompt:remove')")
    @Log(title = "系统提示词配置", businessType = BusinessType.DELETE)
	@DeleteMapping("/{promptIds}")
    public AjaxResult remove(@PathVariable Long[] promptIds)
    {
        return toAjax(aiSystemPromptService.deleteAiSystemPromptByPromptIds(promptIds));
    }

    /**
     * 获取默认的系统提示词配置
     */
    @PreAuthorize("@ss.hasPermi('lawyers:systemPrompt:query')")
    @GetMapping("/default")
    public AjaxResult getDefault(String promptType, String sceneType)
    {
        return success(aiSystemPromptService.getDefaultAiSystemPrompt(promptType, sceneType));
    }

    /**
     * 设置默认提示词
     */
    @PreAuthorize("@ss.hasPermi('lawyers:systemPrompt:edit')")
    @Log(title = "系统提示词配置", businessType = BusinessType.UPDATE)
    @PutMapping("/setDefault/{promptId}")
    public AjaxResult setDefault(@PathVariable Long promptId)
    {
        return toAjax(aiSystemPromptService.setDefaultPrompt(promptId));
    }

    /**
     * 预览提示词效果
     */
    @PreAuthorize("@ss.hasPermi('lawyers:systemPrompt:preview')")
    @Log(title = "系统提示词配置", businessType = BusinessType.OTHER)
    @PostMapping("/preview/{promptId}")
    public AjaxResult preview(@PathVariable Long promptId, @RequestBody String variables)
    {
        String result = aiSystemPromptService.previewPrompt(promptId, variables);
        return success(result);
    }
}