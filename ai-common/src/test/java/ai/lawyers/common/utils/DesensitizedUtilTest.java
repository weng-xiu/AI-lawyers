package ai.lawyers.common.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/**
 * 脱敏工具测试：密码、车牌与手机号掩码规则。
 *
 * @author ai-lawyers
 */
class DesensitizedUtilTest
{
    @Test
    void password_全部替换为星号()
    {
        assertEquals("******", DesensitizedUtil.password("123456"));
    }

    @Test
    void password_空值返回空串()
    {
        assertEquals("", DesensitizedUtil.password(null));
        assertEquals("", DesensitizedUtil.password("  "));
    }

    @Test
    void mobilePhone_保留前三后四()
    {
        assertEquals("138****8000", DesensitizedUtil.mobilePhone("13800008000"));
    }

    @Test
    void mobilePhone_短号整体掩码()
    {
        assertEquals("******", DesensitizedUtil.mobilePhone("123456"));
        assertEquals("", DesensitizedUtil.mobilePhone(""));
    }

    @Test
    void mobilePhone_超长号码仍保留前三后四()
    {
        assertEquals("010****8000", DesensitizedUtil.mobilePhone("01012348000"));
    }

    @Test
    void carLicense_普通车牌掩码中间三位()
    {
        assertEquals("京A1***5", DesensitizedUtil.carLicense("京A12345"));
    }

    @Test
    void carLicense_新能源车牌掩码中间四位()
    {
        assertEquals("京A1****6", DesensitizedUtil.carLicense("京A123456"));
    }

    @Test
    void carLicense_非法长度不处理()
    {
        assertEquals("ABC", DesensitizedUtil.carLicense("ABC"));
        assertEquals("", DesensitizedUtil.carLicense(null));
    }
}
