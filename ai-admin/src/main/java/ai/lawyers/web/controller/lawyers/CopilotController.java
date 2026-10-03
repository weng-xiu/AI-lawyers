package ai.lawyers.web.controller.lawyers;

import java.util.Calendar;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import ai.lawyers.common.core.controller.BaseController;
import ai.lawyers.common.core.domain.AjaxResult;
import ai.lawyers.common.utils.StringUtils;
import ai.lawyers.system.domain.lawyers.AiCallTicket;
import ai.lawyers.system.domain.lawyers.AiCopilotFeedback;
import ai.lawyers.system.domain.lawyers.AiLegalKnowledgeChunk;
import ai.lawyers.system.mapper.lawyers.AiLegalKnowledgeChunkMapper;
import ai.lawyers.system.service.lawyers.IAiCallTicketService;
import ai.lawyers.system.service.lawyers.voice.copilot.IAiCopilotFeedbackService;

/**
 * P3-F4 / P1-8：坐席 Copilot 深化 Controller。
 *
 * <p>① 法条溯源详情（chunkId → 标题/条号/出处/原文）；
 * ② 建议行为埋点（采纳/修改/忽略，服务端补坐席信息）；
 * ③ 区间采纳率统计（总体/按坐席，供 F10 大屏与管理报表）；
 * ④ P1-8 代执行白名单动作确认（createTicket 草稿人工确认后提交、queryTicket 查询工单进度，
 * 模型不直接落库）。</p>
 *
 * @author ai-lawyers
 */
@RestController
@RequestMapping("/lawyers/copilot")
public class CopilotController extends BaseController
{
    private static final Logger log = LoggerFactory.getLogger(CopilotController.class);

    /** 统计默认回溯天数（beginTime/endTime 未传时） */
    private static final int DEFAULT_RANGE_DAYS = 30;

    @Autowired
    private IAiCopilotFeedbackService feedbackService;

    @Autowired
    private AiLegalKnowledgeChunkMapper chunkMapper;

    /** P1-8：确认建单（代执行白名单） */
    @Autowired
    private IAiCallTicketService ticketService;

    /** 法条溯源详情（点击推荐法条时弹窗） */
    @GetMapping("/chunk/{chunkId}")
    public AjaxResult chunk(@PathVariable("chunkId") Long chunkId)
    {
        if (chunkId == null)
        {
            return AjaxResult.error("chunkId 不能为空");
        }
        AiLegalKnowledgeChunk chunk = chunkMapper.selectChunkById(chunkId);
        if (chunk == null)
        {
            return AjaxResult.error("法条内容不存在或已下线");
        }
        AjaxResult ok = AjaxResult.success(chunk);
        return ok;
    }

    /** 建议行为埋点（best-effort，接口本身恒成功避免打扰坐席操作） */
    @PostMapping("/feedback")
    public AjaxResult feedback(@RequestBody AiCopilotFeedback feedback)
    {
        feedbackService.record(feedback);
        return AjaxResult.success();
    }

    /**
     * 区间采纳率统计。
     *
     * @param dimension total=总体（默认）/ agent=按坐席
     */
    @GetMapping("/adoption")
    public AjaxResult adoption(@RequestParam(value = "dimension", required = false) String dimension,
                               @RequestParam(value = "beginTime", required = false) Date beginTime,
                               @RequestParam(value = "endTime", required = false) Date endTime)
    {
        Date end = endTime == null ? new Date() : endTime;
        Date begin = beginTime == null ? addDays(end, -DEFAULT_RANGE_DAYS) : beginTime;
        if ("agent".equalsIgnoreCase(dimension))
        {
            return AjaxResult.success(feedbackService.adoptionByAgent(begin, end));
        }
        return AjaxResult.success(feedbackService.adoptionTotal(begin, end));
    }

