package ai.lawyers.common.core.domain.entity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * 用户实体测试：管理员判定、律师扩展字段与关联对象读写。
 *
 * @author ai-lawyers
 */
class SysUserTest
{
    @Test
    void 构造器_仅用户标识()
    {
        SysUser user = new SysUser(10L);

        assertEquals(10L, user.getUserId());
        assertNull(user.getUserName());
    }

    @Test
    void isAdmin_仅用户标识为1时成立()
    {
        assertTrue(new SysUser(1L).isAdmin());
        assertTrue(SysUser.isAdmin(1L));

        assertFalse(new SysUser(2L).isAdmin());
        assertFalse(SysUser.isAdmin(null));
        assertFalse(new SysUser().isAdmin());
    }

    @Test
    void 律师扩展字段读写()
    {
        SysUser user = new SysUser();
        user.setLawyerFlag("1");
        user.setLawyerLicense("11010120260000001");
        user.setLawFirm("示例律师事务所");
        user.setSpecialty("婚姻家事,合同纠纷");
        user.setPracticeYears(8);
        user.setLawyerIntro("擅长民商事诉讼");
        user.setAgentId(1001L);
        user.setSipExtension("8001");
        user.setCallMode("1");
        user.setUserType("01");

        assertEquals("1", user.getLawyerFlag());
        assertEquals("11010120260000001", user.getLawyerLicense());
        assertEquals("示例律师事务所", user.getLawFirm());
        assertEquals("婚姻家事,合同纠纷", user.getSpecialty());
        assertEquals(8, user.getPracticeYears());
        assertEquals("擅长民商事诉讼", user.getLawyerIntro());
        assertEquals(1001L, user.getAgentId());
        assertEquals("8001", user.getSipExtension());
        assertEquals("1", user.getCallMode());
        assertEquals("01", user.getUserType());
    }

    @Test
    void 基础字段与关联对象读写()
    {
        SysUser user = new SysUser();
        user.setUserName("zhangsan");
        user.setNickName("张三");
        user.setEmail("zhangsan@example.com");
        user.setPhonenumber("13800000000");
        user.setSex("0");
        user.setStatus("0");
        user.setDelFlag("0");

        SysDept dept = new SysDept();
        user.setDept(dept);
        List<SysRole> roles = List.of(new SysRole());
        user.setRoles(roles);
        user.setRoleIds(new Long[] { 1L, 2L });
        user.setPostIds(new Long[] { 3L });
        user.setRoleId(1L);

        assertEquals("zhangsan", user.getUserName());
        assertEquals("张三", user.getNickName());
        assertEquals("zhangsan@example.com", user.getEmail());
        assertEquals("13800000000", user.getPhonenumber());
        assertEquals("0", user.getSex());
        assertEquals("0", user.getStatus());
        assertEquals("0", user.getDelFlag());
        assertSame(dept, user.getDept());
        assertSame(roles, user.getRoles());
        assertEquals(2, user.getRoleIds().length);
        assertEquals(1, user.getPostIds().length);
        assertEquals(1L, user.getRoleId());
    }
}
