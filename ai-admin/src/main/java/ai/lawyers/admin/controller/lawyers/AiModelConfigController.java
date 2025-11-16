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
import ai.lawyers.system.domain.lawyers.AiModelConfig;
import ai.lawyers.system.service.lawyers.IAiModelConfigService;
import ai.lawyers.common.utils.poi.ExcelUtil;
import ai.lawyers.common.core.page.TableDataInfo;

/**
 * AI模型参数配置Controller
 * 
 * @author ai-lawyers
 * @date 2025-07-15
 */
@RestController
@RequestMapping("/lawyers/modelConfig")
public class AiModelConfigController extends BaseController
{
    @Autowired
    private IAiModelConfigService aiModelConfigService;

    /**
     * 查询AI模型参数配置列表
     */
    @PreAuthorize("@ss.hasPermi('lawyers:modelConfig:list')")
    @GetMapping("/list")
    public TableDataInfo list(AiModelConfig aiModelConfig)
    {
        startPage();
        List<AiModelConfig> list = aiModelConfigService.selectAiModelConfigList(aiModelConfig);
        return getDataTable(list);
    }

    /**
     * 导出AI模型参数配置列表
     */
    @PreAuthorize("@ss.hasPermi('lawyers:modelConfig:export')")
    @Log(title = "AI模型参数配置", businessType = BusinessType.EXPORT)
    @PostMapping("/export")
    public void export(HttpServletResponse response, AiModelConfig aiModelConfig)
    {
        List<AiModelConfig> list = aiModelConfigService.selectAiModelConfigList(aiModelConfig);
        ExcelUtil<AiModelConfig> util = new ExcelUtil<AiModelConfig>(AiModelConfig.class);
        util.exportExcel(response, list, "AI模型参数配置数据");
    }

    /**
     * 获取AI模型参数配置详细信息
     */
    @PreAuthorize("@ss.hasPermi('lawyers:modelConfig:query')")
    @GetMapping(value = "/{configId}")
    public AjaxResult getInfo(@PathVariable("configId") Long configId)
    {
        return success(aiModelConfigService.selectAiModelConfigByConfigId(configId));
    }

    /**
     * 新增AI模型参数配置
     */
    @PreAuthorize("@ss.hasPermi('lawyers:modelConfig:add')")
    @Log(title = "AI模型参数配置", businessType = BusinessType.INSERT)
    @PostMapping
    public AjaxResult add(@RequestBody AiModelConfig aiModelConfig)
    {
        return toAjax(aiModelConfigService.insertAiModelConfig(aiModelConfig));
    }

    /**
     * 修改AI模型参数配置
     */
    @PreAuthorize("@ss.hasPermi('lawyers:modelConfig:edit')")
    @Log(title = "AI模型参数配置", businessType = BusinessType.UPDATE)
    @PutMapping
    public AjaxResult edit(@RequestBody AiModelConfig aiModelConfig)
    {
        return toAjax(aiModelConfigService.updateAiModelConfig(aiModelConfig));
    }

    /**
     * 删除AI模型参数配置
     */
    @PreAuthorize("@ss.hasPermi('lawyers:modelConfig:remove')")
    @Log(title = "AI模型参数配置", businessType = BusinessType.DELETE)
	@DeleteMapping("/{configIds}")
    public AjaxResult remove(@PathVariable Long[] configIds)
    {
        return toAjax(aiModelConfigService.deleteAiModelConfigByConfigIds(configIds));
    }

    /**
     * 获取默认的AI模型配置
     */
    @PreAuthorize("@ss.hasPermi('lawyers:modelConfig:query')")
    @GetMapping("/default")
    public AjaxResult getDefault()
    {
        return success(aiModelConfigService.getDefaultAiModelConfig());
    }

    /**
     * 设置默认配置
     */
    @PreAuthorize("@ss.hasPermi('lawyers:modelConfig:edit')")
    @Log(title = "AI模型参数配置", businessType = BusinessType.UPDATE)
    @PutMapping("/setDefault/{configId}")
    public AjaxResult setDefault(@PathVariable Long configId)
    {
        return toAjax(aiModelConfigService.setDefaultConfig(configId));
    }

    /**
     * 测试AI模型连接
     */
    @PreAuthorize("@ss.hasPermi('lawyers:modelConfig:test')")
    @Log(title = "AI模型参数配置", businessType = BusinessType.OTHER)
    @PostMapping("/test/{configId}")
    public AjaxResult test(@PathVariable Long configId)
    {
        boolean result = aiModelConfigService.testAiModelConnection(configId);
        return result ? success("连接测试成功") : error("连接测试失败");
    }
}