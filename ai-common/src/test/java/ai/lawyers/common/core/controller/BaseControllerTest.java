package ai.lawyers.common.core.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Date;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.WebDataBinder;

import ai.lawyers.common.constant.HttpStatus;
import ai.lawyers.common.core.domain.AjaxResult;
import ai.lawyers.common.core.domain.entity.SysUser;
import ai.lawyers.common.core.domain.model.LoginUser;
import ai.lawyers.common.core.page.TableDataInfo;
import ai.lawyers.common.utils.DateUtils;

/**
 * 控制器基类测试：统一响应、分页数据包装、登录用户获取与日期绑定。
 *
 * @author ai-lawyers
 */
class BaseControllerTest
{
    private final BaseController controller = new BaseController();

    @AfterEach
    void tearDown()
    {
        SecurityContextHolder.clearContext();
    }

    @Test
    void 成功与失败与警告响应()
    {
        assertEquals(HttpStatus.SUCCESS, controller.success().get(AjaxResult.CODE_TAG));
        assertEquals("操作失败", controller.error().get(AjaxResult.MSG_TAG));
        assertEquals("已处理", controller.success("已处理").get(AjaxResult.MSG_TAG));
        assertEquals("失败原因", controller.error("失败原因").get(AjaxResult.MSG_TAG));
        assertEquals(HttpStatus.WARN, controller.warn("注意").get(AjaxResult.CODE_TAG));

        Object data = new Object();
        assertEquals(data, controller.success(data).get(AjaxResult.DATA_TAG));
    }

    @Test
    void toAjax_按影响行数与布尔结果判断()
    {
        assertTrue(controller.toAjax(1).isSuccess());
        assertTrue(controller.toAjax(0).isError());
        assertTrue(controller.toAjax(true).isSuccess());
        assertTrue(controller.toAjax(false).isError());
    }

    @Test
    void redirect_添加重定向前缀()
    {
        assertEquals("redirect:/system/user", controller.redirect("/system/user"));
    }

    @Test
    void getDataTable_包装列表与总数()
    {
        List<String> rows = List.of("a", "b", "c");

        TableDataInfo table = controller.getDataTable(rows);

        assertEquals(HttpStatus.SUCCESS, table.getCode());
        assertEquals("查询成功", table.getMsg());
        assertEquals(rows, table.getRows());
        assertEquals(3L, table.getTotal());
    }

    @Test
    void 登录用户信息读取()
    {
        SysUser user = new SysUser();
        user.setUserId(10L);
        user.setUserName("zhangsan");
        LoginUser loginUser = new LoginUser(10L, 20L, user, Set.of());
        SecurityContextHolder.getContext()
                .setAuthentication(new UsernamePasswordAuthenticationToken(loginUser, null, List.of()));

        assertEquals(loginUser, controller.getLoginUser());
        assertEquals(10L, controller.getUserId());
        assertEquals(20L, controller.getDeptId());
        assertEquals("zhangsan", controller.getUsername());
    }

    @Test
    void initBinder_字符串转日期()
    {
        WebDataBinder binder = new WebDataBinder(new Object());
        controller.initBinder(binder);

        Date date = (Date) binder.convertIfNecessary("2026-11-18", Date.class);

        assertNotNull(date);
        assertEquals("2026-11-18", DateUtils.parseDateToStr("yyyy-MM-dd", date));
        assertFalse(binder.getBindingResult().hasErrors());
    }
}
