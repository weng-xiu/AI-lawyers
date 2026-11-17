package ai.lawyers.common.exception.file;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import org.junit.jupiter.api.Test;

import ai.lawyers.common.exception.base.BaseException;

/**
 * 文件异常测试：模块标识与参数透传。
 *
 * @author ai-lawyers
 */
class FileExceptionTest
{
    @Test
    void 构造器_归属文件模块()
    {
        Object[] args = { "a.txt" };
        FileException exception = new FileException("file.upload.error", args);

        assertEquals("file", exception.getModule());
        assertEquals("file.upload.error", exception.getCode());
        assertSame(args, exception.getArgs());
        assertNull(exception.getDefaultMessage());
        // 继承自基础异常，属于非受检异常
        assertEquals(true, exception instanceof BaseException);
    }
}
