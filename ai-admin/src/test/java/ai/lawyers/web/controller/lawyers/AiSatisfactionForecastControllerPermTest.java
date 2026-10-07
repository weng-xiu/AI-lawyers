package ai.lawyers.web.controller.lawyers;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import ai.lawyers.system.service.lawyers.IAiSatisfactionForecastService;
import ai.lawyers.web.support.AbstractPermMvcTest;

/**
 * {@link AiSatisfactionForecastController} 权限校验——2 个端点均复用
 * lawyers:report:view 权限码，逐端点验证未登录 401、权限码不符 403、持权放行 200。
 */
public class AiSatisfactionForecastControllerPermTest extends AbstractPermMvcTest
{
    @Mock
    private IAiSatisfactionForecastService forecastService;

    private final List<Endpoint> endpoints = new ArrayList<>();

    @BeforeEach
    void setUp()
    {
        MockitoAnnotations.openMocks(this);
        when(forecastService.list(any(), any(), anyInt(), anyInt())).thenReturn(new ArrayList<>());
        startMvc(AiSatisfactionForecastController.class);
        buildEndpoints();
    }

    @Override
    protected void registerMocks()
    {
        registerMock("forecastService", forecastService);
    }

    private void buildEndpoints()
    {
        String base = "/lawyers/satisfactionForecast";
        String begin = "2026-10-01 00:00:00";
        String end = "2026-10-02 00:00:00";
        endpoints.add(new Endpoint("lawyers:report:view",
                get(base + "/summary").param("beginTime", begin).param("endTime", end)));
        endpoints.add(new Endpoint("lawyers:report:view",
                get(base + "/list").param("beginTime", begin).param("endTime", end)));
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
