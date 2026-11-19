package ai.lawyers.web.controller.system;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
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
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import ai.lawyers.common.core.domain.AjaxResult;
import ai.lawyers.common.core.domain.entity.SysDept;
import ai.lawyers.common.core.domain.entity.SysUser;
import ai.lawyers.common.core.domain.model.LoginUser;
import ai.lawyers.common.utils.SecurityUtils;
import ai.lawyers.system.service.ISysDeptService;

/**
 * 部门管理接口单元测试
 *
 * @author ai-lawyers
 */
@ExtendWith(MockitoExtension.class)
class SysDeptControllerTest
{
    @Mock
    private ISysDeptService deptService;

    @InjectMocks
    private SysDeptController controller;

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

    private SysDept dept(Long deptId, String name)
    {
        SysDept dept = new SysDept();
        dept.setDeptId(deptId);
        dept.setDeptName(name);
        return dept;
    }

    @Test
    void 列表透传服务结果()
    {
        SysDept query = new SysDept();
        List<SysDept> depts = new ArrayList<SysDept>();
        when(deptService.selectDeptList(query)).thenReturn(depts);

        AjaxResult result = controller.list(query);

        assertSame(depts, result.get(AjaxResult.DATA_TAG));
    }

    @Test
    void 排除节点去掉自身与下级()
    {
        List<SysDept> depts = new ArrayList<SysDept>();
        SysDept self = dept(5L, "法务部");
        SysDept child = dept(6L, "诉讼组");
        child.setAncestors("0,5");
        SysDept other = dept(7L, "客服部");
        other.setAncestors("0,1");
        depts.add(self);
        depts.add(child);
        depts.add(other);
        when(deptService.selectDeptList(any(SysDept.class))).thenReturn(depts);

        AjaxResult result = controller.excludeChild(5L);

        @SuppressWarnings("unchecked")
        List<SysDept> rows = (List<SysDept>) result.get(AjaxResult.DATA_TAG);
        assertEquals(1, rows.size());
        assertEquals(7L, rows.get(0).getDeptId());
    }

    @Test
    void 详情先校验数据权限()
    {
        SysDept dept = dept(5L, "法务部");
        when(deptService.selectDeptById(5L)).thenReturn(dept);

        assertSame(dept, controller.getInfo(5L).get(AjaxResult.DATA_TAG));
        verify(deptService).checkDeptDataScope(5L);
    }

    @Test
    void 新增部门名称重复返回错误()
    {
        SysDept dept = dept(null, "法务部");
        when(deptService.checkDeptNameUnique(dept)).thenReturn(false);

        AjaxResult result = controller.add(dept);

        assertEquals(500, result.get(AjaxResult.CODE_TAG));
        assertEquals("新增部门'法务部'失败，部门名称已存在", result.get(AjaxResult.MSG_TAG));
        verify(deptService, never()).insertDept(any(SysDept.class));
    }

    @Test
    void 新增部门写入操作人()
    {
        SysDept dept = dept(null, "法务部");
        when(deptService.checkDeptNameUnique(dept)).thenReturn(true);
        when(deptService.insertDept(dept)).thenReturn(1);

        try (MockedStatic<SecurityUtils> mocked = mockStatic(SecurityUtils.class))
        {
            mocked.when(SecurityUtils::getLoginUser).thenReturn(loginUser("admin"));
            assertEquals(200, controller.add(dept).get(AjaxResult.CODE_TAG));
        }
        assertEquals("admin", dept.getCreateBy());
    }

    @Test
    void 修改部门校验上级与停用约束()
    {
        SysDept duplicate = dept(5L, "法务部");
        when(deptService.checkDeptNameUnique(duplicate)).thenReturn(false);

        SysDept selfParent = dept(5L, "法务部");
        selfParent.setParentId(5L);
        when(deptService.checkDeptNameUnique(selfParent)).thenReturn(true);

        SysDept disabled = dept(6L, "诉讼组");
        disabled.setParentId(1L);
        disabled.setStatus("1");
        when(deptService.checkDeptNameUnique(disabled)).thenReturn(true);
        when(deptService.selectNormalChildrenDeptById(6L)).thenReturn(2);

        assertEquals("修改部门'法务部'失败，部门名称已存在", controller.edit(duplicate).get(AjaxResult.MSG_TAG));
        assertEquals("修改部门'法务部'失败，上级部门不能是自己", controller.edit(selfParent).get(AjaxResult.MSG_TAG));
        assertEquals("该部门包含未停用的子部门！", controller.edit(disabled).get(AjaxResult.MSG_TAG));
    }

    @Test
    void 修改部门写入更新人()
    {
        SysDept dept = dept(5L, "法务部");
        dept.setParentId(1L);
        when(deptService.checkDeptNameUnique(dept)).thenReturn(true);
        when(deptService.updateDept(dept)).thenReturn(0);

        try (MockedStatic<SecurityUtils> mocked = mockStatic(SecurityUtils.class))
        {
            mocked.when(SecurityUtils::getLoginUser).thenReturn(loginUser("auditor"));
            assertEquals(500, controller.edit(dept).get(AjaxResult.CODE_TAG));
        }
        assertEquals("auditor", dept.getUpdateBy());
    }

    @Test
    void 删除部门拦截子部门与用户()
    {
        when(deptService.hasChildByDeptId(5L)).thenReturn(true);
        when(deptService.hasChildByDeptId(6L)).thenReturn(false);
        when(deptService.checkDeptExistUser(6L)).thenReturn(true);

        assertEquals(601, controller.remove(5L).get(AjaxResult.CODE_TAG));
        assertEquals(601, controller.remove(6L).get(AjaxResult.CODE_TAG));
        verify(deptService, never()).deleteDeptById(any());
    }

    @Test
    void 删除部门成功返回影响行数()
    {
        when(deptService.hasChildByDeptId(8L)).thenReturn(false);
        when(deptService.checkDeptExistUser(8L)).thenReturn(false);
        when(deptService.deleteDeptById(8L)).thenReturn(1);

        assertEquals(200, controller.remove(8L).get(AjaxResult.CODE_TAG));
        verify(deptService).checkDeptDataScope(8L);
    }
}
