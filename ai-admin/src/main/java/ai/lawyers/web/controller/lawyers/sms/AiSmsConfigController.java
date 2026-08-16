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
import ai.lawyers.system.domain.lawyers.sms.AiSmsConfig;
import ai.lawyers.system.mapper.lawyers.sms.AiSmsConfigMapper;

/**
 * 短信通道配置Controller
 *
 * @author ai-lawyers
 */
@RestController
@RequestMapping("/lawyers/sms/config")
public class AiSmsConfigController extends BaseController
{
    @Autowired
    private AiSmsConfigMapper aiSmsConfigMapper;

    @PreAuthorize("@ss.hasPermi('lawyers:smsConfig:view')")
    @GetMapping("/list")
    public TableDataInfo list(AiSmsConfig query)
    {
        startPage();
        return getDataTable(aiSmsConfigMapper.selectAiSmsConfigList(query));
    }

    @PreAuthorize("@ss.hasPermi('lawyers:smsConfig:view')")
    @GetMapping("/{configId}")
    public AjaxResult getInfo(@PathVariable Long configId)
    {
        return success(aiSmsConfigMapper.selectAiSmsConfigByConfigId(configId));
    }

    @Log(title = "短信通道", businessType = BusinessType.INSERT)
    @PostMapping
    public AjaxResult add(@RequestBody AiSmsConfig config)
    {
        config.setCreateBy(getUsername());
        return toAjax(aiSmsConfigMapper.insertAiSmsConfig(config));
    }

    @Log(title = "短信通道", businessType = BusinessType.UPDATE)
    @PutMapping
    public AjaxResult edit(@RequestBody AiSmsConfig config)
    {
        config.setUpdateBy(getUsername());
        return toAjax(aiSmsConfigMapper.updateAiSmsConfig(config));
    }

    @Log(title = "短信通道", businessType = BusinessType.DELETE)
    @DeleteMapping("/{configIds}")
    public AjaxResult remove(@PathVariable Long[] configIds)
    {
        return toAjax(aiSmsConfigMapper.deleteAiSmsConfigByConfigIds(configIds));
    }

    /**
     * 全部启用通道（供设计器下拉选择）
     */
    @GetMapping("/enabled")
    public AjaxResult enabled()
    {
        AiSmsConfig query = new AiSmsConfig();
        query.setStatus("1");
        List<AiSmsConfig> list = aiSmsConfigMapper.selectAiSmsConfigList(query);
        return success(list);
    }
}
