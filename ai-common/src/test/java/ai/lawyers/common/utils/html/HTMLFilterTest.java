package ai.lawyers.common.utils.html;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * HTML 富文本过滤测试：纯文本透传、注释剥离、白名单标签保留、
 * 非法标签与属性移除、javascript 协议锚点化、未闭合标签补齐、空标签对清理。
 *
 * @author ai-lawyers
 */
class HTMLFilterTest
{
    private final HTMLFilter filter = new HTMLFilter();

    @Test
    void 默认配置开关()
    {
        assertTrue(filter.isStripComments());
        assertFalse(filter.isAlwaysMakeTags());
    }

    @Test
    void 纯文本原样返回()
    {
        assertEquals("你好，请问有什么可以帮您？", filter.filter("你好，请问有什么可以帮您？"));
    }

    @Test
    void 剥离html注释()
    {
        assertEquals("前后", filter.filter("前<!-- 这是注释 -->后"));
    }

    @Test
    void 白名单标签保留并统一小写()
    {
        assertEquals("<b>粗体</b>", filter.filter("<b>粗体</b>"));
        assertEquals("<b>粗体</b>", filter.filter("<B>粗体</B>"));
    }

    @Test
    void 非白名单标签移除但保留文本()
    {
        assertEquals("alert(1)", filter.filter("<script>alert(1)</script>"));
    }

    @Test
    void 图片仅保留白名单属性()
    {
        String result = filter.filter("<img src=\"a.png\" onerror=\"alert(1)\">");

        assertTrue(result.contains("<img"));
        assertTrue(result.contains("src="));
        assertTrue(result.contains("a.png"));
        assertFalse(result.contains("onerror"));
        assertTrue(result.endsWith(" />"));
    }

    @Test
    void javascript协议改为本地锚点()
    {
        String result = filter.filter("<a href=\"javascript:alert(1)\">点我</a>");

        assertTrue(result.contains("#alert(1)"));
        assertFalse(result.contains("javascript:"));
        assertTrue(result.contains("点我"));
    }

    @Test
    void 合法协议链接保留()
    {
        String result = filter.filter("<a href=\"https://example.com/a\" target=\"_blank\">链接</a>");

        assertTrue(result.contains("https://example.com/a"));
        assertTrue(result.contains("target="));
        assertTrue(result.contains("链接"));
    }

    @Test
    void 未闭合标签自动补齐()
    {
        assertEquals("<b>缺右</b>", filter.filter("<b>缺右"));
    }

    @Test
    void 空标签对被清理()
    {
        assertEquals("", filter.filter("<b></b>"));
    }

    @Test
    void htmlSpecialChars转义特殊字符()
    {
        assertEquals("a&amp;b&quot;c&lt;d&gt;e", HTMLFilter.htmlSpecialChars("a&b\"c<d>e"));
    }

    @Test
    void chr按码点转字符()
    {
        assertEquals("A", HTMLFilter.chr(65));
    }
}
