package ai.lawyers.framework.web.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedList;
import java.util.List;

import org.junit.jupiter.api.Test;

import ai.lawyers.framework.web.domain.server.Cpu;
import ai.lawyers.framework.web.domain.server.Jvm;
import ai.lawyers.framework.web.domain.server.Mem;
import ai.lawyers.framework.web.domain.server.Sys;
import ai.lawyers.framework.web.domain.server.SysFile;

/**
 * 服务器信息测试：默认子对象、属性读写与文件大小换算。
 *
 * @author ai-lawyers
 */
class ServerTest
{
    @Test
    void 默认构造_各子对象已初始化且磁盘列表为空()
    {
        Server server = new Server();

        assertNotNull(server.getCpu());
        assertNotNull(server.getMem());
        assertNotNull(server.getJvm());
        assertNotNull(server.getSys());
        assertTrue(server.getSysFiles().isEmpty());
        assertTrue(server.getSysFiles() instanceof LinkedList);
    }

    @Test
    void setter_替换各子对象()
    {
        Server server = new Server();
        Cpu cpu = new Cpu();
        Mem mem = new Mem();
        Jvm jvm = new Jvm();
        Sys sys = new Sys();
        List<SysFile> files = new LinkedList<>();

        server.setCpu(cpu);
        server.setMem(mem);
        server.setJvm(jvm);
        server.setSys(sys);
        server.setSysFiles(files);

        assertSame(cpu, server.getCpu());
        assertSame(mem, server.getMem());
        assertSame(jvm, server.getJvm());
        assertSame(sys, server.getSys());
        assertSame(files, server.getSysFiles());
    }

    @Test
    void convertFileSize_字节与KB换算()
    {
        Server server = new Server();

        assertEquals("500 B", server.convertFileSize(500));
        assertEquals("1.0 KB", server.convertFileSize(1024));
        // 超过 100 的 KB 不保留小数
        assertEquals("150 KB", server.convertFileSize(1024L * 150));
    }

    @Test
    void convertFileSize_MB与GB换算()
    {
        Server server = new Server();

        assertEquals("5.0 MB", server.convertFileSize(1024L * 1024 * 5));
        assertEquals("200 MB", server.convertFileSize(1024L * 1024 * 200));
        assertEquals("2.0 GB", server.convertFileSize(1024L * 1024 * 1024 * 2));
    }
}
