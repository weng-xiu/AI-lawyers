package ai.lawyers.common.core.domain.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * 用户注册对象测试：继承登录字段并补充昵称、手机号、邮箱、性别与用户类型。
 *
 * @author ai-lawyers
 */
class RegisterBodyTest
{
    @Test
    void 继承登录对象字段()
    {
        RegisterBody body = new RegisterBody();
        body.setUsername("lawyer");
        body.setPassword("secret");

        assertTrue(body instanceof LoginBody);
        assertEquals("lawyer", body.getUsername());
        assertEquals("secret", body.getPassword());
    }

    @Test
    void 可写入注册扩展字段()
    {
        RegisterBody body = new RegisterBody();
        body.setNickName("张律师");
        body.setPhonenumber("13800000000");
        body.setEmail("lawyer@example.com");
        body.setSex("0");
        body.setUserType("00");

        assertEquals("张律师", body.getNickName());
        assertEquals("13800000000", body.getPhonenumber());
        assertEquals("lawyer@example.com", body.getEmail());
        assertEquals("0", body.getSex());
        assertEquals("00", body.getUserType());
    }
}
