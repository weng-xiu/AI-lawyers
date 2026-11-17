package ai.lawyers.framework.web.domain.server;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

/**
 * 磁盘文件信息测试：字段读写。
 *
 * @author ai-lawyers
 */
class SysFileTest
{
    @Test
    void setter与getter_读写全部字段()
    {
        SysFile sysFile = new SysFile();
        sysFile.setDirName("/");
        sysFile.setSysTypeName("ext4");
        sysFile.setTypeName("系统盘");
        sysFile.setTotal("100.0 GB");
        sysFile.setFree("40.0 GB");
        sysFile.setUsed("60.0 GB");
        sysFile.setUsage(60.0);

        assertEquals("/", sysFile.getDirName());
        assertEquals("ext4", sysFile.getSysTypeName());
        assertEquals("系统盘", sysFile.getTypeName());
        assertEquals("100.0 GB", sysFile.getTotal());
        assertEquals("40.0 GB", sysFile.getFree());
        assertEquals("60.0 GB", sysFile.getUsed());
        assertEquals(60.0, sysFile.getUsage());
    }

    @Test
    void 默认构造_字符串为空且使用率为零()
    {
        SysFile sysFile = new SysFile();

        assertNull(sysFile.getDirName());
        assertNull(sysFile.getSysTypeName());
        assertNull(sysFile.getTypeName());
        assertNull(sysFile.getTotal());
        assertNull(sysFile.getFree());
        assertNull(sysFile.getUsed());
        assertEquals(0.0, sysFile.getUsage());
    }
}
