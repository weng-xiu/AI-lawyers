package ai.lawyers.system.service.impl;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.MockitoAnnotations;
import org.springframework.test.util.ReflectionTestUtils;
import ai.lawyers.common.core.domain.entity.SysRole;
import ai.lawyers.common.exception.ServiceException;
import ai.lawyers.common.utils.SecurityUtils;
import ai.lawyers.common.utils.spring.SpringUtils;
import ai.lawyers.system.domain.SysRoleDept;
import ai.lawyers.system.domain.SysRoleMenu;
import ai.lawyers.system.domain.SysUserRole;
import ai.lawyers.system.mapper.SysRoleDeptMapper;
import ai.lawyers.system.mapper.SysRoleMapper;
import ai.lawyers.system.mapper.SysRoleMenuMapper;
import ai.lawyers.system.mapper.SysUserRoleMapper;

/**
 * 角色服务单测。
 *
 * <p>覆盖角色查询、用户角色勾选标记、权限字符拆分、名称/权限字符唯一性校验、
 * 超级管理员与数据权限校验、新增/修改/授权/删除角色及其菜单部门关联、
 * 已分配角色删除拦截，以及用户角色授权与取消授权。</p>
 *
 * @author ai-lawyers
 */
class SysRoleServiceImplTest
{
    private SysRoleServiceImpl service;

    @Mock private SysRoleMapper roleMapper;
    @Mock private SysRoleMenuMapper roleMenuMapper;
    @Mock private SysUserRoleMapper userRoleMapper;
    @Mock private SysRoleDeptMapper roleDeptMapper;

    @BeforeEach
    void setUp()
    {
        MockitoAnnotations.openMocks(this);
        service = new SysRoleServiceImpl();
        ReflectionTestUtils.setField(service, "roleMapper", roleMapper);
        ReflectionTestUtils.setField(service, "roleMenuMapper", roleMenuMapper);
        ReflectionTestUtils.setField(service, "userRoleMapper", userRoleMapper);
        ReflectionTestUtils.setField(service, "roleDeptMapper", roleDeptMapper);
    }

    private static SysRole role(Long roleId, String roleName)
    {
        SysRole role = new SysRole(roleId);
        role.setRoleName(roleName);
        return role;
    }

    // ---------------- 查询 ----------------

    @Test
    void selectRoleList_delegates()
    {
        SysRole query = new SysRole();
        List<SysRole> list = Collections.singletonList(role(2L, "客服"));
        when(roleMapper.selectRoleList(query)).thenReturn(list);

        assertSame(list, service.selectRoleList(query));
    }

    @Test
    void selectRolesByUserId_marksAssignedRoles()
    {
        SysRole r1 = role(1L, "管理员");
        SysRole r2 = role(2L, "客服");
        when(roleMapper.selectRolePermissionByUserId(7L)).thenReturn(Collections.singletonList(r2));
        try (MockedStatic<SpringUtils> spring = mockStatic(SpringUtils.class))
        {
            spring.when(() -> SpringUtils.getAopProxy(service)).thenReturn(service);
            when(roleMapper.selectRoleList(any())).thenReturn(Arrays.asList(r1, r2));

            List<SysRole> roles = service.selectRolesByUserId(7L);

            assertTrue(roles.get(1).isFlag());
            assertFalse(roles.get(0).isFlag());
        }
    }

    @Test
    void selectRolePermissionByUserId_splitsKeysAndSkipsNull()
    {
        SysRole r1 = new SysRole();
        r1.setRoleKey("a,b");
        SysRole r2 = new SysRole();
        r2.setRoleKey("c");
        when(roleMapper.selectRolePermissionByUserId(7L)).thenReturn(Arrays.asList(r1, null, r2));

        Set<String> perms = service.selectRolePermissionByUserId(7L);

        assertEquals(3, perms.size());
        assertTrue(perms.containsAll(Arrays.asList("a", "b", "c")));
    }

    @Test
    void selectRoleAll_queriesThroughAopProxy()
    {
        List<SysRole> all = Collections.singletonList(role(2L, "客服"));
        try (MockedStatic<SpringUtils> spring = mockStatic(SpringUtils.class))
        {
            spring.when(() -> SpringUtils.getAopProxy(service)).thenReturn(service);
            when(roleMapper.selectRoleList(any())).thenReturn(all);

            assertSame(all, service.selectRoleAll());
        }
    }

    @Test
    void selectRoleListByUserId_delegates()
    {
        List<Long> ids = Collections.singletonList(2L);
        when(roleMapper.selectRoleListByUserId(7L)).thenReturn(ids);

        assertSame(ids, service.selectRoleListByUserId(7L));
    }

    @Test
    void selectRoleById_delegates()
    {
        SysRole r = role(2L, "客服");
        when(roleMapper.selectRoleById(2L)).thenReturn(r);

        assertSame(r, service.selectRoleById(2L));
    }

    // ---------------- 唯一性校验 ----------------

