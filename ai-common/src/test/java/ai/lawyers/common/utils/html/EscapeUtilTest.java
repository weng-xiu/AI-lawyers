package ai.lawyers.common.utils.html;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

/**
 * HTML 转义工具测试：编码、解码与标签清理。
 *
 * @author ai-lawyers
 */
class EscapeUtilTest
{
    @Test
    void escape_特殊字符转为百分号编码()
    {
        assertEquals("%27%22%26%3c%3e", EscapeUtil.escape("'\"&<>"));
    }

    @Test
    void escape_中文与换行分别按unicode和单字节编码()
    {
        assertEquals("%u6cd5%0a", EscapeUtil.escape("法\n"));
    }

    @Test
    void escape_空文本返回空串()
    {
        assertEquals("", EscapeUtil.escape(null));
        assertEquals("", EscapeUtil.escape(""));
    }

    @Test
    void unescape_还原转义内容()
    {
        String html = "<script>alert(1)</script>";

        assertEquals(html, EscapeUtil.unescape(EscapeUtil.escape(html)));
    }

    @Test
    void unescape_保留普通文本并处理空值()
    {
        assertEquals("a b", EscapeUtil.unescape("a%20b"));
        assertNull(EscapeUtil.unescape(null));
    }

    @Test
    void clean_清除危险标签保留内容()
    {
        assertEquals("alert(1)", EscapeUtil.clean("<script>alert(1)</script>"));
        assertEquals("无标签", EscapeUtil.clean("无标签"));
    }

    @Test
    void clean_保留白名单内的加粗标签()
    {
        assertEquals("<b>加粗</b>", EscapeUtil.clean("<b>加粗</b>"));
    }
}
