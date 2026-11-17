package ai.lawyers.framework.web.domain.server;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

/**
 * 系统信息测试：字段读写。
 *
 * @author ai-lawyers
 */
class SysTest
{
    @Test
    void setter与getter_读写全部字段()
    {
        Sys sys = new Sys();
        sys.setComputerName("ai-lawyers-01");
        sys.setComputerIp("10.0.0.8");
        sys.setUserDir("/opt/ai-lawyers");
        sys.setOsName("Linux");
        sys.setOsArch("amd64");

        assertEquals("ai-lawyers-01", sys.getComputerName());
        assertEquals("10.0.0.8", sys.getComputerIp());
        assertEquals("/opt/ai-lawyers", sys.getUserDir());
        assertEquals("Linux", sys.getOsName());
        assertEquals("amd64", sys.getOsArch());
    }

    @Test
    void 默认构造_字段为空()
    {
        Sys sys = new Sys();

        assertNull(sys.getComputerName());
        assertNull(sys.getComputerIp());
        assertNull(sys.getUserDir());
        assertNull(sys.getOsName());
        assertNull(sys.getOsArch());
    }
}
