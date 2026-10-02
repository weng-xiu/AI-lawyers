package ai.lawyers.web.controller.agent;

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
import ai.lawyers.system.service.lawyers.agent.IAiAgentConfigService;
import ai.lawyers.web.controller.lawyers.agent.AiAgentConfigController;
import ai.lawyers.web.support.AbstractPermMvcTest;

/**
 * {@link AiAgentConfigController} 权限校验——8 个端点、6 个权限边界，
 * 逐端点验证未登录 401、权限码不符 403、持对应权限码放行 200。
 */
public class AiAgentConfigControllerPermTest extends AbstractPermMvcTest
{
    @Mock
    private IAiAgentConfigService aiAgentConfigService;

    private final List<Endpoint> endpoints = new ArrayList<>();

    @BeforeEach
    void setUp()
    {
        MockitoAnnotations.openMocks(this);
        when(aiAgentConfigService.selectAiAgentConfigList(any())).thenReturn(new ArrayList<>());
        when(aiAgentConfigService.selectActiveAgentConfigs()).thenReturn(new ArrayList<>());
        startMvc(AiAgentConfigController.class);
        buildEndpoints();
    }

    @Override
    protected void registerMocks()
    {
        registerMock("aiAgentConfigService", aiAgentConfigService);
    }

    private void buildEndpoints()
    {
        String base = "/lawyers/agent/config";
        endpoints.add(new Endpoint("lawyers:agent:config:list", get(base + "/list")));
        // 导出走 Excel 写入，仅校验 401/403，放行 200 不纳入矩阵
        endpoints.add(new Endpoint("lawyers:agent:config:export",
                post(base + "/export"), false));
        endpoints.add(new Endpoint("lawyers:agent:config:query", get(base + "/1")));
        endpoints.add(new Endpoint("lawyers:agent:config:add",
                post(base).contentType(MediaType.APPLICATION_JSON).content("{}")));
        endpoints.add(new Endpoint("lawyers:agent:config:edit",
                put(base).contentType(MediaType.APPLICATION_JSON).content("{}")));
        endpoints.add(new Endpoint("lawyers:agent:config:remove", delete(base + "/1")));
        endpoints.add(new Endpoint("lawyers:agent:config:list", get(base + "/active")));
        endpoints.add(new Endpoint("lawyers:agent:config:test", get(base + "/test/1")));
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
    void securedEndpoints_wrongPermission_403() throws Exception
    {
        for (Endpoint endpoint : endpoints)
        {
            if (endpoint.permission == null)
            {
                continue;
            }
            mockMvc.perform(endpoint.builder.with(loginAs("lawyers:nope")))
                    .andExpect(status().isForbidden());
        }
    }

    @Test
    void securedEndpoints_withCorrectPermission_200() throws Exception
    {
        for (Endpoint endpoint : endpoints)
        {
            if (endpoint.permission == null || !endpoint.expect200)
            {
                continue;
            }
            mockMvc.perform(endpoint.builder.with(loginAs(endpoint.permission)))
                    .andExpect(status().isOk());
        }
    }

    private static class Endpoint
    {
        private final String permission;

        private final MockHttpServletRequestBuilder builder;

        private final boolean expect200;

        Endpoint(String permission, MockHttpServletRequestBuilder builder)
        {
            this(permission, builder, true);
        }

        Endpoint(String permission, MockHttpServletRequestBuilder builder, boolean expect200)
        {
            this.permission = permission;
            this.builder = builder;
            this.expect200 = expect200;
        }
    }
}
