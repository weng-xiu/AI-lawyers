package ai.lawyers.common.exception.file;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/**
 * 文件名超长异常测试：错误码与长度参数。
 *
 * @author ai-lawyers
 */
class FileNameLengthLimitExceededExceptionTest
{
    @Test
    void 构造器_携带最大文件名长度()
    {
        FileNameLengthLimitExceededException exception = new FileNameLengthLimitExceededException(100);

        assertEquals("file", exception.getModule());
        assertEquals("upload.filename.exceed.length", exception.getCode());
        assertArrayEquals(new Object[] { 100 }, exception.getArgs());
    }
}
