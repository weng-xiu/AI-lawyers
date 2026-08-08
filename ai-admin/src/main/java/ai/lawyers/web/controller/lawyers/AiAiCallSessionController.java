package ai.lawyers.web.controller.lawyers;

import java.util.List;
import javax.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import ai.lawyers.common.annotation.Log;
import ai.lawyers.common.core.controller.BaseController;
import ai.lawyers.common.core.domain.AjaxResult;
import ai.lawyers.common.core.page.TableDataInfo;
import ai.lawyers.common.enums.BusinessType;
import ai.lawyers.common.utils.poi.ExcelUtil;
import ai.lawyers.system.domain.lawyers.AiAiCallSession;
import ai.lawyers.system.service.lawyers.IAiAiCallSessionService;

/**
 * AI律师辅助会话 Controller（独立链路）
 *
 * 与人工接听 Controller（AiCallAgentStatusController / AiCallRecordController）完全独立：
 *  - 独立路由前缀 /lawyers/ai/assist；
 *  - 独立处理函数 analyze（意图识别+法条推荐）、summarize（小结+结束）；
 *  - 不修改人工通话状态，仅管理自身的 AI 辅助会话。
 */
@RestController
@RequestMapping("/lawyers/ai/assist")
public class AiAiCallSessionController extends BaseController
{
    @Autowired
    private IAiAiCallSessionService aiAiCallSessionService;

    @PreAuthorize("@ss.hasPermi('lawyers:ai:assist:list')")
    @GetMapping("/list")
    public TableDataInfo list(AiAiCallSession aiAiCallSession)
    {
        startPage();
        List<AiAiCallSession> list = aiAiCallSessionService.selectAiAiCallSessionList(aiAiCallSession);
        return getDataTable(list);
    }

    @PreAuthorize("@ss.hasPermi('lawyers:ai:assist:export')")
    @Log(title = "AI辅助会话", businessType = BusinessType.EXPORT)
    @PostMapping("/export")
    public void export(HttpServletResponse response, AiAiCallSession aiAiCallSession)
    {
        List<AiAiCallSession> list = aiAiCallSessionService.selectAiAiCallSessionList(aiAiCallSession);
        ExcelUtil<AiAiCallSession> util = new ExcelUtil<AiAiCallSession>(AiAiCallSession.class);
        util.exportExcel(response, list, "AI辅助会话数据");
    }

    @PreAuthorize("@ss.hasPermi('lawyers:ai:assist:query')")
    @GetMapping(value = "/{sessionId}")
    public AjaxResult getInfo(@PathVariable("sessionId") Long sessionId)
    {
        return success(aiAiCallSessionService.selectAiAiCallSessionBySessionId(sessionId));
    }

    @PreAuthorize("@ss.hasPermi('lawyers:ai:assist:query')")
    @GetMapping(value = "/byRecord")
    public AjaxResult getByRecord(@RequestParam("recordId") Long recordId)
    {
        return success(aiAiCallSessionService.selectAiAiCallSessionByRecordId(recordId));
    }

    /**
     * 独立处理函数：触发 AI 分析 + 法条/话术推荐
     */
    @PreAuthorize("@ss.hasPermi('lawyers:ai:assist:analyze')")
    @Log(title = "AI辅助会话-分析推荐", businessType = BusinessType.UPDATE)
    @PostMapping("/analyze")
    public AjaxResult analyze(@RequestBody java.util.Map<String, Object> param)
    {
        Long sessionId = param.get("sessionId") != null ? Long.valueOf(param.get("sessionId").toString()) : null;
        String callSummary = param.get("callSummary") != null ? param.get("callSummary").toString() : null;
        AiAiCallSession result = aiAiCallSessionService.analyzeAndRecommend(sessionId, callSummary);
        return success(result);
    }

    /**
     * 独立处理函数：生成小结并结束 AI 辅助会话
     */
    @PreAuthorize("@ss.hasPermi('lawyers:ai:assist:summarize')")
    @Log(title = "AI辅助会话-小结结束", businessType = BusinessType.UPDATE)
    @PostMapping("/summarize")
    public AjaxResult summarize(@RequestBody java.util.Map<String, Object> param)
    {
        Long sessionId = param.get("sessionId") != null ? Long.valueOf(param.get("sessionId").toString()) : null;
        String callSummary = param.get("callSummary") != null ? param.get("callSummary").toString() : null;
        AiAiCallSession result = aiAiCallSessionService.summarizeAndEnd(sessionId, callSummary);
        return success(result);
    }

    @PreAuthorize("@ss.hasPermi('lawyers:ai:assist:remove')")
    @Log(title = "AI辅助会话", businessType = BusinessType.DELETE)
    @DeleteMapping("/{sessionIds}")
    public AjaxResult remove(@PathVariable Long[] sessionIds)
    {
        return toAjax(aiAiCallSessionService.deleteAiAiCallSessionBySessionIds(sessionIds));
    }
}
