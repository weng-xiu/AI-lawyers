package ai.lawyers.common.core.domain.entity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * 菜单实体测试：默认子菜单与路由字段读写。
 *
 * @author ai-lawyers
 */
class SysMenuTest
{
    @Test
    void 默认子菜单为空列表()
    {
        assertTrue(new SysMenu().getChildren().isEmpty());
    }

    @Test
    void 字段读写()
    {
        SysMenu menu = new SysMenu();
        menu.setMenuId(1L);
        menu.setMenuName("用户管理");
        menu.setParentName("系统管理");
        menu.setParentId(0L);
        menu.setOrderNum(1);
        menu.setPath("user");
        menu.setComponent("system/user/index");
        menu.setQuery("{\"id\":1}");
        menu.setRouteName("User");
        menu.setIsFrame("1");
        menu.setIsCache("0");
        menu.setMenuType("C");
        menu.setVisible("0");
        menu.setStatus("0");
        menu.setPerms("system:user:list");
        menu.setIcon("user");

        assertEquals(1L, menu.getMenuId());
        assertEquals("用户管理", menu.getMenuName());
        assertEquals("系统管理", menu.getParentName());
        assertEquals(0L, menu.getParentId());
        assertEquals(1, menu.getOrderNum());
        assertEquals("user", menu.getPath());
        assertEquals("system/user/index", menu.getComponent());
        assertEquals("{\"id\":1}", menu.getQuery());
        assertEquals("User", menu.getRouteName());
        assertEquals("1", menu.getIsFrame());
        assertEquals("0", menu.getIsCache());
        assertEquals("C", menu.getMenuType());
        assertEquals("0", menu.getVisible());
        assertEquals("0", menu.getStatus());
        assertEquals("system:user:list", menu.getPerms());
        assertEquals("user", menu.getIcon());
    }

    @Test
    void 子菜单可替换且toString包含名称()
    {
        SysMenu menu = new SysMenu();
        menu.setMenuName("系统管理");
        menu.setChildren(List.of(new SysMenu()));

        assertEquals(1, menu.getChildren().size());
        assertTrue(menu.toString().contains("系统管理"));
    }
}
