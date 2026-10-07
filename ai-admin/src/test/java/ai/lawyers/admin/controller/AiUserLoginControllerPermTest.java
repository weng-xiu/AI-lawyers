package ai.lawyers.admin.controller;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import ai.lawyers.common.core.redis.RedisCache;
import ai.lawyers.framework.web.service.AiUserLoginService;
import ai.lawyers.framework.web.service.SysPermissionService;
import ai.lawyers.framework.web.service.TokenService;
import ai.lawyers.system.service.ISysConfigService;
import ai.lawyers.system.service.ISysMenuService;
import ai.lawyers.web.support.AbstractPermMvcTest;
import com.google.code.kaptcha.Producer;

/**
 * {@link AiUserLoginController} 权限校验——/login、/captchaImage 标注 @Anonymous
 * 属公开接口（跳过）；logout/getInfo/getRouters 无 @PreAuthorize 但必须登录，
 * 仅验证未登录 401、登录（任意权限）后 200，不验证 403。
 */
public class AiUserLoginControllerPermTest extends AbstractPermMvcTest
{
    @Mock
    private AiUserLoginService loginService;

    @Mock
    private ISysMenuService menuService;

    @Mock
    private SysPermissionService permissionService;

    @Mock
    private TokenService tokenService;

    @Mock
    private ISysConfigService configService;

    @Mock
    private RedisCache redisCache;

    @Mock(name = "captchaProducer")
    private Producer captchaProducer;

    @Mock(name = "captchaProducerMath")
    private Producer captchaProducerMath;

    private final List<MockHttpServletRequestBuilder> endpoints = new ArrayList<>();

    @BeforeEach
    void setUp()
    {
        MockitoAnnotations.openMocks(this);
        when(menuService.selectMenuTreeByUserId(anyLong())).thenReturn(new ArrayList<>());
        startMvc(AiUserLoginController.class);
        buildEndpoints();
    }

    @Override
    protected void registerMocks()
    {
        registerMock("loginService", loginService);
        registerMock("menuService", menuService);
        registerMock("permissionService", permissionService);
        registerMock("tokenService", tokenService);
        registerMock("configService", configService);
        registerMock("redisCache", redisCache);
        registerMock("captchaProducer", captchaProducer);
        registerMock("captchaProducerMath", captchaProducerMath);
    }

    private void buildEndpoints()
    {
        // /aiuser/login 与 /aiuser/captchaImage 标注 @Anonymous，属公开接口，跳过鉴权断言
        endpoints.add(post("/aiuser/logout").contentType(MediaType.APPLICATION_JSON).content("{}"));
        endpoints.add(get("/aiuser/getInfo"));
        endpoints.add(get("/aiuser/getRouters"));
    }

    @Test
    void allEndpoints_unauthenticated_401() throws Exception
    {
        for (MockHttpServletRequestBuilder endpoint : endpoints)
        {
            mockMvc.perform(endpoint).andExpect(status().isUnauthorized());
        }
    }

    @Test
    void allEndpoints_whenLoggedIn_200() throws Exception
    {
        for (MockHttpServletRequestBuilder endpoint : endpoints)
        {
            mockMvc.perform(endpoint.with(loginAs()))
                    .andExpect(status().isOk());
        }
    }
}
