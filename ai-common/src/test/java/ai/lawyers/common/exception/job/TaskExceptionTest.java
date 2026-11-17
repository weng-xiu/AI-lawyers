package ai.lawyers.common.exception.job;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import org.junit.jupiter.api.Test;

/**
 * 计划策略异常测试：错误码、消息与内嵌异常。
 *
 * @author ai-lawyers
 */
class TaskExceptionTest
{
    @Test
    void 错误码与消息()
    {
        TaskException e = new TaskException("任务已存在", TaskException.Code.TASK_EXISTS);

        assertEquals("任务已存在", e.getMessage());
        assertEquals(TaskException.Code.TASK_EXISTS, e.getCode());
        assertNull(e.getCause());
    }

    @Test
    void 携带内嵌异常()
    {
        IllegalStateException cause = new IllegalStateException("线程池关闭");
        TaskException e = new TaskException("任务启动失败", TaskException.Code.CONFIG_ERROR, cause);

        assertEquals("任务启动失败", e.getMessage());
        assertEquals(TaskException.Code.CONFIG_ERROR, e.getCode());
        assertSame(cause, e.getCause());
    }

    @Test
    void 错误码枚举包含各类任务失败场景()
    {
        assertEquals(6, TaskException.Code.values().length);
        assertEquals(TaskException.Code.UNKNOWN, TaskException.Code.valueOf("UNKNOWN"));
    }
}
