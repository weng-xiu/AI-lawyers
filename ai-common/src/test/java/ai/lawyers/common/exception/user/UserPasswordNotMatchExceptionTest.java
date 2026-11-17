package ai.lawyers.common.exception.user;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

/**
 * 密码不匹配异常测试：模块与错误码。
 *
 * @author ai-lawyers
 */
class UserPasswordNotMatchExceptionTest
{
    @Test
    void 构造器_使用密码不匹配错误码()
    {
        UserPasswordNotMatchException exception = new UserPasswordNotMatchException();

        assertEquals("user", exception.getModule());
        assertEquals("user.password.not.match", exception.getCode());
        assertNull(exception.getArgs());
    }
}
