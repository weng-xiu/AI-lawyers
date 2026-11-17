package ai.lawyers.common.exception;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * 演示模式异常测试：无消息构造。
 *
 * @author ai-lawyers
 */
class DemoModeExceptionTest
{
    @Test
    void 构造器_无消息()
    {
        DemoModeException exception = new DemoModeException();

        assertNull(exception.getMessage());
        assertTrue(exception instanceof RuntimeException);
    }
}
