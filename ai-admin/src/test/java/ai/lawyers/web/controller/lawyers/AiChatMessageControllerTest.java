package ai.lawyers.web.controller.lawyers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletResponse;
import com.github.pagehelper.PageHelper;
import ai.lawyers.common.core.domain.AjaxResult;
import ai.lawyers.common.core.domain.entity.SysUser;
import ai.lawyers.common.core.domain.model.LoginUser;
import ai.lawyers.common.core.page.TableDataInfo;
import ai.lawyers.common.utils.SecurityUtils;
import ai.lawyers.framework.websocket.ChatWebSocketServer;
import ai.lawyers.system.domain.lawyers.AiChatMessage;
import ai.lawyers.system.domain.lawyers.AiChatSession;
import ai.lawyers.system.service.lawyers.IAiChatMessageService;
import ai.lawyers.system.service.lawyers.IAiChatSessionService;

/**
 * 聊天消息接口单元测试
 *
 * @author ai-lawyers
 */
@ExtendWith(MockitoExtension.class)
class AiChatMessageControllerTest
{
    @Mock
    private IAiChatMessageService aiChatMessageService;

    @Mock
    private IAiChatSessionService aiChatSessionService;

    @InjectMocks
    private AiChatMessageController controller;

    @AfterEach
    void tearDown()
    {
        PageHelper.clearPage();
    }

    private LoginUser loginUser(String username)
    {
        SysUser user = new SysUser();
        user.setUserName(username);
        return new LoginUser(1L, 2L, user, null);
    }

    private AiChatMessage message()
    {
        AiChatMessage message = new AiChatMessage();
        message.setSessionId(9L);
        message.setContent("您好，请问有什么可以帮您");
        return message;
    }

    @Test
    void 消息列表按会话与分页上限查询()
    {
        List<AiChatMessage> list = new ArrayList<AiChatMessage>();
        list.add(message());
        when(aiChatMessageService.selectAiChatMessageList(org.mockito.ArgumentMatchers.any(AiChatMessage.class)))
                .thenReturn(list);

        TableDataInfo table = controller.list(9L);

        assertEquals(200, table.getCode());
        assertSame(list, table.getRows());
        ArgumentCaptor<AiChatMessage> captor = ArgumentCaptor.forClass(AiChatMessage.class);
        verify(aiChatMessageService).selectAiChatMessageList(captor.capture());
        assertEquals(9L, captor.getValue().getSessionId());
    }

    @Test
    void 发送消息补默认值并截断会话摘要()
    {
        AiChatMessage message = new AiChatMessage();
        message.setSessionId(9L);
        StringBuilder content = new StringBuilder();
        for (int i = 0; i < 150; i++)
        {
            content.append('好');
        }
        message.setContent(content.toString());
        when(aiChatMessageService.insertAiChatMessage(message)).thenReturn(1);

        try (MockedStatic<SecurityUtils> mocked = mockStatic(SecurityUtils.class);
             MockedStatic<ChatWebSocketServer> ws = mockStatic(ChatWebSocketServer.class))
        {
            mocked.when(SecurityUtils::getLoginUser).thenReturn(loginUser("agent"));
            assertEquals(200, controller.send(message).get(AjaxResult.CODE_TAG));
            ws.verify(() -> ChatWebSocketServer.broadcast(eq("9"), anyString()));
        }
        assertEquals("agent", message.getCreateBy());
        assertEquals("2", message.getSenderType());
        assertEquals("agent", message.getSenderName());
        assertEquals("1", message.getMsgType());
        assertEquals("1", message.getIsRead());
        assertNotNull(message.getSendTime());

        ArgumentCaptor<AiChatSession> captor = ArgumentCaptor.forClass(AiChatSession.class);
        verify(aiChatSessionService).updateAiChatSession(captor.capture());
        AiChatSession session = captor.getValue();
        assertEquals(9L, session.getSessionId());
        assertEquals(103, session.getLastMessage().length());
        assertTrue(session.getLastMessage().endsWith("..."));
        assertEquals("agent", session.getUpdateBy());
        assertNotNull(session.getLastMessageTime());
    }

    @Test
    void 发送消息保留显式字段()
    {
        AiChatMessage message = message();
        message.setSenderType("1");
        message.setSenderName("客户");
        message.setMsgType("2");
        message.setIsRead("0");
        Date sendTime = new Date(1700000000000L);
        message.setSendTime(sendTime);
        when(aiChatMessageService.insertAiChatMessage(message)).thenReturn(1);

        try (MockedStatic<SecurityUtils> mocked = mockStatic(SecurityUtils.class);
             MockedStatic<ChatWebSocketServer> ws = mockStatic(ChatWebSocketServer.class))
        {
            mocked.when(SecurityUtils::getLoginUser).thenReturn(loginUser("agent"));
            assertEquals(200, controller.send(message).get(AjaxResult.CODE_TAG));
        }
        assertEquals("1", message.getSenderType());
        assertEquals("客户", message.getSenderName());
        assertEquals("2", message.getMsgType());
        assertEquals("0", message.getIsRead());
        assertSame(sendTime, message.getSendTime());

        ArgumentCaptor<AiChatSession> captor = ArgumentCaptor.forClass(AiChatSession.class);
        verify(aiChatSessionService).updateAiChatSession(captor.capture());
        assertEquals("您好，请问有什么可以帮您", captor.getValue().getLastMessage());
    }

    @Test
    void 关闭与转接会话写操作人()
    {
        try (MockedStatic<SecurityUtils> mocked = mockStatic(SecurityUtils.class))
        {
            mocked.when(SecurityUtils::getLoginUser).thenReturn(loginUser("agent"));
            assertEquals(200, controller.close(9L).get(AjaxResult.CODE_TAG));
            AiChatSession session = new AiChatSession();
            session.setSessionId(10L);
            assertEquals(200, controller.transfer(session).get(AjaxResult.CODE_TAG));
            assertEquals("agent", session.getUpdateBy());
        }
        ArgumentCaptor<AiChatSession> captor = ArgumentCaptor.forClass(AiChatSession.class);
        verify(aiChatSessionService, org.mockito.Mockito.times(2)).updateAiChatSession(captor.capture());
        assertEquals(9L, captor.getAllValues().get(0).getSessionId());
        assertEquals("1", captor.getAllValues().get(0).getStatus());
        assertEquals("agent", captor.getAllValues().get(0).getUpdateBy());
    }

    @Test
    void 标记已读与消息详情()
    {
        AiChatMessage detail = message();
        when(aiChatMessageService.selectAiChatMessageByMessageId(6L)).thenReturn(detail);

        assertEquals(200, controller.markRead(9L).get(AjaxResult.CODE_TAG));
        verify(aiChatMessageService).markReadBySessionId(9L);
        assertSame(detail, controller.getInfo(6L).get(AjaxResult.DATA_TAG));
    }

    @Test
    void 导出会话消息Excel() throws Exception
    {
        List<AiChatMessage> list = new ArrayList<AiChatMessage>();
        list.add(message());
        when(aiChatMessageService.selectAiChatMessageList(org.mockito.ArgumentMatchers.any(AiChatMessage.class)))
                .thenReturn(list);
        MockHttpServletResponse response = new MockHttpServletResponse();

        controller.export(response, 9L);

        assertTrue(response.getContentType().contains("spreadsheetml"));
        assertTrue(response.getContentAsByteArray().length > 0);
    }
}
