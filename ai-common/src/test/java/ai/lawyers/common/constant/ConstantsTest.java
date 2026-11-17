package ai.lawyers.common.constant;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.charset.StandardCharsets;
import java.util.Locale;

import org.junit.jupiter.api.Test;

/**
 * 通用常量测试：字符集、成功/失败标记、登录状态、权限标识与安全白名单。
 *
 * @author ai-lawyers
 */
class ConstantsTest
{
    @Test
    void 字符集与协议前缀()
    {
        assertEquals(StandardCharsets.UTF_8.name(), Constants.UTF8);
        assertEquals("GBK", Constants.GBK);
        assertEquals(Locale.SIMPLIFIED_CHINESE, Constants.DEFAULT_LOCALE);
        assertEquals("http://", Constants.HTTP);
        assertEquals("https://", Constants.HTTPS);
    }

    @Test
    void 成功失败与登录状态标记()
    {
        assertEquals("0", Constants.SUCCESS);
        assertEquals("1", Constants.FAIL);
        assertEquals("Success", Constants.LOGIN_SUCCESS);
        assertEquals("Error", Constants.LOGIN_FAIL);
        assertEquals("Logout", Constants.LOGOUT);
        assertEquals("Register", Constants.REGISTER);
    }

    @Test
    void 权限与令牌标识()
    {
        assertEquals("*:*:*", Constants.ALL_PERMISSION);
        assertEquals("admin", Constants.SUPER_ADMIN);
        assertEquals(",", Constants.ROLE_DELIMETER);
        assertEquals("," , Constants.PERMISSION_DELIMETER);
        assertEquals("token", Constants.TOKEN);
        assertEquals("Bearer ", Constants.TOKEN_PREFIX);
        assertEquals("login_user_key", Constants.LOGIN_USER_KEY);
        assertEquals("/profile", Constants.RESOURCE_PREFIX);
        assertEquals(2, Constants.CAPTCHA_EXPIRATION);
    }

    @Test
    void JWT声明键名()
    {
        assertEquals("userid", Constants.JWT_USERID);
        assertEquals("sub", Constants.JWT_USERNAME);
        assertEquals("avatar", Constants.JWT_AVATAR);
        assertEquals("created", Constants.JWT_CREATED);
        assertEquals("authorities", Constants.JWT_AUTHORITIES);
    }

    @Test
    void 反序列化与任务执行白名单()
    {
        assertArrayEquals(new String[] { "org.springframework", "ai.lawyers" }, Constants.JSON_WHITELIST_STR);
        assertArrayEquals(new String[] { "ai.lawyers.quartz.task" }, Constants.JOB_WHITELIST_STR);
        assertArrayEquals(new String[] { "rmi:", "ldap:", "ldaps:" },
                new String[] { Constants.LOOKUP_RMI, Constants.LOOKUP_LDAP, Constants.LOOKUP_LDAPS });
        assertEquals("java.net.URL", Constants.JOB_ERROR_STR[0]);
    }
}
