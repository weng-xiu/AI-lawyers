package ai.lawyers.common.constant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * 用户常量测试：状态标记、菜单类型与长度限制。
 *
 * @author ai-lawyers
 */
class UserConstantsTest
{
    @Test
    void 状态与唯一性标记()
    {
        assertEquals("SYS_USER", UserConstants.SYS_USER);
        assertEquals("0", UserConstants.NORMAL);
        assertEquals("1", UserConstants.USER_DISABLE);
        assertEquals("0", UserConstants.ROLE_NORMAL);
        assertEquals("1", UserConstants.ROLE_DISABLE);
        assertEquals("0", UserConstants.DEPT_NORMAL);
        assertEquals("1", UserConstants.DEPT_DISABLE);
        assertEquals("0", UserConstants.DICT_NORMAL);

        assertTrue(UserConstants.UNIQUE);
        assertFalse(UserConstants.NOT_UNIQUE);
    }

    @Test
    void 菜单类型与组件标识()
    {
        assertEquals("Y", UserConstants.YES);
        assertEquals("0", UserConstants.YES_FRAME);
        assertEquals("1", UserConstants.NO_FRAME);
        assertEquals("M", UserConstants.TYPE_DIR);
        assertEquals("C", UserConstants.TYPE_MENU);
        assertEquals("F", UserConstants.TYPE_BUTTON);
        assertEquals("Layout", UserConstants.LAYOUT);
        assertEquals("ParentView", UserConstants.PARENT_VIEW);
        assertEquals("InnerLink", UserConstants.INNER_LINK);
    }

    @Test
    void 用户名与密码长度限制()
    {
        assertEquals(2, UserConstants.USERNAME_MIN_LENGTH);
        assertEquals(20, UserConstants.USERNAME_MAX_LENGTH);
        assertEquals(5, UserConstants.PASSWORD_MIN_LENGTH);
        assertEquals(20, UserConstants.PASSWORD_MAX_LENGTH);
    }
}
