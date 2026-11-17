package ai.lawyers.common.core.text;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;

import org.junit.jupiter.api.Test;

/**
 * 类型转换器测试：空值兜底、基础类型互转、布尔/枚举/大数转换、
 * 数组拆分、字节与字符集转换、全角半角互换、金额大写。
 *
 * @author ai-lawyers
 */
class ConvertTest
{
    /** 枚举转换测试用类型 */
    private enum Color
    {
        RED, GREEN
    }

    @Test
    void toStr_空值与对象转换()
    {
        assertNull(Convert.toStr(null));
        assertEquals("d", Convert.toStr(null, "d"));
        assertEquals("abc", Convert.toStr("abc"));
        assertEquals("123", Convert.toStr(123));
    }

    @Test
    void toChar_空值默认值与首字符()
    {
        assertNull(Convert.toChar(null));
        assertEquals('x', Convert.toChar(null, 'x'));
        assertEquals('a', Convert.toChar("abc"));
        // 空白字符串按空处理，返回默认值
        assertEquals('x', Convert.toChar(" ", 'x'));
        assertEquals('A', Convert.toChar('A'));
    }

    @Test
    void toByte与toShort_数值与字符串()
    {
        assertEquals(Byte.valueOf((byte) 12), Convert.toByte("12"));
        assertEquals(Byte.valueOf((byte) 12), Convert.toByte(12));
        assertNull(Convert.toByte("x"));
        assertEquals(Byte.valueOf((byte) 9), Convert.toByte("x", (byte) 9));

        assertEquals(Short.valueOf((short) 3), Convert.toShort(3));
        assertEquals(Short.valueOf((short) 30), Convert.toShort(" 30 "));
        assertEquals(Short.valueOf((short) 7), Convert.toShort("bad", (short) 7));
    }

    @Test
    void toInt与toLong_默认值与科学计数法()
    {
        assertEquals(Integer.valueOf(42), Convert.toInt(" 42 "));
        assertNull(Convert.toInt("x"));
        assertEquals(Integer.valueOf(5), Convert.toInt(null, 5));

        assertEquals(Long.valueOf(100L), Convert.toLong(100));
        // 字符串走 BigDecimal，支持科学计数法
        assertEquals(Long.valueOf(1500L), Convert.toLong("1.5E3"));
        assertNull(Convert.toLong("x"));
    }

    @Test
    void toDouble与toFloat_字符串解析()
    {
        assertEquals(1.25d, Convert.toDouble("1.25").doubleValue());
        assertEquals(2.5d, Convert.toDouble(2.5).doubleValue());
        assertNull(Convert.toDouble("x"));

        assertEquals(2.5f, Convert.toFloat("2.5").floatValue());
        assertNull(Convert.toFloat("x"));
        assertEquals(1.5f, Convert.toFloat(null, 1.5f).floatValue());
    }

    @Test
    void toNumber_数字格式化解析()
    {
        assertEquals(1234, Convert.toNumber("1,234").intValue());
        assertNull(Convert.toNumber("x"));
        assertEquals(6, Convert.toNumber("x", 6).intValue());
    }

    @Test
    void toBool_支持中文与常见别名()
    {
        assertTrue(Convert.toBool("true"));
        assertTrue(Convert.toBool("YES"));
        assertTrue(Convert.toBool("ok"));
        assertTrue(Convert.toBool("1"));
        assertTrue(Convert.toBool("是"));
        assertFalse(Convert.toBool("NO"));
        assertFalse(Convert.toBool("0"));
        assertFalse(Convert.toBool("否"));
        // 无法识别时返回默认值
        assertEquals(Boolean.TRUE, Convert.toBool("unknown", true));
        assertNull(Convert.toBool("unknown"));
        assertTrue(Convert.toBool(Boolean.TRUE));
    }

    @Test
    void toEnum_名称与实例转换()
    {
        assertSame(Color.RED, Convert.toEnum(Color.class, "RED"));
        assertSame(Color.GREEN, Convert.toEnum(Color.class, Color.GREEN));
        assertSame(Color.GREEN, Convert.toEnum(Color.class, "NOT_EXIST", Color.GREEN));
        assertNull(Convert.toEnum(Color.class, null));
    }

    @Test
    void toBigInteger与toBigDecimal_类型分支()
    {
        assertEquals(BigInteger.valueOf(100L), Convert.toBigInteger("100"));
        assertEquals(BigInteger.valueOf(100L), Convert.toBigInteger(100L));
        assertNull(Convert.toBigInteger("x"));
        assertEquals(BigInteger.ONE, Convert.toBigInteger(null, BigInteger.ONE));

        assertEquals(new BigDecimal("3"), Convert.toBigDecimal(3));
        assertEquals(new BigDecimal("1.5"), Convert.toBigDecimal(1.5d));
        assertEquals("1.10", Convert.toBigDecimal("1.10").toPlainString());
        assertNull(Convert.toBigDecimal("x"));
    }

    @Test
    void 数组转换_默认值与空串()
    {
        assertArrayEquals(new Integer[] { 1, 2, 0 }, Convert.toIntArray("1,2,x"));
        assertArrayEquals(new Integer[] {}, Convert.toIntArray(""));
        assertArrayEquals(new Integer[] { 1, 2 }, Convert.toIntArray("-", "1-2"));

        assertArrayEquals(new Long[] { 1L, 2L, null }, Convert.toLongArray("1,2,x"));
        assertArrayEquals(new Long[] {}, Convert.toLongArray(""));

        assertArrayEquals(new String[] { "a", "b" }, Convert.toStrArray("a,b"));
        assertArrayEquals(new String[] {}, Convert.toStrArray(""));
    }

    @Test
    void str与utf8Str_字节缓冲区转换()
    {
        assertNull(Convert.utf8Str(null));
        assertEquals("中文", Convert.utf8Str("中文"));
        assertEquals("AB", Convert.str(new Byte[] { 65, 66 }, StandardCharsets.UTF_8));
        assertNull(Convert.str((byte[]) null, StandardCharsets.UTF_8));
        assertEquals("hi", Convert.str(ByteBuffer.wrap("hi".getBytes(StandardCharsets.UTF_8)), StandardCharsets.UTF_8));
        assertNull(Convert.str((ByteBuffer) null, "UTF-8"));
        assertEquals("中文", Convert.str("中文".getBytes(StandardCharsets.UTF_8), "UTF-8"));
    }

    @Test
    void 全角半角互转_支持排除集合()
    {
        assertEquals("Ａ　ｂ", Convert.toSBC("A b"));
        assertEquals("A b", Convert.toDBC("Ａ　ｂ"));

        Set<Character> keep = new HashSet<>();
        keep.add('A');
        assertEquals("A　ｂ", Convert.toSBC("A b", keep));

        Set<Character> keepFullWidth = new HashSet<>();
        keepFullWidth.add('Ａ');
        assertEquals("Ａ b", Convert.toDBC("Ａ　ｂ", keepFullWidth));
    }

    @Test
    void digitUppercase_零与整数金额()
    {
        assertEquals("零元整", Convert.digitUppercase(0));
        assertEquals("壹佰元整", Convert.digitUppercase(100));
        assertTrue(Convert.digitUppercase(-100).startsWith("负"));
    }
}
