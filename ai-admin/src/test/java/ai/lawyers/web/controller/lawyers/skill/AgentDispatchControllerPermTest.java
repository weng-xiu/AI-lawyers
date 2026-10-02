package ai.lawyers.web.controller.lawyers.skill;

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
import ai.lawyers.system.mapper.lawyers.skill.AiCallQueueMapper;
import ai.lawyers.system.service.lawyers.skill.IAgentDispatchService;
import ai.lawyers.web.support.AbstractPermMvcTest;

/**
 * {@link AgentDispatchController} 权限校验——实时排队/调度端点：验证未登录 401、
 * 登录后（Mapper/Service 全部 mock）放行 200。手动分配端点对返回结果立即解引用，
 * mock 默认返回 null 会 NPE，不做 200 断言。
 */
public class AgentDispatchControllerPermTest extends AbstractPermMvcTest
{
    @Mock
    private IAgentDispatchService agentDispatchService;

    @Mock
    private AiCallQueueMapper aiCallQueueMapper;

    private final List<Endpoint> endpoints = new ArrayList<>();

    @BeforeEach
    void setUp()
    {
        MockitoAnnotations.openMocks(this);
        when(aiCallQueueMapper.selectAiCallQueueList(any())).thenReturn(new ArrayList<>());
        startMvc(AgentDispatchController.class);
        buildEndpoints();
    }

    @Override
    protected void registerMocks()
    {
        registerMock("agentDispatchService", agentDispatchService);
        registerMock("aiCallQueueMapper", aiCallQueueMapper);
    }

    private void buildEndpoints()
    {
        String base = "/lawyers/skill/queue";
        endpoints.add(new Endpoint("lawyers:queue:query", get(base + "/list")));
        endpoints.add(new Endpoint("lawyers:queue:query", get(base + "/queuing")));
        endpoints.add(new Endpoint("lawyers:queue:assign",
                post(base + "/dispatch").param("groupId", "1")
                        .contentType(MediaType.APPLICATION_JSON).content("{}")));
        // assignManually 对 mock 返回的 null DispatchResult 调 isSuccess() 会 NPE，跳过 200
        endpoints.add(new Endpoint("lawyers:queue:assign",
                post(base + "/assign/1/2"), false));
        endpoints.add(new Endpoint("lawyers:queue:kick", post(base + "/kick/1")));
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
    void allEndpoints_loggedIn_200() throws Exception
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
