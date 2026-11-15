package ai.lawyers.system.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.test.util.ReflectionTestUtils;
import ai.lawyers.common.constant.UserConstants;
import ai.lawyers.common.core.domain.TreeSelect;
import ai.lawyers.common.core.domain.entity.SysMenu;
import ai.lawyers.common.core.domain.entity.SysRole;
import ai.lawyers.system.domain.vo.RouterVo;
import ai.lawyers.system.mapper.SysMenuMapper;
import ai.lawyers.system.mapper.SysRoleMapper;
import ai.lawyers.system.mapper.SysRoleMenuMapper;

/**
 * 菜单服务单测。
 *
 * <p>覆盖管理员/普通用户菜单与权限查询、角色菜单严格模式、路由构建（目录/菜单框架/内链）、
 * 组件与路由名称推导、菜单树与下拉树组装、同级菜单名唯一校验，以及增删改查委托。</p>
 *
 * @author ai-lawyers
 */
class SysMenuServiceImplTest
{
    private SysMenuServiceImpl service;

    @Mock private SysMenuMapper menuMapper;
    @Mock private SysRoleMapper roleMapper;
    @Mock private SysRoleMenuMapper roleMenuMapper;

    @BeforeEach
    void setUp()
    {
        MockitoAnnotations.openMocks(this);
        service = new SysMenuServiceImpl();
        ReflectionTestUtils.setField(service, "menuMapper", menuMapper);
        ReflectionTestUtils.setField(service, "roleMapper", roleMapper);
        ReflectionTestUtils.setField(service, "roleMenuMapper", roleMenuMapper);
    }

    // ---------------- 查询 ----------------

    @Test
    void selectMenuList_admin_queriesAll()
    {
        SysMenu query = new SysMenu();
        List<SysMenu> all = Collections.singletonList(menu(1L, 0L, "系统管理", "system", "M", "1", null));
        when(menuMapper.selectMenuList(query)).thenReturn(all);

        assertSame(all, service.selectMenuList(query, 1L));
        verify(menuMapper).selectMenuList(query);
    }

    @Test
    void selectMenuList_normalUser_bindsUserIdParam()
    {
        SysMenu query = new SysMenu();
        when(menuMapper.selectMenuListByUserId(query)).thenReturn(Collections.emptyList());

        assertTrue(service.selectMenuList(query, 2L).isEmpty());
        assertEquals(Long.valueOf(2L), query.getParams().get("userId"));
    }

    @Test
    void selectMenuList_byUserId_usesAdminBranch()
    {
        when(menuMapper.selectMenuList(any(SysMenu.class))).thenReturn(Collections.emptyList());

        assertTrue(service.selectMenuList(1L).isEmpty());
        verify(menuMapper).selectMenuList(any(SysMenu.class));
    }

    @Test
    void selectMenuPermsByUserId_splitsTrimAndSkipsBlank()
    {
        when(menuMapper.selectMenuPermsByUserId(2L))
                .thenReturn(Arrays.asList("a:b,c:d", " e:f ", "", null));

        Set<String> perms = service.selectMenuPermsByUserId(2L);

        assertEquals(3, perms.size());
        assertTrue(perms.contains("a:b"));
        assertTrue(perms.contains("c:d"));
        assertTrue(perms.contains("e:f"));
    }

    @Test
    void selectMenuPermsByRoleId_splits()
    {
        when(menuMapper.selectMenuPermsByRoleId(3L)).thenReturn(Collections.singletonList("a:b:c,a:b:d"));

        assertEquals(2, service.selectMenuPermsByRoleId(3L).size());
    }

    @Test
    void selectMenuTreeByUserId_admin_usesAllAndBuildsTree()
    {
        SysMenu parent = menu(1L, 0L, "系统管理", "system", "M", "1", null);
        SysMenu child = menu(2L, 1L, "用户管理", "user", "C", "1", "system/user/index");
        when(menuMapper.selectMenuTreeAll()).thenReturn(new ArrayList<>(Arrays.asList(parent, child)));

        List<SysMenu> tree = service.selectMenuTreeByUserId(1L);

        assertEquals(1, tree.size());
        assertEquals(1, tree.get(0).getChildren().size());
        assertEquals("用户管理", tree.get(0).getChildren().get(0).getMenuName());
    }

