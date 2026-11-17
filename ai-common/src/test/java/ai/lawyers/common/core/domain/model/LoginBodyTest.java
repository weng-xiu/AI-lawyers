package ai.lawyers.common.core.domain.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

/**
 * 用户登录对象测试：账号、密码、验证码与唯一标识的读写。
 *
 * @author ai-lawyers
 */
class LoginBodyTest
{
    @Test
    void 默认字段为空()
    {
        LoginBody body = new LoginBody();

        assertNull(body.getUsername());
        assertNull(body.getPassword());
        assertNull(body.getCode());
        assertNull(body.getUuid());
    }

    @Test
    void 可写入登录参数()
    {
        LoginBody body = new LoginBody();
        body.setUsername("admin");
        body.setPassword("secret");
        body.setCode("1234");
        body.setUuid("uuid-1");

        assertEquals("admin", body.getUsername());
        assertEquals("secret", body.getPassword());
        assertEquals("1234", body.getCode());
        assertEquals("uuid-1", body.getUuid());
    }
}