    /**
     * P1-8：代执行白名单——确认建单。
     *
     * <p>请求体为 Copilot 草稿经坐席人工确认/修改后的字段（title/content/priority/recordId），
     * 复用 {@code lawyers:call:ticket:add} 权限；空标题/空内容拒绝。模型任何阶段不直接落库。</p>
     */
    @PreAuthorize("@ss.hasPermi('lawyers:call:ticket:add')")
    @PostMapping("/actions/createTicket")
    public AjaxResult createTicket(@RequestBody AiCallTicket draft)
    {
        if (draft == null || StringUtils.isEmpty(draft.getTitle())
                || StringUtils.isEmpty(draft.getContent()))
        {
            return AjaxResult.error("工单标题与内容不能为空");
        }
        // 仅接收白名单字段，忽略客户端可能伪造的状态/处理人
        AiCallTicket ticket = new AiCallTicket();
        ticket.setTitle(draft.getTitle().trim());
        ticket.setContent(draft.getContent().trim());
        ticket.setPriority(StringUtils.isNotEmpty(draft.getPriority()) ? draft.getPriority() : "2");
        ticket.setRecordId(draft.getRecordId());
        ticket.setStatus("0");
        ticketService.insertAiCallTicket(ticket);
        return AjaxResult.success(ticket);
    }

    /**
     * P1-8：代执行白名单——查询工单办理进度，生成口语化播报话术。
     *
     * <p>仅返回脱敏的进度信息（工单号/状态/办理人/最近办理内容/时间），不含来电号码，
     * 话术供坐席口头播报给来电人（坐席辅助场景不自动 TTS）。</p>
     */
    @PreAuthorize("@ss.hasPermi('lawyers:call:ticket:query')")
    @GetMapping("/actions/queryTicket/{ticketId}")
    public AjaxResult queryTicket(@PathVariable("ticketId") Long ticketId)
    {
        if (ticketId == null)
        {
            return AjaxResult.error("ticketId 不能为空");
        }
        AiCallTicket ticket = ticketService.selectAiCallTicketByTicketId(ticketId);
        if (ticket == null)
        {
            return AjaxResult.error("未查询到该工单");
        }
        String statusName = ticketStatusName(ticket.getStatus());
        StringBuilder speech = new StringBuilder();
        speech.append("您工单号为").append(ticket.getTicketNo()).append("的咨询，")
                .append("当前状态是").append(statusName);
        if (StringUtils.isNotEmpty(ticket.getAssignUserName()))
        {
            speech.append("，由").append(ticket.getAssignUserName()).append("负责跟进");
        }
        if (StringUtils.isNotEmpty(ticket.getProcessContent()))
        {
            speech.append("，最新办理情况是：").append(ticket.getProcessContent());
        }
        if ("2".equals(ticket.getStatus()) || "3".equals(ticket.getStatus()))
        {
            speech.append("，已办理完结，请您留意后续反馈");
        }
        else
        {
            speech.append("，请您耐心等待，我们会尽快处理");
        }

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("ticketId", ticket.getTicketId());
        data.put("ticketNo", ticket.getTicketNo());
        data.put("title", ticket.getTitle());
        data.put("status", ticket.getStatus());
        data.put("statusName", statusName);
        data.put("assignUserName", ticket.getAssignUserName());
        data.put("processContent", ticket.getProcessContent());
        data.put("createTime", ticket.getCreateTime());
        data.put("processTime", ticket.getProcessTime());
        data.put("speech", speech.toString());
        return AjaxResult.success(data);
    }

    /** 工单状态码转中文 */
    private static String ticketStatusName(String status)
    {
        if ("1".equals(status)) { return "处理中"; }
        if ("2".equals(status)) { return "已完成"; }
        if ("3".equals(status)) { return "已归档"; }
        return "待处理";
    }

    private static Date addDays(Date base, int days)
    {
        Calendar c = Calendar.getInstance();
        c.setTime(base);
        c.add(Calendar.DATE, days);
        return c.getTime();
    }
}
