package ai.lawyers.common.core.domain.entity;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;

import org.junit.jupiter.api.Test;

/**
 * 角色实体测试：管理员判定、树形选择标记与关联标识读写。
 *
 * @author ai-lawyers
 */
class SysRoleTest
{
    @Test
    void 构造器_仅角色标识()
    {
        SysRole role = new SysRole(2L);

        assertEquals(2L, role.getRoleId());
        assertNull(role.getRoleName());
    }

    @Test
    void isAdmin_角色标识为1时成立()
    {
        assertTrue(new SysRole(1L).isAdmin());
        assertTrue(SysRole.isAdmin(1L));

        assertFalse(new SysRole(2L).isAdmin());
        assertFalse(SysRole.isAdmin(null));
    }

    @Test
    void 默认树选择标记与存在标识为false()
    {
        SysRole role = new SysRole();

        assertFalse(role.isMenuCheckStrictly());
        assertFalse(role.isDeptCheckStrictly());
        assertFalse(role.isFlag());
    }

    @Test
    void 字段与关联标识读写()
    {
        SysRole role = new SysRole();
        role.setRoleName("律师");
        role.setRoleKey("lawyer");
        role.setRoleSort(1);
        role.setDataScope("1");
        role.setStatus("0");
        role.setDelFlag("0");
        role.setMenuCheckStrictly(true);
        role.setDeptCheckStrictly(true);
        role.setFlag(true);
        role.setMenuIds(new Long[] { 1L, 2L });
        role.setDeptIds(new Long[] { 3L });
        role.setPermissions(Set.of("system:user:list"));

        assertEquals("律师", role.getRoleName());
        assertEquals("lawyer", role.getRoleKey());
        assertEquals(1, role.getRoleSort());
        assertEquals("1", role.getDataScope());
        assertEquals("0", role.getStatus());
        assertEquals("0", role.getDelFlag());
        assertTrue(role.isMenuCheckStrictly());
        assertTrue(role.isDeptCheckStrictly());
        assertTrue(role.isFlag());
        assertArrayEquals(new Long[] { 1L, 2L }, role.getMenuIds());
        assertArrayEquals(new Long[] { 3L }, role.getDeptIds());
        assertEquals(Set.of("system:user:list"), role.getPermissions());
    }
}
