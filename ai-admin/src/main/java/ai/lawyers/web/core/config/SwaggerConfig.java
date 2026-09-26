package ai.lawyers.web.core.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import ai.lawyers.common.config.RuoYiConfig;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springdoc.core.GroupedOpenApi;

/**
 * Swagger/OpenAPI 接口文档配置（springdoc-openapi，替代已停维护的 springfox）
 *
 * @author ruoyi
 */
@Configuration
public class SwaggerConfig
{
    /** 系统基础配置 */
    @Autowired
    private RuoYiConfig ruoyiConfig;

    private static final String AUTH_SCHEME = "Authorization";

    /**
     * 全局 OpenAPI 元信息 + 统一 Authorization 请求头安全方案
     */
    @Bean
    public OpenAPI customOpenAPI()
    {
        return new OpenAPI()
                .info(new Info()
                        .title("标题：广东12348公共法律服务热线_接口文档")
                        .description("描述：12348 话务系统后端接口文档")
                        .contact(new Contact().name(ruoyiConfig.getName()))
                        .version("版本号:" + ruoyiConfig.getVersion()))
                .components(new Components().addSecuritySchemes(AUTH_SCHEME,
                        new SecurityScheme()
                                .type(SecurityScheme.Type.APIKEY)
                                .in(SecurityScheme.In.HEADER)
                                .name(AUTH_SCHEME)))
                .addSecurityItem(new SecurityRequirement().addList(AUTH_SCHEME));
    }

    /**
     * 接口分组：仅扫描标注了 @Operation 的接口
     */
    @Bean
    public GroupedOpenApi publicApi()
    {
        return GroupedOpenApi.builder()
                .group("public")
                .pathsToMatch("/**")
                .build();
    }
}
