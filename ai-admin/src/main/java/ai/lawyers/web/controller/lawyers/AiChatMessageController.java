package ai.lawyers.web.controller.lawyers;

import java.util.Date;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import ai.lawyers.common.annotation.Log;
import ai.lawyers.common.core.controller.BaseController;
import ai.lawyers.common.core.domain.AjaxResult;
import ai.lawyers.common.core.page.TableDataInfo;
import ai.lawyers.common.enums.BusinessType;
import ai.lawyers.common.utils.poi.ExcelUtil;
import ai.lawyers.framework.websocket.ChatWebSocketServer;
import com.alibaba.fastjson2.JSON;
import ai.lawyers.system.domain.lawyers.AiChatMessage;
import ai.lawyers.system.domain.lawyers.AiChatSession;
import ai.lawyers.system.service.lawyers.IAiChatMessageService;
import ai.lawyers.system.service.lawyers.IAiChatSessionService;

/**
 * 聊天消息Controller
 *
 * @author ai-lawyers
 */
@RestController
@RequestMapping("/lawyers/chatMessage")
public class AiChatMessageController extends BaseController
{
    @Autowired
    private IAiChatMessageService aiChatMessageService;

    @Autowired
    private IAiChatSessionService aiChatSessionService;

    /**
     * 获取指定会话的消息列表（按发送时间正序）
     */
    @PreAuthorize("@ss.hasPermi('lawyers:chat:query')")
    @GetMapping("/list/{sessionId}")
    public TableDataInfo list(@PathVariable("sessionId") Long sessionId)
    {
        AiChatMessage query = new AiChatMessage();
        query.setSessionId(sessionId);
        List<AiChatMessage> list = aiChatMessageService.selectAiChatMessageList(query);
        return getDataTable(list);
    }

    /**
     * 坐席发送消息
     */
    @PreAuthorize("@ss.hasPermi('lawyers:chat:send')")
    @Log(title = "图文消息", businessType = BusinessType.INSERT)
    @PostMapping("/send")
    public AjaxResult send(@RequestBody AiChatMessage message)
    {
        message.setCreateBy(getUsername());
        if (message.getSenderType() == null || message.getSenderType().isEmpty()) {
            message.setSenderType("2");
        }
        if (message.getSenderName() == null || message.getSenderName().isEmpty()) {
            message.setSenderName(getUsername());
        }
        if (message.getMsgType() == null || message.getMsgType().isEmpty()) {
            message.setMsgType("1");
        }
        if (message.getSendTime() == null) {
            message.setSendTime(new Date());
        }
        if (message.getIsRead() == null) {
            message.setIsRead("1");
        }
        int rows = aiChatMessageService.insertAiChatMessage(message);

        // 更新会话的最后消息信息
        AiChatSession session = new AiChatSession();
        session.setSessionId(message.getSessionId());
        session.setLastMessage(truncateContent(message.getContent()));
        session.setLastMessageTime(new Date());
        session.setUpdateBy(getUsername());
        aiChatSessionService.updateAiChatSession(session);

        // P3-D4：落库后向会话房间 WS 推送，前端从 5s 轮询升级为实时收推
        try
        {
            ChatWebSocketServer.broadcast(String.valueOf(message.getSessionId()), JSON.toJSONString(message));
        }
        catch (Exception e)
        {
            logger.warn("ChatWS 推送失败，走轮询兜底 sessionId={}: {}", message.getSessionId(), e.getMessage());
        }

        return toAjax(rows);
    }

    /**
     * 关闭会话
     */
    @PreAuthorize("@ss.hasPermi('lawyers:chat:close')")
    @Log(title = "图文会话关闭", businessType = BusinessType.UPDATE)
    @PutMapping("/close/{sessionId}")
    public AjaxResult close(@PathVariable("sessionId") Long sessionId)
    {
        AiChatSession session = new AiChatSession();
        session.setSessionId(sessionId);
        session.setStatus("1");
        session.setUpdateBy(getUsername());
        aiChatSessionService.updateAiChatSession(session);
        return success();
    }

    /**
     * 转接会话
     */
    @PreAuthorize("@ss.hasPermi('lawyers:chat:transfer')")
    @Log(title = "图文会话转接", businessType = BusinessType.UPDATE)
    @PutMapping("/transfer")
    public AjaxResult transfer(@RequestBody AiChatSession session)
    {
        session.setUpdateBy(getUsername());
        aiChatSessionService.updateAiChatSession(session);
        return success();
    }

    /**
     * 标记会话消息已读
     */
    @PreAuthorize("@ss.hasPermi('lawyers:chat:query')")
    @PutMapping("/read/{sessionId}")
    public AjaxResult markRead(@PathVariable("sessionId") Long sessionId)
    {
        aiChatMessageService.markReadBySessionId(sessionId);
        return success();
    }

    /**
     * 获取消息详情
     */
    @PreAuthorize("@ss.hasPermi('lawyers:chat:query')")
    @GetMapping(value = "/{messageId}")
    public AjaxResult getInfo(@PathVariable("messageId") Long messageId)
    {
        return success(aiChatMessageService.selectAiChatMessageByMessageId(messageId));
    }

    /**
     * 导出消息
     */
    @PreAuthorize("@ss.hasPermi('lawyers:chat:export')")
    @Log(title = "图文消息", businessType = BusinessType.EXPORT)
    @PostMapping("/export")
    public void export(HttpServletResponse response, @RequestParam("sessionId") Long sessionId)
    {
        AiChatMessage query = new AiChatMessage();
        query.setSessionId(sessionId);
        List<AiChatMessage> list = aiChatMessageService.selectAiChatMessageList(query);
        ExcelUtil<AiChatMessage> util = new ExcelUtil<AiChatMessage>(AiChatMessage.class);
        util.exportExcel(response, list, "图文消息数据");
    }

    private String truncateContent(String content) {
        if (content == null) return "";
        return content.length() > 100 ? content.substring(0, 100) + "..." : content;
    }
}
