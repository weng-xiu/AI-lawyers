package ai.lawyers.framework.web.domain.server;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * JVM 信息测试：内存换算、使用率与运行时信息。
 *
 * @author ai-lawyers
 */
class JvmTest
{
    private static final long ONE_MB = 1024L * 1024;

    @Test
    void getTotal与getMax与getFree_换算为MB()
    {
        Jvm jvm = new Jvm();
        jvm.setTotal(ONE_MB * 512);
        jvm.setMax(ONE_MB * 1024);
        jvm.setFree(ONE_MB * 256);

        assertEquals(512.0, jvm.getTotal());
        assertEquals(1024.0, jvm.getMax());
        assertEquals(256.0, jvm.getFree());
    }

    @Test
    void getUsed与getUsage_按总量与空闲内存计算()
    {
        Jvm jvm = new Jvm();
        jvm.setTotal(ONE_MB * 512);
        jvm.setFree(ONE_MB * 128);

        assertEquals(384.0, jvm.getUsed());
        assertEquals(75.0, jvm.getUsage());
    }

    @Test
    void getVersion与getHome_返回设置值()
    {
        Jvm jvm = new Jvm();
        jvm.setVersion("17.0.20");
        jvm.setHome("D:\\jdks\\dragonwell17");

        assertEquals("17.0.20", jvm.getVersion());
        assertEquals("D:\\jdks\\dragonwell17", jvm.getHome());
    }

    @Test
    void getName与getInputArgs_来自运行时()
    {
        Jvm jvm = new Jvm();

        assertNotNull(jvm.getName());
        assertNotNull(jvm.getInputArgs());
    }

    @Test
    void getStartTime与getRunTime_格式正确()
    {
        Jvm jvm = new Jvm();

        assertTrue(jvm.getStartTime().matches("\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}"));
        assertTrue(jvm.getRunTime().contains("天"));
        assertTrue(jvm.getRunTime().contains("小时"));
    }
}
