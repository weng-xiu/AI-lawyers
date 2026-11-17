package ai.lawyers.common.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import ai.lawyers.common.constant.HttpStatus;
import ai.lawyers.common.core.domain.entity.SysUser;
import ai.lawyers.common.core.domain.model.LoginUser;
import ai.lawyers.common.exception.ServiceException;

/**
 * 安全工具测试：登录用户读取、密码加密校验与权限/角色匹配。
 *
 * @author ai-lawyers
 */
class SecurityUtilsTest
{
    @AfterEach
    void 清理安全上下文()
    {
        SecurityContextHolder.clearContext();
    }

    @Test
    void 未认证时_读取用户信息抛出未授权异常()
    {
        ServiceException userIdError = assertThrows(ServiceException.class, SecurityUtils::getUserId);
        assertEquals(HttpStatus.UNAUTHORIZED, userIdError.getCode());

        ServiceException deptError = assertThrows(ServiceException.class, SecurityUtils::getDeptId);
        assertEquals(HttpStatus.UNAUTHORIZED, deptError.getCode());

        ServiceException usernameError = assertThrows(ServiceException.class, SecurityUtils::getUsername);
        assertEquals(HttpStatus.UNAUTHORIZED, usernameError.getCode());

        ServiceException loginUserError = assertThrows(ServiceException.class, SecurityUtils::getLoginUser);
        assertEquals(HttpStatus.UNAUTHORIZED, loginUserError.getCode());
    }

    @Test
    void 已认证时_读取用户ID部门与账户()
    {
        LoginUser loginUser = loginUser(7L, 3L, "lawyer", new HashSet<>());
        authenticate(loginUser);

        assertSame(loginUser, SecurityUtils.getLoginUser());
        assertEquals(Long.valueOf(7L), SecurityUtils.getUserId());
        assertEquals(Long.valueOf(3L), SecurityUtils.getDeptId());
        assertEquals("lawyer", SecurityUtils.getUsername());
        assertNotNull(SecurityUtils.getAuthentication());
    }

    @Test
    void isAdmin_仅用户ID为一为管理员()
    {
        assertTrue(SecurityUtils.isAdmin(1L));
        assertFalse(SecurityUtils.isAdmin(2L));
        assertFalse(SecurityUtils.isAdmin(null));
    }

    @Test
    void encryptPassword与matchesPassword_BCrypt校验()
    {
        String encoded = SecurityUtils.encryptPassword("admin123");

        assertTrue(encoded.startsWith("$2"));
        assertTrue(SecurityUtils.matchesPassword("admin123", encoded));
        assertFalse(SecurityUtils.matchesPassword("wrong", encoded));
    }

    @Test
    void hasPermi_通配与精确权限匹配()
    {
        Set<String> permissions = new HashSet<>(Arrays.asList("system:user:list", "system:role:*", " "));

        assertTrue(SecurityUtils.hasPermi(permissions, "system:user:list"));
        assertTrue(SecurityUtils.hasPermi(permissions, "system:role:add"));
        assertFalse(SecurityUtils.hasPermi(permissions, "system:user:remove"));
        // 空白权限被忽略
        assertFalse(SecurityUtils.hasPermi(Collections.singleton(" "), "system:user:list"));
        // 全权限通配符
        assertTrue(SecurityUtils.hasPermi(Collections.singleton("*:*:*"), "任意:权限:标识"));
    }

    @Test
    void hasRole_超级管理员与通配角色匹配()
    {
        assertTrue(SecurityUtils.hasRole(Collections.singleton("admin"), "common"));
        assertTrue(SecurityUtils.hasRole(Collections.singleton("lawyer:*"), "lawyer:edit"));
        assertFalse(SecurityUtils.hasRole(Collections.singleton("common"), "admin"));
        assertFalse(SecurityUtils.hasRole(Collections.singleton(" "), "common"));
    }

    /** 将登录用户写入安全上下文 */
    private void authenticate(LoginUser loginUser)
    {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(loginUser, null, Collections.emptyList()));
    }

    /** 构造登录用户 */
    private LoginUser loginUser(Long userId, Long deptId, String userName, Set<String> permissions)
    {
        SysUser user = new SysUser(userId);
        user.setDeptId(deptId);
        user.setUserName(userName);
        return new LoginUser(userId, deptId, user, permissions);
    }
}
