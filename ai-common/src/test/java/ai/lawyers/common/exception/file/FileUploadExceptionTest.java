package ai.lawyers.common.exception.file;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;

/**
 * 文件上传异常测试：消息/原因构造、getCause 透出以及带原因时的堆栈输出。
 *
 * @author ai-lawyers
 */
class FileUploadExceptionTest
{
    @Test
    void 默认构造无消息与原因()
    {
        FileUploadException exception = new FileUploadException();

        assertNull(exception.getMessage());
        assertNull(exception.getCause());
    }

    @Test
    void 消息构造仅保留消息()
    {
        FileUploadException exception = new FileUploadException("上传失败");

        assertEquals("上传失败", exception.getMessage());
        assertNull(exception.getCause());
    }

    @Test
    void 消息与原因构造透出原因()
    {
        IllegalArgumentException cause = new IllegalArgumentException("文件过大");
        FileUploadException exception = new FileUploadException("上传失败", cause);

        assertEquals("上传失败", exception.getMessage());
        assertSame(cause, exception.getCause());
    }

    @Test
    void 有原因时向Writer输出causedBy()
    {
        StringWriter writer = new StringWriter();
        new FileUploadException("上传失败", new IllegalStateException("磁盘满"))
                .printStackTrace(new PrintWriter(writer));

        assertTrue(writer.toString().contains("Caused by:"));
        assertTrue(writer.toString().contains("磁盘满"));
    }

    @Test
    void 无原因时向Writer不输出causedBy()
    {
        StringWriter writer = new StringWriter();
        new FileUploadException("上传失败").printStackTrace(new PrintWriter(writer));

        assertFalse(writer.toString().contains("Caused by:"));
        assertTrue(writer.toString().contains("上传失败"));
    }

    @Test
    void 有原因时向Stream输出causedBy() throws Exception
    {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        new FileUploadException("上传失败", new IllegalStateException("磁盘满"))
                .printStackTrace(new PrintStream(out, true, "UTF-8"));

        String text = new String(out.toByteArray(), StandardCharsets.UTF_8);
        assertTrue(text.contains("Caused by:"));
        assertTrue(text.contains("磁盘满"));
    }
}
