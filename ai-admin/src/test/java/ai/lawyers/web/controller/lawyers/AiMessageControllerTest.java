package ai.lawyers.web.controller.lawyers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import ai.lawyers.common.core.domain.AjaxResult;
import ai.lawyers.common.core.domain.entity.SysUser;
import ai.lawyers.common.core.domain.model.LoginUser;
import ai.lawyers.common.core.page.TableDataInfo;
import ai.lawyers.common.utils.SecurityUtils;
import ai.lawyers.system.domain.lawyers.AiMessage;
import ai.lawyers.system.service.lawyers.IAiMessageService;

/**
 * 站内信接口单元测试
 *
 * @author ai-lawyers
 */
@ExtendWith(MockitoExtension.class)
class AiMessageControllerTest
{
    @Mock
    private IAiMessageService aiMessageService;

    @InjectMocks
    private AiMessageController controller;

    @AfterEach
    void tearDown()
    {
        RequestContextHolder.resetRequestAttributes();
    }

    private LoginUser loginUser(String username)
    {
        SysUser user = new SysUser();
        user.setUserName(username);
        return new LoginUser(1L, 2L, user, null);
    }

    @Test
    void 列表强制按当前登录用户过滤()
    {
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(new MockHttpServletRequest()));
        AiMessage query = new AiMessage();
        List<AiMessage> messages = new ArrayList<AiMessage>();
        messages.add(new AiMessage());
        try (MockedStatic<SecurityUtils> mocked = mockStatic(SecurityUtils.class))
        {
            mocked.when(SecurityUtils::getLoginUser).thenReturn(loginUser("agent"));
            when(aiMessageService.selectAiMessageList(query)).thenReturn(messages);

            TableDataInfo table = controller.list(query);

            assertEquals(200, table.getCode());
            assertEquals(1, table.getTotal());
            assertEquals(1L, query.getReceiverUserId());
            verify(aiMessageService).selectAiMessageList(query);
        }
    }

    @Test
    void 未读数按登录用户统计()
    {
        try (MockedStatic<SecurityUtils> mocked = mockStatic(SecurityUtils.class))
        {
            mocked.when(SecurityUtils::getLoginUser).thenReturn(loginUser("agent"));
            when(aiMessageService.countUnread(1L)).thenReturn(5);

            AjaxResult result = controller.unreadCount();

            assertEquals(5, result.get(AjaxResult.DATA_TAG));
            verify(aiMessageService).countUnread(1L);
        }
    }

    @Test
    void 详情先标记已读再返回消息()
    {
        AiMessage message = new AiMessage();
        message.setMessageId(9L);
        try (MockedStatic<SecurityUtils> mocked = mockStatic(SecurityUtils.class))
        {
            mocked.when(SecurityUtils::getLoginUser).thenReturn(loginUser("agent"));
            when(aiMessageService.markRead(9L, 1L)).thenReturn(1);
            when(aiMessageService.selectAiMessageByMessageId(9L)).thenReturn(message);

            AjaxResult result = controller.getInfo(9L);

            assertSame(message, result.get(AjaxResult.DATA_TAG));
            verify(aiMessageService).markRead(9L, 1L);
        }
    }

    @Test
    void 单条已读返回影响行数()
    {
        try (MockedStatic<SecurityUtils> mocked = mockStatic(SecurityUtils.class))
        {
            mocked.when(SecurityUtils::getLoginUser).thenReturn(loginUser("agent"));
            when(aiMessageService.markRead(9L, 1L)).thenReturn(0);

            AjaxResult result = controller.read(9L);

            assertEquals(500, result.get(AjaxResult.CODE_TAG));
        }
    }

    @Test
    void 全部已读返回条数()
    {
        try (MockedStatic<SecurityUtils> mocked = mockStatic(SecurityUtils.class))
        {
            mocked.when(SecurityUtils::getLoginUser).thenReturn(loginUser("agent"));
            when(aiMessageService.markAllRead(1L)).thenReturn(3);

            AjaxResult result = controller.readAll();

            assertEquals("已全部标记为已读", result.get(AjaxResult.MSG_TAG));
            assertEquals(3, result.get(AjaxResult.DATA_TAG));
        }
    }

    @Test
    void 删除消息限定本人()
    {
        Long[] messageIds = new Long[] { 3L, 4L };
        try (MockedStatic<SecurityUtils> mocked = mockStatic(SecurityUtils.class))
        {
            mocked.when(SecurityUtils::getLoginUser).thenReturn(loginUser("agent"));
            when(aiMessageService.deleteAiMessageByMessageIds(messageIds, 1L)).thenReturn(2);

            AjaxResult result = controller.remove(messageIds);

            assertEquals(200, result.get(AjaxResult.CODE_TAG));
            verify(aiMessageService).deleteAiMessageByMessageIds(messageIds, 1L);
        }
        verify(aiMessageService, never()).deleteAiMessageByMessageIds(messageIds, 2L);
    }
}
