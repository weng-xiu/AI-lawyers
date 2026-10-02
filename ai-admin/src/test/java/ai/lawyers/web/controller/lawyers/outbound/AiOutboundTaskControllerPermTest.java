package ai.lawyers.web.controller.lawyers.outbound;

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
import ai.lawyers.system.service.lawyers.outbound.IAiOutboundTaskService;
import ai.lawyers.system.service.lawyers.outbound.IOutboundExecutionService;
import ai.lawyers.web.support.AbstractPermMvcTest;

/**
 * {@link AiOutboundTaskController} 权限校验——逐端点验证未登录 401、权限码不符 403、
 * 持对应权限码放行 200。{@code /event} 网关回调为匿名端点，单独验证。
 */
public class AiOutboundTaskControllerPermTest extends AbstractPermMvcTest
{
    @Mock
    private IAiOutboundTaskService aiOutboundTaskService;

    @Mock
    private IOutboundExecutionService outboundExecutionService;

    private final List<Endpoint> endpoints = new ArrayList<>();

    @BeforeEach
    void setUp()
    {
        MockitoAnnotations.openMocks(this);
        when(aiOutboundTaskService.selectAiOutboundTaskList(any())).thenReturn(new ArrayList<>());
        startMvc(AiOutboundTaskController.class);
        buildEndpoints();
    }

    @Override
    protected void registerMocks()
    {
        registerMock("aiOutboundTaskService", aiOutboundTaskService);
        registerMock("outboundExecutionService", outboundExecutionService);
    }

    private void buildEndpoints()
    {
        String base = "/lawyers/outbound/task";
        endpoints.add(new Endpoint("lawyers:outbound:task:list", get(base + "/list")));
        endpoints.add(new Endpoint("lawyers:outbound:task:export", post(base + "/export")));
        endpoints.add(new Endpoint("lawyers:outbound:task:query", get(base + "/1")));
        endpoints.add(new Endpoint("lawyers:outbound:task:add",
                post(base).contentType(MediaType.APPLICATION_JSON).content("{}")));
        endpoints.add(new Endpoint("lawyers:outbound:task:edit",
                put(base).contentType(MediaType.APPLICATION_JSON).content("{}")));
        endpoints.add(new Endpoint("lawyers:outbound:task:remove", delete(base + "/1")));
        endpoints.add(new Endpoint("lawyers:outbound:task:query", get(base + "/generateNo")));
        endpoints.add(new Endpoint("lawyers:outbound:task:start", post(base + "/start/1")));
        endpoints.add(new Endpoint("lawyers:outbound:task:pause", post(base + "/pause/1")));
        endpoints.add(new Endpoint("lawyers:outbound:task:stop", post(base + "/stop/1")));
        endpoints.add(new Endpoint("lawyers:outbound:task:start", post(base + "/execute/1")));
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

    @Test
    void eventCallback_unauthenticated_401() throws Exception
    {
        // 迷你容器不处理 @Anonymous，匿名访问仍被 URL 层拦截
        mockMvc.perform(post("/lawyers/outbound/task/event")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"callUuid\":\"uuid-1\",\"event\":\"ANSWERED\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void eventCallback_loggedIn_200() throws Exception
    {
        mockMvc.perform(post("/lawyers/outbound/task/event")
                        .with(loginAs("lawyers:nope"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"callUuid\":\"uuid-1\",\"event\":\"ANSWERED\"}"))
                .andExpect(status().isOk());
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
