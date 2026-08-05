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
import ai.lawyers.system.domain.lawyers.AiChatSession;
import ai.lawyers.system.service.lawyers.IAiChatSessionService;

/**
 * 图文会话Controller
 *
 * @author ai-lawyers
 */
@RestController
@RequestMapping("/lawyers/chatSession")
public class AiChatSessionController extends BaseController
{
    @Autowired
    private IAiChatSessionService aiChatSessionService;

    @PreAuthorize("@ss.hasPermi('lawyers:chatSession:list')")
    @GetMapping("/list")
    public TableDataInfo list(AiChatSession aiChatSession)
    {
        startPage();
        List<AiChatSession> list = aiChatSessionService.selectAiChatSessionList(aiChatSession);
        return getDataTable(list);
    }

    @PreAuthorize("@ss.hasPermi('lawyers:chatSession:export')")
    @Log(title = "图文会话", businessType = BusinessType.EXPORT)
    @PostMapping("/export")
    public void export(HttpServletResponse response, AiChatSession aiChatSession)
    {
        List<AiChatSession> list = aiChatSessionService.selectAiChatSessionList(aiChatSession);
        ExcelUtil<AiChatSession> util = new ExcelUtil<AiChatSession>(AiChatSession.class);
        util.exportExcel(response, list, "图文会话数据");
    }

    @PreAuthorize("@ss.hasPermi('lawyers:chatSession:query')")
    @GetMapping(value = "/{sessionId}")
    public AjaxResult getInfo(@PathVariable("sessionId") Long sessionId)
    {
        return success(aiChatSessionService.selectAiChatSessionBySessionId(sessionId));
    }

    @PreAuthorize("@ss.hasPermi('lawyers:chatSession:add')")
    @Log(title = "图文会话", businessType = BusinessType.INSERT)
    @PostMapping
    public AjaxResult add(@RequestBody AiChatSession aiChatSession)
    {
        aiChatSession.setCreateBy(getUsername());
        if (aiChatSession.getAssignee() == null || aiChatSession.getAssignee().isEmpty()) {
            aiChatSession.setAssignee(getUsername());
        }
        if (aiChatSession.getStatus() == null || aiChatSession.getStatus().isEmpty()) {
            aiChatSession.setStatus("0");
        }
        if (aiChatSession.getChannel() == null || aiChatSession.getChannel().isEmpty()) {
            aiChatSession.setChannel("1");
        }
        if (aiChatSession.getUnreadCount() == null) {
            aiChatSession.setUnreadCount(0);
        }
        return toAjax(aiChatSessionService.insertAiChatSession(aiChatSession));
    }

    @PreAuthorize("@ss.hasPermi('lawyers:chatSession:edit')")
    @Log(title = "图文会话", businessType = BusinessType.UPDATE)
    @PutMapping
    public AjaxResult edit(@RequestBody AiChatSession aiChatSession)
    {
        aiChatSession.setUpdateBy(getUsername());
        return toAjax(aiChatSessionService.updateAiChatSession(aiChatSession));
    }

    @PreAuthorize("@ss.hasPermi('lawyers:chatSession:remove')")
    @Log(title = "图文会话", businessType = BusinessType.DELETE)
    @DeleteMapping("/{sessionIds}")
    public AjaxResult remove(@PathVariable Long[] sessionIds)
    {
        return toAjax(aiChatSessionService.deleteAiChatSessionBySessionIds(sessionIds));
    }

    /** 会话统计 */
    @PreAuthorize("@ss.hasPermi('lawyers:chatSession:list')")
    @GetMapping("/stats")
    public AjaxResult getStats()
    {
        return success(aiChatSessionService.selectChatSessionStats());
    }
}
