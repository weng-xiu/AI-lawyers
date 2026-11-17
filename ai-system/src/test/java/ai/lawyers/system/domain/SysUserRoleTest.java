package ai.lawyers.system.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * 用户角色关联实体测试：属性读写与字符串输出。
 *
 * @author ai-lawyers
 */
class SysUserRoleTest
{
    @Test
    void 属性读写()
    {
        SysUserRole userRole = new SysUserRole();
        userRole.setUserId(200L);
        userRole.setRoleId(3L);

        assertEquals(200L, userRole.getUserId());
        assertEquals(3L, userRole.getRoleId());
    }

    @Test
    void 默认值为空()
    {
        SysUserRole userRole = new SysUserRole();

        assertNull(userRole.getUserId());
        assertNull(userRole.getRoleId());
    }

    @Test
    void 字符串输出包含关联标识()
    {
        SysUserRole userRole = new SysUserRole();
        userRole.setUserId(201L);
        userRole.setRoleId(4L);

        String text = userRole.toString();
        assertTrue(text.contains("roleId"));
        assertTrue(text.contains("201"));
    }
}
