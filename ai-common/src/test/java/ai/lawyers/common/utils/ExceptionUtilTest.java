package ai.lawyers.common.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;

import org.junit.jupiter.api.Test;

/**
 * 错误信息处理测试：根因消息提取、无消息兜底、完整堆栈输出。
 *
 * @author ai-lawyers
 */
class ExceptionUtilTest
{
    @Test
    void getRootErrorMessage_取最深层根因消息()
    {
        Exception e = new RuntimeException("外层", new IllegalStateException("根因"));

        assertEquals("根因", ExceptionUtil.getRootErrorMessage(e));
    }

    @Test
    void getRootErrorMessage_无嵌套时取自身消息()
    {
        assertEquals("直接异常", ExceptionUtil.getRootErrorMessage(new IOException("直接异常")));
    }

    @Test
    void getRootErrorMessage_根因无消息返回null文本()
    {
        Exception e = new RuntimeException("外层", new IllegalStateException());

        assertEquals("null", ExceptionUtil.getRootErrorMessage(e));
    }

    @Test
    void getExceptionMessage_输出异常类型消息与堆栈()
    {
        Exception e = new IllegalArgumentException("参数错误");

        String message = ExceptionUtil.getExceptionMessage(e);

        assertTrue(message.contains("java.lang.IllegalArgumentException: 参数错误"));
        assertTrue(message.contains("\tat "));
    }

    @Test
    void getExceptionMessage_包含cause链()
    {
        Exception e = new RuntimeException("外层", new IllegalStateException("内层"));

        String message = ExceptionUtil.getExceptionMessage(e);

        assertTrue(message.contains("外层"));
        assertTrue(message.contains("Caused by: java.lang.IllegalStateException: 内层"));
    }
}
