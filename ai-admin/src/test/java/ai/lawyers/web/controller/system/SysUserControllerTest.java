package ai.lawyers.web.controller.system;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
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
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import ai.lawyers.common.core.domain.AjaxResult;
import ai.lawyers.common.core.domain.TreeSelect;
import ai.lawyers.common.core.domain.entity.SysDept;
import ai.lawyers.common.core.domain.entity.SysRole;
import ai.lawyers.common.core.domain.entity.SysUser;
import ai.lawyers.common.core.domain.model.LoginUser;
import ai.lawyers.common.core.page.TableDataInfo;
import ai.lawyers.common.utils.SecurityUtils;
import ai.lawyers.system.service.ISysDeptService;
import ai.lawyers.system.service.ISysPostService;
import ai.lawyers.system.service.ISysRoleService;
import ai.lawyers.system.service.ISysUserService;

/**
 * 用户管理接口单元测试
 *
 * @author ai-lawyers
 */
@ExtendWith(MockitoExtension.class)
class SysUserControllerTest
{
    @Mock
    private ISysUserService userService;

    @Mock
    private ISysRoleService roleService;

    @Mock
    private ISysDeptService deptService;

    @Mock
    private ISysPostService postService;

    @InjectMocks
    private SysUserController controller;

    @AfterEach
    void tearDown()
    {
        RequestContextHolder.resetRequestAttributes();
    }

    private LoginUser loginUser(long userId)
    {
        SysUser user = new SysUser();
        user.setUserId(userId);
        user.setUserName("operator");
        return new LoginUser(userId, 2L, user, null);
    }

    private SysUser user()
    {
        SysUser user = new SysUser();
        user.setUserId(2L);
        user.setUserName("agent");
        user.setNickName("客服一号");
        return user;
    }

    private SysRole role(long roleId, String roleKey)
    {
        SysRole role = new SysRole();
        role.setRoleId(roleId);
        role.setRoleKey(roleKey);
        return role;
    }

    @Test
    void 列表返回分页数据()
    {
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(new MockHttpServletRequest()));
        SysUser query = new SysUser();
        List<SysUser> list = new ArrayList<SysUser>();
        list.add(user());
        when(userService.selectUserList(query)).thenReturn(list);

        TableDataInfo table = controller.list(query);

