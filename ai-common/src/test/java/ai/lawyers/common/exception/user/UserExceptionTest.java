package ai.lawyers.common.exception.user;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

/**
 * 用户信息异常测试：模块固定为 user。
 *
 * @author ai-lawyers
 */
class UserExceptionTest
{
    @Test
    void 构造器_归属用户模块()
    {
        Object[] args = { "zhangsan" };
        UserException exception = new UserException("user.custom.error", args);

        assertEquals("user", exception.getModule());
        assertEquals("user.custom.error", exception.getCode());
        assertArrayEquals(args, exception.getArgs());
        assertNull(exception.getDefaultMessage());
    }
}
