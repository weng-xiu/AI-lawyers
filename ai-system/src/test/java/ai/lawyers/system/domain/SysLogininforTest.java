package ai.lawyers.system.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.Date;
import org.junit.jupiter.api.Test;

/**
 * 登录日志实体测试：属性读写与默认值。
 *
 * @author ai-lawyers
 */
class SysLogininforTest
{
    @Test
    void 属性读写()
    {
        Date loginTime = new Date();
        SysLogininfor logininfor = new SysLogininfor();
        logininfor.setInfoId(1L);
        logininfor.setUserName("admin");
        logininfor.setStatus("0");
        logininfor.setIpaddr("127.0.0.1");
        logininfor.setLoginLocation("内网IP");
        logininfor.setBrowser("Chrome");
        logininfor.setOs("Windows 10");
        logininfor.setMsg("登录成功");
        logininfor.setLoginTime(loginTime);

        assertEquals(1L, logininfor.getInfoId());
        assertEquals("admin", logininfor.getUserName());
        assertEquals("0", logininfor.getStatus());
        assertEquals("127.0.0.1", logininfor.getIpaddr());
        assertEquals("内网IP", logininfor.getLoginLocation());
        assertEquals("Chrome", logininfor.getBrowser());
        assertEquals("Windows 10", logininfor.getOs());
        assertEquals("登录成功", logininfor.getMsg());
        assertEquals(loginTime, logininfor.getLoginTime());
    }

    @Test
    void 默认值为空()
    {
        SysLogininfor logininfor = new SysLogininfor();

        assertNull(logininfor.getInfoId());
        assertNull(logininfor.getUserName());
        assertNull(logininfor.getStatus());
        assertNull(logininfor.getLoginTime());
    }
}
