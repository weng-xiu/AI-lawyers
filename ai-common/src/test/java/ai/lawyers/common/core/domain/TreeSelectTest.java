package ai.lawyers.common.core.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import ai.lawyers.common.core.domain.entity.SysDept;
import ai.lawyers.common.core.domain.entity.SysMenu;

/**
 * 树结构实体测试：部门/菜单转树节点、停用状态映射、子节点递归、空子节点前置条件。
 *
 * @author ai-lawyers
 */
class TreeSelectTest
{
    @Test
    void 由部门构造_正常部门不置灰并递归子节点()
    {
        SysDept dept = dept(1L, "司法局", "0");
        SysDept child = dept(2L, "法律援助中心", "0");
        SysDept grandChild = dept(3L, "咨询窗口", "1");
        child.setChildren(List.of(grandChild));
        dept.setChildren(List.of(child));

        TreeSelect tree = new TreeSelect(dept);

        assertEquals(Long.valueOf(1L), tree.getId());
        assertEquals("司法局", tree.getLabel());
        assertFalse(tree.isDisabled());
        assertEquals(1, tree.getChildren().size());
        TreeSelect childNode = tree.getChildren().get(0);
        assertEquals("法律援助中心", childNode.getLabel());
        assertEquals(1, childNode.getChildren().size());
        // 停用部门置灰
        assertTrue(childNode.getChildren().get(0).isDisabled());
    }

    @Test
    void 由部门构造_停用状态置灰()
    {
        TreeSelect tree = new TreeSelect(dept(9L, "历史部门", "1"));

        assertTrue(tree.isDisabled());
        assertTrue(tree.getChildren().isEmpty());
    }

    @Test
    void 由菜单构造_映射菜单标识与子菜单()
    {
        SysMenu menu = new SysMenu();
        menu.setMenuId(10L);
        menu.setMenuName("系统管理");
        SysMenu sub = new SysMenu();
        sub.setMenuId(11L);
        sub.setMenuName("用户管理");
        menu.setChildren(List.of(sub));

        TreeSelect tree = new TreeSelect(menu);

        assertEquals(Long.valueOf(10L), tree.getId());
        assertEquals("系统管理", tree.getLabel());
        assertFalse(tree.isDisabled());
        assertEquals("用户管理", tree.getChildren().get(0).getLabel());
        assertEquals(Long.valueOf(11L), tree.getChildren().get(0).getId());
    }

    @Test
    void 默认构造与setter()
    {
        TreeSelect tree = new TreeSelect();
        assertNull(tree.getId());
        assertNull(tree.getLabel());
        assertFalse(tree.isDisabled());
        assertNull(tree.getChildren());

        tree.setId(5L);
        tree.setLabel("根节点");
        tree.setDisabled(true);
        tree.setChildren(new ArrayList<>());

        assertEquals(Long.valueOf(5L), tree.getId());
        assertEquals("根节点", tree.getLabel());
        assertTrue(tree.isDisabled());
        assertTrue(tree.getChildren().isEmpty());
    }

    @Test
    void 部门子节点为空时构造抛空指针()
    {
        SysDept dept = dept(1L, "司法局", "0");
        dept.setChildren(null);

        assertThrows(NullPointerException.class, () -> new TreeSelect(dept));
    }

    /** 构造部门并设置名称与状态 */
    private SysDept dept(Long id, String name, String status)
    {
        SysDept dept = new SysDept();
        dept.setDeptId(id);
        dept.setDeptName(name);
        dept.setStatus(status);
        return dept;
    }
}