        assertEquals(200, table.getCode());
        assertEquals(1, table.getTotal());
        assertEquals(list, table.getRows());
    }

    @Test
    void 导出写入Excel响应()
    {
        when(userService.selectUserList(any(SysUser.class))).thenReturn(List.of(user()));
        MockHttpServletResponse response = new MockHttpServletResponse();

        controller.export(response, new SysUser());

        assertTrue(response.getContentType().contains("spreadsheetml"));
        assertTrue(response.getContentAsByteArray().length > 0);
    }

    @Test
    void 查询详情带用户编号返回岗位与角色()
    {
        SysUser user = user();
        user.setRoles(List.of(role(1L, "admin"), role(3L, "call:agent")));
        List<Long> postIds = List.of(5L);
        List<SysRole> allRoles = List.of(role(1L, "admin"), role(3L, "call:agent"));
        when(userService.selectUserById(2L)).thenReturn(user);
        when(postService.selectPostListByUserId(2L)).thenReturn(postIds);
        when(roleService.selectRoleAll()).thenReturn(allRoles);
        when(postService.selectPostAll()).thenReturn(new ArrayList<ai.lawyers.system.domain.SysPost>());

        AjaxResult result = controller.getInfo(2L);

        verify(userService).checkUserDataScope(2L);
        assertSame(user, result.get(AjaxResult.DATA_TAG));
        assertSame(postIds, result.get("postIds"));
        assertEquals(List.of(1L, 3L), result.get("roleIds"));
        @SuppressWarnings("unchecked")
        List<SysRole> roles = (List<SysRole>) result.get("roles");
        assertEquals(1, roles.size());
        assertEquals("call:agent", roles.get(0).getRoleKey());
    }

    @Test
    void 查询详情不带用户编号仅返回选项()
    {
        List<SysRole> allRoles = List.of(role(1L, "admin"), role(3L, "call:agent"));
        when(roleService.selectRoleAll()).thenReturn(allRoles);
        when(postService.selectPostAll()).thenReturn(new ArrayList<ai.lawyers.system.domain.SysPost>());

        AjaxResult result = controller.getInfo(null);

        assertTrue(result.containsKey("roles"));
        assertEquals(1, ((List<?>) result.get("roles")).size());
        verify(userService, never()).selectUserById(any());
    }

    @Test
    void 新增用户账号重复()
    {
        SysUser user = user();
        when(userService.checkUserNameUnique(user)).thenReturn(false);

        AjaxResult result = controller.add(user);

        assertEquals(500, result.get(AjaxResult.CODE_TAG));
        assertEquals("新增用户'agent'失败，登录账号已存在", result.get(AjaxResult.MSG_TAG));
        verify(userService, never()).insertUser(any(SysUser.class));
    }

    @Test
    void 新增用户手机号重复()
    {
        SysUser user = user();
        user.setPhonenumber("13800000000");
        when(userService.checkUserNameUnique(user)).thenReturn(true);
        when(userService.checkPhoneUnique(user)).thenReturn(false);

        assertEquals("新增用户'agent'失败，手机号码已存在", controller.add(user).get(AjaxResult.MSG_TAG));
        verify(userService, never()).insertUser(any(SysUser.class));
    }

    @Test
    void 新增用户加密密码并写创建人()
    {
        SysUser user = user();
        user.setPassword("123456");
        when(userService.checkUserNameUnique(user)).thenReturn(true);
        when(userService.insertUser(user)).thenReturn(1);

        try (MockedStatic<SecurityUtils> mocked = mockStatic(SecurityUtils.class))
        {
            mocked.when(SecurityUtils::getLoginUser).thenReturn(loginUser(1L));
            mocked.when(() -> SecurityUtils.encryptPassword("123456")).thenReturn("encrypted");
            assertEquals(200, controller.add(user).get(AjaxResult.CODE_TAG));
        }
        assertEquals("encrypted", user.getPassword());
        assertEquals("operator", user.getCreateBy());
    }

    @Test
    void 修改用户账号重复()
    {
        SysUser user = user();
        when(userService.checkUserNameUnique(user)).thenReturn(false);

        assertEquals("修改用户'agent'失败，登录账号已存在", controller.edit(user).get(AjaxResult.MSG_TAG));
        verify(userService, never()).updateUser(any(SysUser.class));
    }

    @Test
    void 修改用户写更新人()
    {
        SysUser user = user();
        when(userService.checkUserNameUnique(user)).thenReturn(true);
        when(userService.updateUser(user)).thenReturn(0);

        try (MockedStatic<SecurityUtils> mocked = mockStatic(SecurityUtils.class))
        {
            mocked.when(SecurityUtils::getLoginUser).thenReturn(loginUser(1L));
            assertEquals(500, controller.edit(user).get(AjaxResult.CODE_TAG));
        }
        assertEquals("operator", user.getUpdateBy());
    }

    @Test
    void 删除当前用户被拒绝()
    {
        try (MockedStatic<SecurityUtils> mocked = mockStatic(SecurityUtils.class))
        {
            mocked.when(SecurityUtils::getLoginUser).thenReturn(loginUser(1L));

            assertEquals("当前用户不能删除", controller.remove(new Long[] { 1L, 2L }).get(AjaxResult.MSG_TAG));
        }
        verify(userService, never()).deleteUserByIds(any());
    }

    @Test
    void 删除其他用户成功()
    {
        Long[] ids = new Long[] { 3L, 4L };
        when(userService.deleteUserByIds(ids)).thenReturn(2);

        try (MockedStatic<SecurityUtils> mocked = mockStatic(SecurityUtils.class))
        {
            mocked.when(SecurityUtils::getLoginUser).thenReturn(loginUser(1L));
            assertEquals(200, controller.remove(ids).get(AjaxResult.CODE_TAG));
        }
    }

    @Test
    void 重置密码加密并写更新人()
    {
        SysUser user = user();
        user.setPassword("newPass");
        when(userService.resetPwd(user)).thenReturn(1);

        try (MockedStatic<SecurityUtils> mocked = mockStatic(SecurityUtils.class))
        {
            mocked.when(SecurityUtils::getLoginUser).thenReturn(loginUser(1L));
            mocked.when(() -> SecurityUtils.encryptPassword("newPass")).thenReturn("encrypted");
            assertEquals(200, controller.resetPwd(user).get(AjaxResult.CODE_TAG));
        }
        assertEquals("encrypted", user.getPassword());
        assertEquals("operator", user.getUpdateBy());
        verify(userService).checkUserAllowed(user);
        verify(userService).checkUserDataScope(2L);
    }

    @Test
    void 状态修改写更新人()
    {
        SysUser user = user();
        when(userService.updateUserStatus(user)).thenReturn(1);

        try (MockedStatic<SecurityUtils> mocked = mockStatic(SecurityUtils.class))
        {
            mocked.when(SecurityUtils::getLoginUser).thenReturn(loginUser(1L));
            assertEquals(200, controller.changeStatus(user).get(AjaxResult.CODE_TAG));
        }
        assertEquals("operator", user.getUpdateBy());
    }

    @Test
    void 查看授权角色过滤管理员角色()
    {
        SysUser user = user();
        List<SysRole> roles = List.of(role(1L, "admin"), role(3L, "call:agent"));
        when(userService.selectUserById(2L)).thenReturn(user);
        when(roleService.selectRolesByUserId(2L)).thenReturn(roles);

        AjaxResult result = controller.authRole(2L);

        assertSame(user, result.get("user"));
        assertEquals(1, ((List<?>) result.get("roles")).size());
    }

    @Test
    void 用户授权角色校验并提交()
    {
        Long[] roleIds = new Long[] { 3L, 4L };

        controller.insertAuthRole(2L, roleIds);

        verify(userService).checkUserDataScope(2L);
        verify(roleService).checkRoleDataScope(roleIds);
        verify(userService).insertUserAuth(2L, roleIds);
    }

    @Test
    void 部门树透传服务结果()
    {
        List<TreeSelect> tree = new ArrayList<TreeSelect>();
        when(deptService.selectDeptTreeList(any(SysDept.class))).thenReturn(tree);

        assertSame(tree, controller.deptTree(new SysDept()).get(AjaxResult.DATA_TAG));
    }
}
