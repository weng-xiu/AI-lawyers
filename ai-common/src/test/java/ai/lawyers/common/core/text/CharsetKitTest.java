package ai.lawyers.common.core.text;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;

/**
 * 字符集工具测试：字符集常量、默认值回退与编码转换。
 *
 * @author ai-lawyers
 */
class CharsetKitTest
{
    @Test
    void charset_空值回退默认字符集()
    {
        assertSame(Charset.defaultCharset(), CharsetKit.charset(null));
        assertSame(Charset.defaultCharset(), CharsetKit.charset(""));
        assertSame(StandardCharsets.UTF_8, CharsetKit.charset("UTF-8"));
        assertSame(CharsetKit.CHARSET_GBK, CharsetKit.charset("GBK"));
    }

    @Test
    void convert_ISO8859_1与UTF8之间往返()
    {
        String original = "法律援助";
        String corrupted = new String(original.getBytes(StandardCharsets.UTF_8), StandardCharsets.ISO_8859_1);

        assertEquals(original, CharsetKit.convert(corrupted, StandardCharsets.ISO_8859_1, StandardCharsets.UTF_8));
        assertEquals(original,
                CharsetKit.convert(corrupted, CharsetKit.ISO_8859_1, CharsetKit.UTF_8));
    }

    @Test
    void convert_空字符集使用默认值()
    {
        String original = "测试";
        String corrupted = new String(original.getBytes(StandardCharsets.UTF_8), StandardCharsets.ISO_8859_1);

        // 源字符集为空按 ISO-8859-1，目标字符集为空按 UTF-8
        assertEquals(original, CharsetKit.convert(corrupted, (Charset) null, (Charset) null));
        assertEquals(original, CharsetKit.convert(corrupted, (Charset) null, StandardCharsets.UTF_8));
    }

    @Test
    void convert_空串与同字符集原样返回()
    {
        assertNull(CharsetKit.convert(null, StandardCharsets.UTF_8, StandardCharsets.UTF_8));
        assertEquals("", CharsetKit.convert("", StandardCharsets.UTF_8, StandardCharsets.ISO_8859_1));
        assertEquals("abc", CharsetKit.convert("abc", StandardCharsets.UTF_8, StandardCharsets.UTF_8));
    }

    @Test
    void systemCharset_返回默认字符集名称()
    {
        assertEquals(Charset.defaultCharset().name(), CharsetKit.systemCharset());
    }
}
