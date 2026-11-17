package ai.lawyers.framework.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.CacheControl;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;
import org.springframework.web.servlet.config.annotation.InterceptorRegistration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;

import ai.lawyers.common.config.RuoYiConfig;
import ai.lawyers.framework.interceptor.RepeatSubmitInterceptor;

/**
 * 通用配置测试：静态资源映射、重复提交拦截器与跨域配置。
 *
 * @author ai-lawyers
 */
class ResourcesConfigTest
{
    private final ResourcesConfig config = new ResourcesConfig();

    private String oldProfile;

    @BeforeEach
    void setUp()
    {
        oldProfile = RuoYiConfig.getProfile();
        new RuoYiConfig().setProfile("/data/upload");
        ReflectionTestUtils.setField(config, "repeatSubmitInterceptor", mock(RepeatSubmitInterceptor.class));
    }

    @AfterEach
    void tearDown()
    {
        new RuoYiConfig().setProfile(oldProfile);
    }

    @Test
    void addResourceHandlers_映射上传目录与swagger资源()
    {
        ResourceHandlerRegistry registry = mock(ResourceHandlerRegistry.class);
        ResourceHandlerRegistration registration = mock(ResourceHandlerRegistration.class);
        when(registry.addResourceHandler(anyString())).thenReturn(registration);
        when(registration.addResourceLocations(any(String[].class))).thenReturn(registration);
        when(registration.setCacheControl(any(CacheControl.class))).thenReturn(registration);

        config.addResourceHandlers(registry);

        verify(registry).addResourceHandler("/profile/**");
        verify(registration).addResourceLocations("file:/data/upload/");
        verify(registry).addResourceHandler("/swagger-ui/**");
        verify(registration).setCacheControl(any(CacheControl.class));
    }

    @Test
    void addInterceptors_拦截全部路径()
    {
        InterceptorRegistry registry = mock(InterceptorRegistry.class);
        InterceptorRegistration registration = mock(InterceptorRegistration.class);
        when(registry.addInterceptor(any(RepeatSubmitInterceptor.class))).thenReturn(registration);

        config.addInterceptors(registry);

        verify(registry).addInterceptor(any(RepeatSubmitInterceptor.class));
        verify(registration).addPathPatterns("/**");
    }

    @Test
    void corsFilter_允许全部来源请求头与方法()
    {
        CorsFilter filter = config.corsFilter();
        assertNotNull(filter);
        assertTrue(ReflectionTestUtils.getField(filter, "configSource") instanceof UrlBasedCorsConfigurationSource);

        CorsConfigurationSource source = (CorsConfigurationSource) ReflectionTestUtils.getField(filter, "configSource");
        CorsConfiguration cors = source.getCorsConfiguration(new MockHttpServletRequest("GET", "/api/test"));

        assertNotNull(cors);
        assertTrue(cors.getAllowedOriginPatterns().contains("*"));
        assertTrue(cors.getAllowedHeaders().contains("*"));
        assertTrue(cors.getAllowedMethods().contains("*"));
        assertEquals(1800L, cors.getMaxAge());
    }
}
