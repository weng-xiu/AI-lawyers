package ai.lawyers.common.exception.user;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * 验证码失效异常测试：继承关系与错误码。
 *
 * @author ai-lawyers
 */
class CaptchaExpireExceptionTest
{
    @Test
    void 构造器_使用验证码失效错误码()
    {
        CaptchaExpireException exception = new CaptchaExpireException();

        assertEquals("user", exception.getModule());
        assertEquals("user.jcaptcha.expire", exception.getCode());
        assertNull(exception.getArgs());
        assertTrue(exception instanceof UserException);
    }
}
