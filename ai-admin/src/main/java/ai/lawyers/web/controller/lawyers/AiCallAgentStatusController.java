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
        int result = aiCallAgentStatusService.agentLogin(agentId, userId, ip);
        if (result > 0) {
            return success("坐席登录成功");
        }
        if (result == -1) {
            return error("该工号已绑定其他账号，请使用本人绑定的工号签入");
        }
        return error("坐席登录失败，坐席不存在");
    }

    @PreAuthorize("@ss.hasPermi('lawyers:call:agent:login')")
    @GetMapping("/my")
    public AjaxResult myAgent()
    {
        // 查询当前登录账号绑定的工号
        return success(aiCallAgentStatusService.selectAiCallAgentStatusByUserId(getUserId()));
    }

    @PreAuthorize("@ss.hasPermi('lawyers:call:agent:logout')")
    @Log(title = "坐席注销", businessType = BusinessType.UPDATE)
    @PostMapping("/logout")
    public AjaxResult agentLogout(@RequestBody java.util.Map<String, Object> params)
    {
        Long agentId = params.get("agentId") != null ? Long.valueOf(params.get("agentId").toString()) : null;
        Long userId = params.get("userId") != null ? Long.valueOf(params.get("userId").toString()) : null;
        int result = aiCallAgentStatusService.agentLogout(agentId, userId);
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

    @PreAuthorize("@ss.hasPermi('lawyers:call:agent:status')")
    @Log(title = "坐席当前状态", businessType = BusinessType.OTHER)
    @GetMapping("/current/{agentId}")
    public AjaxResult getCurrentAgent(@PathVariable("agentId") Long agentId)
    {
        return success(aiCallAgentStatusService.selectAiCallAgentStatusByAgentId(agentId));
    }

    @PreAuthorize("@ss.hasPermi('lawyers:call:agent:status')")
    @Log(title = "切换应答模式", businessType = BusinessType.UPDATE)
    @PostMapping("/callMode")
    public AjaxResult updateCallMode(@RequestBody java.util.Map<String, Object> params)
    {
        Long agentId = Long.valueOf(params.get("agentId").toString());
        String callMode = params.get("callMode").toString();
        int result = aiCallAgentStatusService.updateCallMode(agentId, callMode);
        if (result > 0) {
            return success("应答模式切换成功");
        }
        return error("应答模式切换失败");
    }

    @PreAuthorize("@ss.hasPermi('lawyers:call:agent:status')")
    @Log(title = "外呼", businessType = BusinessType.OTHER)
    @PostMapping("/makeCall")
    public AjaxResult makeCall(@RequestBody java.util.Map<String, Object> params)
    {
        Long agentId = Long.valueOf(params.get("agentId").toString());
        String phone = params.get("phone").toString();
        AiCallAgentStatus agent = aiCallAgentStatusService.makeCall(agentId, phone);
        if (agent != null) {
            return success(agent);
        }
        return error("外呼失败，坐席不存在");
    }

    @PreAuthorize("@ss.hasPermi('lawyers:call:agent:status')")
    @Log(title = "通话保持", businessType = BusinessType.OTHER)
    @PostMapping("/hold")
    public AjaxResult holdCall(@RequestBody java.util.Map<String, Object> params)
    {
        Long agentId = Long.valueOf(params.get("agentId").toString());
        int result = aiCallAgentStatusService.holdCall(agentId);
        if (result > 0) {
            return success("通话已保持");
        }
        return error("通话保持失败，当前无通话或状态异常");
    }

    @PreAuthorize("@ss.hasPermi('lawyers:call:agent:status')")
    @Log(title = "通话恢复", businessType = BusinessType.OTHER)
    @PostMapping("/resume")
    public AjaxResult resumeCall(@RequestBody java.util.Map<String, Object> params)
    {
        Long agentId = Long.valueOf(params.get("agentId").toString());
        int result = aiCallAgentStatusService.resumeCall(agentId);
        if (result > 0) {
            return success("通话已恢复");
        }
        return error("通话恢复失败，当前未处于保持状态");
    }

    @PreAuthorize("@ss.hasPermi('lawyers:call:agent:status')")
    @Log(title = "通话转接", businessType = BusinessType.OTHER)
    @PostMapping("/transfer")
    public AjaxResult transferCall(@RequestBody java.util.Map<String, Object> params)
    {
        Long agentId = Long.valueOf(params.get("agentId").toString());
        Long toAgentId = Long.valueOf(params.get("toAgentId").toString());
        String remark = params.get("remark") != null ? params.get("remark").toString() : "";
        int result = aiCallAgentStatusService.transferCall(agentId, toAgentId, remark);
        if (result > 0) {
            return success("通话已转接");
        }
        return error("通话转接失败，当前无通话");
    }

    @PreAuthorize("@ss.hasPermi('lawyers:call:agent:status')")
    @Log(title = "咨询", businessType = BusinessType.OTHER)
    @PostMapping("/consult")
    public AjaxResult consultCall(@RequestBody java.util.Map<String, Object> params)
    {
        Long agentId = Long.valueOf(params.get("agentId").toString());
        Long toAgentId = Long.valueOf(params.get("toAgentId").toString());
        int result = aiCallAgentStatusService.consultCall(agentId, toAgentId);
        if (result > 0) {
            return success("咨询已发起");
        }
        return error("咨询失败，当前无通话或状态异常");
    }

    @PreAuthorize("@ss.hasPermi('lawyers:call:agent:status')")
    @Log(title = "三方通话", businessType = BusinessType.OTHER)
    @PostMapping("/threeWay")
    public AjaxResult threeWayCall(@RequestBody java.util.Map<String, Object> params)
    {
        Long agentId = Long.valueOf(params.get("agentId").toString());
        Long toAgentId = Long.valueOf(params.get("toAgentId").toString());
        int result = aiCallAgentStatusService.threeWayCall(agentId, toAgentId);
        if (result > 0) {
            return success("三方通话已建立");
        }
        return error("三方通话失败，当前无通话或状态异常");
    }

    @PreAuthorize("@ss.hasPermi('lawyers:call:agent:status')")
    @Log(title = "话后整理", businessType = BusinessType.OTHER)
    @PostMapping("/afterWork")
    public AjaxResult afterWork(@RequestBody java.util.Map<String, Object> params)
    {
        Long agentId = Long.valueOf(params.get("agentId").toString());
        int result = aiCallAgentStatusService.afterWork(agentId);
        if (result > 0) {
            return success("已进入话后整理");
        }
        return error("话后整理失败");
    }

    @PreAuthorize("@ss.hasPermi('lawyers:call:agent:status')")
    @Log(title = "挂机", businessType = BusinessType.OTHER)
    @PostMapping("/hangup")
    public AjaxResult hangup(@RequestBody java.util.Map<String, Object> params)
    {
        Long agentId = Long.valueOf(params.get("agentId").toString());
        int result = aiCallAgentStatusService.hangup(agentId);
        if (result > 0) {
            return success("通话已挂断");
        }
        return error("挂机失败");
    }

    @PreAuthorize("@ss.hasPermi('lawyers:call:agent:status')")
    @Log(title = "机器人接管", businessType = BusinessType.OTHER)
    @PostMapping("/robotTakeover")
    public AjaxResult robotTakeover(@RequestBody java.util.Map<String, Object> params)
    {
        Long agentId = Long.valueOf(params.get("agentId").toString());
        int result = aiCallAgentStatusService.robotTakeover(agentId);
        if (result > 0) {
            return success("已转交机器人接管");
        }
        return error("机器人接管失败，当前无通话");
    }

    @PreAuthorize("@ss.hasPermi('lawyers:call:agent:status')")
    @Log(title = "IVR转接", businessType = BusinessType.OTHER)
    @PostMapping("/ivrTransfer")
    public AjaxResult ivrTransfer(@RequestBody java.util.Map<String, Object> params)
    {
        Long agentId = Long.valueOf(params.get("agentId").toString());
        String ivrNodeId = params.get("ivrNodeId") != null ? params.get("ivrNodeId").toString() : "";
        int result = aiCallAgentStatusService.ivrTransfer(agentId, ivrNodeId);
        if (result > 0) {
            return success("已转接至IVR");
        }
        return error("IVR转接失败，当前无通话");
    }

    @PreAuthorize("@ss.hasPermi('lawyers:call:agent:list')")
    @GetMapping("/todayRecords/{agentId}")
    public AjaxResult getTodayRecords(@PathVariable("agentId") Long agentId)
    {
        return success(aiCallAgentStatusService.selectTodayRecordsByAgentId(agentId));
    }
}
