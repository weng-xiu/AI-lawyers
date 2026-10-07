package ai.lawyers.web.controller.lawyers;

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
import ai.lawyers.system.service.lawyers.IAiCallBehaviorRiskService;
import ai.lawyers.web.support.AbstractPermMvcTest;

/**
 * {@link AiCallBehaviorRiskController} 权限校验——3 个端点、2 个权限边界
 * （list 复用 lawyers:hotspot:list，scan/review 复用 lawyers:hotspot:edit），
 * 逐端点验证未登录 401、权限码不符 403、持对应权限码放行 200。
 */
public class AiCallBehaviorRiskControllerPermTest extends AbstractPermMvcTest
{
    @Mock
    private IAiCallBehaviorRiskService riskService;

    private final List<Endpoint> endpoints = new ArrayList<>();

    @BeforeEach
    void setUp()
    {
        MockitoAnnotations.openMocks(this);
        when(riskService.selectRiskList(any())).thenReturn(new ArrayList<>());
        startMvc(AiCallBehaviorRiskController.class);
        buildEndpoints();
    }

    @Override
    protected void registerMocks()
    {
        registerMock("riskService", riskService);
    }

    private void buildEndpoints()
    {
        String base = "/lawyers/callRisk";
        endpoints.add(new Endpoint("lawyers:hotspot:list", get(base + "/list")));
        endpoints.add(new Endpoint("lawyers:hotspot:edit", post(base + "/scan")));
        endpoints.add(new Endpoint("lawyers:hotspot:edit",
                post(base + "/review").contentType(MediaType.APPLICATION_JSON).content("{}")));
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