    @Test
    void checkRoleNameUnique_whenNotExists_returnsUnique()
    {
        when(roleMapper.checkRoleNameUnique("客服")).thenReturn(null);

        assertTrue(service.checkRoleNameUnique(role(null, "客服")));
    }

    @Test
    void checkRoleNameUnique_whenSameRoleId_returnsUnique()
    {
        when(roleMapper.checkRoleNameUnique("客服")).thenReturn(role(5L, "客服"));

        assertTrue(service.checkRoleNameUnique(role(5L, "客服")));
    }

    @Test
    void checkRoleNameUnique_whenOtherRoleId_returnsNotUnique()
    {
        when(roleMapper.checkRoleNameUnique("客服")).thenReturn(role(6L, "客服"));

        assertFalse(service.checkRoleNameUnique(role(5L, "客服")));
    }

    @Test
    void checkRoleKeyUnique_whenOtherRoleId_returnsNotUnique()
    {
        SysRole db = new SysRole(6L);
        db.setRoleKey("cs");
        SysRole input = new SysRole(5L);
        input.setRoleKey("cs");
        when(roleMapper.checkRoleKeyUnique("cs")).thenReturn(db);

        assertFalse(service.checkRoleKeyUnique(input));
    }

    @Test
    void checkRoleKeyUnique_whenNotExists_returnsUnique()
    {
        SysRole input = new SysRole(null);
        input.setRoleKey("cs");
        when(roleMapper.checkRoleKeyUnique("cs")).thenReturn(null);

        assertTrue(service.checkRoleKeyUnique(input));
    }

    // ---------------- 权限校验 ----------------

    @Test
    void checkRoleAllowed_adminRole_throws()
    {
        assertThrows(ServiceException.class, () -> service.checkRoleAllowed(new SysRole(1L)));
    }

    @Test
    void checkRoleAllowed_normalOrNewRole_passes()
    {
        assertDoesNotThrow(() -> service.checkRoleAllowed(new SysRole(2L)));
        assertDoesNotThrow(() -> service.checkRoleAllowed(new SysRole(null)));
    }

    @Test
    void checkRoleDataScope_currentAdmin_skipsQuery()
    {
        try (MockedStatic<SecurityUtils> security = mockStatic(SecurityUtils.class))
        {
            security.when(SecurityUtils::getUserId).thenReturn(1L);

            assertDoesNotThrow(() -> service.checkRoleDataScope(2L, 3L));
        }
        verify(roleMapper, never()).selectRoleList(any());
    }

    @Test
    void checkRoleDataScope_visible_passes()
    {
        try (MockedStatic<SecurityUtils> security = mockStatic(SecurityUtils.class);
             MockedStatic<SpringUtils> spring = mockStatic(SpringUtils.class))
        {
            security.when(SecurityUtils::getUserId).thenReturn(2L);
            spring.when(() -> SpringUtils.getAopProxy(service)).thenReturn(service);
            when(roleMapper.selectRoleList(any())).thenReturn(Collections.singletonList(role(2L, "客服")));

            assertDoesNotThrow(() -> service.checkRoleDataScope(2L));
        }
    }

    @Test
    void checkRoleDataScope_notVisible_throws()
    {
        try (MockedStatic<SecurityUtils> security = mockStatic(SecurityUtils.class);
             MockedStatic<SpringUtils> spring = mockStatic(SpringUtils.class))
        {
            security.when(SecurityUtils::getUserId).thenReturn(2L);
            spring.when(() -> SpringUtils.getAopProxy(service)).thenReturn(service);
            when(roleMapper.selectRoleList(any())).thenReturn(Collections.emptyList());

            ServiceException ex = assertThrows(ServiceException.class, () -> service.checkRoleDataScope(9L));
            assertEquals("没有权限访问角色数据！", ex.getMessage());
        }
    }

    @Test
    void countUserRoleByRoleId_delegates()
    {
        when(userRoleMapper.countUserRoleByRoleId(2L)).thenReturn(3);

        assertEquals(3, service.countUserRoleByRoleId(2L));
    }

    // ---------------- 新增/修改 ----------------

    @Test
    void insertRole_insertsRoleAndMenus()
    {
        SysRole r = role(2L, "客服");
        r.setMenuIds(new Long[] { 10L, 11L });
        when(roleMenuMapper.batchRoleMenu(any())).thenReturn(2);

        assertEquals(2, service.insertRole(r));

        verify(roleMapper).insertRole(r);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<SysRoleMenu>> captor = ArgumentCaptor.forClass(List.class);
        verify(roleMenuMapper).batchRoleMenu(captor.capture());
        assertEquals(2, captor.getValue().size());
        assertEquals(2L, captor.getValue().get(0).getRoleId());
        assertEquals(10L, captor.getValue().get(0).getMenuId());
    }

    @Test
    void insertRole_withoutMenus_returnsDefaultOne()
    {
        SysRole r = role(2L, "客服");
        r.setMenuIds(new Long[0]);

        assertEquals(1, service.insertRole(r));

        verify(roleMenuMapper, never()).batchRoleMenu(any());
    }

