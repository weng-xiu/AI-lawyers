package ai.lawyers.common.exception.base;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

/**
 * 基础异常测试：各构造器字段赋值、默认消息回退（code 为空时）。
 *
 * @author ai-lawyers
 */
class BaseExceptionTest
{
    @Test
    void 完整构造器赋值全部字段()
    {
        Object[] args = new Object[] { "张三" };
        BaseException e = new BaseException("用户模块", "E100", args, "默认提示");

        assertEquals("用户模块", e.getModule());
        assertEquals("E100", e.getCode());
        assertArrayEquals(args, e.getArgs());
        assertEquals("默认提示", e.getDefaultMessage());
    }

    @Test
    void 仅默认消息构造_getMessage返回默认消息()
    {
        BaseException e = new BaseException("操作失败");

        assertNull(e.getModule());
        assertNull(e.getCode());
        assertNull(e.getArgs());
        assertEquals("操作失败", e.getDefaultMessage());
        assertEquals("操作失败", e.getMessage());
    }

    @Test
    void 模块与默认消息构造()
    {
        BaseException e = new BaseException("用户模块", "新增失败");

        assertEquals("用户模块", e.getModule());
        assertNull(e.getCode());
        assertEquals("新增失败", e.getMessage());
    }

    @Test
    void 错误码与参数构造_模块为空且参数保留()
    {
        Object[] args = new Object[] { 1, "参数" };
        BaseException e = new BaseException("E200", args);

        assertNull(e.getModule());
        assertEquals("E200", e.getCode());
        assertArrayEquals(args, e.getArgs());
        assertNull(e.getDefaultMessage());
    }
}
