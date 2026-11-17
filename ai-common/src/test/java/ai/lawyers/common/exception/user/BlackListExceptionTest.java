package ai.lawyers.common.exception.user;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

/**
 * 黑名单异常测试：模块与错误码。
 *
 * @author ai-lawyers
 */
class BlackListExceptionTest
{
    @Test
    void 构造器_使用登录拦截错误码()
    {
        BlackListException exception = new BlackListException();

        assertEquals("user", exception.getModule());
        assertEquals("login.blocked", exception.getCode());
        assertNull(exception.getArgs());
        assertNull(exception.getDefaultMessage());
    }
}
