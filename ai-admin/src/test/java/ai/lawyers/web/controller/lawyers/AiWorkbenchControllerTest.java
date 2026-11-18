package ai.lawyers.web.controller.lawyers;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ai.lawyers.common.core.domain.AjaxResult;
import ai.lawyers.common.core.domain.entity.SysUser;
import ai.lawyers.common.core.domain.model.LoginUser;
import ai.lawyers.common.utils.SecurityUtils;
import ai.lawyers.system.domain.lawyers.AiCallRecord;
import ai.lawyers.system.domain.lawyers.AiNotice;
import ai.lawyers.system.domain.lawyers.AiTodo;
import ai.lawyers.system.service.lawyers.IWorkbenchService;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * 统一工作台接口测试：登录用户隔离与各聚合数据透传。
 *
 * @author ai-lawyers
 */
class AiWorkbenchControllerTest
{
    private static final Long USER_ID = 7L;

    private AiWorkbenchController controller;

    private IWorkbenchService workbenchService;

    @BeforeEach
    void setUp()
    {
        workbenchService = mock(IWorkbenchService.class);
        controller = new AiWorkbenchController();
        ReflectionTestUtils.setField(controller, "workbenchService", workbenchService);
    }

    @Test
    void 统计卡使用当前登录用户()
    {
        Map<String, Object> stats = Map.of("todayCalls", 12);
        when(workbenchService.getWorkbenchStats(USER_ID)).thenReturn(stats);

        try (MockedStatic<SecurityUtils> mocked = Mockito.mockStatic(SecurityUtils.class))
        {
            mocked.when(SecurityUtils::getLoginUser).thenReturn(loginUser());

            AjaxResult result = controller.stats();

            assertSame(stats, result.get(AjaxResult.DATA_TAG));
            verify(workbenchService).getWorkbenchStats(USER_ID);
        }
    }

    @Test
    void 待办列表使用当前登录用户()
    {
        List<AiTodo> todos = List.of(new AiTodo());
        when(workbenchService.getWorkbenchTodos(USER_ID)).thenReturn(todos);

        try (MockedStatic<SecurityUtils> mocked = Mockito.mockStatic(SecurityUtils.class))
        {
            mocked.when(SecurityUtils::getLoginUser).thenReturn(loginUser());

            AjaxResult result = controller.todos();

            assertSame(todos, result.get(AjaxResult.DATA_TAG));
        }
    }

    @Test
    void 最近通话按条数查询()
    {
        List<AiCallRecord> calls = List.of(new AiCallRecord());
        when(workbenchService.getWorkbenchRecentCalls(5)).thenReturn(calls);

        AjaxResult result = controller.recentCalls(5);

        assertSame(calls, result.get(AjaxResult.DATA_TAG));
        verify(workbenchService).getWorkbenchRecentCalls(5);
    }

    @Test
    void 公告按条数查询()
    {
        List<AiNotice> notices = List.of(new AiNotice());
        when(workbenchService.getWorkbenchNotices(3)).thenReturn(notices);

        AjaxResult result = controller.notices(3);

        assertSame(notices, result.get(AjaxResult.DATA_TAG));
        verify(workbenchService).getWorkbenchNotices(3);
    }

    private LoginUser loginUser()
    {
        SysUser user = new SysUser();
        user.setUserId(USER_ID);
        return new LoginUser(USER_ID, 1L, user, null);
    }
}
