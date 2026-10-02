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
import ai.lawyers.system.service.lawyers.outbound.IAiOutboundCalleeService;
import ai.lawyers.web.support.AbstractPermMvcTest;

/**
 * {@link AiOutboundCalleeController} 权限校验——逐端点验证未登录 401、权限码不符 403、
 * 持对应权限码放行 200。
 */
public class AiOutboundCalleeControllerPermTest extends AbstractPermMvcTest
{
    @Mock
    private IAiOutboundCalleeService aiOutboundCalleeService;

    private final List<Endpoint> endpoints = new ArrayList<>();

    @BeforeEach
    void setUp()
    {
        MockitoAnnotations.openMocks(this);
        when(aiOutboundCalleeService.selectAiOutboundCalleeList(any())).thenReturn(new ArrayList<>());
        startMvc(AiOutboundCalleeController.class);
        buildEndpoints();
    }

    @Override
    protected void registerMocks()
    {
        registerMock("aiOutboundCalleeService", aiOutboundCalleeService);
    }

    private void buildEndpoints()
    {
        String base = "/lawyers/outbound/callee";
        endpoints.add(new Endpoint("lawyers:outbound:callee:list", get(base + "/list")));
        endpoints.add(new Endpoint("lawyers:outbound:callee:export", post(base + "/export")));
        endpoints.add(new Endpoint("lawyers:outbound:callee:query", get(base + "/1")));
        endpoints.add(new Endpoint("lawyers:outbound:callee:query", get(base + "/task/1")));
        endpoints.add(new Endpoint("lawyers:outbound:callee:add",
                post(base).contentType(MediaType.APPLICATION_JSON).content("{}")));
        endpoints.add(new Endpoint("lawyers:outbound:callee:edit",
                put(base).contentType(MediaType.APPLICATION_JSON).content("{}")));
        endpoints.add(new Endpoint("lawyers:outbound:callee:remove", delete(base + "/1")));
        endpoints.add(new Endpoint("lawyers:outbound:callee:remove", delete(base + "/task/1")));
        endpoints.add(new Endpoint("lawyers:outbound:callee:add",
                post(base + "/batch/1").contentType(MediaType.APPLICATION_JSON).content("[]")));
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
