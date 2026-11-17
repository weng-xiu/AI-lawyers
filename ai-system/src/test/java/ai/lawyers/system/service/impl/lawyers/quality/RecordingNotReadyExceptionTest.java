package ai.lawyers.system.service.impl.lawyers.quality;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * 录音未就绪可恢复异常测试：消息透传与异常类型。
 *
 * @author ai-lawyers
 */
class RecordingNotReadyExceptionTest
{
    @Test
    void 消息构造保留消息且属于非法状态异常()
    {
        RecordingNotReadyException exception = new RecordingNotReadyException("录音尚未落盘");

        assertEquals("录音尚未落盘", exception.getMessage());
        assertTrue(exception instanceof IllegalStateException);
    }

    @Test
    void 允许空消息()
    {
        RecordingNotReadyException exception = new RecordingNotReadyException(null);

        assertNull(exception.getMessage());
    }
}
