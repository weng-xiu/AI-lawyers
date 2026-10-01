package ai.lawyers.web.support;

import java.io.IOException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.springframework.context.annotation.Bean;
import org.springframework.mock.web.MockServletContext;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.intercept.AuthorizationFilter;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import ai.lawyers.common.core.domain.entity.SysUser;
import ai.lawyers.common.core.domain.model.LoginUser;
import ai.lawyers.framework.web.service.PermissionService;

/**
 * P2-14：Controller 权限校验 MockMvc 基类。
 *
 * <p>迷你 Web 容器（{@link EnableWebMvc} + {@link EnableMethodSecurity} + 真实
 * {@link SecurityFilterChain}），不启动 DB/Redis；断言语义与生产一致：未登录 401、
 * 权限不足 403、权限匹配放行到 Service。</p>
 *
 * @author ai-lawyers
 */
public abstract class AbstractPermMvcTest
{
    protected AnnotationConfigWebApplicationContext context;

    protected MockMvc mockMvc;

    /** refresh 前暂存的 mock，经 BeanFactoryPostProcessor 注册到真实工厂（此时工厂尚未创建）。 */
    private final Map<String, Object> pendingMocks = new LinkedHashMap<>();

    /**
     * 启动迷你容器并构建 MockMvc。
     *
     * @param controllers 待测 Controller 类型（依赖通过 {@link #registerMock} 以 mock 注入）
     */
    protected final void startMvc(Class<?>... controllers)
    {
        context = new AnnotationConfigWebApplicationContext();
        context.setServletContext(new MockServletContext());
        context.register(PermMvcConfig.class);
        registerMocks();
        context.addBeanFactoryPostProcessor(beanFactory ->
                pendingMocks.forEach(beanFactory::registerSingleton));
        for (Class<?> controller : controllers)
        {
            context.register(controller);
        }
        context.refresh();
        // 无 spring-security-test 依赖，手工把真实 FilterChainProxy 接入 MockMvc——
        // 401 由 URL 层 anyRequest authenticated 触发，403 由方法层 @PreAuthorize 触发，
        // ExceptionTranslationFilter 统一翻译为配置中指定的入口/拒绝处理器
        javax.servlet.Filter securityFilterChain =
                (javax.servlet.Filter) context.getBean("springSecurityFilterChain");
        mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .addFilters(securityFilterChain)
                .build();
    }

    /** 子类覆写：在此调用 {@link #registerMock}（容器刷新前）。 */
    protected void registerMocks()
    {
    }

    /**
     * 注册一个按类型注入 Controller 的 mock 单例。
     *
     * @param name bean 名称（同类型多 mock 时需唯一）
     * @param mock mock 实例（@BeforeEach 中 openMocks 后调用）
     */
    protected final void registerMock(String name, Object mock)
    {
        pendingMocks.put(name, mock);
    }

    @AfterEach
    void tearDown()
    {
        if (context != null)
        {
            context.close();
            context = null;
        }
        SecurityContextHolder.clearContext();
    }

    /**
     * 请求属性键：携带待注入的 {@link Authentication}，由链内 TestLoginInjectFilter 写入上下文。
     */
    static final String ATTR_INJECT_AUTH = AbstractPermMvcTest.class.getName() + ".injectAuth";

    /**
     * 构造持有指定权限码的已登录坐席（principal=LoginUser，{@code @ss} 按此判定）。
     * 认证体经请求属性在安全链内注入，模拟生产 JwtAuthenticationTokenFilter 的行为。
     */
    public static RequestPostProcessor loginAs(String... permissions)
    {
        return request -> {
            SysUser user = new SysUser();
            user.setUserId(1001L);
            user.setUserName("perm-tester");
            LoginUser loginUser = new LoginUser(user,
                    permissions.length == 0 ? new HashSet<>() : new HashSet<>(Arrays.asList(permissions)));
            UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                    loginUser, null, loginUser.getAuthorities());
            request.setAttribute(ATTR_INJECT_AUTH, authentication);
            return request;
        };
    }

    /**
     * 链内认证注入过滤器：等价于生产 JwtAuthenticationTokenFilter——在
     * SecurityContextHolderFilter 建立的（空）上下文之上写入本次请求的登录态。
     */
    static class TestLoginInjectFilter extends OncePerRequestFilter
    {
        @Override
        protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                FilterChain filterChain) throws ServletException, IOException
        {
            Authentication authentication = (Authentication) request.getAttribute(ATTR_INJECT_AUTH);
            if (authentication != null)
            {
                SecurityContextHolder.getContext().setAuthentication(authentication);
            }
            filterChain.doFilter(request, response);
        }
    }

    /** 迷你安全配置：URL 层 anyRequest authenticated + 方法层 @PreAuthorize，全真实链路。 */
    @EnableWebSecurity
    @EnableWebMvc
    @EnableMethodSecurity(prePostEnabled = true)
    static class PermMvcConfig
    {
        @Bean("ss")
        PermissionService permissionService()
        {
            return new PermissionService();
        }

        @Bean
        SecurityFilterChain testFilterChain(HttpSecurity http) throws Exception
        {
            http.csrf(csrf -> csrf.disable())
                    .sessionManagement(session -> session
                            .sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                    .exceptionHandling(ex -> ex
                            .authenticationEntryPoint((request, response, authException) ->
                                    response.sendError(401))
                            .accessDeniedHandler((request, response, accessDeniedException) ->
                                    response.sendError(403)))
                    // 在 URL 授权前注入本次请求登录态（模拟生产 JWT 过滤器）
                    .addFilterBefore(new TestLoginInjectFilter(), AuthorizationFilter.class)
                    .authorizeHttpRequests(auth -> auth.anyRequest().authenticated());
            return http.build();
        }
    }
}
