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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.MockitoAnnotations;
import org.springframework.test.util.ReflectionTestUtils;
import ai.lawyers.common.core.domain.TreeSelect;
import ai.lawyers.common.core.domain.entity.SysDept;
import ai.lawyers.common.core.domain.entity.SysRole;
import ai.lawyers.common.exception.ServiceException;
import ai.lawyers.common.utils.SecurityUtils;
import ai.lawyers.common.utils.spring.SpringUtils;
import ai.lawyers.system.mapper.SysDeptMapper;
import ai.lawyers.system.mapper.SysRoleMapper;

/**
 * 部门服务单测。
 *
 * <p>覆盖部门查询、部门树/下拉树构建与递归子节点、角色部门严格模式查询、
 * 名称唯一性校验、数据权限校验、新增部门祖先链与停用父节点拦截、
 * 修改部门时子部门祖先链迁移与父级状态启用、以及删除委托。</p>
 *
 * @author ai-lawyers
 */
class SysDeptServiceImplTest
{
    private SysDeptServiceImpl service;

    @Mock private SysDeptMapper deptMapper;
    @Mock private SysRoleMapper roleMapper;

    @BeforeEach
    void setUp()
    {
        MockitoAnnotations.openMocks(this);
        service = new SysDeptServiceImpl();
        ReflectionTestUtils.setField(service, "deptMapper", deptMapper);
        ReflectionTestUtils.setField(service, "roleMapper", roleMapper);
    }

    private static SysDept dept(Long deptId, Long parentId, String name)
    {
        SysDept dept = new SysDept();
        dept.setDeptId(deptId);
        dept.setParentId(parentId);
        dept.setDeptName(name);
        return dept;
    }

    // ---------------- 查询 ----------------

    @Test
    void selectDeptList_delegates()
    {
        SysDept query = new SysDept();
        List<SysDept> list = Collections.singletonList(dept(100L, 0L, "总公司"));
        when(deptMapper.selectDeptList(query)).thenReturn(list);

        assertSame(list, service.selectDeptList(query));
    }

    @Test
    void selectDeptTreeList_buildsTreeThroughAopProxy()
    {
        SysDept root = dept(100L, 0L, "总公司");
        SysDept child = dept(101L, 100L, "客服部");
        try (MockedStatic<SpringUtils> spring = mockStatic(SpringUtils.class))
        {
            spring.when(() -> SpringUtils.getAopProxy(service)).thenReturn(service);
            when(deptMapper.selectDeptList(any())).thenReturn(Arrays.asList(root, child));

            List<TreeSelect> tree = service.selectDeptTreeList(new SysDept());

            assertEquals(1, tree.size());
            assertEquals("总公司", tree.get(0).getLabel());
            assertEquals(1, tree.get(0).getChildren().size());
            assertEquals("客服部", tree.get(0).getChildren().get(0).getLabel());
        }
    }

    @Test
    void buildDeptTree_linksChildrenRecursively()
    {
        SysDept root = dept(100L, 0L, "总公司");
        SysDept mid = dept(101L, 100L, "客服部");
        SysDept leaf = dept(102L, 101L, "一组");

        List<SysDept> tree = service.buildDeptTree(Arrays.asList(root, mid, leaf));

        assertEquals(1, tree.size());
        assertEquals(1, tree.get(0).getChildren().size());
        assertEquals(1, tree.get(0).getChildren().get(0).getChildren().size());
    }

    @Test
    void buildDeptTree_noRoot_returnsOriginalList()
    {
        SysDept a = dept(1L, 2L, "A");
        SysDept b = dept(2L, 1L, "B");
        List<SysDept> depts = Arrays.asList(a, b);

        List<SysDept> tree = service.buildDeptTree(depts);

        assertSame(depts, tree);
    }

    @Test
    void buildDeptTreeSelect_mapsDepts()
    {
        SysDept root = dept(100L, 0L, "总公司");

        List<TreeSelect> tree = service.buildDeptTreeSelect(Collections.singletonList(root));

        assertEquals(1, tree.size());
        assertEquals(100L, tree.get(0).getId());
        assertEquals("总公司", tree.get(0).getLabel());
    }

    @Test
    void selectDeptListByRoleId_passesStrictlyFlag()
    {
        SysRole role = new SysRole(2L);
        role.setDeptCheckStrictly(true);
        List<Long> ids = Arrays.asList(100L, 101L);
        when(roleMapper.selectRoleById(2L)).thenReturn(role);
        when(deptMapper.selectDeptListByRoleId(2L, true)).thenReturn(ids);

        assertSame(ids, service.selectDeptListByRoleId(2L));
    }

    @Test
    void selectDeptById_delegates()
    {
        SysDept d = dept(100L, 0L, "总公司");
        when(deptMapper.selectDeptById(100L)).thenReturn(d);

        assertSame(d, service.selectDeptById(100L));
    }

