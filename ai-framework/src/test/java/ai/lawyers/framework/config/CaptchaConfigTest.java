package ai.lawyers.framework.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.image.BufferedImage;

import org.junit.jupiter.api.Test;

import com.google.code.kaptcha.impl.DefaultKaptcha;

/**
 * 验证码配置测试：字符验证码与算术验证码生成。
 *
 * @author ai-lawyers
 */
class CaptchaConfigTest
{
    private final CaptchaConfig config = new CaptchaConfig();

    @Test
    void captchaProducer_生成固定尺寸图片()
    {
        DefaultKaptcha kaptcha = config.getKaptchaBean();

        String text = kaptcha.createText();
        assertEquals(4, text.length());

        BufferedImage image = kaptcha.createImage(text);
        assertNotNull(image);
        assertEquals(160, image.getWidth());
        assertEquals(60, image.getHeight());
    }

    @Test
    void captchaProducerMath_生成算术表达式()
    {
        DefaultKaptcha kaptcha = config.getKaptchaBeanMath();

        String text = kaptcha.createText();
        assertTrue(text.contains("@"), "算术验证码文本应包含结果分隔符");
        assertEquals(2, text.split("@").length);

        BufferedImage image = kaptcha.createImage(text);
        assertNotNull(image);
        assertEquals(160, image.getWidth());
        assertEquals(60, image.getHeight());
    }
}
