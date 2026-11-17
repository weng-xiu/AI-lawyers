package ai.lawyers.system.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

/**
 * 在线会话测试：会话信息字段读写。
 *
 * @author ai-lawyers
 */
class SysUserOnlineTest
{
    @Test
    void 字段读写()
    {
        SysUserOnline online = new SysUserOnline();
        online.setTokenId("token-1");
        online.setDeptName("技术部");
        online.setUserName("张三");
        online.setIpaddr("10.0.0.1");
        online.setLoginLocation("内网IP");
        online.setBrowser("Chrome");
        online.setOs("Windows 10");
        online.setLoginTime(1700000000000L);

        assertEquals("token-1", online.getTokenId());
        assertEquals("技术部", online.getDeptName());
        assertEquals("张三", online.getUserName());
        assertEquals("10.0.0.1", online.getIpaddr());
        assertEquals("内网IP", online.getLoginLocation());
        assertEquals("Chrome", online.getBrowser());
        assertEquals("Windows 10", online.getOs());
        assertEquals(1700000000000L, online.getLoginTime());
    }

    @Test
    void 默认字段为空()
    {
        SysUserOnline online = new SysUserOnline();

        assertNull(online.getTokenId());
        assertNull(online.getLoginTime());
    }
}
