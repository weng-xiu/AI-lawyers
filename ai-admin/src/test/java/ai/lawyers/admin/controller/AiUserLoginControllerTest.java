package ai.lawyers.admin.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import ai.lawyers.common.config.RuoYiConfig;
import ai.lawyers.common.constant.CacheConstants;
import ai.lawyers.common.constant.Constants;
import ai.lawyers.common.core.domain.AjaxResult;
import ai.lawyers.common.core.domain.entity.SysMenu;
import ai.lawyers.common.core.domain.entity.SysUser;
import ai.lawyers.common.core.domain.model.LoginBody;
import ai.lawyers.common.core.domain.model.LoginUser;
import ai.lawyers.common.core.redis.RedisCache;
import ai.lawyers.common.utils.DateUtils;
import ai.lawyers.common.utils.SecurityUtils;
import ai.lawyers.framework.web.service.AiUserLoginService;
import ai.lawyers.framework.web.service.SysPermissionService;
import ai.lawyers.framework.web.service.TokenService;
import ai.lawyers.system.service.ISysConfigService;
import ai.lawyers.system.service.ISysMenuService;
import com.google.code.kaptcha.Producer;

/**
 * 公众端登录接口单元测试
 *
 * @author ai-lawyers
 */
@ExtendWith(MockitoExtension.class)
class AiUserLoginControllerTest
{
    @Mock
    private AiUserLoginService loginService;

    @Mock
    private ISysMenuService menuService;

    @Mock
    private SysPermissionService permissionService;

    @Mock
    private TokenService tokenService;

    @Mock
    private ISysConfigService configService;

    @Mock
    private RedisCache redisCache;

    @Mock
    private Producer captchaProducer;

    @Mock
    private Producer captchaProducerMath;

    @InjectMocks
    private AiUserLoginController controller;

    @BeforeEach
    void setUp()
    {
        ReflectionTestUtils.setField(controller, "captchaProducer", captchaProducer);
        ReflectionTestUtils.setField(controller, "captchaProducerMath", captchaProducerMath);
    }

    private SysUser user()
    {
        SysUser user = new SysUser();
        user.setUserId(1L);
        user.setUserName("portal");
        return user;
    }

    private LoginUser loginUser(String token, Set<String> permissions)
    {
        LoginUser loginUser = new LoginUser(1L, 2L, user(), permissions);
        loginUser.setToken(token);
        return loginUser;
    }

    @Test
    void 登录返回令牌()
    {
        LoginBody body = new LoginBody();
        body.setUsername("portal");
        body.setPassword("pwd");
        body.setCode("1234");
        body.setUuid("uuid-1");
        when(loginService.login("portal", "pwd", "1234", "uuid-1")).thenReturn("token-1");

        AjaxResult result = controller.login(body);

        assertEquals(200, result.get(AjaxResult.CODE_TAG));
        assertEquals("token-1", result.get(Constants.TOKEN));
    }

    @Test
    void 退出登录删除令牌缓存()
    {
        LoginUser loginUser = loginUser("token-1", new HashSet<String>());

        try (MockedStatic<SecurityUtils> mocked = mockStatic(SecurityUtils.class))
        {
            mocked.when(SecurityUtils::getLoginUser).thenReturn(loginUser);
            assertEquals(200, controller.logout().get(AjaxResult.CODE_TAG));
        }
        verify(tokenService).delLoginUser("token-1");
    }

    @Test
    void 用户信息权限变化时刷新令牌()
    {
        LoginUser loginUser = loginUser("token-1", new HashSet<String>());
        Set<String> roles = Set.of("portal");
        Set<String> permissions = Set.of("lawyers:portal:query");
        when(permissionService.getRolePermission(any(SysUser.class))).thenReturn(roles);
        when(permissionService.getMenuPermission(any(SysUser.class))).thenReturn(permissions);
        when(configService.selectConfigByKey("sys.account.initPasswordModify")).thenReturn("0");
        when(configService.selectConfigByKey("sys.account.passwordValidateDays")).thenReturn("0");

        try (MockedStatic<SecurityUtils> mocked = mockStatic(SecurityUtils.class))
        {
            mocked.when(SecurityUtils::getLoginUser).thenReturn(loginUser);
            AjaxResult result = controller.getInfo();
            assertEquals(200, result.get(AjaxResult.CODE_TAG));
            assertEquals(roles, result.get("roles"));
            assertEquals(permissions, result.get("permissions"));
            assertEquals(Boolean.FALSE, result.get("isDefaultModifyPwd"));
            assertEquals(Boolean.FALSE, result.get("isPasswordExpired"));
        }
        verify(tokenService).refreshToken(loginUser);
    }

    @Test
    void 用户端路由树()
    {
        List<SysMenu> menus = new ArrayList<SysMenu>();
        List<ai.lawyers.system.domain.vo.RouterVo> routers = new ArrayList<ai.lawyers.system.domain.vo.RouterVo>();
        when(menuService.selectMenuTreeByUserId(5L)).thenReturn(menus);
        when(menuService.buildMenus(menus)).thenReturn(routers);

        try (MockedStatic<SecurityUtils> mocked = mockStatic(SecurityUtils.class))
        {
            mocked.when(SecurityUtils::getUserId).thenReturn(5L);
            assertEquals(routers, controller.getRouters().get(AjaxResult.DATA_TAG));
        }
    }

    @Test
    void 算式验证码返回可朗读等式() throws Exception
    {
        when(captchaProducerMath.createText()).thenReturn("7+5@12");
        when(captchaProducerMath.createImage("7+5")).thenReturn(new BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB));

        try (MockedStatic<RuoYiConfig> mocked = mockStatic(RuoYiConfig.class))
        {
            mocked.when(RuoYiConfig::getCaptchaType).thenReturn("math");
            AjaxResult result = controller.getCode();
            assertEquals(Boolean.TRUE, result.get("captchaEnabled"));
            assertEquals("7加5，等于几", result.get("expr"));
            assertNotNull(result.get("uuid"));
            assertTrue(String.valueOf(result.get("img")).length() > 0);
        }
        verify(redisCache).setCacheObject(anyString(), eq("12"), eq(Constants.CAPTCHA_EXPIRATION),
                eq(TimeUnit.MINUTES));
    }

    @Test
    void 字符验证码返回图片且等式为空() throws Exception
    {
        when(captchaProducer.createText()).thenReturn("ABCD");
        when(captchaProducer.createImage("ABCD")).thenReturn(new BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB));

        try (MockedStatic<RuoYiConfig> mocked = mockStatic(RuoYiConfig.class))
        {
            mocked.when(RuoYiConfig::getCaptchaType).thenReturn("char");
            AjaxResult result = controller.getCode();
            assertNull(result.get("expr"));
            assertTrue(String.valueOf(result.get("img")).length() > 0);
        }
        verify(redisCache).setCacheObject(anyString(), eq("ABCD"), eq(Constants.CAPTCHA_EXPIRATION),
                eq(TimeUnit.MINUTES));
    }

    @Test
    void 初始密码与密码过期校验()
    {
        when(configService.selectConfigByKey("sys.account.initPasswordModify")).thenReturn("1");
        assertTrue(controller.initPasswordIsModify(null));
        assertFalse(controller.initPasswordIsModify(new Date()));

        when(configService.selectConfigByKey("sys.account.passwordValidateDays")).thenReturn("30");
        assertTrue(controller.passwordIsExpiration(null));
        assertFalse(controller.passwordIsExpiration(DateUtils.getNowDate()));
        assertTrue(controller.passwordIsExpiration(DateUtils.addDays(DateUtils.getNowDate(), -31)));
    }
}