    @Test
    void selectNormalChildrenDeptById_delegates()
    {
        when(deptMapper.selectNormalChildrenDeptById(100L)).thenReturn(2);

        assertEquals(2, service.selectNormalChildrenDeptById(100L));
    }

    // ---------------- 存在性校验 ----------------

    @Test
    void hasChildByDeptId_returnsWhetherPositive()
    {
        when(deptMapper.hasChildByDeptId(100L)).thenReturn(1, 0);

        assertTrue(service.hasChildByDeptId(100L));
        assertFalse(service.hasChildByDeptId(100L));
    }

    @Test
    void checkDeptExistUser_returnsWhetherPositive()
    {
        when(deptMapper.checkDeptExistUser(100L)).thenReturn(2, 0);

        assertTrue(service.checkDeptExistUser(100L));
        assertFalse(service.checkDeptExistUser(100L));
    }

    // ---------------- 唯一性校验 ----------------

    @Test
    void checkDeptNameUnique_whenNotExists_returnsUnique()
    {
        when(deptMapper.checkDeptNameUnique("客服部", 100L)).thenReturn(null);

        assertTrue(service.checkDeptNameUnique(dept(null, 100L, "客服部")));
    }

    @Test
    void checkDeptNameUnique_whenSameDeptId_returnsUnique()
    {
        when(deptMapper.checkDeptNameUnique("客服部", 100L)).thenReturn(dept(101L, 100L, "客服部"));

        assertTrue(service.checkDeptNameUnique(dept(101L, 100L, "客服部")));
    }

    @Test
    void checkDeptNameUnique_whenOtherDeptId_returnsNotUnique()
    {
        when(deptMapper.checkDeptNameUnique("客服部", 100L)).thenReturn(dept(102L, 100L, "客服部"));

        assertFalse(service.checkDeptNameUnique(dept(101L, 100L, "客服部")));
    }

    // ---------------- 数据权限 ----------------

    @Test
    void checkDeptDataScope_nullDeptId_skips()
    {
        try (MockedStatic<SecurityUtils> security = mockStatic(SecurityUtils.class))
        {
            security.when(SecurityUtils::getUserId).thenReturn(2L);

            assertDoesNotThrow(() -> service.checkDeptDataScope(null));
        }
        verify(deptMapper, never()).selectDeptList(any());
    }

    @Test
    void checkDeptDataScope_currentAdmin_skipsQuery()
    {
        try (MockedStatic<SecurityUtils> security = mockStatic(SecurityUtils.class))
        {
            security.when(SecurityUtils::getUserId).thenReturn(1L);

            assertDoesNotThrow(() -> service.checkDeptDataScope(100L));
        }
        verify(deptMapper, never()).selectDeptList(any());
    }

    @Test
    void checkDeptDataScope_visible_passes()
    {
        try (MockedStatic<SecurityUtils> security = mockStatic(SecurityUtils.class);
             MockedStatic<SpringUtils> spring = mockStatic(SpringUtils.class))
        {
            security.when(SecurityUtils::getUserId).thenReturn(2L);
            spring.when(() -> SpringUtils.getAopProxy(service)).thenReturn(service);
            when(deptMapper.selectDeptList(any())).thenReturn(Collections.singletonList(dept(100L, 0L, "总公司")));

            assertDoesNotThrow(() -> service.checkDeptDataScope(100L));
        }
    }

    @Test
    void checkDeptDataScope_notVisible_throws()
    {
        try (MockedStatic<SecurityUtils> security = mockStatic(SecurityUtils.class);
             MockedStatic<SpringUtils> spring = mockStatic(SpringUtils.class))
        {
            security.when(SecurityUtils::getUserId).thenReturn(2L);
            spring.when(() -> SpringUtils.getAopProxy(service)).thenReturn(service);
            when(deptMapper.selectDeptList(any())).thenReturn(Collections.emptyList());

            ServiceException ex = assertThrows(ServiceException.class, () -> service.checkDeptDataScope(999L));
            assertEquals("没有权限访问部门数据！", ex.getMessage());
        }
    }

    // ---------------- 新增 ----------------

    @Test
    void insertDept_normalParent_buildsAncestors()
    {
        SysDept parent = dept(100L, 0L, "总公司");
        parent.setStatus("0");
        parent.setAncestors("0");
        SysDept child = dept(null, 100L, "客服部");
        when(deptMapper.selectDeptById(100L)).thenReturn(parent);
        when(deptMapper.insertDept(child)).thenReturn(1);

        assertEquals(1, service.insertDept(child));
        assertEquals("0,100", child.getAncestors());
    }

