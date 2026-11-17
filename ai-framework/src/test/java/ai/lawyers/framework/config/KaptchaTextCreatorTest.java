package ai.lawyers.framework.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;

/**
 * 验证码文本生成器测试：校验算式格式与计算结果一致。
 *
 * @author ai-lawyers
 */
class KaptchaTextCreatorTest
{
    /** 形如 3*5=?@15 的算式格式 */
    private static final Pattern PATTERN = Pattern.compile("^(\\d+)([+\\-*/])(\\d+)=\\?@(-?\\d+)$");

    @Test
    void getText_生成可校验的算式()
    {
        KaptchaTextCreator creator = new KaptchaTextCreator();
        // 结果随机，多次采样覆盖乘法、除法、加减法分支
        for (int i = 0; i < 300; i++)
        {
            String text = creator.getText();
            Matcher matcher = PATTERN.matcher(text);
            assertTrue(matcher.matches(), "算式格式不合法: " + text);

            int first = Integer.parseInt(matcher.group(1));
            int second = Integer.parseInt(matcher.group(3));
            int result = Integer.parseInt(matcher.group(4));
            switch (matcher.group(2))
            {
                case "+":
                    assertEquals(first + second, result, text);
                    break;
                case "-":
                    assertEquals(first - second, result, text);
                    break;
                case "*":
                    assertEquals(first * second, result, text);
                    break;
                default:
                    // 除法写作「被除数/除数」，第二个操作数为除数且必须整除
                    assertEquals(0, first % second, text);
                    assertEquals(first / second, result, text);
                    break;
            }
        }
    }

    @Test
    void getText_操作数在0到9之间()
    {
        KaptchaTextCreator creator = new KaptchaTextCreator();
        for (int i = 0; i < 100; i++)
        {
            Matcher matcher = PATTERN.matcher(creator.getText());
            assertTrue(matcher.matches());
            int first = Integer.parseInt(matcher.group(1));
            int second = Integer.parseInt(matcher.group(3));
            assertTrue(first >= 0 && first <= 9);
            assertTrue(second >= 0 && second <= 9);
        }
    }
}
