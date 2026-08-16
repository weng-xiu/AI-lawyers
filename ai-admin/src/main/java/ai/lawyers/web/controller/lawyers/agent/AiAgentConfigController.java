package ai.lawyers.web.controller.lawyers.agent;

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
import ai.lawyers.system.domain.lawyers.agent.AiAgentConfig;
import ai.lawyers.system.service.lawyers.agent.IAiAgentConfigService;

/**
 * AI智能体配置Controller
 *
 * @author ai-lawyers
 */
@RestController
@RequestMapping("/lawyers/agent/config")
public class AiAgentConfigController extends BaseController
{
    @Autowired
    private IAiAgentConfigService aiAgentConfigService;

    @PreAuthorize("@ss.hasPermi('lawyers:agent:config:list')")
    @GetMapping("/list")
    public TableDataInfo list(AiAgentConfig aiAgentConfig)
    {
        startPage();
        List<AiAgentConfig> list = aiAgentConfigService.selectAiAgentConfigList(aiAgentConfig);
        return getDataTable(list);
    }

    @PreAuthorize("@ss.hasPermi('lawyers:agent:config:export')")
    @Log(title = "AI智能体配置", businessType = BusinessType.EXPORT)
    @PostMapping("/export")
    public void export(HttpServletResponse response, AiAgentConfig aiAgentConfig)
    {
        List<AiAgentConfig> list = aiAgentConfigService.selectAiAgentConfigList(aiAgentConfig);
        ExcelUtil<AiAgentConfig> util = new ExcelUtil<AiAgentConfig>(AiAgentConfig.class);
        util.exportExcel(response, list, "AI智能体配置数据");
    }

    @PreAuthorize("@ss.hasPermi('lawyers:agent:config:query')")
    @GetMapping(value = "/{agentId}")
    public AjaxResult getInfo(@PathVariable("agentId") Long agentId)
    {
        return success(aiAgentConfigService.selectAiAgentConfigByAgentId(agentId));
    }

    @PreAuthorize("@ss.hasPermi('lawyers:agent:config:add')")
    @Log(title = "AI智能体配置", businessType = BusinessType.INSERT)
    @PostMapping
    public AjaxResult add(@RequestBody AiAgentConfig aiAgentConfig)
    {
        aiAgentConfig.setCreateBy(getUsername());
        return toAjax(aiAgentConfigService.insertAiAgentConfig(aiAgentConfig));
    }

    @PreAuthorize("@ss.hasPermi('lawyers:agent:config:edit')")
    @Log(title = "AI智能体配置", businessType = BusinessType.UPDATE)
    @PutMapping
    public AjaxResult edit(@RequestBody AiAgentConfig aiAgentConfig)
    {
        aiAgentConfig.setUpdateBy(getUsername());
        return toAjax(aiAgentConfigService.updateAiAgentConfig(aiAgentConfig));
    }

    @PreAuthorize("@ss.hasPermi('lawyers:agent:config:remove')")
    @Log(title = "AI智能体配置", businessType = BusinessType.DELETE)
    @DeleteMapping("/{agentIds}")
    public AjaxResult remove(@PathVariable Long[] agentIds)
    {
        return toAjax(aiAgentConfigService.deleteAiAgentConfigByAgentIds(agentIds));
    }

    /**
     * 启用状态的智能体列表（供设计器下拉选择）
     */
    @PreAuthorize("@ss.hasPermi('lawyers:agent:config:list')")
    @GetMapping("/active")
    public AjaxResult active()
    {
        return success(aiAgentConfigService.selectActiveAgentConfigs());
    }

    /**
     * 连接测试
     */
    @PreAuthorize("@ss.hasPermi('lawyers:agent:config:test')")
    @Log(title = "AI智能体连接测试", businessType = BusinessType.OTHER)
    @GetMapping("/test/{agentId}")
    public AjaxResult test(@PathVariable("agentId") Long agentId)
    {
        boolean ok = aiAgentConfigService.testConnection(agentId);
        return ok ? success("连接正常") : AjaxResult.error("连接失败，请检查模型配置或接口地址");
    }
}
