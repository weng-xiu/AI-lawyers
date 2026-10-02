package ai.lawyers.web.controller.lawyers;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import ai.lawyers.system.service.lawyers.queue.StreamQueueService;
import ai.lawyers.web.support.AbstractPermMvcTest;

/**
 * P2-14：{@link AiQueueMonitorController} 权限校验——4 个端点、2 个权限边界。
 * mock 未注册任何队列：水位概览返回空行，死信列表/重投/删除走"队列不存在"分支（HTTP 仍 200）。
 */
public class AiQueueMonitorControllerPermTest extends AbstractPermMvcTest
{
    @Mock
    private StreamQueueService streamQueueService;

    private final List<Endpoint> endpoints = new ArrayList<>();

    @BeforeEach
    void setUp()
    {
        MockitoAnnotations.openMocks(this);
        when(streamQueueService.registeredQueues()).thenReturn(Collections.emptySet());
        startMvc(AiQueueMonitorController.class);
        buildEndpoints();
    }

    @Override
    protected void registerMocks()
    {
        registerMock("streamQueueService", streamQueueService);
    }

    private void buildEndpoints()
    {
        String base = "/lawyers/queueMonitor";
        endpoints.add(new Endpoint("lawyers:queueMonitor:list", get(base + "/list")));
        endpoints.add(new Endpoint("lawyers:queueMonitor:list",
                get(base + "/dead/list").param("queue", "ticketNotify")));
        endpoints.add(new Endpoint("lawyers:queueMonitor:replay",
                post(base + "/dead/replay").param("queue", "ticketNotify").param("id", "1")));
        endpoints.add(new Endpoint("lawyers:queueMonitor:replay",
                delete(base + "/dead/ticketNotify/1")));
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
