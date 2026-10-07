package ai.lawyers.web.controller.lawyers.collab;

import static org.mockito.ArgumentMatchers.any;
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
import ai.lawyers.system.mapper.lawyers.AiCallerProfileMapper;
import ai.lawyers.system.service.lawyers.IAiChannelIdentityService;
import ai.lawyers.system.service.lawyers.IAiUnifiedSessionService;
import ai.lawyers.web.support.AbstractPermMvcTest;

/**
 * P2-14：{@link AiChannelIdentityController} 权限校验——6 个端点、4 个权限边界，
 * 逐端点验证未登录 401、权限码不符 403、持对应权限码放行 200。
 * requestBind/confirm/unbind 共用 lawyers:channelIdentity:unbind。
 */
public class AiChannelIdentityControllerPermTest extends AbstractPermMvcTest
{
    @Mock
    private IAiChannelIdentityService channelIdentityService;

    @Mock
    private IAiUnifiedSessionService unifiedSessionService;

    @Mock
    private AiCallerProfileMapper callerProfileMapper;

    private final List<Endpoint> endpoints = new ArrayList<>();

    @BeforeEach
    void setUp()
    {
        MockitoAnnotations.openMocks(this);
        when(channelIdentityService.selectIdentityList(any())).thenReturn(new ArrayList<>());
        when(unifiedSessionService.timeline(any(), any())).thenReturn(new ArrayList<>());
        startMvc(AiChannelIdentityController.class);
        buildEndpoints();
    }

    @Override
    protected void registerMocks()
    {
        registerMock("channelIdentityService", channelIdentityService);
        registerMock("unifiedSessionService", unifiedSessionService);
        registerMock("callerProfileMapper", callerProfileMapper);
    }

    private void buildEndpoints()
    {
        String base = "/lawyers/channel";
        endpoints.add(new Endpoint("lawyers:channelIdentity:list", get(base + "/identity/list")));
        endpoints.add(new Endpoint("lawyers:channelIdentity:unbind",
                post(base + "/identity/requestBind").contentType(MediaType.APPLICATION_JSON).content("{}")));
        endpoints.add(new Endpoint("lawyers:channelIdentity:unbind",
                post(base + "/identity/confirm/1").contentType(MediaType.APPLICATION_JSON).content("{}")));
        endpoints.add(new Endpoint("lawyers:channelIdentity:unbind",
                post(base + "/identity/unbind/1").contentType(MediaType.APPLICATION_JSON).content("{}")));
        endpoints.add(new Endpoint("lawyers:call:popup:query", get(base + "/timeline").param("profileId", "1")));
        endpoints.add(new Endpoint("lawyers:channelIdentity:query",
                get(base + "/identity/bound/1")));
    }

    @Test
    void allEndpoints_unauthenticated_401() throws Exception
    {
        for (Endpoint endpoint : endpoints)
        {
            mockMvc.perform(endpoint.builder).andExpect(status().isUnauthorized());
        }
    }

    @Test
    void allEndpoints_wrongPermission_403() throws Exception
    {
        for (Endpoint endpoint : endpoints)
        {
            mockMvc.perform(endpoint.builder.with(loginAs("lawyers:nope")))
                    .andExpect(status().isForbidden());
        }
    }

    @Test
    void allEndpoints_withCorrectPermission_200() throws Exception
    {
        for (Endpoint endpoint : endpoints)
        {
            mockMvc.perform(endpoint.builder.with(loginAs(endpoint.permission)))
                    .andExpect(status().isOk());
        }
    }

    private static class Endpoint
    {
        private final String permission;

        private final MockHttpServletRequestBuilder builder;

        Endpoint(String permission, MockHttpServletRequestBuilder builder)
        {
            this.permission = permission;
            this.builder = builder;
        }
    }
}
