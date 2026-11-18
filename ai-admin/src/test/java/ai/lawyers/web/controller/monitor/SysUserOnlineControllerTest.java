package ai.lawyers.web.controller.monitor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import ai.lawyers.common.constant.CacheConstants;
import ai.lawyers.common.core.domain.AjaxResult;
import ai.lawyers.common.core.domain.entity.SysUser;
import ai.lawyers.common.core.domain.model.LoginUser;
import ai.lawyers.common.core.page.TableDataInfo;
import ai.lawyers.common.core.redis.RedisCache;
import ai.lawyers.system.domain.SysUserOnline;
import ai.lawyers.system.service.ISysUserOnlineService;

/**
 * 在线用户监控接口单元测试
 *
 * @author ai-lawyers
 */
@ExtendWith(MockitoExtension.class)
class SysUserOnlineControllerTest
{
    @Mock
    private ISysUserOnlineService userOnlineService;

    @Mock
    private RedisCache redisCache;

    @InjectMocks
    private SysUserOnlineController controller;

    @AfterEach
    void tearDown()
    {
        RequestContextHolder.resetRequestAttributes();
    }

    private LoginUser loginUser(String userName)
    {
        SysUser user = new SysUser();
        user.setUserName(userName);
        return new LoginUser(1L, 2L, user, null);
    }

    @Test
    void 无筛选条件按会话逐条组装()
    {
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(new MockHttpServletRequest()));
        when(redisCache.keys(CacheConstants.LOGIN_TOKEN_KEY + "*"))
                .thenReturn(Arrays.asList("login_tokens:a", "login_tokens:b"));
        when(redisCache.getCacheObject(anyString())).thenReturn(loginUser("admin"));
        when(userOnlineService.loginUserToUserOnline(any(LoginUser.class)))
                .thenReturn(new SysUserOnline(), new SysUserOnline());

        TableDataInfo table = controller.list(null, null);

        assertEquals(200, table.getCode());
        assertEquals(2, table.getTotal());
    }

    @Test
    void 同时按IP与用户名筛选()
    {
        when(redisCache.keys(CacheConstants.LOGIN_TOKEN_KEY + "*"))
                .thenReturn(Arrays.asList("login_tokens:c"));
        when(redisCache.getCacheObject("login_tokens:c")).thenReturn(loginUser("admin"));
        when(userOnlineService.selectOnlineByInfo(anyString(), anyString(), any(LoginUser.class)))
                .thenReturn(new SysUserOnline());

        TableDataInfo table = controller.list("127.0.0.1", "admin");

        assertEquals(1, table.getTotal());
        verify(userOnlineService).selectOnlineByInfo(eq("127.0.0.1"), eq("admin"), any(LoginUser.class));
    }

    @Test
    void 仅按用户名筛选走用户名查询()
    {
        when(redisCache.keys(CacheConstants.LOGIN_TOKEN_KEY + "*"))
                .thenReturn(Arrays.asList("login_tokens:d"));
        when(redisCache.getCacheObject("login_tokens:d")).thenReturn(loginUser("admin"));
        when(userOnlineService.selectOnlineByUserName(anyString(), any(LoginUser.class)))
                .thenReturn(new SysUserOnline());

        TableDataInfo table = controller.list(null, "admin");

        assertEquals(1, table.getTotal());
        verify(userOnlineService).selectOnlineByUserName(eq("admin"), any(LoginUser.class));
    }

    @Test
    void 强退用户删除会话缓存()
    {
        AjaxResult result = controller.forceLogout("token-9");

        assertEquals(200, result.get(AjaxResult.CODE_TAG));
        verify(redisCache).deleteObject(CacheConstants.LOGIN_TOKEN_KEY + "token-9");
    }
}