    @Test
    void selectMenuTreeByUserId_normal_usesUserTree()
    {
        when(menuMapper.selectMenuTreeByUserId(2L)).thenReturn(new ArrayList<>());

        assertTrue(service.selectMenuTreeByUserId(2L).isEmpty());
    }

    @Test
    void selectMenuListByRoleId_passesStrictFlag()
    {
        SysRole role = new SysRole();
        role.setMenuCheckStrictly(false);
        when(roleMapper.selectRoleById(3L)).thenReturn(role);
        when(menuMapper.selectMenuListByRoleId(3L, false))
                .thenReturn(Collections.singletonList(9L));

        assertEquals(Collections.singletonList(9L), service.selectMenuListByRoleId(3L));
    }

    // ---------------- 路由构建 ----------------

    @Test
    void buildMenus_directoryWithChildren()
    {
        SysMenu dir = menu(1L, 0L, "系统管理", "system", "M", "1", null);
        SysMenu child = menu(2L, 1L, "用户管理", "user", "C", "1", "system/user/index");
        dir.setChildren(Collections.singletonList(child));
        dir.setVisible("1");

        List<RouterVo> routers = service.buildMenus(Collections.singletonList(dir));

        RouterVo router = routers.get(0);
        assertTrue(router.getHidden());
        assertEquals("System", router.getName());
        assertEquals("/system", router.getPath());
        assertEquals(UserConstants.LAYOUT, router.getComponent());
        assertTrue(router.getAlwaysShow());
        assertEquals("noRedirect", router.getRedirect());
        assertEquals(1, router.getChildren().size());
        assertEquals("system/user/index", router.getChildren().get(0).getComponent());
        assertEquals("User", router.getChildren().get(0).getName());
        assertEquals("系统管理", router.getMeta().getTitle());
    }

    @Test
    void buildMenus_topLevelMenuFrame_wrapsChild()
    {
        SysMenu menu = menu(3L, 0L, "个人中心", "profile", "C", "1", "system/user/profile/index");

        RouterVo router = service.buildMenus(Collections.singletonList(menu)).get(0);

        assertNull(router.getMeta());
        assertEquals("/", router.getPath());
        assertEquals(1, router.getChildren().size());
        assertEquals("Profile", router.getChildren().get(0).getName());
        assertEquals("profile", router.getChildren().get(0).getPath());
    }

    @Test
    void buildMenus_topLevelInnerLink_rewritesPath()
    {
        SysMenu menu = menu(4L, 0L, "官网", "http://www.example.com/docs", "M", "1", null);

        RouterVo router = service.buildMenus(Collections.singletonList(menu)).get(0);

        assertEquals("/", router.getPath());
        assertEquals("官网", router.getMeta().getTitle());
        RouterVo child = router.getChildren().get(0);
        assertEquals("example/com/docs", child.getPath());
        assertEquals(UserConstants.INNER_LINK, child.getComponent());
        assertEquals("Example/com/docs", child.getName());
    }

    @Test
    void buildMenus_nestedInnerLink_rewritesRouterPath()
    {
        SysMenu menu = menu(5L, 1L, "帮助", "https://help.example.com", "C", "1", null);

        RouterVo router = service.buildMenus(Collections.singletonList(menu)).get(0);

        assertEquals("help/example/com", router.getPath());
        assertEquals(UserConstants.INNER_LINK, router.getComponent());
    }

    @Test
    void buildMenus_parentViewComponent()
    {
        SysMenu menu = menu(6L, 1L, "二级目录", "second", "M", "1", "");

        RouterVo router = service.buildMenus(Collections.singletonList(menu)).get(0);

        assertEquals(UserConstants.PARENT_VIEW, router.getComponent());
        assertEquals("Second", router.getName());
    }

