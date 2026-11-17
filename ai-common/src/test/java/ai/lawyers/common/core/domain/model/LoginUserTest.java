package ai.lawyers.common.core.domain.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;

import org.junit.jupiter.api.Test;

import ai.lawyers.common.core.domain.entity.SysUser;

/**
 * 登录用户测试：构造器、用户信息委托与固定安全标记。
 *
 * @author ai-lawyers
 */
class LoginUserTest
{
    @Test
    void 构造器_保存用户与权限()
    {
        SysUser user = user();
        Set<String> permissions = Set.of("system:user:list");

        LoginUser loginUser = new LoginUser(user, permissions);

        assertSame(user, loginUser.getUser());
        assertEquals(permissions, loginUser.getPermissions());
    }

    @Test
    void 构造器_带用户与部门标识()
    {
        SysUser user = user();

        LoginUser loginUser = new LoginUser(1L, 2L, user, Set.of());

        assertEquals(1L, loginUser.getUserId());
        assertEquals(2L, loginUser.getDeptId());
        assertSame(user, loginUser.getUser());
    }

    @Test
    void 用户名与密码委托给用户实体()
    {
        SysUser user = user();
        user.setUserName("zhangsan");
        user.setPassword("secret");

        LoginUser loginUser = new LoginUser(user, Set.of());

        assertEquals("zhangsan", loginUser.getUsername());
        assertEquals("secret", loginUser.getPassword());
    }

    @Test
    void 安全标记始终为真且无权限集合()
    {
        LoginUser loginUser = new LoginUser(user(), Set.of());

        assertTrue(loginUser.isAccountNonExpired());
        assertTrue(loginUser.isAccountNonLocked());
        assertTrue(loginUser.isCredentialsNonExpired());
        assertTrue(loginUser.isEnabled());
        assertNull(loginUser.getAuthorities());
    }

    @Test
    void 属性读写()
    {
        LoginUser loginUser = new LoginUser();
        loginUser.setUserId(10L);
        loginUser.setDeptId(20L);
        loginUser.setToken("token");
        loginUser.setLoginTime(100L);
        loginUser.setExpireTime(200L);
        loginUser.setIpaddr("127.0.0.1");
        loginUser.setLoginLocation("内网IP");
        loginUser.setBrowser("Chrome");
        loginUser.setOs("Windows");

        assertEquals(10L, loginUser.getUserId());
        assertEquals(20L, loginUser.getDeptId());
        assertEquals("token", loginUser.getToken());
        assertEquals(100L, loginUser.getLoginTime());
        assertEquals(200L, loginUser.getExpireTime());
        assertEquals("127.0.0.1", loginUser.getIpaddr());
        assertEquals("内网IP", loginUser.getLoginLocation());
        assertEquals("Chrome", loginUser.getBrowser());
        assertEquals("Windows", loginUser.getOs());
    }

    private static SysUser user()
    {
        return new SysUser();
    }
}
