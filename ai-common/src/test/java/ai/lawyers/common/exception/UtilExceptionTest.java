package ai.lawyers.common.exception;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.io.IOException;

import org.junit.jupiter.api.Test;

/**
 * 工具异常测试：消息与原始异常传递。
 *
 * @author ai-lawyers
 */
class UtilExceptionTest
{
    @Test
    void 构造器_仅消息()
    {
        UtilException exception = new UtilException("生成失败");

        assertEquals("生成失败", exception.getMessage());
        assertNull(exception.getCause());
    }

    @Test
    void 构造器_包装原始异常()
    {
        IOException cause = new IOException("磁盘不可用");
        UtilException exception = new UtilException(cause);

        assertEquals("磁盘不可用", exception.getMessage());
        assertSame(cause, exception.getCause());
    }

    @Test
    void 构造器_消息与原始异常()
    {
        IOException cause = new IOException("磁盘不可用");
        UtilException exception = new UtilException("读取配置失败", cause);

        assertEquals("读取配置失败", exception.getMessage());
        assertSame(cause, exception.getCause());
    }
}
