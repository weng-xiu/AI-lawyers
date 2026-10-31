package ai.lawyers.web.controller.lawyers;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
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
import ai.lawyers.system.domain.lawyers.schedule.AiShiftChangeRequest;
import ai.lawyers.system.domain.lawyers.schedule.AiShiftChangeRequestVO;
import ai.lawyers.system.service.lawyers.IAiCallAgentStatusService;
import ai.lawyers.system.service.lawyers.schedule.IAiAgentScheduleService;
import ai.lawyers.system.service.lawyers.schedule.IAiShiftChangeService;

/**
 * 坐席换班申请与审批 Controller（F9）
 *
 * <p>权限复用排班菜单权限，避免新增菜单 SQL：</p>
 * <ul>
 *   <li>{@code lawyers:schedule:list} 查看申请列表/我的排班；</li>
 *   <li>{@code lawyers:schedule:add} 提交申请、撤销本人申请；</li>
 *   <li>{@code lawyers:schedule:edit} 班组长审批通过/驳回（审批后自动改写排班）。</li>
 * </ul>
 *
 * <p>申请人与审批人身份均由服务端按当前登录账号解析，
 * 请求体中的 applicantAgentId 会被强制覆盖，避免越权代他人申请。</p>
 */
@RestController
@RequestMapping("/lawyers/shift-change")
public class AiShiftChangeController extends BaseController
{
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    @Autowired
    private IAiShiftChangeService shiftChangeService;

    @Autowired
    private IAiCallAgentStatusService aiCallAgentStatusService;

    @Autowired
    private IAiAgentScheduleService aiAgentScheduleService;

    /** 换班申请列表（班组长视角，可按状态/类型/申请人过滤） */
    @PreAuthorize("@ss.hasPermi('lawyers:schedule:list')")
    @GetMapping("/list")
    public TableDataInfo list(AiShiftChangeRequest query)
    {
        startPage();
        List<AiShiftChangeRequestVO> list = shiftChangeService.selectShiftChangeList(query);
        return getDataTable(list);
    }

    /** 我的换班申请（坐席视角，强制按当前登录账号绑定坐席过滤） */
    @PreAuthorize("@ss.hasPermi('lawyers:schedule:list')")
    @GetMapping("/mine")
    public TableDataInfo mine(AiShiftChangeRequest query)
    {
        Long agentId = currentAgentId();
        if (agentId == null)
        {
            return getDataTable(new java.util.ArrayList<>());
        }
        startPage();
        List<AiShiftChangeRequestVO> list = shiftChangeService.selectMyShiftChangeList(agentId, query);
        return getDataTable(list);
    }

    /** 换班申请详情 */
    @PreAuthorize("@ss.hasPermi('lawyers:schedule:query')")
    @GetMapping("/{requestId}")
    public AjaxResult getInfo(@PathVariable("requestId") Long requestId)
    {
        return success(shiftChangeService.selectShiftChangeById(requestId));
    }

    /**
     * 本人近期排班（申请弹窗用）：默认取「今天 ~ 今天+14 天」，
     * 未绑定坐席时返回空列表而不是全部坐席的排班。
     */
    @PreAuthorize("@ss.hasPermi('lawyers:schedule:list')")
    @GetMapping("/my-schedules")
    public AjaxResult mySchedules(@RequestParam(required = false) String startDate,
                                  @RequestParam(required = false) String endDate)
    {
        Long agentId = currentAgentId();
        if (agentId == null)
        {
            return success(new java.util.ArrayList<>());
        }
        String begin = startDate == null || startDate.isEmpty() ? LocalDate.now().format(DATE_FMT) : startDate;
        String end = endDate == null || endDate.isEmpty()
                ? LocalDate.now().plusDays(14).format(DATE_FMT) : endDate;
        AiAgentScheduleVO query = new AiAgentScheduleVO();
        query.setAgentId(agentId);
        query.setStatus(1);
        query.getParams().put("beginDate", begin);
        query.getParams().put("endDate", end);
        return success(aiAgentScheduleService.selectScheduleList(query));
    }

