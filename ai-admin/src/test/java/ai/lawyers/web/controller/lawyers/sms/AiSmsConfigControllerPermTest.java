package ai.lawyers.web.controller.lawyers.sms;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import ai.lawyers.system.mapper.lawyers.sms.AiSmsConfigMapper;
import ai.lawyers.web.support.AbstractPermMvcTest;

/**
 * {@link AiSmsConfigController} 权限校验——共 6 个端点：list、详情 2 个端点带
 * {@code lawyers:smsConfig:view} 权限码，走 401/403/200 三段式；新增、修改、删除、
 * 启用列表 4 个端点无 {@code @PreAuthorize}（仅登录即可），只验证未登录 401 与
 * 任意登录用户 200。
 */
public class AiSmsConfigControllerPermTest extends AbstractPermMvcTest
{
    @Mock
    private AiSmsConfigMapper aiSmsConfigMapper;

    private final List<Endpoint> protectedEndpoints = new ArrayList<>();

    private final List<MockHttpServletRequestBuilder> loginOnlyEndpoints = new ArrayList<>();

    @BeforeEach
    void setUp()
    {
        MockitoAnnotations.openMocks(this);
        when(aiSmsConfigMapper.selectAiSmsConfigList(any())).thenReturn(new ArrayList<>());
        startMvc(AiSmsConfigController.class);
        buildEndpoints();
    }

    @Override
    protected void registerMocks()
    {
        registerMock("aiSmsConfigMapper", aiSmsConfigMapper);
    }

    private void buildEndpoints()
    {
        String base = "/lawyers/sms/config";
        protectedEndpoints.add(new Endpoint("lawyers:smsConfig:view", get(base + "/list")));
        protectedEndpoints.add(new Endpoint("lawyers:smsConfig:view", get(base + "/1")));
        loginOnlyEndpoints.add(post(base).contentType(MediaType.APPLICATION_JSON).content("{}"));
        loginOnlyEndpoints.add(put(base).contentType(MediaType.APPLICATION_JSON).content("{}"));
        loginOnlyEndpoints.add(delete(base + "/1"));
        loginOnlyEndpoints.add(get(base + "/enabled"));
    }

    @Test
    void protectedEndpoints_unauthenticated_401() throws Exception
    {
        for (Endpoint endpoint : protectedEndpoints)
        {
            mockMvc.perform(endpoint.builder).andExpect(status().isUnauthorized());
        }
    }

    @Test
    void protectedEndpoints_wrongPermission_403() throws Exception
    {
        for (Endpoint endpoint : protectedEndpoints)
        {
            mockMvc.perform(endpoint.builder.with(loginAs("lawyers:nope")))
                    .andExpect(status().isForbidden());
        }
    }

    @Test
    void protectedEndpoints_withCorrectPermission_200() throws Exception
    {
        for (Endpoint endpoint : protectedEndpoints)
        {
            mockMvc.perform(endpoint.builder.with(loginAs(endpoint.permission)))
                    .andExpect(status().isOk());
        }
    }

    @Test
    void loginOnlyEndpoints_unauthenticated_401() throws Exception
    {
        for (MockHttpServletRequestBuilder endpoint : loginOnlyEndpoints)
        {
            mockMvc.perform(endpoint).andExpect(status().isUnauthorized());
        }
    }

    @Test
    void loginOnlyEndpoints_anyLogin_200() throws Exception
    {
        for (MockHttpServletRequestBuilder endpoint : loginOnlyEndpoints)
        {
            mockMvc.perform(endpoint.with(loginAs("lawyers:nope")))
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
