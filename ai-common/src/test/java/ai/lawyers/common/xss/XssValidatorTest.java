package ai.lawyers.common.xss;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * XSS 校验注解实现测试：空文本放行、普通文本放行、HTML 标签拦截。
 *
 * @author ai-lawyers
 */
class XssValidatorTest
{
    private final XssValidator validator = new XssValidator();

    @Test
    void 空文本视为合法()
    {
        assertTrue(validator.isValid(null, null));
        assertTrue(validator.isValid("", null));
        assertTrue(validator.isValid("   ", null));
    }

    @Test
    void 普通文本合法()
    {
        assertTrue(validator.isValid("我想咨询劳动仲裁", null));
        assertTrue(validator.isValid("1 < 2 且没有闭合符", null));
        assertTrue(validator.isValid("邮箱 a@b.com", null));
    }

    @Test
    void 包含HTML标签视为非法()
    {
        assertFalse(validator.isValid("<script>alert(1)</script>", null));
        assertFalse(validator.isValid("<b>粗体</b>", null));
        assertFalse(validator.isValid("<img src=x onerror=alert(1)/>", null));
        assertFalse(validator.isValid("请勿输入<a href='#'>链接</a>", null));
        // 尖括号成对出现即按标签拦截（宁严勿漏）
        assertFalse(validator.isValid("1 < 2 且 3 > 2", null));
    }

    @Test
    void containsHtml静态方法()
    {
        assertFalse(XssValidator.containsHtml("纯文本内容"));
        assertTrue(XssValidator.containsHtml("<div>内容</div>"));
    }
}
