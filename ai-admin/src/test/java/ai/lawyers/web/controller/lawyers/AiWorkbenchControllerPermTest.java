package ai.lawyers.web.controller.lawyers;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import ai.lawyers.system.service.lawyers.IWorkbenchService;
import ai.lawyers.web.support.AbstractPermMvcTest;

/**
 * P2-14：{@link AiWorkbenchController} 权限校验——4 个端点共用
 * lawyers:workbench:index（工位聚合对持工位权限的坐席开放）。
 */
public class AiWorkbenchControllerPermTest extends AbstractPermMvcTest
{
    @Mock
    private IWorkbenchService workbenchService;

    private final List<Endpoint> endpoints = new ArrayList<>();

    @BeforeEach
    void setUp()
    {
        MockitoAnnotations.openMocks(this);
        startMvc(AiWorkbenchController.class);
        buildEndpoints();
    }

    @Override
    protected void registerMocks()
    {
        registerMock("workbenchService", workbenchService);
    }

    private void buildEndpoints()
    {
        String base = "/lawyers/workbench";
        endpoints.add(new Endpoint("lawyers:workbench:index", get(base + "/stats")));
        endpoints.add(new Endpoint("lawyers:workbench:index", get(base + "/todos")));
        endpoints.add(new Endpoint("lawyers:workbench:index",
                get(base + "/recentCalls").param("limit", "10")));
        endpoints.add(new Endpoint("lawyers:workbench:index",
                get(base + "/notices").param("limit", "10")));
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
