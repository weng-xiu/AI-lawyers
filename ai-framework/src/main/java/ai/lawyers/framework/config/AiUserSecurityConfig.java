package ai.lawyers.framework.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.authentication.logout.LogoutFilter;
import ai.lawyers.framework.security.filter.JwtAuthenticationTokenFilter;
import ai.lawyers.framework.security.handle.AuthenticationEntryPointImpl;
import ai.lawyers.framework.security.handle.LogoutSuccessHandlerImpl;
import org.springframework.web.filter.CorsFilter;
import org.springframework.stereotype.Component;

/**
 * 用户端spring security配置
 * 
 * @author AI律师
 */
@Component
public class AiUserSecurityConfig
{
    /**
     * 用户端用户认证逻辑
     */
    @Autowired
    @Qualifier("aiUserDetailsService")
    private UserDetailsService userDetailsService;
    
    /**
     * 认证失败处理类
     */
    @Autowired
    private AuthenticationEntryPointImpl unauthorizedHandler;

    /**
     * 退出处理类
     */
    @Autowired
    private LogoutSuccessHandlerImpl logoutSuccessHandler;

    /**
     * token认证过滤器
     */
    @Autowired
    private JwtAuthenticationTokenFilter authenticationTokenFilter;
    
    /**
     * 跨域过滤器
     */
    @Autowired
    private CorsFilter corsFilter;

    /**
     * 用户端身份验证实现
     */
    @Bean("userAuthenticationManager")
    public AuthenticationManager userAuthenticationManager()
    {
        DaoAuthenticationProvider daoAuthenticationProvider = new DaoAuthenticationProvider();
        daoAuthenticationProvider.setUserDetailsService(userDetailsService);
        daoAuthenticationProvider.setPasswordEncoder(bCryptPasswordEncoder());
        return new ProviderManager(daoAuthenticationProvider);
    }

    /**
     * 用户端BCryptPasswordEncoder
     */
    @Bean("userBCryptPasswordEncoder")
    public BCryptPasswordEncoder bCryptPasswordEncoder()
    {
        return new BCryptPasswordEncoder();
    }
}