    @Test
    void updateRole_updatesAndRebuildsMenus()
    {
        SysRole r = role(2L, "客服");
        r.setMenuIds(new Long[] { 10L });
        when(roleMenuMapper.batchRoleMenu(any())).thenReturn(1);

        assertEquals(1, service.updateRole(r));

        verify(roleMapper).updateRole(r);
        verify(roleMenuMapper).deleteRoleMenuByRoleId(2L);
    }

    @Test
    void updateRoleStatus_delegates()
    {
        SysRole r = role(2L, "客服");
        when(roleMapper.updateRole(r)).thenReturn(1);

        assertEquals(1, service.updateRoleStatus(r));
    }

    @Test
    void authDataScope_updatesAndRebuildsDepts()
    {
        SysRole r = role(2L, "客服");
        r.setDeptIds(new Long[] { 100L, 101L });
        when(roleDeptMapper.batchRoleDept(any())).thenReturn(2);

        assertEquals(2, service.authDataScope(r));

        verify(roleMapper).updateRole(r);
        verify(roleDeptMapper).deleteRoleDeptByRoleId(2L);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<SysRoleDept>> captor = ArgumentCaptor.forClass(List.class);
        verify(roleDeptMapper).batchRoleDept(captor.capture());
        assertEquals(2, captor.getValue().size());
    }

    @Test
    void authDataScope_withoutDepts_returnsDefaultOne()
    {
        SysRole r = role(2L, "客服");
        r.setDeptIds(new Long[0]);

        assertEquals(1, service.authDataScope(r));

        verify(roleDeptMapper, never()).batchRoleDept(any());
    }

    // ---------------- 删除 ----------------

    @Test
    void deleteRoleById_removesRelationsThenRole()
    {
        when(roleMapper.deleteRoleById(2L)).thenReturn(1);

        assertEquals(1, service.deleteRoleById(2L));

        verify(roleMenuMapper).deleteRoleMenuByRoleId(2L);
        verify(roleDeptMapper).deleteRoleDeptByRoleId(2L);
    }

    @Test
    void deleteRoleByIds_removesRelationsAndRoles()
    {
        Long[] ids = new Long[] { 2L, 3L };
        when(roleMapper.selectRoleById(2L)).thenReturn(role(2L, "客服"));
        when(roleMapper.selectRoleById(3L)).thenReturn(role(3L, "坐席"));
        when(userRoleMapper.countUserRoleByRoleId(2L)).thenReturn(0);
        when(userRoleMapper.countUserRoleByRoleId(3L)).thenReturn(0);
        when(roleMapper.deleteRoleByIds(ids)).thenReturn(2);

        int rows;

        try (MockedStatic<SecurityUtils> security = mockStatic(SecurityUtils.class))
        {
            security.when(SecurityUtils::getUserId).thenReturn(1L);
            rows = service.deleteRoleByIds(ids);
        }

        assertEquals(2, rows);
        verify(roleMenuMapper).deleteRoleMenu(ids);
        verify(roleDeptMapper).deleteRoleDept(ids);
    }

    @Test
    void deleteRoleByIds_assignedRole_throws()
    {
        when(roleMapper.selectRoleById(2L)).thenReturn(role(2L, "客服"));
        when(userRoleMapper.countUserRoleByRoleId(2L)).thenReturn(3);

        try (MockedStatic<SecurityUtils> security = mockStatic(SecurityUtils.class))
        {
            security.when(SecurityUtils::getUserId).thenReturn(1L);

            ServiceException ex = assertThrows(ServiceException.class,
                () -> service.deleteRoleByIds(new Long[] { 2L }));
            assertEquals("客服已分配,不能删除", ex.getMessage());
        }
        verify(roleMapper, never()).deleteRoleByIds(any());
    }

    // ---------------- 用户角色授权 ----------------

    @Test
    void deleteAuthUser_delegates()
    {
        SysUserRole ur = new SysUserRole();
        when(userRoleMapper.deleteUserRoleInfo(ur)).thenReturn(1);

        assertEquals(1, service.deleteAuthUser(ur));
    }

    @Test
    void deleteAuthUsers_delegates()
    {
        Long[] userIds = new Long[] { 5L, 6L };
        when(userRoleMapper.deleteUserRoleInfos(2L, userIds)).thenReturn(2);

        assertEquals(2, service.deleteAuthUsers(2L, userIds));
    }

    @Test
    void insertAuthUsers_batchesUserRoles()
    {
        Long[] userIds = new Long[] { 5L, 6L };
        when(userRoleMapper.batchUserRole(any())).thenReturn(2);

        assertEquals(2, service.insertAuthUsers(2L, userIds));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<SysUserRole>> captor = ArgumentCaptor.forClass(List.class);
        verify(userRoleMapper).batchUserRole(captor.capture());
        assertEquals(2, captor.getValue().size());
        assertEquals(2L, captor.getValue().get(0).getRoleId());
        assertEquals(5L, captor.getValue().get(0).getUserId());
    }
}
