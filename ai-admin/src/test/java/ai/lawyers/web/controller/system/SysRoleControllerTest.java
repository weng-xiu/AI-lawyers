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
import java.util.HashSet;
import java.util.List;
import java.util.Set;
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
import ai.lawyers.common.core.domain.entity.SysDept;
import ai.lawyers.common.core.domain.entity.SysRole;
import ai.lawyers.common.core.domain.entity.SysUser;
import ai.lawyers.common.core.domain.model.LoginUser;
import ai.lawyers.common.core.page.TableDataInfo;
import ai.lawyers.common.utils.SecurityUtils;
import ai.lawyers.framework.web.service.SysPermissionService;
import ai.lawyers.framework.web.service.TokenService;
import ai.lawyers.system.domain.SysUserRole;
import ai.lawyers.system.service.ISysDeptService;
import ai.lawyers.system.service.ISysRoleService;
import ai.lawyers.system.service.ISysUserService;

/**
 * 角色管理接口单元测试
 *
 * @author ai-lawyers
 */
@ExtendWith(MockitoExtension.class)
class SysRoleControllerTest
{
    @Mock
    private ISysRoleService roleService;

    @Mock
    private TokenService tokenService;

    @Mock
    private SysPermissionService permissionService;

    @Mock
    private ISysUserService userService;

    @Mock
    private ISysDeptService deptService;

    @InjectMocks
    private SysRoleController controller;

    @AfterEach
    void tearDown()
    {
        RequestContextHolder.resetRequestAttributes();
    }

    private SysRole role()
    {
        SysRole role = new SysRole();
        role.setRoleId(3L);
        role.setRoleName("客服主管");
        role.setRoleKey("call:manager");
        return role;
    }

    private LoginUser loginUser(long userId)
    {
        SysUser user = new SysUser();
        user.setUserId(userId);
        user.setUserName("operator");
        return new LoginUser(userId, 2L, user, null);
    }

    @Test
    void 列表返回分页数据()
    {
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(new MockHttpServletRequest()));
        SysRole query = new SysRole();
        List<SysRole> list = new ArrayList<SysRole>();
        list.add(role());
        when(roleService.selectRoleList(query)).thenReturn(list);

        TableDataInfo table = controller.list(query);

