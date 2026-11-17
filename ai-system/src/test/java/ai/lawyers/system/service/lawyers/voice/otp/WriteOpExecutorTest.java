package ai.lawyers.system.service.lawyers.voice.otp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * 写操作执行器结果测试：成功携带业务引用，失败不外抛。
 *
 * @author ai-lawyers
 */
class WriteOpExecutorTest
{
    @Test
    void ok_标记成功并携带消息与引用()
    {
        WriteOpExecutor.ExecutionResult result = WriteOpExecutor.ExecutionResult.ok("短信已提交", "msg-88");

        assertTrue(result.isSuccess());
        assertEquals("短信已提交", result.getMessage());
        assertEquals("msg-88", result.getRef());
    }

    @Test
    void ok_业务引用可为空()
    {
        WriteOpExecutor.ExecutionResult result = WriteOpExecutor.ExecutionResult.ok("回访单已创建", null);

        assertTrue(result.isSuccess());
        assertNull(result.getRef());
    }

    @Test
    void fail_标记失败且无引用()
    {
        WriteOpExecutor.ExecutionResult result = WriteOpExecutor.ExecutionResult.fail("模板不存在");

        assertFalse(result.isSuccess());
        assertEquals("模板不存在", result.getMessage());
        assertNull(result.getRef());
    }
}
