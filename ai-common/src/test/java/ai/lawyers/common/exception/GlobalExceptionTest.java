package ai.lawyers.common.exception;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import org.junit.jupiter.api.Test;

/**
 * 全局异常测试：消息与明细的读取、链式赋值、默认空值。
 *
 * @author ai-lawyers
 */
class GlobalExceptionTest
{
    @Test
    void 空构造_消息与明细为空()
    {
        GlobalException e = new GlobalException();

        assertNull(e.getMessage());
        assertNull(e.getDetailMessage());
    }

    @Test
    void 消息构造()
    {
        GlobalException e = new GlobalException("系统繁忙");

        assertEquals("系统繁忙", e.getMessage());
        assertNull(e.getDetailMessage());
    }

    @Test
    void setter链式返回自身()
    {
        GlobalException e = new GlobalException();

        assertSame(e, e.setMessage("参数错误"));
        assertSame(e, e.setDetailMessage("字段[名称]不能为空"));
        assertEquals("参数错误", e.getMessage());
        assertEquals("字段[名称]不能为空", e.getDetailMessage());
    }
}
