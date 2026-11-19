package ai.lawyers.web.controller.lawyers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import ai.lawyers.common.core.domain.AjaxResult;
import ai.lawyers.common.core.domain.entity.SysUser;
import ai.lawyers.common.core.domain.model.LoginUser;
import ai.lawyers.common.core.page.TableDataInfo;
import ai.lawyers.common.utils.SecurityUtils;
import ai.lawyers.system.domain.lawyers.AiChatSession;
import ai.lawyers.system.service.lawyers.IAiChatSessionService;

/**
 * 图文会话接口单元测试
 *
 * @author ai-lawyers
 */
@ExtendWith(MockitoExtension.class)
class AiChatSessionControllerTest
{
    @Mock
    private IAiChatSessionService aiChatSessionService;

    @InjectMocks
    private AiChatSessionController controller;

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
    void 列表返回分页数据()
    {
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(new MockHttpServletRequest()));
        AiChatSession query = new AiChatSession();
        List<AiChatSession> list = new ArrayList<AiChatSession>();
        list.add(new AiChatSession());
        when(aiChatSessionService.selectAiChatSessionList(query)).thenReturn(list);

        TableDataInfo table = controller.list(query);

        assertEquals(200, table.getCode());
        assertEquals(1, table.getTotal());
        assertEquals(list, table.getRows());
    }

    @Test
    void 导出写入Excel响应()
    {
        when(aiChatSessionService.selectAiChatSessionList(any(AiChatSession.class)))
                .thenReturn(List.of(new AiChatSession()));
        MockHttpServletResponse response = new MockHttpServletResponse();

        controller.export(response, new AiChatSession());

        assertTrue(response.getContentType().contains("spreadsheetml"));
        assertTrue(response.getContentAsByteArray().length > 0);
    }

    @Test
    void 详情透传服务结果()
    {
        AiChatSession session = new AiChatSession();
        session.setSessionId(6L);
        when(aiChatSessionService.selectAiChatSessionBySessionId(6L)).thenReturn(session);

        AjaxResult result = controller.getInfo(6L);

        assertSame(session, result.get(AjaxResult.DATA_TAG));
    }

    @Test
    void 新增补齐默认受理人与状态()
    {
        AiChatSession session = new AiChatSession();
        when(aiChatSessionService.insertAiChatSession(any(AiChatSession.class))).thenReturn(1);

        try (MockedStatic<SecurityUtils> mocked = mockStatic(SecurityUtils.class))
        {
            mocked.when(SecurityUtils::getLoginUser).thenReturn(loginUser("agent"));
            AjaxResult result = controller.add(session);
            assertEquals(200, result.get(AjaxResult.CODE_TAG));
        }
        assertEquals("agent", session.getCreateBy());
        assertEquals("agent", session.getAssignee());
        assertEquals("0", session.getStatus());
        assertEquals("1", session.getChannel());
        assertEquals(0, session.getUnreadCount());
    }

    @Test
    void 修改写入更新人()
    {
        AiChatSession session = new AiChatSession();
        when(aiChatSessionService.updateAiChatSession(any(AiChatSession.class))).thenReturn(0);

        try (MockedStatic<SecurityUtils> mocked = mockStatic(SecurityUtils.class))
        {
            mocked.when(SecurityUtils::getLoginUser).thenReturn(loginUser("agent"));
            AjaxResult result = controller.edit(session);
            assertEquals(500, result.get(AjaxResult.CODE_TAG));
        }
        assertEquals("agent", session.getUpdateBy());
    }

    @Test
    void 批量删除返回影响行数()
    {
        Long[] sessionIds = new Long[] { 2L, 3L };
        when(aiChatSessionService.deleteAiChatSessionBySessionIds(sessionIds)).thenReturn(2);

        AjaxResult result = controller.remove(sessionIds);

        assertEquals(200, result.get(AjaxResult.CODE_TAG));
        verify(aiChatSessionService).deleteAiChatSessionBySessionIds(sessionIds);
    }

    @Test
    void 会话统计透传服务结果()
    {
        Map<String, Object> stats = new HashMap<String, Object>();
        stats.put("total", 12);
        when(aiChatSessionService.selectChatSessionStats()).thenReturn(stats);

        AjaxResult result = controller.getStats();

        assertSame(stats, result.get(AjaxResult.DATA_TAG));
    }
}
