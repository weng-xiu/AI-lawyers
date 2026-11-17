package ai.lawyers.common.exception.user;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

/**
 * 用户不存在异常测试：模块与错误码。
 *
 * @author ai-lawyers
 */
class UserNotExistsExceptionTest
{
    @Test
    void 构造器_使用用户不存在错误码()
    {
        UserNotExistsException exception = new UserNotExistsException();

        assertEquals("user", exception.getModule());
        assertEquals("user.not.exists", exception.getCode());
        assertNull(exception.getArgs());
    }
}
