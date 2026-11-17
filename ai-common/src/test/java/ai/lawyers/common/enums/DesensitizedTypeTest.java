package ai.lawyers.common.enums;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/**
 * 脱敏类型测试：姓名、密码、身份证、手机号、邮箱、银行卡、车牌各类脱敏规则。
 *
 * @author ai-lawyers
 */
class DesensitizedTypeTest
{
    @Test
    void 姓名保留首尾字符()
    {
        assertEquals("张*丰", DesensitizedType.USERNAME.desensitizer().apply("张三丰"));
        assertEquals("张*", DesensitizedType.USERNAME.desensitizer().apply("张三"));
    }

    @Test
    void 密码全部星号且空值返回空串()
    {
        assertEquals("********", DesensitizedType.PASSWORD.desensitizer().apply("secret12"));
        assertEquals("", DesensitizedType.PASSWORD.desensitizer().apply(""));
        assertEquals("", DesensitizedType.PASSWORD.desensitizer().apply(null));
    }

    @Test
    void 身份证中间十位星号()
    {
        assertEquals("1101** **** ****1234",
                DesensitizedType.ID_CARD.desensitizer().apply("110101199001011234"));
    }

    @Test
    void 手机号保留前三后四()
    {
        assertEquals("138****8000", DesensitizedType.PHONE.desensitizer().apply("13800138000"));
    }

    @Test
    void 邮箱保留首字母与域名()
    {
        assertEquals("a****@example.com",
                DesensitizedType.EMAIL.desensitizer().apply("alice@example.com"));
    }

    @Test
    void 银行卡保留最后三位()
    {
        assertEquals("**** **** **** **** 012",
                DesensitizedType.BANK_CARD.desensitizer().apply("622202123456789012"));
    }

    @Test
    void 车牌普通与新能源分别脱敏()
    {
        assertEquals("京A1***5", DesensitizedType.CAR_LICENSE.desensitizer().apply("京A12345"));
        assertEquals("京AD****5", DesensitizedType.CAR_LICENSE.desensitizer().apply("京AD12345"));
        // 非法长度不处理
        assertEquals("京A123", DesensitizedType.CAR_LICENSE.desensitizer().apply("京A123"));
        assertEquals("", DesensitizedType.CAR_LICENSE.desensitizer().apply(""));
    }
}
