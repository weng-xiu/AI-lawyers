package ai.lawyers.web.controller.lawyers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
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
import ai.lawyers.system.domain.lawyers.AiCallTicket;
import ai.lawyers.system.domain.lawyers.AiTodo;
import ai.lawyers.system.service.lawyers.IAiTodoService;

/**
 * 工作台待办接口单元测试
 *
 * @author ai-lawyers
 */
@ExtendWith(MockitoExtension.class)
class AiTodoControllerTest
{
    @Mock
    private IAiTodoService aiTodoService;

    @InjectMocks
    private AiTodoController controller;

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
    void 列表默认限定当前用户()
    {
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(new MockHttpServletRequest()));
        AiTodo query = new AiTodo();
        List<AiTodo> list = new ArrayList<AiTodo>();
        list.add(new AiTodo());
        when(aiTodoService.selectAiTodoList(query)).thenReturn(list);

        try (MockedStatic<SecurityUtils> mocked = mockStatic(SecurityUtils.class))
        {
            mocked.when(SecurityUtils::getLoginUser).thenReturn(loginUser("agent"));
            TableDataInfo table = controller.list(query);

            assertEquals(1, table.getTotal());
        }
        assertEquals(1L, query.getUserId());
    }

    @Test
    void 列表保留显式指定的用户()
    {
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(new MockHttpServletRequest()));
        AiTodo query = new AiTodo();
        query.setUserId(9L);
        when(aiTodoService.selectAiTodoList(query)).thenReturn(new ArrayList<AiTodo>());

        controller.list(query);

        assertEquals(9L, query.getUserId());
    }

    @Test
    void 导出写入Excel响应()
    {
        when(aiTodoService.selectAiTodoList(any(AiTodo.class))).thenReturn(List.of(new AiTodo()));
        MockHttpServletResponse response = new MockHttpServletResponse();

        controller.export(response, new AiTodo());

        assertTrue(response.getContentType().contains("spreadsheetml"));
    }

    @Test
    void 详情与关联工单透传服务结果()
    {
        AiTodo todo = new AiTodo();
        todo.setTodoId(5L);
        AiCallTicket ticket = new AiCallTicket();
        when(aiTodoService.selectAiTodoByTodoId(5L)).thenReturn(todo);
        when(aiTodoService.selectLinkedTicket(5L)).thenReturn(ticket);

        assertSame(todo, controller.getInfo(5L).get(AjaxResult.DATA_TAG));
        assertSame(ticket, controller.getLinkedTicket(5L).get(AjaxResult.DATA_TAG));
    }

    @Test
    void 新增补齐创建人与默认用户()
    {
        AiTodo todo = new AiTodo();
        when(aiTodoService.insertAiTodo(todo)).thenReturn(1);

        try (MockedStatic<SecurityUtils> mocked = mockStatic(SecurityUtils.class))
        {
            mocked.when(SecurityUtils::getLoginUser).thenReturn(loginUser("agent"));
            assertEquals(200, controller.add(todo).get(AjaxResult.CODE_TAG));
        }
        assertEquals("agent", todo.getCreateBy());
        assertEquals(1L, todo.getUserId());
    }

    @Test
    void 修改写入更新人()
    {
        AiTodo todo = new AiTodo();
        when(aiTodoService.updateAiTodo(todo)).thenReturn(0);

        try (MockedStatic<SecurityUtils> mocked = mockStatic(SecurityUtils.class))
        {
            mocked.when(SecurityUtils::getLoginUser).thenReturn(loginUser("agent"));
            assertEquals(500, controller.edit(todo).get(AjaxResult.CODE_TAG));
        }
        assertEquals("agent", todo.getUpdateBy());
    }

    @Test
    void 处理延后忽略分别设置状态()
    {
        when(aiTodoService.updateAiTodo(any(AiTodo.class))).thenReturn(1);

        try (MockedStatic<SecurityUtils> mocked = mockStatic(SecurityUtils.class))
        {
            mocked.when(SecurityUtils::getLoginUser).thenReturn(loginUser("agent"));
            controller.process(5L);
            controller.defer(5L);
            controller.ignore(5L);
        }
        ArgumentCaptor<AiTodo> captor = ArgumentCaptor.forClass(AiTodo.class);
        verify(aiTodoService, org.mockito.Mockito.times(3)).updateAiTodo(captor.capture());
        assertEquals("1", captor.getAllValues().get(0).getStatus());
        assertEquals("2", captor.getAllValues().get(1).getStatus());
        assertEquals("3", captor.getAllValues().get(2).getStatus());
        assertEquals("agent", captor.getAllValues().get(0).getUpdateBy());
    }

    @Test
    void 批量删除返回影响行数()
    {
        Long[] todoIds = new Long[] { 1L, 2L };
        when(aiTodoService.deleteAiTodoByTodoIds(todoIds)).thenReturn(2);

        assertEquals(200, controller.remove(todoIds).get(AjaxResult.CODE_TAG));
        verify(aiTodoService).deleteAiTodoByTodoIds(todoIds);
    }
}
