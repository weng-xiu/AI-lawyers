package ai.lawyers.framework.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;

import jakarta.servlet.DispatcherType;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.test.util.ReflectionTestUtils;

import ai.lawyers.common.filter.RepeatableFilter;
import ai.lawyers.common.filter.XssFilter;

/**
 * 过滤器配置测试：XSS 过滤器与请求体可重复读过滤器注册。
 *
 * @author ai-lawyers
 */
class FilterConfigTest
{
    private final FilterConfig config = new FilterConfig();

    @BeforeEach
    void setUp()
    {
        ReflectionTestUtils.setField(config, "excludes", "/sys/notice,/common/upload");
        ReflectionTestUtils.setField(config, "urlPatterns", "/system/*,/monitor/*");
    }

    @Test
    @SuppressWarnings("rawtypes")
    void xssFilterRegistration_注册路径与排除参数()
    {
        FilterRegistrationBean registration = config.xssFilterRegistration();

        assertInstanceOf(XssFilter.class, registration.getFilter());
        assertEquals(FilterRegistrationBean.HIGHEST_PRECEDENCE, registration.getOrder());
        assertTrue(registration.getUrlPatterns().contains("/system/*"));
        assertTrue(registration.getUrlPatterns().contains("/monitor/*"));

        Map<String, String> initParameters = registration.getInitParameters();
        assertEquals("/sys/notice,/common/upload", initParameters.get("excludes"));
    }

    @Test
    @SuppressWarnings("rawtypes")
    void someFilterRegistration_注册可重复读请求体过滤器()
    {
        FilterRegistrationBean registration = config.someFilterRegistration();

        assertInstanceOf(RepeatableFilter.class, registration.getFilter());
        assertEquals(FilterRegistrationBean.LOWEST_PRECEDENCE, registration.getOrder());
        assertTrue(registration.getUrlPatterns().contains("/*"));
    }
}
