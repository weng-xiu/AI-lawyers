package ai.lawyers.common.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

/**
 * 精确浮点运算测试：加减乘除与四舍五入。
 *
 * @author ai-lawyers
 */
class ArithTest
{
    @Test
    void add_规避二进制浮点误差()
    {
        assertEquals(0.3d, Arith.add(0.1d, 0.2d));
    }

    @Test
    void sub_规避二进制浮点误差()
    {
        assertEquals(0.9d, Arith.sub(1.0d, 0.1d));
    }

    @Test
    void mul_规避二进制浮点误差()
    {
        assertEquals(3.6d, Arith.mul(1.2d, 3.0d));
    }

    @Test
    void div_默认保留十位小数()
    {
        assertEquals(0.3333333333d, Arith.div(1.0d, 3.0d));
        assertEquals(2.5d, Arith.div(5.0d, 2.0d));
    }

    @Test
    void div_指定精度四舍五入()
    {
        assertEquals(0.33d, Arith.div(1.0d, 3.0d, 2));
    }

    @Test
    void div_被除数为零返回零避免除零异常()
    {
        assertEquals(0.0d, Arith.div(0.0d, 3.0d));
    }

    @Test
    void div_精度为负抛参数异常()
    {
        assertThrows(IllegalArgumentException.class, () -> Arith.div(1.0d, 3.0d, -1));
    }

    @Test
    void round_按位数四舍五入()
    {
        assertEquals(1.24d, Arith.round(1.235d, 2));
        assertEquals(2.0d, Arith.round(1.5d, 0));
    }

    @Test
    void round_精度为负抛参数异常()
    {
        assertThrows(IllegalArgumentException.class, () -> Arith.round(1.5d, -1));
    }
}
