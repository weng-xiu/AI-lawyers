package ai.lawyers.admin.controller.lawyers;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.ArrayList;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import ai.lawyers.common.core.domain.entity.SysUser;
import ai.lawyers.common.core.domain.model.LoginUser;
import ai.lawyers.system.domain.lawyers.AiCallerProfile;
import ai.lawyers.system.domain.lawyers.AiChannelIdentity;
import ai.lawyers.system.mapper.lawyers.AiCallerProfileMapper;
import ai.lawyers.system.service.lawyers.IAiCallTicketService;
import ai.lawyers.system.service.lawyers.IAiChannelIdentityService;
import ai.lawyers.system.service.lawyers.IAiExternalOrgService;
import ai.lawyers.web.support.AbstractPermMvcTest;

/**
 * {@link AiUserPortalController} 权限校验——除 /services/orgs 标注 @Anonymous
 * （公开目录，跳过）外，6 个端点均无 @PreAuthorize，但强制要求公众端登录态且数据按
 * 本人手机号归属。仅验证未登录 401、登录（已绑手机号、任意权限）后 200，不验证 403。
 */
public class AiUserPortalControllerPermTest extends AbstractPermMvcTest
{
    /** 与基类注入过滤器约定一致的请求属性键（基类字段包内可见，此处按同值派生）。 */
    private static final String ATTR_INJECT_AUTH = AbstractPermMvcTest.class.getName() + ".injectAuth";

    @Mock
    private IAiCallTicketService callTicketService;

    @Mock
    private IAiExternalOrgService externalOrgService;

    @Mock
    private AiCallerProfileMapper callerProfileMapper;

    @Mock
    private IAiChannelIdentityService channelIdentityService;

    private final java.util.List<MockHttpServletRequestBuilder> endpoints = new ArrayList<>();

    @BeforeEach
    void setUp()
    {
        MockitoAnnotations.openMocks(this);
        when(callTicketService.selectAiCallTicketList(any())).thenReturn(new ArrayList<>());
        AiCallerProfile profile = new AiCallerProfile();
        profile.setProfileId(10L);
        when(callerProfileMapper.selectAiCallerProfileByCallerNumberIndex(any())).thenReturn(profile);
        AiChannelIdentity identity = new AiChannelIdentity();
        identity.setId(1L);
        identity.setProfileId(10L);
        when(channelIdentityService.selectIdentityById(1L)).thenReturn(identity);
        when(channelIdentityService.selectBoundByProfileId(10L)).thenReturn(new ArrayList<>());
        startMvc(AiUserPortalController.class);
        buildEndpoints();
    }

    @Override
    protected void registerMocks()
    {
        registerMock("callTicketService", callTicketService);
        registerMock("externalOrgService", externalOrgService);
        registerMock("callerProfileMapper", callerProfileMapper);
        registerMock("channelIdentityService", channelIdentityService);
    }

    private void buildEndpoints()
    {
        String base = "/lawyers/portal";
        endpoints.add(get(base + "/ticket/list"));
        endpoints.add(post(base + "/preference")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"languagePreference\":\"zh-CN\",\"careMode\":0}"));
        endpoints.add(get(base + "/channel/list"));
        endpoints.add(post(base + "/channel/requestBind")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"channelType\":\"WECHAT_MINI\",\"channelUid\":\"openid-x\"}"));
        endpoints.add(post(base + "/channel/confirm")
                .contentType(MediaType.APPLICATION_JSON).content("{\"id\":1}"));
        endpoints.add(post(base + "/channel/unbind")
                .contentType(MediaType.APPLICATION_JSON).content("{\"id\":1}"));
        // /services/orgs 标注 @Anonymous，属公开目录，跳过鉴权断言
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
            mockMvc.perform(endpoint.with(loginAsPortalUser()))
                    .andExpect(status().isOk());
        }
    }

    /** 构造已绑定手机号的公众端登录态（门户全部接口按手机号做归属）。 */
    private static RequestPostProcessor loginAsPortalUser()
    {
        return request -> {
            SysUser user = new SysUser();
            user.setUserId(2002L);
            user.setUserName("portal-tester");
            user.setPhonenumber("15800000000");
            LoginUser loginUser = new LoginUser(user, new java.util.HashSet<>());
            UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                    loginUser, null, loginUser.getAuthorities());
            request.setAttribute(ATTR_INJECT_AUTH, authentication);
            return request;
        };
    }
}
