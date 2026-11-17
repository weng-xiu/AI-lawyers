package ai.lawyers.system.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * 角色部门关联实体测试：属性读写与字符串输出。
 *
 * @author ai-lawyers
 */
class SysRoleDeptTest
{
    @Test
    void 属性读写()
    {
        SysRoleDept roleDept = new SysRoleDept();
        roleDept.setRoleId(5L);
        roleDept.setDeptId(103L);

        assertEquals(5L, roleDept.getRoleId());
        assertEquals(103L, roleDept.getDeptId());
    }

    @Test
    void 默认值为空()
    {
        SysRoleDept roleDept = new SysRoleDept();

        assertNull(roleDept.getRoleId());
        assertNull(roleDept.getDeptId());
    }

    @Test
    void 字符串输出包含关联标识()
    {
        SysRoleDept roleDept = new SysRoleDept();
        roleDept.setRoleId(6L);
        roleDept.setDeptId(104L);

        String text = roleDept.toString();
        assertTrue(text.contains("deptId"));
        assertTrue(text.contains("104"));
    }
}
