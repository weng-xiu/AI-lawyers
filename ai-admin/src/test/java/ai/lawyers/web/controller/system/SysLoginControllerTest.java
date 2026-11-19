package ai.lawyers.web.controller.system;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import ai.lawyers.common.constant.Constants;
import ai.lawyers.common.core.domain.AjaxResult;
import ai.lawyers.common.core.domain.entity.SysMenu;
import ai.lawyers.common.core.domain.entity.SysUser;
import ai.lawyers.common.core.domain.model.LoginBody;
import ai.lawyers.common.core.domain.model.LoginUser;
import ai.lawyers.common.utils.SecurityUtils;
import ai.lawyers.framework.web.service.SysLoginService;
import ai.lawyers.framework.web.service.SysPermissionService;
import ai.lawyers.framework.web.service.TokenService;
import ai.lawyers.system.domain.vo.RouterVo;
import ai.lawyers.system.service.ISysConfigService;
import ai.lawyers.system.service.ISysMenuService;

/**
 * 登录验证接口单元测试
 *
 * @author ai-lawyers
 */
@ExtendWith(MockitoExtension.class)
class SysLoginControllerTest
{
    @Mock
    private SysLoginService loginService;

    @Mock
    private ISysMenuService menuService;

    @Mock
    private SysPermissionService permissionService;

    @Mock
    private TokenService tokenService;

    @Mock
    private ISysConfigService configService;

    @InjectMocks
    private SysLoginController controller;

    private LoginUser loginUser(Set<String> permissions)
    {
        SysUser user = new SysUser();
        user.setUserName("admin");
        return new LoginUser(1L, 2L, user, permissions);
    }

    @Test
    void 登录返回令牌()
    {
        LoginBody body = new LoginBody();
        body.setUsername("admin");
        body.setPassword("admin123");
        body.setCode("1234");
        body.setUuid("uuid-1");
        when(loginService.login("admin", "admin123", "1234", "uuid-1")).thenReturn("token-abc");

        AjaxResult result = controller.login(body);

        assertEquals("token-abc", result.get(Constants.TOKEN));
    }

    @Test
    void 获取用户信息并在权限变化时刷新令牌()
    {
        Set<String> oldPermissions = new HashSet<String>(Arrays.asList("system:user:list"));
        Set<String> permissions = new HashSet<String>(Arrays.asList("system:user:list", "system:role:list"));
        Set<String> roles = new HashSet<String>(Arrays.asList("admin"));
        LoginUser loginUser = loginUser(oldPermissions);
        when(permissionService.getRolePermission(loginUser.getUser())).thenReturn(roles);
        when(permissionService.getMenuPermission(loginUser.getUser())).thenReturn(permissions);
        when(configService.selectConfigByKey("sys.account.initPasswordModify")).thenReturn("0");
        when(configService.selectConfigByKey("sys.account.passwordValidateDays")).thenReturn("0");

        try (MockedStatic<SecurityUtils> mocked = mockStatic(SecurityUtils.class))
        {
            mocked.when(SecurityUtils::getLoginUser).thenReturn(loginUser);
            AjaxResult result = controller.getInfo();

            assertSame(loginUser.getUser(), result.get("user"));
            assertSame(roles, result.get("roles"));
            assertSame(permissions, result.get("permissions"));
            assertEquals(Boolean.FALSE, result.get("isDefaultModifyPwd"));
            assertEquals(Boolean.FALSE, result.get("isPasswordExpired"));
        }
        verify(tokenService).refreshToken(loginUser);
    }

    @Test
    void 权限未变化时不刷新令牌()
    {
        Set<String> permissions = new HashSet<String>(Arrays.asList("system:user:list"));
        LoginUser loginUser = loginUser(permissions);
        when(permissionService.getRolePermission(loginUser.getUser())).thenReturn(new HashSet<String>());
        when(permissionService.getMenuPermission(loginUser.getUser())).thenReturn(permissions);
        when(configService.selectConfigByKey("sys.account.initPasswordModify")).thenReturn("1");
        when(configService.selectConfigByKey("sys.account.passwordValidateDays")).thenReturn("30");

        try (MockedStatic<SecurityUtils> mocked = mockStatic(SecurityUtils.class))
        {
            mocked.when(SecurityUtils::getLoginUser).thenReturn(loginUser);
            AjaxResult result = controller.getInfo();

            assertEquals(Boolean.TRUE, result.get("isDefaultModifyPwd"));
            assertEquals(Boolean.TRUE, result.get("isPasswordExpired"));
        }
        verify(tokenService, never()).refreshToken(loginUser);
    }

    @Test
    void 获取路由按登录用户构建()
    {
        List<SysMenu> menus = new ArrayList<SysMenu>();
        List<RouterVo> built = new ArrayList<RouterVo>();
        when(menuService.selectMenuTreeByUserId(1L)).thenReturn(menus);
        when(menuService.buildMenus(menus)).thenReturn(built);

        try (MockedStatic<SecurityUtils> mocked = mockStatic(SecurityUtils.class))
        {
            mocked.when(SecurityUtils::getUserId).thenReturn(1L);
            AjaxResult result = controller.getRouters();

            assertSame(built, result.get(AjaxResult.DATA_TAG));
        }
    }

    @Test
    void 初始密码提醒与过期判断()
    {
        when(configService.selectConfigByKey("sys.account.initPasswordModify")).thenReturn("1");
        when(configService.selectConfigByKey("sys.account.passwordValidateDays")).thenReturn("30");

        assertTrue(controller.initPasswordIsModify(null));
        assertFalse(controller.initPasswordIsModify(new Date()));
        assertTrue(controller.passwordIsExpiration(null));
        assertFalse(controller.passwordIsExpiration(new Date()));
    }

    @Test
    void 未配置密码有效期时不过期()
    {
        when(configService.selectConfigByKey("sys.account.passwordValidateDays")).thenReturn(null);

        assertFalse(controller.passwordIsExpiration(null));
    }
}
