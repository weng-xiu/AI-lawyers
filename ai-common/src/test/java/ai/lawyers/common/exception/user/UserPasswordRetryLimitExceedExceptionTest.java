package ai.lawyers.common.exception.user;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/**
 * 密码重试超限异常测试：错误码与重试参数。
 *
 * @author ai-lawyers
 */
class UserPasswordRetryLimitExceedExceptionTest
{
    @Test
    void 构造器_携带重试次数与锁定时间()
    {
        UserPasswordRetryLimitExceedException exception = new UserPasswordRetryLimitExceedException(5, 10);

        assertEquals("user", exception.getModule());
        assertEquals("user.password.retry.limit.exceed", exception.getCode());
        assertArrayEquals(new Object[] { 5, 10 }, exception.getArgs());
    }
}
