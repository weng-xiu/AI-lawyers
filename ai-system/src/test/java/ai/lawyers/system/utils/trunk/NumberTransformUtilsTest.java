package ai.lawyers.system.utils.trunk;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import ai.lawyers.system.domain.lawyers.trunk.AiCallTrunk;

/**
 * 号码变换工具测试：清洗（空格/横线/括号/国际前缀）、按线路配置做出局变换
 * （去位/加前缀/主叫字冠）、主叫显号解析、合法性校验与脱敏。
 *
 * @author ai-lawyers
 */
class NumberTransformUtilsTest
{
    private static AiCallTrunk trunk(Integer stripDigits, String addPrefix, String callerPrefix, String callerDisplay)
    {
        AiCallTrunk trunk = new AiCallTrunk();
        trunk.setStripDigits(stripDigits);
        trunk.setAddPrefix(addPrefix);
        trunk.setCallerPrefix(callerPrefix);
        trunk.setCallerDisplay(callerDisplay);
        return trunk;
    }

    @Test
    void normalize_空值与空串()
    {
        assertThat(NumberTransformUtils.normalize(null)).isEmpty();
        assertThat(NumberTransformUtils.normalize("")).isEmpty();
    }

    @Test
    void normalize_去除分隔符与括号()
    {
        assertThat(NumberTransformUtils.normalize(" 138-0013-8000 ")).isEqualTo("13800138000");
        assertThat(NumberTransformUtils.normalize("(010)8888 6666")).isEqualTo("01088886666");
        assertThat(NumberTransformUtils.normalize("（020）8888-6666")).isEqualTo("02088886666");
    }

    @Test
    void normalize_去国际区号()
    {
        assertThat(NumberTransformUtils.normalize("+86 138-0013-8000")).isEqualTo("13800138000");
        assertThat(NumberTransformUtils.normalize("008613800138000")).isEqualTo("13800138000");
        assertThat(NumberTransformUtils.normalize("8613800138000")).isEqualTo("13800138000");
        // 非 13 位的 86 前缀不裁剪
        assertThat(NumberTransformUtils.normalize("861380013")).isEqualTo("861380013");
    }

    @Test
    void transform_无线路配置仅清洗()
    {
        assertThat(NumberTransformUtils.transform("+86 138-0013-8000", null)).isEqualTo("13800138000");
    }

    @Test
    void transform_去位加前缀与主叫字冠()
    {
        AiCallTrunk trunk = trunk(1, "9", "0", "02088886666");

        // 13800138000 → 去 1 位 → 3800138000 → 加 9 → 93800138000 → 加主叫字冠 0
        assertThat(NumberTransformUtils.transform("13800138000", trunk)).isEqualTo("093800138000");
    }

    @Test
    void transform_去位数不小于号码长度时不裁剪()
    {
        AiCallTrunk trunk = trunk(20, null, null, null);

        assertThat(NumberTransformUtils.transform("13800138000", trunk)).isEqualTo("13800138000");
    }

    @Test
    void transform_去位数为空或零不裁剪()
    {
        assertThat(NumberTransformUtils.transform("13800138000", trunk(null, "9", null, null)))
                .isEqualTo("913800138000");
        assertThat(NumberTransformUtils.transform("13800138000", trunk(0, null, null, null)))
                .isEqualTo("13800138000");
    }

    @Test
    void resolveCaller_业务号码优先()
    {
        AiCallTrunk trunk = trunk(null, null, null, "02088886666");

        assertThat(NumberTransformUtils.resolveCaller("+86 138-0013-8000", trunk)).isEqualTo("13800138000");
    }

    @Test
    void resolveCaller_回退线路显号()
    {
        AiCallTrunk trunk = trunk(null, null, null, "020-8888-6666");

        assertThat(NumberTransformUtils.resolveCaller("  ", trunk)).isEqualTo("02088886666");
        assertThat(NumberTransformUtils.resolveCaller(null, trunk)).isEqualTo("02088886666");
    }

    @Test
    void resolveCaller_均未配置返回空串()
    {
        assertThat(NumberTransformUtils.resolveCaller(null, null)).isEmpty();
        assertThat(NumberTransformUtils.resolveCaller(null, trunk(null, null, null, ""))).isEmpty();
    }

    @Test
    void isValid_数字长度与格式校验()
    {
        assertThat(NumberTransformUtils.isValid("13800138000")).isTrue();
        assertThat(NumberTransformUtils.isValid("+86 138-0013-8000")).isTrue();
        assertThat(NumberTransformUtils.isValid("88886666")).isTrue();
        assertThat(NumberTransformUtils.isValid("1234")).isFalse();
        assertThat(NumberTransformUtils.isValid("1234567890123456")).isFalse();
        assertThat(NumberTransformUtils.isValid("138-abc")).isFalse();
        assertThat(NumberTransformUtils.isValid(null)).isFalse();
        assertThat(NumberTransformUtils.isValid("")).isFalse();
    }

    @Test
    void mask_中间掩码与短号原样返回()
    {
        assertThat(NumberTransformUtils.mask("13800138000")).isEqualTo("138****8000");
        assertThat(NumberTransformUtils.mask("+86 138-0013-8000")).isEqualTo("138****8000");
        assertThat(NumberTransformUtils.mask("1234567")).isEqualTo("1234567");
        assertThat(NumberTransformUtils.mask("123456")).isEqualTo("123456");
        assertThat(NumberTransformUtils.mask(null)).isEmpty();
    }
}
