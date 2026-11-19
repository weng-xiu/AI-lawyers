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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import ai.lawyers.common.core.domain.AjaxResult;
import ai.lawyers.common.core.domain.TreeSelect;
import ai.lawyers.common.core.domain.entity.SysMenu;
import ai.lawyers.common.core.domain.entity.SysUser;
import ai.lawyers.common.core.domain.model.LoginUser;
import ai.lawyers.common.utils.SecurityUtils;
import ai.lawyers.system.service.ISysMenuService;

/**
 * 菜单管理接口单元测试
 *
 * @author ai-lawyers
 */
@ExtendWith(MockitoExtension.class)
class SysMenuControllerTest
{
    @Mock
    private ISysMenuService menuService;

    @InjectMocks
    private SysMenuController controller;

    private LoginUser loginUser(String username)
    {
        SysUser user = new SysUser();
        user.setUserName(username);
        return new LoginUser(1L, 2L, user, null);
    }

    private SysMenu menu(Long menuId, String name)
    {
        SysMenu menu = new SysMenu();
        menu.setMenuId(menuId);
        menu.setMenuName(name);
        menu.setIsFrame("1");
        menu.setPath("system");
        return menu;
    }

    @Test
    void 列表按当前用户查询()
    {
        SysMenu query = new SysMenu();
        List<SysMenu> menus = new ArrayList<SysMenu>();
        when(menuService.selectMenuList(query, 1L)).thenReturn(menus);

        try (MockedStatic<SecurityUtils> mocked = mockStatic(SecurityUtils.class))
        {
            mocked.when(SecurityUtils::getLoginUser).thenReturn(loginUser("admin"));
            assertSame(menus, controller.list(query).get(AjaxResult.DATA_TAG));
        }
    }

    @Test
    void 详情与下拉树透传服务结果()
    {
        SysMenu menu = menu(5L, "系统管理");
        List<SysMenu> menus = new ArrayList<SysMenu>();
        when(menuService.selectMenuById(5L)).thenReturn(menu);
        when(menuService.selectMenuList(any(SysMenu.class), any())).thenReturn(menus);
        when(menuService.buildMenuTreeSelect(menus)).thenReturn(new ArrayList<TreeSelect>());

        try (MockedStatic<SecurityUtils> mocked = mockStatic(SecurityUtils.class))
        {
            mocked.when(SecurityUtils::getLoginUser).thenReturn(loginUser("admin"));
            assertSame(menu, controller.getInfo(5L).get(AjaxResult.DATA_TAG));
            assertEquals(0, ((List<?>) controller.treeselect(new SysMenu()).get(AjaxResult.DATA_TAG)).size());
        }
    }

    @Test
    void 角色菜单树返回选中与菜单()
    {
        List<SysMenu> menus = new ArrayList<SysMenu>();
        List<Long> checkedKeys = new ArrayList<Long>();
        when(menuService.selectMenuList(1L)).thenReturn(menus);
        when(menuService.selectMenuListByRoleId(3L)).thenReturn(checkedKeys);
        when(menuService.buildMenuTreeSelect(menus)).thenReturn(new ArrayList<TreeSelect>());

        try (MockedStatic<SecurityUtils> mocked = mockStatic(SecurityUtils.class))
        {
            mocked.when(SecurityUtils::getLoginUser).thenReturn(loginUser("admin"));
            AjaxResult result = controller.roleMenuTreeselect(3L);

            assertSame(checkedKeys, result.get("checkedKeys"));
            assertEquals(0, ((List<?>) result.get("menus")).size());
        }
    }

    @Test
    void 新增菜单校验名称与外链()
    {
        SysMenu duplicate = menu(null, "系统管理");
        when(menuService.checkMenuNameUnique(duplicate)).thenReturn(false);

        SysMenu badFrame = menu(null, "外部系统");
        badFrame.setIsFrame("0");
        badFrame.setPath("www.example.com");
        when(menuService.checkMenuNameUnique(badFrame)).thenReturn(true);

        assertEquals("新增菜单'系统管理'失败，菜单名称已存在", controller.add(duplicate).get(AjaxResult.MSG_TAG));
        assertEquals("新增菜单'外部系统'失败，地址必须以http(s)://开头",
                controller.add(badFrame).get(AjaxResult.MSG_TAG));
        verify(menuService, never()).insertMenu(any(SysMenu.class));
    }

    @Test
    void 新增菜单写入操作人()
    {
        SysMenu menu = menu(null, "合同管理");
        when(menuService.checkMenuNameUnique(menu)).thenReturn(true);
        when(menuService.insertMenu(menu)).thenReturn(1);

        try (MockedStatic<SecurityUtils> mocked = mockStatic(SecurityUtils.class))
        {
            mocked.when(SecurityUtils::getLoginUser).thenReturn(loginUser("admin"));
            assertEquals(200, controller.add(menu).get(AjaxResult.CODE_TAG));
        }
        assertEquals("admin", menu.getCreateBy());
    }

    @Test
    void 修改菜单校验自己为上级()
    {
        SysMenu menu = menu(5L, "系统管理");
        menu.setParentId(5L);
        when(menuService.checkMenuNameUnique(menu)).thenReturn(true);

        assertEquals("修改菜单'系统管理'失败，上级菜单不能选择自己",
                controller.edit(menu).get(AjaxResult.MSG_TAG));
        verify(menuService, never()).updateMenu(any(SysMenu.class));
    }

    @Test
    void 修改菜单写入更新人()
    {
        SysMenu menu = menu(5L, "系统管理");
        menu.setParentId(1L);
        when(menuService.checkMenuNameUnique(menu)).thenReturn(true);
        when(menuService.updateMenu(menu)).thenReturn(0);

        try (MockedStatic<SecurityUtils> mocked = mockStatic(SecurityUtils.class))
        {
            mocked.when(SecurityUtils::getLoginUser).thenReturn(loginUser("admin"));
            assertEquals(500, controller.edit(menu).get(AjaxResult.CODE_TAG));
        }
        assertEquals("admin", menu.getUpdateBy());
    }

    @Test
    void 删除菜单拦截子菜单与已分配()
    {
        when(menuService.hasChildByMenuId(5L)).thenReturn(true);
        when(menuService.hasChildByMenuId(6L)).thenReturn(false);
        when(menuService.checkMenuExistRole(6L)).thenReturn(true);
        when(menuService.hasChildByMenuId(7L)).thenReturn(false);
        when(menuService.checkMenuExistRole(7L)).thenReturn(false);
        when(menuService.deleteMenuById(7L)).thenReturn(1);

        assertEquals(601, controller.remove(5L).get(AjaxResult.CODE_TAG));
        assertEquals(601, controller.remove(6L).get(AjaxResult.CODE_TAG));
        assertEquals(200, controller.remove(7L).get(AjaxResult.CODE_TAG));
    }
}
