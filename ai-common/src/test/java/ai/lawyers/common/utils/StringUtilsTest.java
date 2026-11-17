package ai.lawyers.common.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * 字符串工具测试：空值兜底、脱敏、截取、分隔转集合、包含判断、
 * 驼峰/下划线互转、Ant 路径匹配与补齐。
 *
 * @author ai-lawyers
 */
class StringUtilsTest
{
    @Test
    void nvl_空值取默认值()
    {
        assertEquals("d", StringUtils.nvl(null, "d"));
        assertEquals("x", StringUtils.nvl("x", "d"));
    }

    @Test
    void isEmpty_集合数组映射字符串()
    {
        assertTrue(StringUtils.isEmpty((List<String>) null));
        assertTrue(StringUtils.isEmpty(Collections.emptyList()));
        assertFalse(StringUtils.isEmpty(Collections.singletonList("a")));

        assertTrue(StringUtils.isEmpty((Object[]) null));
        assertTrue(StringUtils.isEmpty(new Object[0]));
        assertFalse(StringUtils.isEmpty(new Object[]{"a"}));

        assertTrue(StringUtils.isEmpty((HashMap<String, String>) null));
        assertTrue(StringUtils.isEmpty(new HashMap<String, String>()));

        assertTrue(StringUtils.isEmpty((String) null));
        assertTrue(StringUtils.isEmpty(""));
        assertTrue(StringUtils.isEmpty(" "));
        assertTrue(StringUtils.isNotEmpty("a"));
    }

    @Test
    void isNull与isArray()
    {
        assertTrue(StringUtils.isNull(null));
        assertTrue(StringUtils.isNotNull(""));
        assertTrue(StringUtils.isArray(new int[0]));
        assertFalse(StringUtils.isArray("x"));
    }

    @Test
    void trim_空值返回空串()
    {
        assertEquals("", StringUtils.trim(null));
        assertEquals("a", StringUtils.trim("  a  "));
    }

    @Test
    void hide_区间脱敏()
    {
        assertEquals("138****8000", StringUtils.hide("13800138000", 3, 7));
        // 起始越界返回空串
        assertEquals("", StringUtils.hide("abc", 5, 6));
        // 起始大于结束不替换，返回空串
        assertEquals("", StringUtils.hide("abcdef", 4, 2));
        // 结束越界按长度截断
        assertEquals("ab****", StringUtils.hide("abcdef", 2, 99));
        assertEquals("", StringUtils.hide(null, 0, 1));
    }

    @Test
    void substring_负偏移与越界兜底()
    {
        assertEquals("", StringUtils.substring(null, 1));
        assertEquals("bc", StringUtils.substring("abc", -2));
        assertEquals("", StringUtils.substring("abc", 5));
        assertEquals("bc", StringUtils.substring("abcd", 1, 3));
        assertEquals("cd", StringUtils.substring("abcd", -2, 4));
        assertEquals("", StringUtils.substring("abcd", 3, 1));
        assertEquals("bcd", StringUtils.substring("abcd", 1, 99));
    }

    @Test
    void substringBetweenLast_首尾标记截取()
    {
        assertEquals("a][b", StringUtils.substringBetweenLast("prefix[a][b]suffix", "[", "]"));
        assertEquals("", StringUtils.substringBetweenLast("no-marker", "[", "]"));
        assertEquals("", StringUtils.substringBetweenLast(null, "[", "]"));
        assertEquals("", StringUtils.substringBetweenLast("abc", "", "]"));
    }

    @Test
    void hasText_空白串视为无文本()
    {
        assertFalse(StringUtils.hasText(null));
        assertFalse(StringUtils.hasText(""));
        assertFalse(StringUtils.hasText("   "));
        assertTrue(StringUtils.hasText(" a "));
    }

    @Test
    void format_占位符顺序替换()
    {
        assertEquals("你好张三，工单8", StringUtils.format("你好{}，工单{}", "张三", 8));
        assertEquals("无占位", StringUtils.format("无占位", new Object[0]));
        assertNull(StringUtils.format(null, "a"));
    }

