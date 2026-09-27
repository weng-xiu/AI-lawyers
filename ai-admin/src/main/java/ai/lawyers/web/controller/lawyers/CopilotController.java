package ai.lawyers.web.controller.lawyers;

import java.util.Calendar;
import java.util.Date;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import ai.lawyers.common.core.controller.BaseController;
import ai.lawyers.common.core.domain.AjaxResult;
import ai.lawyers.system.domain.lawyers.AiCopilotFeedback;
import ai.lawyers.system.domain.lawyers.AiLegalKnowledgeChunk;
import ai.lawyers.system.mapper.lawyers.AiLegalKnowledgeChunkMapper;
import ai.lawyers.system.service.lawyers.voice.copilot.IAiCopilotFeedbackService;

/**
 * P3-F4：坐席 Copilot 深化 Controller。
 *
 * <p>① 法条溯源详情（chunkId → 标题/条号/出处/原文）；
 * ② 建议行为埋点（采纳/修改/忽略，服务端补坐席信息）；
 * ③ 区间采纳率统计（总体/按坐席，供 F10 大屏与管理报表）。</p>
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

    private static Date addDays(Date base, int days)
    {
        Calendar c = Calendar.getInstance();
        c.setTime(base);
        c.add(Calendar.DATE, days);
        return c.getTime();
    }
}
