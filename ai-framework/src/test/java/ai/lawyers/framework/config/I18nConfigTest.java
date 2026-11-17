package ai.lawyers.framework.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Locale;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.servlet.LocaleResolver;
import org.springframework.web.servlet.config.annotation.InterceptorRegistration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.i18n.LocaleChangeInterceptor;
import org.springframework.web.servlet.i18n.SessionLocaleResolver;

import ai.lawyers.common.constant.Constants;

/**
 * 国际化配置测试：默认语言解析、语言参数拦截器注册。
 *
 * @author ai-lawyers
 */
class I18nConfigTest
{
    private final I18nConfig config = new I18nConfig();

    @Test
    void localeResolver_默认语言为配置常量()
    {
        LocaleResolver resolver = config.localeResolver();

        assertEquals(SessionLocaleResolver.class, resolver.getClass());
        assertEquals(Constants.DEFAULT_LOCALE, resolver.resolveLocale(new MockHttpServletRequest()));
    }

    @Test
    void localeChangeInterceptor_参数名为lang()
    {
        LocaleChangeInterceptor interceptor = config.localeChangeInterceptor();

        assertEquals("lang", interceptor.getParamName());
    }

    @Test
    void addInterceptors_注册语言切换拦截器()
    {
        InterceptorRegistry registry = mock(InterceptorRegistry.class);
        InterceptorRegistration registration = mock(InterceptorRegistration.class);
        when(registry.addInterceptor(org.mockito.ArgumentMatchers.any(LocaleChangeInterceptor.class)))
                .thenReturn(registration);

        config.addInterceptors(registry);

        verify(registry).addInterceptor(org.mockito.ArgumentMatchers.any(LocaleChangeInterceptor.class));
    }

    @Test
    void localeResolver_支持中文与英文语言标记()
    {
        LocaleResolver resolver = config.localeResolver();
        MockHttpServletRequest request = new MockHttpServletRequest();

        assertEquals(Locale.SIMPLIFIED_CHINESE, resolver.resolveLocale(request));
    }
}