    @Test
    void ishttp_仅http与https()
    {
        assertTrue(StringUtils.ishttp("http://a.com"));
        assertTrue(StringUtils.ishttp("https://a.com"));
        assertFalse(StringUtils.ishttp("ftp://a.com"));
        assertFalse(StringUtils.ishttp(null));
    }

    @Test
    void str2List与str2Set_过滤空白项()
    {
        assertEquals(Arrays.asList("a", "b", "c"), StringUtils.str2List("a,b,,c", ","));
        assertTrue(StringUtils.str2List(null, ",").isEmpty());
        assertEquals(Arrays.asList("a", "b"), StringUtils.str2List("a, b", ",", true, true));
        assertEquals(2, StringUtils.str2Set("a,a,b", ",").size());
    }

    @Test
    void containsAny_集合与数组任一匹配()
    {
        assertTrue(StringUtils.containsAny(Arrays.asList("a", "b"), "b", "c"));
        assertFalse(StringUtils.containsAny(Arrays.asList("a"), "b"));
        assertFalse(StringUtils.containsAny(null, "a"));
    }

    @Test
    void containsAnyIgnoreCase_忽略大小写()
    {
        assertTrue(StringUtils.containsAnyIgnoreCase("Hello World", "hello", "x"));
        assertFalse(StringUtils.containsAnyIgnoreCase("Hello", "abc"));
        assertFalse(StringUtils.containsAnyIgnoreCase(null, "a"));
    }

    @Test
    void toUnderScoreCase_驼峰转下划线()
    {
        assertEquals("hello_world", StringUtils.toUnderScoreCase("helloWorld"));
        assertEquals("hello", StringUtils.toUnderScoreCase("hello"));
        assertEquals("user_id", StringUtils.toUnderScoreCase("userId"));
        assertNull(StringUtils.toUnderScoreCase(null));
    }

    @Test
    void inStringIgnoreCase_忽略大小写与首尾空白()
    {
        assertTrue(StringUtils.inStringIgnoreCase("ADMIN", " admin "));
        assertFalse(StringUtils.inStringIgnoreCase("admin", "root"));
        assertFalse(StringUtils.inStringIgnoreCase(null, "admin"));
    }

    @Test
    void convertToCamelCase_下划线转首字母大写()
    {
        assertEquals("HelloWorld", StringUtils.convertToCamelCase("HELLO_WORLD"));
        assertEquals("HelloWorld", StringUtils.convertToCamelCase("hello_world"));
        assertEquals("Hello", StringUtils.convertToCamelCase("hello"));
        assertEquals("AB", StringUtils.convertToCamelCase("_a__b_"));
        assertEquals("", StringUtils.convertToCamelCase(null));
        assertEquals("", StringUtils.convertToCamelCase(""));
    }

    @Test
    void toCamelCase_下划线转驼峰()
    {
        assertEquals("userName", StringUtils.toCamelCase("user_name"));
        assertEquals("userName", StringUtils.toCamelCase("USER_NAME"));
        assertEquals("name", StringUtils.toCamelCase("name"));
        assertNull(StringUtils.toCamelCase(null));
    }

    @Test
    void matches与isMatch_Ant路径规则()
    {
        assertTrue(StringUtils.isMatch("/system/**", "/system/user/list"));
        assertFalse(StringUtils.isMatch("/system/*", "/system/user/list"));
        assertTrue(StringUtils.matches("/system/user/list", Arrays.asList("/other/**", "/system/**")));
        assertFalse(StringUtils.matches("", Arrays.asList("/system/**")));
        assertFalse(StringUtils.matches("/system/user", null));
    }

    @Test
    void cast_原样返回()
    {
        Object value = "abc";

        assertEquals("abc", StringUtils.<String>cast(value));
    }

    @Test
    void padl_左补齐与保留末位()
    {
        assertEquals("007", StringUtils.padl(7, 3));
        assertEquals("345", StringUtils.padl(12345, 3));
        assertEquals("xxabc", StringUtils.padl("abc", 5, 'x'));
        assertEquals("def", StringUtils.padl("abcdef", 3, '0'));
        assertEquals("00", StringUtils.padl((String) null, 2, '0'));
    }
}
