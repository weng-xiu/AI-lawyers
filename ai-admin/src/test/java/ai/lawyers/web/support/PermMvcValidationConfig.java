package ai.lawyers.web.support;

import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.validation.Validator;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

/**
 * P2-14：在权限基类迷你容器之上启用真实 Bean Validation 的配置。
 *
 * <p>继承 {@link AbstractPermMvcTest.PermMvcConfig} 的安全链与 {@code @ss}，
 * 仅把恒通过 Validator 替换为真实 {@link LocalValidatorFactoryBean}，
 * 使 {@code @RequestBody @Valid} 上的 {@code @NotBlank/@Size} 等约束在 400 路径中真实生效。</p>
 *
 * @author ai-lawyers
 */
@EnableWebSecurity
@EnableWebMvc
@EnableMethodSecurity(prePostEnabled = true)
public class PermMvcValidationConfig extends AbstractPermMvcTest.PermMvcConfig
{
    private final LocalValidatorFactoryBean validator;

    public PermMvcValidationConfig()
    {
        // 未注册为受管 Bean，手工完成初始化（构建底层 ValidatorFactory）。
        validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();
    }

    @Override
    public Validator getValidator()
    {
        return validator;
    }
}
