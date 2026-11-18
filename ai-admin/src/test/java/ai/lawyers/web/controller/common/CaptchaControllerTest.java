package ai.lawyers.web.controller.common;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.awt.image.BufferedImage;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletResponse;
import com.google.code.kaptcha.Producer;
import ai.lawyers.common.config.RuoYiConfig;
import ai.lawyers.common.constant.CacheConstants;
import ai.lawyers.common.constant.Constants;
import ai.lawyers.common.core.domain.AjaxResult;
import ai.lawyers.common.core.redis.RedisCache;
import ai.lawyers.common.utils.uuid.IdUtils;
import ai.lawyers.system.service.ISysConfigService;

/**
 * 验证码接口单元测试
 *
 * @author ai-lawyers
 */
@ExtendWith(MockitoExtension.class)
class CaptchaControllerTest
{
    @Mock(name = "captchaProducer")
    private Producer captchaProducer;

    @Mock(name = "captchaProducerMath")
    private Producer captchaProducerMath;

    @Mock
    private RedisCache redisCache;

    @Mock
    private ISysConfigService configService;

    @InjectMocks
    private CaptchaController controller;

    private BufferedImage image()
    {
        return new BufferedImage(80, 30, BufferedImage.TYPE_INT_RGB);
    }

    @Test
    void 数字计算型验证码写入缓存并返回图片() throws Exception
    {
        when(captchaProducerMath.createText()).thenReturn("3+2@5");
        when(captchaProducerMath.createImage("3+2")).thenReturn(image());
        try (MockedStatic<RuoYiConfig> config = mockStatic(RuoYiConfig.class);
                MockedStatic<IdUtils> idUtils = mockStatic(IdUtils.class))
        {
            config.when(RuoYiConfig::getCaptchaType).thenReturn("math");
            idUtils.when(IdUtils::simpleUUID).thenReturn("uuid-math");

            AjaxResult result = controller.getCode(new MockHttpServletResponse());

            assertEquals(Boolean.TRUE, result.get("captchaEnabled"));
            assertEquals("uuid-math", result.get("uuid"));
            assertNotNull(result.get("img"));
            assertTrue(result.get("img").toString().length() > 0);
            verify(redisCache).setCacheObject(eq(CacheConstants.CAPTCHA_CODE_KEY + "uuid-math"),
                    eq("5"), eq(Constants.CAPTCHA_EXPIRATION), eq(TimeUnit.MINUTES));
        }
    }

    @Test
    void 字符型验证码以文本作为校验码() throws Exception
    {
        when(captchaProducer.createText()).thenReturn("AbCd");
        when(captchaProducer.createImage("AbCd")).thenReturn(image());
        try (MockedStatic<RuoYiConfig> config = mockStatic(RuoYiConfig.class);
                MockedStatic<IdUtils> idUtils = mockStatic(IdUtils.class))
        {
            config.when(RuoYiConfig::getCaptchaType).thenReturn("char");
            idUtils.when(IdUtils::simpleUUID).thenReturn("uuid-char");

            AjaxResult result = controller.getCode(new MockHttpServletResponse());

            assertEquals("uuid-char", result.get("uuid"));
            verify(redisCache).setCacheObject(eq(CacheConstants.CAPTCHA_CODE_KEY + "uuid-char"),
                    eq("AbCd"), eq(Constants.CAPTCHA_EXPIRATION), eq(TimeUnit.MINUTES));
        }
    }
}
