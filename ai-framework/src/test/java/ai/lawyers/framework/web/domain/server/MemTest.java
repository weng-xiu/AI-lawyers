package ai.lawyers.framework.web.domain.server;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/**
 * 内存信息测试：字节到 GB 换算与使用率计算。
 *
 * @author ai-lawyers
 */
class MemTest
{
    private static final long ONE_GB = 1024L * 1024 * 1024;

    @Test
    void getTotal与getUsed与getFree_换算为GB()
    {
        Mem mem = new Mem();
        mem.setTotal(ONE_GB * 4);
        mem.setUsed(ONE_GB * 2);
        mem.setFree(ONE_GB * 1);

        assertEquals(4.0, mem.getTotal());
        assertEquals(2.0, mem.getUsed());
        assertEquals(1.0, mem.getFree());
    }

    @Test
    void getTotal_非整GB时保留两位小数()
    {
        Mem mem = new Mem();
        mem.setTotal(ONE_GB / 2);

        assertEquals(0.5, mem.getTotal());
    }

    @Test
    void getUsage_按已用与总量计算百分比()
    {
        Mem mem = new Mem();
        mem.setTotal(ONE_GB * 4);
        mem.setUsed(ONE_GB);

        assertEquals(25.0, mem.getUsage());
    }
}