    @Test
    void buildMenus_visibleZero_notHidden()
    {
        SysMenu menu = menu(7L, 1L, "用户管理", "user", "C", "1", "system/user/index");
        menu.setVisible("0");
        menu.setIsCache("1");

        RouterVo router = service.buildMenus(Collections.singletonList(menu)).get(0);

        assertFalse(router.getHidden());
        assertTrue(router.getMeta().isNoCache());
    }

    // ---------------- 组件与名称推导 ----------------

    @Test
    void getRouteName_menuFrame_returnsEmpty()
    {
        SysMenu menu = menu(8L, 0L, "外链", "http://a.com", "C", "1", null);

        assertEquals("", service.getRouteName(menu));
        assertEquals("Abc", service.getRouteName("abc", "x"));
        assertEquals("Path", service.getRouteName("", "path"));
    }

    @Test
    void isMenuFrameAndInnerLinkRules()
    {
        SysMenu frame = menu(9L, 0L, "首页", "index", "C", "1", "index");
        assertTrue(service.isMenuFrame(frame));
        assertFalse(service.isInnerLink(frame));

        SysMenu link = menu(10L, 1L, "外链", "http://a.com", "C", "1", null);
        assertTrue(service.isInnerLink(link));
        assertFalse(service.isMenuFrame(link));

        SysMenu iframe = menu(11L, 0L, "内嵌", "http://a.com", "C", "0", null);
        assertFalse(service.isInnerLink(iframe));
    }

    @Test
    void isParentViewRule()
    {
        assertTrue(service.isParentView(menu(12L, 1L, "目录", "d", "M", "1", null)));
        assertFalse(service.isParentView(menu(13L, 0L, "目录", "d", "M", "1", null)));
        assertFalse(service.isParentView(menu(14L, 1L, "菜单", "m", "C", "1", null)));
    }

    @Test
    void getComponent_prefersConfiguredComponent()
    {
        assertEquals("custom/index",
                service.getComponent(menu(15L, 1L, "自定义", "c", "C", "1", "custom/index")));
        assertEquals(UserConstants.LAYOUT,
                service.getComponent(menu(16L, 0L, "目录", "d", "M", "1", null)));
    }

    @Test
    void innerLinkReplaceEach_rewritesSchemeAndDots()
    {
        assertEquals("example/com/a", service.innerLinkReplaceEach("https://example.com/a"));
        assertEquals("example/com", service.innerLinkReplaceEach("www.example.com"));
    }

    @Test
    void getRouterPath_topLevelDirWithLeadingSlash()
    {
        assertEquals("/system", service.getRouterPath(menu(17L, 0L, "系统", "system", "M", "1", null)));
        assertEquals("/", service.getRouterPath(menu(18L, 0L, "首页", "index", "C", "1", "index")));
        assertEquals("user", service.getRouterPath(menu(19L, 1L, "用户", "user", "C", "1", null)));
    }

    // ---------------- 树组装 ----------------

    @Test
    void buildMenuTree_returnsRootsWithChildren()
    {
        SysMenu root = menu(20L, 0L, "根", "root", "M", "1", null);
        SysMenu child = menu(21L, 20L, "子", "child", "C", "1", "x");
        SysMenu grandChild = menu(22L, 21L, "孙", "grand", "C", "1", "y");

        List<SysMenu> tree = service.buildMenuTree(
                new ArrayList<>(Arrays.asList(child, grandChild, root)));

        assertEquals(1, tree.size());
        assertEquals(Long.valueOf(20L), tree.get(0).getMenuId());
        assertEquals(Long.valueOf(21L), tree.get(0).getChildren().get(0).getMenuId());
        assertEquals(Long.valueOf(22L), tree.get(0).getChildren().get(0).getChildren().get(0).getMenuId());
    }