    @Test
    void insertDept_disabledParent_throws()
    {
        SysDept parent = dept(100L, 0L, "总公司");
        parent.setStatus("1");
        when(deptMapper.selectDeptById(100L)).thenReturn(parent);

        ServiceException ex = assertThrows(ServiceException.class,
            () -> service.insertDept(dept(null, 100L, "客服部")));
        assertEquals("部门停用，不允许新增", ex.getMessage());
        verify(deptMapper, never()).insertDept(any());
    }

    // ---------------- 修改 ----------------

    @Test
    void updateDept_movesChildrenAndEnablesParents()
    {
        SysDept newParent = dept(200L, 0L, "分公司");
        newParent.setAncestors("0");
        SysDept oldDept = dept(101L, 100L, "客服部");
        oldDept.setAncestors("0,100");
        SysDept child = dept(102L, 101L, "一组");
        child.setAncestors("0,100,101");
        SysDept dept = dept(101L, 200L, "客服部");
        dept.setStatus("0");
        when(deptMapper.selectDeptById(200L)).thenReturn(newParent);
        when(deptMapper.selectDeptById(101L)).thenReturn(oldDept);
        when(deptMapper.selectChildrenDeptById(101L)).thenReturn(Collections.singletonList(child));
        when(deptMapper.updateDept(dept)).thenReturn(1);

        assertEquals(1, service.updateDept(dept));

        assertEquals("0,200", dept.getAncestors());
        assertEquals("0,200,101", child.getAncestors());
        verify(deptMapper).updateDeptChildren(any());
        ArgumentCaptor<Long[]> idsCaptor = ArgumentCaptor.forClass(Long[].class);
        verify(deptMapper).updateDeptStatusNormal(idsCaptor.capture());
        assertEquals(Arrays.asList(0L, 200L), Arrays.asList(idsCaptor.getValue()));
    }

    @Test
    void updateDept_disabledStatus_doesNotEnableParents()
    {
        SysDept newParent = dept(200L, 0L, "分公司");
        newParent.setAncestors("0");
        SysDept oldDept = dept(101L, 100L, "客服部");
        oldDept.setAncestors("0,100");
        SysDept dept = dept(101L, 200L, "客服部");
        dept.setStatus("1");
        when(deptMapper.selectDeptById(200L)).thenReturn(newParent);
        when(deptMapper.selectDeptById(101L)).thenReturn(oldDept);
        when(deptMapper.selectChildrenDeptById(101L)).thenReturn(Collections.emptyList());
        when(deptMapper.updateDept(dept)).thenReturn(1);

        assertEquals(1, service.updateDept(dept));

        verify(deptMapper, never()).updateDeptChildren(any());
        verify(deptMapper, never()).updateDeptStatusNormal(any());
    }

    @Test
    void updateDept_topLevelAncestors_doesNotEnableParents()
    {
        SysDept dept = dept(100L, 0L, "总公司");
        dept.setStatus("0");
        dept.setAncestors("0");
        when(deptMapper.selectDeptById(0L)).thenReturn(null);
        when(deptMapper.selectDeptById(100L)).thenReturn(null);
        when(deptMapper.updateDept(dept)).thenReturn(1);

        assertEquals(1, service.updateDept(dept));

        verify(deptMapper, never()).updateDeptStatusNormal(any());
    }

    @Test
    void updateDept_missingParentOrOld_skipsChildrenUpdate()
    {
        SysDept dept = dept(101L, 200L, "客服部");
        dept.setStatus("1");
        when(deptMapper.selectDeptById(200L)).thenReturn(null);
        when(deptMapper.selectDeptById(101L)).thenReturn(null);
        when(deptMapper.updateDept(dept)).thenReturn(1);

        assertEquals(1, service.updateDept(dept));

        verify(deptMapper, never()).selectChildrenDeptById(any());
        verify(deptMapper, never()).updateDeptChildren(any());
    }

    @Test
    void updateDeptChildren_replacesOldAncestors()
    {
        SysDept child = dept(102L, 101L, "一组");
        child.setAncestors("0,100,101");
        when(deptMapper.selectChildrenDeptById(101L)).thenReturn(Collections.singletonList(child));

        service.updateDeptChildren(101L, "0,200", "0,100");

        assertEquals("0,200,101", child.getAncestors());
        verify(deptMapper).updateDeptChildren(Collections.singletonList(child));
    }

    @Test
    void updateDeptChildren_noChildren_skipsUpdate()
    {
        when(deptMapper.selectChildrenDeptById(101L)).thenReturn(Collections.emptyList());

        service.updateDeptChildren(101L, "0,200", "0,100");

        verify(deptMapper, never()).updateDeptChildren(any());
    }

    // ---------------- 删除 ----------------

    @Test
    void deleteDeptById_delegates()
    {
        when(deptMapper.deleteDeptById(100L)).thenReturn(1);

        assertEquals(1, service.deleteDeptById(100L));
    }
}
