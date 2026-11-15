package ai.lawyers.system.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ai.lawyers.common.core.domain.entity.SysDept;
import ai.lawyers.common.core.domain.entity.SysUser;
import ai.lawyers.common.core.domain.model.LoginUser;
import ai.lawyers.system.domain.SysUserOnline;

/**
 * 在线用户服务单测。
 *
 * <p>覆盖按登录地址、用户名称、地址+名称组合匹配在线用户，
 * 以及登录用户到在线用户视图的字段转换。</p>
 *
 * @author ai-lawyers
 */
class SysUserOnlineServiceImplTest
{
    private SysUserOnlineServiceImpl service;

    @BeforeEach
    void setUp()
    {
        service = new SysUserOnlineServiceImpl();
    }

    private static LoginUser loginUser()
    {
        SysDept dept = new SysDept();
        dept.setDeptName("研发部");

        SysUser user = new SysUser();
        user.setUserName("admin");
        user.setDept(dept);

        LoginUser loginUser = new LoginUser();
        loginUser.setUser(user);
        loginUser.setToken("token-1");
        loginUser.setIpaddr("127.0.0.1");
        loginUser.setLoginLocation("内网");
        loginUser.setBrowser("Chrome");
        loginUser.setOs("Windows");
        loginUser.setLoginTime(1000L);
        return loginUser;
    }

    @Test
    void selectOnlineByIpaddr_matched_returnsOnline()
    {
        SysUserOnline online = service.selectOnlineByIpaddr("127.0.0.1", loginUser());

        assertNotNull(online);
        assertEquals("admin", online.getUserName());
    }

    @Test
    void selectOnlineByIpaddr_mismatched_returnsNull()
    {
        assertNull(service.selectOnlineByIpaddr("10.0.0.1", loginUser()));
    }

    @Test
    void selectOnlineByUserName_matched_returnsOnline()
    {
        SysUserOnline online = service.selectOnlineByUserName("admin", loginUser());

        assertNotNull(online);
        assertEquals("127.0.0.1", online.getIpaddr());
    }

    @Test
    void selectOnlineByUserName_mismatched_returnsNull()
    {
        assertNull(service.selectOnlineByUserName("guest", loginUser()));
    }

    @Test
    void selectOnlineByInfo_matched_returnsOnline()
    {
        SysUserOnline online = service.selectOnlineByInfo("127.0.0.1", "admin", loginUser());

        assertNotNull(online);
        assertEquals("token-1", online.getTokenId());
    }

    @Test
    void selectOnlineByInfo_ipMismatched_returnsNull()
    {
        assertNull(service.selectOnlineByInfo("10.0.0.1", "admin", loginUser()));
    }

    @Test
    void selectOnlineByInfo_userNameMismatched_returnsNull()
    {
        assertNull(service.selectOnlineByInfo("127.0.0.1", "guest", loginUser()));
    }

    @Test
    void loginUserToUserOnline_nullUser_returnsNull()
    {
        assertNull(service.loginUserToUserOnline(null));
    }

    @Test
    void loginUserToUserOnline_withoutSysUser_returnsNull()
    {
        assertNull(service.loginUserToUserOnline(new LoginUser()));
    }

    @Test
    void loginUserToUserOnline_mapsAllFields()
    {
        SysUserOnline online = service.loginUserToUserOnline(loginUser());

        assertEquals("token-1", online.getTokenId());
        assertEquals("admin", online.getUserName());
        assertEquals("127.0.0.1", online.getIpaddr());
        assertEquals("内网", online.getLoginLocation());
        assertEquals("Chrome", online.getBrowser());
        assertEquals("Windows", online.getOs());
        assertEquals(1000L, online.getLoginTime());
        assertEquals("研发部", online.getDeptName());
    }

    @Test
    void loginUserToUserOnline_withoutDept_leavesDeptNameNull()
    {
        LoginUser loginUser = loginUser();
        loginUser.getUser().setDept(null);

        assertNull(service.loginUserToUserOnline(loginUser).getDeptName());
    }
}
