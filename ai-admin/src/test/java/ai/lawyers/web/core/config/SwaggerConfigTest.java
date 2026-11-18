package ai.lawyers.web.core.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ai.lawyers.common.config.RuoYiConfig;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.junit.jupiter.api.Test;
import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * 接口文档配置测试：OpenAPI 元信息、鉴权方案与分组。
 *
 * @author ai-lawyers
 */
class SwaggerConfigTest
{
    @Test
    void 元信息包含系统名称与版本()
    {
        SwaggerConfig config = newConfig();

        OpenAPI openAPI = config.customOpenAPI();

        assertTrue(openAPI.getInfo().getTitle().contains("12348"));
        assertEquals("AI律师法律服务平台", openAPI.getInfo().getContact().getName());
        assertEquals("版本号:1.0.0", openAPI.getInfo().getVersion());
    }

    @Test
    void 注册Authorization请求头鉴权方案()
    {
        OpenAPI openAPI = newConfig().customOpenAPI();

        SecurityScheme scheme = openAPI.getComponents().getSecuritySchemes().get("Authorization");

        assertNotNull(scheme);
        assertEquals(SecurityScheme.Type.APIKEY, scheme.getType());
        assertEquals(SecurityScheme.In.HEADER, scheme.getIn());
        assertEquals("Authorization", scheme.getName());
        assertEquals(1, openAPI.getSecurity().size());
        assertTrue(openAPI.getSecurity().get(0).containsKey("Authorization"));
    }

    @Test
    void 接口分组名称为public()
    {
        GroupedOpenApi api = newConfig().publicApi();

        assertEquals("public", api.getGroup());
    }

    private SwaggerConfig newConfig()
    {
        RuoYiConfig ruoYiConfig = new RuoYiConfig();
        ruoYiConfig.setName("AI律师法律服务平台");
        ruoYiConfig.setVersion("1.0.0");

        SwaggerConfig config = new SwaggerConfig();
        ReflectionTestUtils.setField(config, "ruoyiConfig", ruoYiConfig);
        return config;
    }
}
