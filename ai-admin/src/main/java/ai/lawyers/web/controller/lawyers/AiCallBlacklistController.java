package ai.lawyers.web.controller.lawyers;

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
import ai.lawyers.common.utils.SecurityUtils;
import ai.lawyers.common.utils.StringUtils;
import ai.lawyers.system.domain.lawyers.AiCallBlacklist;
import ai.lawyers.system.service.lawyers.IAiCallBlacklistService;

/**
 * 通话黑白名单 Controller
 */
@RestController
@RequestMapping("/lawyers/blacklist")
public class AiCallBlacklistController extends BaseController
{
    @Autowired
    private IAiCallBlacklistService blacklistService;

    @PreAuthorize("@ss.hasPermi('lawyers:blacklist:list')")
    @GetMapping("/list")
    public TableDataInfo list(AiCallBlacklist query)
    {
        startPage();
        List<AiCallBlacklist> list = blacklistService.selectList(query);
        return getDataTable(list);
    }

    @PreAuthorize("@ss.hasPermi('lawyers:blacklist:query')")
    @GetMapping(value = "/{id}")
    public AjaxResult getInfo(@PathVariable("id") Long id)
    {
        return success(blacklistService.selectById(id));
    }

    @PreAuthorize("@ss.hasPermi('lawyers:blacklist:add')")
    @Log(title = "通话黑白名单", businessType = BusinessType.INSERT)
    @PostMapping
    public AjaxResult add(@RequestBody AiCallBlacklist entity)
    {
        entity.setCreateBy(SecurityUtils.getUsername());
        return toAjax(blacklistService.insert(entity));
    }

    @PreAuthorize("@ss.hasPermi('lawyers:blacklist:edit')")
    @Log(title = "通话黑白名单", businessType = BusinessType.UPDATE)
    @PutMapping
    public AjaxResult edit(@RequestBody AiCallBlacklist entity)
    {
        entity.setUpdateBy(SecurityUtils.getUsername());
        return toAjax(blacklistService.update(entity));
    }

    @PreAuthorize("@ss.hasPermi('lawyers:blacklist:remove')")
    @Log(title = "通话黑白名单", businessType = BusinessType.DELETE)
    @DeleteMapping("/{ids}")
    public AjaxResult remove(@PathVariable Long[] ids)
    {
        return toAjax(blacklistService.deleteByIds(ids));
    }

    /**
     * 检查号码状态：blacklisted / whitelisted / normal
     */
    @PreAuthorize("@ss.hasPermi('lawyers:blacklist:query')")
    @GetMapping("/check/{phoneNumber}")
    public AjaxResult check(@PathVariable("phoneNumber") String phoneNumber)
    {
        AjaxResult result = AjaxResult.success();
        if (StringUtils.isEmpty(phoneNumber))
        {
            result.put("status", "normal");
            return result;
        }
        String status;
        if (blacklistService.isBlacklisted(phoneNumber))
        {
            status = "blacklisted";
        }
        else if (blacklistService.isWhitelisted(phoneNumber))
        {
            status = "whitelisted";
        }
        else
        {
            status = "normal";
        }
        result.put("phoneNumber", phoneNumber);
        result.put("status", status);
        return result;
    }
}