        assertEquals(200, table.getCode());
        assertEquals(1, table.getTotal());
        assertEquals(list, table.getRows());
    }

    @Test
    void 导出写入Excel响应()
    {
        when(roleService.selectRoleList(any(SysRole.class))).thenReturn(List.of(role()));
        MockHttpServletResponse response = new MockHttpServletResponse();

        controller.export(response, new SysRole());

        assertTrue(response.getContentType().contains("spreadsheetml"));
        assertTrue(response.getContentAsByteArray().length > 0);
    }

    @Test
    void 详情校验数据权限()
    {
        SysRole role = role();
        when(roleService.selectRoleById(3L)).thenReturn(role);

        assertSame(role, controller.getInfo(3L).get(AjaxResult.DATA_TAG));
        verify(roleService).checkRoleDataScope(3L);
    }

    @Test
    void 新增角色名称重复()
    {
        SysRole role = role();
        when(roleService.checkRoleNameUnique(role)).thenReturn(false);

        AjaxResult result = controller.add(role);

        assertEquals(500, result.get(AjaxResult.CODE_TAG));
        assertEquals("新增角色'客服主管'失败，角色名称已存在", result.get(AjaxResult.MSG_TAG));
        verify(roleService, never()).insertRole(any(SysRole.class));
    }

    @Test
    void 新增角色权限重复()
    {
        SysRole role = role();
        when(roleService.checkRoleNameUnique(role)).thenReturn(true);
        when(roleService.checkRoleKeyUnique(role)).thenReturn(false);

        assertEquals("新增角色'客服主管'失败，角色权限已存在",
                controller.add(role).get(AjaxResult.MSG_TAG));
        verify(roleService, never()).insertRole(any(SysRole.class));
    }

    @Test
    void 新增角色写入创建人()
    {
        SysRole role = role();
        when(roleService.checkRoleNameUnique(role)).thenReturn(true);
        when(roleService.checkRoleKeyUnique(role)).thenReturn(true);
        when(roleService.insertRole(role)).thenReturn(1);

        try (MockedStatic<SecurityUtils> mocked = mockStatic(SecurityUtils.class))
        {
            mocked.when(SecurityUtils::getLoginUser).thenReturn(loginUser(2L));
            assertEquals(200, controller.add(role).get(AjaxResult.CODE_TAG));
        }
        assertEquals("operator", role.getCreateBy());
    }

    @Test
    void 修改角色名称重复()
    {
        SysRole role = role();
        when(roleService.checkRoleNameUnique(role)).thenReturn(false);

        assertEquals("修改角色'客服主管'失败，角色名称已存在",
                controller.edit(role).get(AjaxResult.MSG_TAG));
        verify(roleService, never()).updateRole(any(SysRole.class));
    }

    @Test
    void 修改角色成功刷新非管理员权限缓存()
    {
        SysRole role = role();
        LoginUser loginUser = loginUser(2L);
        SysUser refreshed = new SysUser();
        refreshed.setUserId(2L);
        refreshed.setUserName("operator");
        Set<String> permissions = new HashSet<String>();
        permissions.add("system:role:list");
        when(roleService.checkRoleNameUnique(role)).thenReturn(true);
        when(roleService.checkRoleKeyUnique(role)).thenReturn(true);
        when(roleService.updateRole(role)).thenReturn(1);
        when(userService.selectUserByUserName("operator")).thenReturn(refreshed);
        when(permissionService.getMenuPermission(refreshed)).thenReturn(permissions);

        try (MockedStatic<SecurityUtils> mocked = mockStatic(SecurityUtils.class))
        {
            mocked.when(SecurityUtils::getLoginUser).thenReturn(loginUser);
            assertEquals(200, controller.edit(role).get(AjaxResult.CODE_TAG));
        }
        assertEquals("operator", role.getUpdateBy());
        assertSame(refreshed, loginUser.getUser());
        assertEquals(permissions, loginUser.getPermissions());
        verify(tokenService).setLoginUser(loginUser);
    }

    @Test
    void 修改角色更新失败提示联系管理员()
    {
        SysRole role = role();
        when(roleService.checkRoleNameUnique(role)).thenReturn(true);
        when(roleService.checkRoleKeyUnique(role)).thenReturn(true);
        when(roleService.updateRole(role)).thenReturn(0);

        try (MockedStatic<SecurityUtils> mocked = mockStatic(SecurityUtils.class))
        {
            mocked.when(SecurityUtils::getLoginUser).thenReturn(loginUser(2L));
            assertEquals("修改角色'客服主管'失败，请联系管理员",
                    controller.edit(role).get(AjaxResult.MSG_TAG));
        }
        verify(tokenService, never()).setLoginUser(any(LoginUser.class));
    }

    @Test
    void 数据权限与状态修改委托服务()
    {
        SysRole role = role();
        when(roleService.authDataScope(role)).thenReturn(1);
        when(roleService.updateRoleStatus(role)).thenReturn(0);

        try (MockedStatic<SecurityUtils> mocked = mockStatic(SecurityUtils.class))
        {
            mocked.when(SecurityUtils::getLoginUser).thenReturn(loginUser(2L));
            assertEquals(200, controller.dataScope(role).get(AjaxResult.CODE_TAG));
            assertEquals(500, controller.changeStatus(role).get(AjaxResult.CODE_TAG));
        }
        assertEquals("operator", role.getUpdateBy());
    }

    @Test
    void 删除与选项查询透传()
    {
        Long[] ids = new Long[] { 3L, 4L };
        List<SysRole> all = List.of(role());
        when(roleService.deleteRoleByIds(ids)).thenReturn(2);
        when(roleService.selectRoleAll()).thenReturn(all);

        assertEquals(200, controller.remove(ids).get(AjaxResult.CODE_TAG));
        assertSame(all, controller.optionselect().get(AjaxResult.DATA_TAG));
    }

    @Test
    void 已分配与未分配用户分页返回()
    {
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(new MockHttpServletRequest()));
        SysUser query = new SysUser();
        List<SysUser> allocated = new ArrayList<SysUser>();
        List<SysUser> unallocated = new ArrayList<SysUser>();
        when(userService.selectAllocatedList(query)).thenReturn(allocated);
        when(userService.selectUnallocatedList(query)).thenReturn(unallocated);

        assertSame(allocated, controller.allocatedList(query).getRows());
        assertSame(unallocated, controller.unallocatedList(query).getRows());
    }

    @Test
    void 取消与批量授权用户()
    {
        SysUserRole userRole = new SysUserRole();
        userRole.setRoleId(3L);
        userRole.setUserId(5L);
        Long[] userIds = new Long[] { 5L, 6L };
        when(roleService.deleteAuthUser(userRole)).thenReturn(1);
        when(roleService.deleteAuthUsers(3L, userIds)).thenReturn(2);
        when(roleService.insertAuthUsers(3L, userIds)).thenReturn(2);

        assertEquals(200, controller.cancelAuthUser(userRole).get(AjaxResult.CODE_TAG));
        assertEquals(200, controller.cancelAuthUserAll(3L, userIds).get(AjaxResult.CODE_TAG));
        assertEquals(200, controller.selectAuthUserAll(3L, userIds).get(AjaxResult.CODE_TAG));
        verify(roleService).checkRoleDataScope(3L);
    }

    @Test
    void 部门树返回已选节点与树数据()
    {
        List<Long> checked = List.of(1L, 2L);
        List<ai.lawyers.common.core.domain.TreeSelect> depts = new ArrayList<ai.lawyers.common.core.domain.TreeSelect>();
        when(deptService.selectDeptListByRoleId(3L)).thenReturn(checked);
        when(deptService.selectDeptTreeList(any(SysDept.class))).thenReturn(depts);

        AjaxResult result = controller.deptTree(3L);

        assertEquals(checked, result.get("checkedKeys"));
        assertSame(depts, result.get("depts"));
    }
}