    /**
     * 互换可选的对方排班：与申请人排班同一天、他人名下、状态有效。
     * 申请人排班不属于当前坐席时拒绝，避免用他人排班试探别人的排班表。
     */
    @PreAuthorize("@ss.hasPermi('lawyers:schedule:list')")
    @GetMapping("/swap-targets")
    public AjaxResult swapTargets(@RequestParam("applicantScheduleId") Long applicantScheduleId)
    {
        Long agentId = currentAgentId();
        if (agentId == null)
        {
            return success(new java.util.ArrayList<>());
        }
        AiAgentSchedule mine = aiAgentScheduleService.selectAiAgentScheduleByScheduleId(applicantScheduleId);
        if (mine == null || mine.getAgentId() == null || !mine.getAgentId().equals(agentId)
                || mine.getScheduleDate() == null)
        {
            return error("排班不存在或不属于当前坐席");
        }
        AiAgentScheduleVO query = new AiAgentScheduleVO();
        query.setStatus(1);
        query.getParams().put("beginDate", mine.getScheduleDate());
        query.getParams().put("endDate", mine.getScheduleDate());
        List<AiAgentScheduleVO> list = aiAgentScheduleService.selectScheduleList(query);
        List<AiAgentScheduleVO> targets = new java.util.ArrayList<>();
        if (list != null)
        {
            for (AiAgentScheduleVO row : list)
            {
                // 换班对象只能是同一日他人的排班
                if (row.getAgentId() != null && !row.getAgentId().equals(agentId))
                {
                    targets.add(row);
                }
            }
        }
        return success(targets);
    }

    /** 提交换班申请（申请人身份由服务端解析，忽略请求体里的 applicantAgentId） */
    @PreAuthorize("@ss.hasPermi('lawyers:schedule:add')")
    @Log(title = "换班申请", businessType = BusinessType.INSERT)
    @PostMapping
    public AjaxResult apply(@RequestBody AiShiftChangeRequest request)
    {
        Long agentId = currentAgentId();
        if (agentId == null)
        {
            return error("当前账号未绑定坐席，无法申请换班");
        }
        request.setApplicantAgentId(agentId);
        request.setCreateBy(SecurityUtils.getUsername());
        return toAjax(shiftChangeService.applyShiftChange(request));
    }

    /** 审批通过（自动改写排班：互换换班次 / 转班换坐席） */
    @PreAuthorize("@ss.hasPermi('lawyers:schedule:edit')")
    @Log(title = "换班审批-通过", businessType = BusinessType.UPDATE)
    @PutMapping("/approve/{requestId}")
    public AjaxResult approve(@PathVariable("requestId") Long requestId,
                              @RequestBody(required = false) Map<String, Object> body)
    {
        String remark = body == null || body.get("approveRemark") == null ? null
                : String.valueOf(body.get("approveRemark"));
        return toAjax(shiftChangeService.approveShiftChange(requestId, SecurityUtils.getUsername(), remark));
    }

    /** 审批驳回 */
    @PreAuthorize("@ss.hasPermi('lawyers:schedule:edit')")
    @Log(title = "换班审批-驳回", businessType = BusinessType.UPDATE)
    @PutMapping("/reject/{requestId}")
    public AjaxResult reject(@PathVariable("requestId") Long requestId,
                             @RequestBody(required = false) Map<String, Object> body)
    {
        String remark = body == null || body.get("approveRemark") == null ? null
                : String.valueOf(body.get("approveRemark"));
        return toAjax(shiftChangeService.rejectShiftChange(requestId, SecurityUtils.getUsername(), remark));
    }

    /** 撤销本人的待审批申请 */
    @PreAuthorize("@ss.hasPermi('lawyers:schedule:add')")
    @Log(title = "换班申请-撤销", businessType = BusinessType.UPDATE)
    @PutMapping("/cancel/{requestId}")
    public AjaxResult cancel(@PathVariable("requestId") Long requestId)
    {
        Long agentId = currentAgentId();
        if (agentId == null)
        {
            return error("当前账号未绑定坐席，无法撤销换班申请");
        }
        return toAjax(shiftChangeService.cancelShiftChange(requestId, agentId, SecurityUtils.getUsername()));
    }

    /** 当前登录账号绑定的坐席ID；未绑定时返回 null */
    private Long currentAgentId()
    {
        AiCallAgentStatus agent = aiCallAgentStatusService.selectAiCallAgentStatusByUserId(SecurityUtils.getUserId());
        return agent == null ? null : agent.getAgentId();
    }
}
