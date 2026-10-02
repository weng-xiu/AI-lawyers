package ai.lawyers.web.controller.lawyers.schedule;

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
import ai.lawyers.system.service.lawyers.IAiCallAgentStatusService;
import ai.lawyers.system.service.lawyers.schedule.IAiAgentScheduleService;
import ai.lawyers.web.support.AbstractPermMvcTest;

/**
 * {@link AiAgentScheduleController} 权限校验——逐端点验证未登录 401、权限码不符 403、
 * 持对应权限码放行 200。
 */
public class AiAgentScheduleControllerPermTest extends AbstractPermMvcTest
{
    @Mock
    private IAiAgentScheduleService aiAgentScheduleService;

    @Mock
    private IAiCallAgentStatusService aiCallAgentStatusService;

    private final List<Endpoint> endpoints = new ArrayList<>();

    @BeforeEach
    void setUp()
    {
        MockitoAnnotations.openMocks(this);
        when(aiAgentScheduleService.selectScheduleList(any())).thenReturn(new ArrayList<>());
        startMvc(AiAgentScheduleController.class);
        buildEndpoints();
    }

    @Override
    protected void registerMocks()
    {
        registerMock("aiAgentScheduleService", aiAgentScheduleService);
        registerMock("aiCallAgentStatusService", aiCallAgentStatusService);
    }

    private void buildEndpoints()
    {
        String base = "/lawyers/schedule/agent";
        endpoints.add(new Endpoint("lawyers:schedule:list", get(base + "/list")));
        endpoints.add(new Endpoint("lawyers:schedule:list",
                get(base + "/range").param("startDate", "2026-01-01").param("endDate", "2026-01-02")));
        endpoints.add(new Endpoint("lawyers:schedule:query", get(base + "/1")));
        endpoints.add(new Endpoint("lawyers:schedule:query",
                get(base + "/today").param("agentId", "1")));
        endpoints.add(new Endpoint("lawyers:schedule:add",
                post(base).contentType(MediaType.APPLICATION_JSON).content("{}")));
        endpoints.add(new Endpoint("lawyers:schedule:add",
                post(base + "/batch").contentType(MediaType.APPLICATION_JSON).content("[]")));
        endpoints.add(new Endpoint("lawyers:schedule:edit",
                put(base).contentType(MediaType.APPLICATION_JSON).content("{}")));
        endpoints.add(new Endpoint("lawyers:schedule:remove", delete(base + "/1")));
        endpoints.add(new Endpoint("lawyers:schedule:check", put(base + "/checkIn")));
        endpoints.add(new Endpoint("lawyers:schedule:check", put(base + "/checkOut")));
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
            if (!endpoint.expect200)
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
