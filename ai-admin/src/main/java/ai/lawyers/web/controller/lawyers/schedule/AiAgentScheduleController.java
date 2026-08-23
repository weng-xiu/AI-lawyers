package ai.lawyers.web.controller.lawyers.schedule;

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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import ai.lawyers.common.annotation.Log;
import ai.lawyers.common.core.controller.BaseController;
import ai.lawyers.common.core.domain.AjaxResult;
import ai.lawyers.common.core.page.TableDataInfo;
import ai.lawyers.common.enums.BusinessType;
import ai.lawyers.common.utils.SecurityUtils;
import ai.lawyers.system.domain.lawyers.AiCallAgentStatus;
import ai.lawyers.system.domain.lawyers.schedule.AiAgentSchedule;
import ai.lawyers.system.domain.lawyers.schedule.AiAgentScheduleVO;
import ai.lawyers.system.service.lawyers.IAiCallAgentStatusService;
import ai.lawyers.system.service.lawyers.schedule.IAiAgentScheduleService;

/**
 * 坐席排班 Controller
 */
@RestController
@RequestMapping("/lawyers/schedule/agent")
public class AiAgentScheduleController extends BaseController
{
    @Autowired
    private IAiAgentScheduleService aiAgentScheduleService;

    @Autowired
    private IAiCallAgentStatusService aiCallAgentStatusService;

    /**
     * 查询排班列表（支持按坐席、班次、日期范围过滤）
     */
    @PreAuthorize("@ss.hasPermi('lawyers:schedule:list')")
    @GetMapping("/list")
    public TableDataInfo list(AiAgentScheduleVO query)
    {
        startPage();
        List<AiAgentScheduleVO> list = aiAgentScheduleService.selectScheduleList(query);
        return getDataTable(list);
    }

    /**
     * 按日期范围查询排班
     */
    @PreAuthorize("@ss.hasPermi('lawyers:schedule:list')")
    @GetMapping("/range")
    public AjaxResult range(@RequestParam("startDate") String startDate,
                            @RequestParam("endDate") String endDate)
    {
        return success(aiAgentScheduleService.selectByDateRange(startDate, endDate));
    }

    /**
     * 获取排班详细信息
     */
    @PreAuthorize("@ss.hasPermi('lawyers:schedule:query')")
    @GetMapping(value = "/{scheduleId}")
    public AjaxResult getInfo(@PathVariable("scheduleId") Long scheduleId)
    {
        return success(aiAgentScheduleService.selectAiAgentScheduleByScheduleId(scheduleId));
    }

    /**
     * 查询今日排班：优先按 agentId；未传则取当前登录账号绑定的坐席
     */
    @PreAuthorize("@ss.hasPermi('lawyers:schedule:query')")
    @GetMapping("/today")
    public AjaxResult today(@RequestParam(value = "agentId", required = false) Long agentId)
    {
        Long targetAgentId = agentId;
        if (targetAgentId == null)
        {
            Long userId = SecurityUtils.getUserId();
            AiCallAgentStatus agent = aiCallAgentStatusService.selectAiCallAgentStatusByUserId(userId);
            if (agent == null)
            {
                return error("当前账号未绑定坐席，无法查询今日排班");
            }
            targetAgentId = agent.getAgentId();
        }
        AiAgentScheduleVO vo = aiAgentScheduleService.getMyScheduleToday(targetAgentId);
        return success(vo);
    }

    /**
     * 新增排班
     */
    @PreAuthorize("@ss.hasPermi('lawyers:schedule:add')")
    @Log(title = "坐席排班", businessType = BusinessType.INSERT)
    @PostMapping
    public AjaxResult add(@RequestBody AiAgentSchedule aiAgentSchedule)
    {
        aiAgentSchedule.setCreateBy(SecurityUtils.getUsername());
        return toAjax(aiAgentScheduleService.insertAiAgentSchedule(aiAgentSchedule));
    }

    /**
     * 批量排班（一个班次可给多个坐席同一天排班）
     */
    @PreAuthorize("@ss.hasPermi('lawyers:schedule:add')")
    @Log(title = "坐席排班-批量排班", businessType = BusinessType.INSERT)
    @PostMapping("/batch")
    public AjaxResult batch(@RequestBody List<AiAgentSchedule> list)
    {
        if (list == null || list.isEmpty())
        {
            return error("排班数据不能为空");
        }
        String username = SecurityUtils.getUsername();
        for (AiAgentSchedule schedule : list)
        {
            schedule.setCreateBy(username);
        }
        return toAjax(aiAgentScheduleService.batchInsert(list));
    }

    /**
     * 修改排班
     */
    @PreAuthorize("@ss.hasPermi('lawyers:schedule:edit')")
    @Log(title = "坐席排班", businessType = BusinessType.UPDATE)
    @PutMapping
    public AjaxResult edit(@RequestBody AiAgentSchedule aiAgentSchedule)
    {
        aiAgentSchedule.setUpdateBy(SecurityUtils.getUsername());
        return toAjax(aiAgentScheduleService.updateAiAgentSchedule(aiAgentSchedule));
    }

    /**
     * 删除排班
     */
    @PreAuthorize("@ss.hasPermi('lawyers:schedule:remove')")
    @Log(title = "坐席排班", businessType = BusinessType.DELETE)
    @DeleteMapping("/{scheduleIds}")
    public AjaxResult remove(@PathVariable Long[] scheduleIds)
    {
        return toAjax(aiAgentScheduleService.deleteAiAgentScheduleByScheduleIds(scheduleIds));
    }

    /**
     * 签到：更新 checkInTime = now()，status=1
     */
    @PreAuthorize("@ss.hasPermi('lawyers:schedule:check')")
    @Log(title = "坐席排班-签到", businessType = BusinessType.UPDATE)
    @PutMapping("/checkIn")
    public AjaxResult checkIn(@RequestParam(value = "agentId", required = false) Long agentId)
    {
        Long targetAgentId = resolveAgentId(agentId);
        if (targetAgentId == null)
        {
            return error("当前账号未绑定坐席，无法签到");
        }
        int rows = aiAgentScheduleService.checkIn(targetAgentId);
        if (rows <= 0)
        {
            return error("今日无排班或已签到");
        }
        return success("签到成功");
    }

    /**
     * 签退：更新 checkOutTime = now()
     */
    @PreAuthorize("@ss.hasPermi('lawyers:schedule:check')")
    @Log(title = "坐席排班-签退", businessType = BusinessType.UPDATE)
    @PutMapping("/checkOut")
    public AjaxResult checkOut(@RequestParam(value = "agentId", required = false) Long agentId)
    {
        Long targetAgentId = resolveAgentId(agentId);
        if (targetAgentId == null)
        {
            return error("当前账号未绑定坐席，无法签退");
        }
        int rows = aiAgentScheduleService.checkOut(targetAgentId);
        if (rows <= 0)
        {
            return error("今日无排班，无法签退");
        }
        return success("签退成功");
    }

    /**
     * 解析目标坐席ID：显式传入优先，否则按当前登录用户绑定的坐席
     */
    private Long resolveAgentId(Long agentId)
    {
        if (agentId != null)
        {
            return agentId;
        }
        Long userId = SecurityUtils.getUserId();
        AiCallAgentStatus agent = aiCallAgentStatusService.selectAiCallAgentStatusByUserId(userId);
        return agent == null ? null : agent.getAgentId();
    }
}
