package ai.lawyers.common.utils;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * N10：{@link DesensitizedUtil#mobilePhone(String)} 手机号日志脱敏边界测试。
 *
 * @author ai-lawyers
 */
class DesensitizedUtilTest
{
    @Test
    void mobilePhone_nullOrBlank_returnsEmpty()
    {
        assertThat(DesensitizedUtil.mobilePhone(null)).isEmpty();
        assertThat(DesensitizedUtil.mobilePhone("")).isEmpty();
        assertThat(DesensitizedUtil.mobilePhone("   ")).isEmpty();
    }

    @Test
    void mobilePhone_shortNumber_fullyMasked()
    {
        // 长度 <7 整体掩码，避免短号原样泄露
        assertThat(DesensitizedUtil.mobilePhone("123")).isEqualTo("***");
        assertThat(DesensitizedUtil.mobilePhone("1")).isEqualTo("*");
    }

    @Test
    void mobilePhone_standardMobile_keepsFirst3Last4()
    {
        // 11 位手机号：前 3 后 4 保留，中间 4 位掩码
        assertThat(DesensitizedUtil.mobilePhone("13800138000")).isEqualTo("138****8000");
    }

    @Test
    void mobilePhone_midLength_masksMiddleOnly()
    {
        // 8 位：仅掩码第 4 位（下标 3，长度-4=4）
        assertThat(DesensitizedUtil.mobilePhone("12345678")).isEqualTo("123*5678");
    }

    @Test
    void mobilePhone_longNumber_keepsFirst3Last4()
    {
        // 超长号码（如带区号 13 位）：同样前 3 后 4，中间全部掩码
        assertThat(DesensitizedUtil.mobilePhone("1234567890123")).isEqualTo("123******0123");
    }
}
