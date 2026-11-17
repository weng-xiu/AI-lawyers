package ai.lawyers.common.exception.file;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/**
 * 文件大小超限异常测试：错误码与大小参数。
 *
 * @author ai-lawyers
 */
class FileSizeLimitExceededExceptionTest
{
    @Test
    void 构造器_携带最大文件大小()
    {
        FileSizeLimitExceededException exception = new FileSizeLimitExceededException(50);

        assertEquals("file", exception.getModule());
        assertEquals("upload.exceed.maxSize", exception.getCode());
        assertArrayEquals(new Object[] { 50L }, exception.getArgs());
    }
}
