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
import ai.lawyers.system.domain.lawyers.AiCallAgentStatus;
import ai.lawyers.system.service.lawyers.IAiCallAgentStatusService;

@RestController
@RequestMapping("/lawyers/call/agent")
public class AiCallAgentStatusController extends BaseController
{
    @Autowired
    private IAiCallAgentStatusService aiCallAgentStatusService;

    @PreAuthorize("@ss.hasPermi('lawyers:call:agent:list')")
    @GetMapping("/list")
    public TableDataInfo list(AiCallAgentStatus aiCallAgentStatus)
    {
        startPage();
        List<AiCallAgentStatus> list = aiCallAgentStatusService.selectAiCallAgentStatusList(aiCallAgentStatus);
        return getDataTable(list);
    }

    @PreAuthorize("@ss.hasPermi('lawyers:call:agent:export')")
    @Log(title = "坐席状态", businessType = BusinessType.EXPORT)
    @PostMapping("/export")
    public void export(HttpServletResponse response, AiCallAgentStatus aiCallAgentStatus)
    {
        List<AiCallAgentStatus> list = aiCallAgentStatusService.selectAiCallAgentStatusList(aiCallAgentStatus);
        ExcelUtil<AiCallAgentStatus> util = new ExcelUtil<AiCallAgentStatus>(AiCallAgentStatus.class);
        util.exportExcel(response, list, "坐席状态数据");
    }

    @PreAuthorize("@ss.hasPermi('lawyers:call:agent:query')")
    @GetMapping(value = "/{agentId}")
    public AjaxResult getInfo(@PathVariable("agentId") Long agentId)
    {
        return success(aiCallAgentStatusService.selectAiCallAgentStatusByAgentId(agentId));
    }

    @PreAuthorize("@ss.hasPermi('lawyers:call:agent:add')")
    @Log(title = "坐席状态", businessType = BusinessType.INSERT)
    @PostMapping
    public AjaxResult add(@RequestBody AiCallAgentStatus aiCallAgentStatus)
    {
        aiCallAgentStatus.setCreateBy(getUsername());
        return toAjax(aiCallAgentStatusService.insertAiCallAgentStatus(aiCallAgentStatus));
    }

    @PreAuthorize("@ss.hasPermi('lawyers:call:agent:edit')")
    @Log(title = "坐席状态", businessType = BusinessType.UPDATE)
    @PutMapping
    public AjaxResult edit(@RequestBody AiCallAgentStatus aiCallAgentStatus)
    {
        aiCallAgentStatus.setUpdateBy(getUsername());
        return toAjax(aiCallAgentStatusService.updateAiCallAgentStatus(aiCallAgentStatus));
    }

    @PreAuthorize("@ss.hasPermi('lawyers:call:agent:remove')")
    @Log(title = "坐席状态", businessType = BusinessType.DELETE)
    @DeleteMapping("/{agentIds}")
    public AjaxResult remove(@PathVariable Long[] agentIds)
    {
        return toAjax(aiCallAgentStatusService.deleteAiCallAgentStatusByAgentIds(agentIds));
    }

    @PreAuthorize("@ss.hasPermi('lawyers:call:agent:login')")
    @Log(title = "坐席登录", businessType = BusinessType.UPDATE)
    @PostMapping("/login")
    public AjaxResult agentLogin(@RequestBody java.util.Map<String, Object> params)
    {
        Long agentId = params.get("agentId") != null ? Long.valueOf(params.get("agentId").toString()) : null;
        Long userId = params.get("userId") != null ? Long.valueOf(params.get("userId").toString()) : null;
        String ip = params.get("ip") != null ? params.get("ip").toString() : "";
        int result = aiCallAgentStatusService.agentLogin(agentId != null ? agentId : userId, ip);
        if (result > 0) {
            return success("坐席登录成功");
        }
        return error("坐席登录失败，坐席不存在");
    }

    @PreAuthorize("@ss.hasPermi('lawyers:call:agent:logout')")
    @Log(title = "坐席注销", businessType = BusinessType.UPDATE)
    @PostMapping("/logout")
    public AjaxResult agentLogout(@RequestBody java.util.Map<String, Object> params)
    {
        Long agentId = params.get("agentId") != null ? Long.valueOf(params.get("agentId").toString()) : null;
        Long userId = params.get("userId") != null ? Long.valueOf(params.get("userId").toString()) : null;
        int result = aiCallAgentStatusService.agentLogout(agentId != null ? agentId : userId);
        if (result > 0) {
            return success("坐席注销成功");
        }
        return error("坐席注销失败");
    }

    @PreAuthorize("@ss.hasPermi('lawyers:call:agent:status')")
    @Log(title = "坐席状态切换", businessType = BusinessType.UPDATE)
    @PostMapping("/status")
    public AjaxResult updateAgentStatus(@RequestBody java.util.Map<String, Object> params)
    {
        Long agentId = Long.valueOf(params.get("agentId").toString());
        String status = params.get("status").toString();
        int result = aiCallAgentStatusService.updateAgentStatus(agentId, status);
        if (result > 0) {
            return success("状态切换成功");
        }
        return error("状态切换失败");
    }

    @PreAuthorize("@ss.hasPermi('lawyers:call:agent:list')")
    @GetMapping("/online")
    public AjaxResult getOnlineAgents()
    {
        List<AiCallAgentStatus> list = aiCallAgentStatusService.selectOnlineAgents();
        return success(list);
    }
}
