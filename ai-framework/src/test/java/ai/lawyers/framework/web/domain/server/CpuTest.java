package ai.lawyers.framework.web.domain.server;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/**
 * CPU 信息测试：使用率按总量换算并保留两位小数。
 *
 * @author ai-lawyers
 */
class CpuTest
{
    @Test
    void getCpuNum_返回核心数()
    {
        Cpu cpu = new Cpu();
        cpu.setCpuNum(8);

        assertEquals(8, cpu.getCpuNum());
    }

    @Test
    void getTotal_总量放大一百倍()
    {
        Cpu cpu = new Cpu();
        cpu.setTotal(12.5);

        assertEquals(1250.0, cpu.getTotal());
    }

    @Test
    void getSys与getUsed与getWait与getFree_按总量折算百分比()
    {
        Cpu cpu = new Cpu();
        cpu.setTotal(200);
        cpu.setSys(50);
        cpu.setUsed(120);
        cpu.setWait(10);
        cpu.setFree(20);

        assertEquals(25.0, cpu.getSys());
        assertEquals(60.0, cpu.getUsed());
        assertEquals(5.0, cpu.getWait());
        assertEquals(10.0, cpu.getFree());
    }

    @Test
    void 百分比_保留两位小数()
    {
        Cpu cpu = new Cpu();
        cpu.setTotal(3);
        cpu.setUsed(1);

        assertEquals(33.33, cpu.getUsed());
    }
}