    @Test
    void buildMenuTree_noRoots_returnsOriginal()
    {
        SysMenu a = menu(23L, 99L, "孤儿A", "a", "C", "1", null);
        SysMenu b = menu(24L, 23L, "孤儿B", "b", "C", "1", null);
        List<SysMenu> menus = new ArrayList<>(Arrays.asList(a, b));

        List<SysMenu> tree = service.buildMenuTree(menus);

        assertEquals(1, tree.size());
        assertEquals(1, tree.get(0).getChildren().size());
    }

    @Test
    void buildMenuTreeSelect_mapsToTreeSelect()
    {
        SysMenu root = menu(25L, 0L, "根", "root", "M", "1", null);
        SysMenu child = menu(26L, 25L, "子", "child", "C", "1", "x");

        List<TreeSelect> selects = service.buildMenuTreeSelect(
                new ArrayList<>(Arrays.asList(root, child)));

        assertEquals(1, selects.size());
        assertEquals("根", selects.get(0).getLabel());
        assertEquals(1, selects.get(0).getChildren().size());
        assertEquals("子", selects.get(0).getChildren().get(0).getLabel());
    }

    // ---------------- 唯一性校验与增删改查 ----------------

    @Test
    void checkMenuNameUnique_sameId_unique()
    {
        SysMenu existing = menu(5L, 1L, "用户", "user", "C", "1", null);
        when(menuMapper.checkMenuNameUnique("用户", 1L)).thenReturn(existing);

        assertTrue(service.checkMenuNameUnique(menu(5L, 1L, "用户", "user", "C", "1", null)));
    }

    @Test
    void checkMenuNameUnique_otherId_notUnique()
    {
        when(menuMapper.checkMenuNameUnique("用户", 1L))
                .thenReturn(menu(5L, 1L, "用户", "user", "C", "1", null));

        assertFalse(service.checkMenuNameUnique(menu(6L, 1L, "用户", "user", "C", "1", null)));
    }

    @Test
    void checkMenuNameUnique_absentOrNullId_unique()
    {
        when(menuMapper.checkMenuNameUnique(anyString(), anyLong())).thenReturn(null);

        assertTrue(service.checkMenuNameUnique(menu(7L, 1L, "新菜单", "new", "C", "1", null)));

        when(menuMapper.checkMenuNameUnique("新菜单", 1L))
                .thenReturn(menu(8L, 1L, "新菜单", "new", "C", "1", null));
        SysMenu withoutId = menu(0L, 1L, "新菜单", "new", "C", "1", null);
        withoutId.setMenuId(null);
        assertFalse(service.checkMenuNameUnique(withoutId));
    }

    @Test
    void childAndRoleUsage_checks()
    {
        when(menuMapper.hasChildByMenuId(1L)).thenReturn(1);
        when(roleMenuMapper.checkMenuExistRole(1L)).thenReturn(2);

        assertTrue(service.hasChildByMenuId(1L));
        assertTrue(service.checkMenuExistRole(1L));
        verify(roleMenuMapper).checkMenuExistRole(1L);
    }

    @Test
    void crud_delegates()
    {
        SysMenu menu = menu(1L, 0L, "系统", "system", "M", "1", null);
        when(menuMapper.selectMenuById(1L)).thenReturn(menu);
        when(menuMapper.insertMenu(menu)).thenReturn(1);
        when(menuMapper.updateMenu(menu)).thenReturn(1);
        when(menuMapper.deleteMenuById(1L)).thenReturn(1);

        assertSame(menu, service.selectMenuById(1L));
        assertEquals(1, service.insertMenu(menu));
        assertEquals(1, service.updateMenu(menu));
        assertEquals(1, service.deleteMenuById(1L));
    }

    // ---------------- 辅助 ----------------

    private SysMenu menu(long menuId, long parentId, String name, String path,
            String type, String frame, String component)
    {
        SysMenu menu = new SysMenu();
        menu.setMenuId(menuId);
        menu.setParentId(parentId);
        menu.setMenuName(name);
        menu.setPath(path);
        menu.setMenuType(type);
        menu.setIsFrame(frame);
        menu.setComponent(component);
        menu.setIcon("icon-" + menuId);
        menu.setVisible("0");
        menu.setIsCache("0");
        return menu;
    }
}
