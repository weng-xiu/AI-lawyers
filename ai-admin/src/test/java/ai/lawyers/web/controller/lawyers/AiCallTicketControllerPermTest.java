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
import ai.lawyers.system.service.lawyers.IAiCallTicketService;
import ai.lawyers.web.support.AbstractPermMvcTest;

/**
 * P2-14：{@link AiCallTicketController} 权限校验——从注解抽取全部 12 个端点，
 * 逐端点验证未登录 401、权限码不符 403、持对应权限码放行（HTTP 200）。
 */
public class AiCallTicketControllerPermTest extends AbstractPermMvcTest
{
    @Mock
    private IAiCallTicketService ticketService;

    /** 端点规格：方法+路径、要求的权限码。 */
    private final List<Endpoint> endpoints = new ArrayList<>();

    @BeforeEach
    void setUp()
    {
        MockitoAnnotations.openMocks(this);
        // 列表/导出经 getDataTable(new PageInfo(list))，null 会 NPE，统一桩空列表
        when(ticketService.selectAiCallTicketList(any())).thenReturn(new ArrayList<>());
        startMvc(AiCallTicketController.class);
        buildEndpoints();
    }

    @Override
    protected void registerMocks()
    {
        registerMock("aiCallTicketService", ticketService);
    }

    private void buildEndpoints()
    {
        String base = "/lawyers/call/ticket";
        endpoints.add(new Endpoint("lawyers:call:ticket:list", get(base + "/list")));
        endpoints.add(new Endpoint("lawyers:call:ticket:export", post(base + "/export")));
        endpoints.add(new Endpoint("lawyers:call:ticket:query", get(base + "/1")));
        endpoints.add(new Endpoint("lawyers:call:ticket:add",
                post(base).contentType(MediaType.APPLICATION_JSON).content("{}")));
        endpoints.add(new Endpoint("lawyers:call:ticket:edit",
                put(base).contentType(MediaType.APPLICATION_JSON).content("{}")));
        endpoints.add(new Endpoint("lawyers:call:ticket:remove", delete(base + "/1")));
        endpoints.add(new Endpoint("lawyers:call:ticket:list", get(base + "/user/9")));
        endpoints.add(new Endpoint("lawyers:call:ticket:process",
                post(base + "/process").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ticketId\":1,\"processContent\":\"已跟进\"}")));
        endpoints.add(new Endpoint("lawyers:call:ticket:complete",
                post(base + "/complete").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ticketId\":1}")));
        endpoints.add(new Endpoint("lawyers:call:ticket:archive",
                post(base + "/archive").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ticketId\":1}")));
        endpoints.add(new Endpoint("lawyers:call:ticket:add", get(base + "/generateNo")));
        endpoints.add(new Endpoint("lawyers:call:ticket:query", get(base + "/1/timeline")));
    }

    @Test
    void allEndpoints_unauthenticated_401() throws Exception
    {
        for (Endpoint endpoint : endpoints)
        {
            mockMvc.perform(endpoint.builder)
                    .andExpect(status().isUnauthorized());
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

    /** 端点规格。 */
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
