package ai.lawyers.web.controller.lawyers;

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
import ai.lawyers.system.service.lawyers.IAiCallForecastService;
import ai.lawyers.web.support.AbstractPermMvcTest;

/**
 * {@link AiCallForecastController} 权限校验——7 个端点、2 个权限边界：
 * 查询/预览/确认/日历列表需 lawyers:report:view，日历写操作需 lawyers:report:export。
 * 逐端点验证未登录 401、权限码不符 403、持对应权限码放行 200。
 */
public class AiCallForecastControllerPermTest extends AbstractPermMvcTest
{
    @Mock
    private IAiCallForecastService forecastService;

    private final List<Endpoint> endpoints = new ArrayList<>();

    @BeforeEach
    void setUp()
    {
        MockitoAnnotations.openMocks(this);
        when(forecastService.selectCalendarList(any())).thenReturn(new ArrayList<>());
        startMvc(AiCallForecastController.class);
        buildEndpoints();
    }

    @Override
    protected void registerMocks()
    {
        registerMock("forecastService", forecastService);
    }

    private void buildEndpoints()
    {
        String base = "/lawyers/forecast";
        endpoints.add(new Endpoint("lawyers:report:view", get(base + "/preview")));
        endpoints.add(new Endpoint("lawyers:report:view", get(base + "/staffing")));
        endpoints.add(new Endpoint("lawyers:report:view",
                post(base + "/confirm").contentType(MediaType.APPLICATION_JSON).content("{}")));
        endpoints.add(new Endpoint("lawyers:report:view", get(base + "/calendar/list")));
        endpoints.add(new Endpoint("lawyers:report:export",
                post(base + "/calendar").contentType(MediaType.APPLICATION_JSON).content("{}")));
        endpoints.add(new Endpoint("lawyers:report:export",
                put(base + "/calendar").contentType(MediaType.APPLICATION_JSON).content("{}")));
        endpoints.add(new Endpoint("lawyers:report:export", delete(base + "/calendar/1")));
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
