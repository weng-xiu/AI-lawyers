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
import ai.lawyers.system.service.lawyers.stat.IDashboardService;
import ai.lawyers.web.support.AbstractPermMvcTest;

/**
 * P2-14：{@link DashboardController} 权限校验——1 个端点，逐端点验证
 * 未登录 401、权限码不符 403、持对应权限码放行 200。
 */
public class DashboardControllerPermTest extends AbstractPermMvcTest
{
    @Mock
    private IDashboardService dashboardService;

    private final List<Endpoint> endpoints = new ArrayList<>();

    @BeforeEach
    void setUp()
    {
        MockitoAnnotations.openMocks(this);
        startMvc(DashboardController.class);
        buildEndpoints();
    }

    @Override
    protected void registerMocks()
    {
        registerMock("dashboardService", dashboardService);
    }

    private void buildEndpoints()
    {
        String base = "/lawyers/dashboard";
        endpoints.add(new Endpoint("lawyers:dashboard:view", get(base + "/data")));
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
