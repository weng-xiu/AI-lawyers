package ai.lawyers.common.exception.file;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * 文件后缀非法异常测试：字段透传与提示消息。
 *
 * @author ai-lawyers
 */
class InvalidExtensionExceptionTest
{
    @Test
    void 构造器_保存后缀信息并生成提示()
    {
        String[] allowed = { "pdf", "docx" };
        InvalidExtensionException exception = new InvalidExtensionException(allowed, "exe", "virus.exe");

        assertArrayEquals(allowed, exception.getAllowedExtension());
        assertEquals("exe", exception.getExtension());
        assertEquals("virus.exe", exception.getFilename());
        assertTrue(exception.getMessage().contains("virus.exe"));
        assertTrue(exception.getMessage().contains("exe"));
        assertTrue(exception.getMessage().contains("pdf"));
    }

    @Test
    void 图片子类_保留父类字段()
    {
        String[] allowed = { "png", "jpg" };
        InvalidExtensionException.InvalidImageExtensionException exception = new InvalidExtensionException.InvalidImageExtensionException(
                allowed, "exe", "a.exe");

        assertArrayEquals(allowed, exception.getAllowedExtension());
        assertEquals("exe", exception.getExtension());
        assertEquals("a.exe", exception.getFilename());
    }
}
