package ai.lawyers.framework.web.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import ai.lawyers.common.constant.UserConstants;
import ai.lawyers.common.core.domain.entity.SysRole;
import ai.lawyers.common.core.domain.entity.SysUser;
import ai.lawyers.system.service.ISysMenuService;
import ai.lawyers.system.service.ISysRoleService;

/**
 * 权限服务测试：管理员全权限、普通用户角色/菜单权限查询、多角色过滤与权限回填。
 *
 * @author ai-lawyers
 */
class SysPermissionServiceTest
{
    private SysPermissionService permissionService;

    private ISysRoleService roleService;

    private ISysMenuService menuService;

    @BeforeEach
    void setUp()
    {
        permissionService = new SysPermissionService();
        roleService = mock(ISysRoleService.class);
        menuService = mock(ISysMenuService.class);
        ReflectionTestUtils.setField(permissionService, "roleService", roleService);
        ReflectionTestUtils.setField(permissionService, "menuService", menuService);
    }

    @Test
    void getRolePermission_管理员直接返回admin()
    {
        Set<String> roles = permissionService.getRolePermission(user(1L));

        assertEquals(Collections.singleton("admin"), roles);
        verifyNoInteractions(roleService);
    }

    @Test
    void getRolePermission_普通用户查询角色权限()
    {
        when(roleService.selectRolePermissionByUserId(2L))
                .thenReturn(new HashSet<>(Arrays.asList("common", "lawyer")));

        Set<String> roles = permissionService.getRolePermission(user(2L));

        assertEquals(new HashSet<>(Arrays.asList("common", "lawyer")), roles);
    }

    @Test
    void getMenuPermission_管理员返回全部权限()
    {
        Set<String> perms = permissionService.getMenuPermission(user(1L));

        assertEquals(Collections.singleton("*:*:*"), perms);
        verifyNoInteractions(menuService);
    }

    @Test
    void getMenuPermission_无角色时按用户查询菜单权限()
    {
        SysUser user = user(2L);
        when(menuService.selectMenuPermsByUserId(2L)).thenReturn(Collections.singleton("system:user:list"));

        Set<String> perms = permissionService.getMenuPermission(user);

        assertEquals(Collections.singleton("system:user:list"), perms);
    }

    @Test
    void getMenuPermission_多角色过滤停用与管理角色并回填权限()
    {
        SysUser user = user(2L);
        SysRole normal = role(10L, UserConstants.ROLE_NORMAL);
        SysRole disabled = role(11L, "1");
        SysRole admin = role(1L, UserConstants.ROLE_NORMAL);
        user.setRoles(Arrays.asList(normal, disabled, admin));

        Set<String> normalPerms = new HashSet<>(Arrays.asList("system:user:list", "system:user:edit"));
        when(menuService.selectMenuPermsByRoleId(10L)).thenReturn(normalPerms);

        Set<String> perms = permissionService.getMenuPermission(user);

        assertEquals(normalPerms, perms);
        // 正常角色回填 permissions 供数据权限匹配
        assertEquals(normalPerms, normal.getPermissions());
        assertNull(disabled.getPermissions());
        assertNull(admin.getPermissions());
        verify(menuService).selectMenuPermsByRoleId(10L);
        verifyNoMoreInteractions(menuService);
    }

    @Test
    void getMenuPermission_空角色列表时按用户查询()
    {
        SysUser user = user(2L);
        user.setRoles(Collections.emptyList());
        when(menuService.selectMenuPermsByUserId(2L)).thenReturn(Collections.singleton("law:case:list"));

        Set<String> perms = permissionService.getMenuPermission(user);

        assertTrue(perms.contains("law:case:list"));
        assertEquals(1, perms.size());
    }

    /** 构造指定用户 ID 的用户 */
    private SysUser user(long userId)
    {
        SysUser user = new SysUser();
        user.setUserId(userId);
        return user;
    }

    /** 构造指定状态的角色 */
    private SysRole role(long roleId, String status)
    {
        SysRole role = new SysRole();
        role.setRoleId(roleId);
        role.setStatus(status);
        return role;
    }
}
