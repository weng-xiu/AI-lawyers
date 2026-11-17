package ai.lawyers.common.core.text;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

/**
 * 字符串格式化测试：占位符顺序替换与转义规则。
 *
 * @author ai-lawyers
 */
class StrFormatterTest
{
    @Test
    void format_按顺序替换占位符()
    {
        assertEquals("this is a for b", StrFormatter.format("this is {} for {}", "a", "b"));
    }

    @Test
    void format_无占位符或无参数时返回模板()
    {
        assertNull(StrFormatter.format(null, "a"));
        assertEquals("plain", StrFormatter.format("plain", "a"));
        assertEquals("{}", StrFormatter.format("{}", new Object[0]));
    }

    @Test
    void format_单转义符使占位符原样输出()
    {
        assertEquals("this is {} for a", StrFormatter.format("this is \\{} for {}", "a", "b"));
    }

    @Test
    void format_双转义符输出反斜杠并替换()
    {
        assertEquals("this is \\a for b", StrFormatter.format("this is \\\\{} for {}", "a", "b"));
    }

    @Test
    void format_参数少于占位符时保留剩余占位符()
    {
        assertEquals("a {}", StrFormatter.format("{} {}", "a"));
    }

    @Test
    void format_参数多于占位符时忽略多余参数()
    {
        assertEquals("a", StrFormatter.format("{}", "a", "b"));
    }
}
