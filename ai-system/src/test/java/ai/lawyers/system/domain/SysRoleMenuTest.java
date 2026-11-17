package ai.lawyers.system.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * 角色菜单关联实体测试：属性读写与字符串输出。
 *
 * @author ai-lawyers
 */
class SysRoleMenuTest
{
    @Test
    void 属性读写()
    {
        SysRoleMenu roleMenu = new SysRoleMenu();
        roleMenu.setRoleId(2L);
        roleMenu.setMenuId(105L);

        assertEquals(2L, roleMenu.getRoleId());
        assertEquals(105L, roleMenu.getMenuId());
    }

    @Test
    void 默认值为空()
    {
        SysRoleMenu roleMenu = new SysRoleMenu();

        assertNull(roleMenu.getRoleId());
        assertNull(roleMenu.getMenuId());
    }

    @Test
    void 字符串输出包含关联标识()
    {
        SysRoleMenu roleMenu = new SysRoleMenu();
        roleMenu.setRoleId(3L);
        roleMenu.setMenuId(2001L);

        String text = roleMenu.toString();
        assertTrue(text.contains("roleId"));
        assertTrue(text.contains("2001"));
    }
}
