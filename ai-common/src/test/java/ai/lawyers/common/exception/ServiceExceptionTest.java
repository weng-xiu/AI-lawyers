package ai.lawyers.common.exception;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import org.junit.jupiter.api.Test;

/**
 * 业务异常测试：构造消息与错误码、链式赋值、默认空值。
 *
 * @author ai-lawyers
 */
class ServiceExceptionTest
{
    @Test
    void 空构造_消息与错误码为空()
    {
        ServiceException e = new ServiceException();

        assertNull(e.getMessage());
        assertNull(e.getCode());
        assertNull(e.getDetailMessage());
    }

    @Test
    void 消息构造_仅设置消息()
    {
        ServiceException e = new ServiceException("用户不存在");

        assertEquals("用户不存在", e.getMessage());
        assertNull(e.getCode());
    }

    @Test
    void 消息与错误码构造()
    {
        ServiceException e = new ServiceException("未授权", 401);

        assertEquals("未授权", e.getMessage());
        assertEquals(Integer.valueOf(401), e.getCode());
    }

    @Test
    void 链式setter返回自身()
    {
        ServiceException e = new ServiceException("原始消息");

        assertSame(e, e.setMessage("新消息"));
        assertSame(e, e.setDetailMessage("字段校验失败"));
        assertEquals("新消息", e.getMessage());
        assertEquals("字段校验失败", e.getDetailMessage());
    }
}
