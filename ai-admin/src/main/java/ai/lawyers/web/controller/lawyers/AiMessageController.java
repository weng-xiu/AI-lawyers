package ai.lawyers.web.controller.lawyers;

import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ai.lawyers.common.annotation.Log;
import ai.lawyers.common.core.controller.BaseController;
import ai.lawyers.common.core.domain.AjaxResult;
import ai.lawyers.common.core.page.TableDataInfo;
import ai.lawyers.common.enums.BusinessType;
import ai.lawyers.system.domain.lawyers.AiMessage;
import ai.lawyers.system.service.lawyers.IAiMessageService;

/**
 * 消息中心-站内信 Controller
 *
 * @author ai-lawyers
 */
@RestController
@RequestMapping("/lawyers/message")
public class AiMessageController extends BaseController
{
    @Autowired
    private IAiMessageService aiMessageService;

    /**
     * 站内信列表（强制按当前登录用户过滤，普通坐席只能看自己的消息）
     */
    @PreAuthorize("@ss.hasPermi('lawyers:message:list')")
    @GetMapping("/list")
    public TableDataInfo list(AiMessage aiMessage)
    {
        startPage();
        aiMessage.setReceiverUserId(getUserId());
        List<AiMessage> list = aiMessageService.selectAiMessageList(aiMessage);
        return getDataTable(list);
    }

    /** 未读消息数（铃铛角标，登录即可用，不单独配菜单权限） */
    @GetMapping("/unreadCount")
    public AjaxResult unreadCount()
    {
        return success(aiMessageService.countUnread(getUserId()));
    }

    /** 消息详情（同时标记已读） */
    @PreAuthorize("@ss.hasPermi('lawyers:message:query')")
    @GetMapping(value = "/{messageId}")
    public AjaxResult getInfo(@PathVariable("messageId") Long messageId)
    {
        aiMessageService.markRead(messageId, getUserId());
        return success(aiMessageService.selectAiMessageByMessageId(messageId));
    }

    /** 标记单条已读 */
    @PreAuthorize("@ss.hasPermi('lawyers:message:read')")
    @Log(title = "站内信已读", businessType = BusinessType.OTHER)
    @PutMapping("/read/{messageId}")
    public AjaxResult read(@PathVariable("messageId") Long messageId)
    {
        return toAjax(aiMessageService.markRead(messageId, getUserId()));
    }

    /** 全部已读 */
    @PreAuthorize("@ss.hasPermi('lawyers:message:read')")
    @Log(title = "站内信全部已读", businessType = BusinessType.OTHER)
    @PutMapping("/readAll")
    public AjaxResult readAll()
    {
        return AjaxResult.success("已全部标记为已读", aiMessageService.markAllRead(getUserId()));
    }

    /** 删除消息（仅能删本人消息） */
    @PreAuthorize("@ss.hasPermi('lawyers:message:remove')")
    @Log(title = "删除站内信", businessType = BusinessType.DELETE)
    @DeleteMapping("/{messageIds}")
    public AjaxResult remove(@PathVariable Long[] messageIds)
    {
        return toAjax(aiMessageService.deleteAiMessageByMessageIds(messageIds, getUserId()));
    }
}
