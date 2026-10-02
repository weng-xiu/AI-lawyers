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
import ai.lawyers.system.service.lawyers.IAiChatSessionService;
import ai.lawyers.web.support.AbstractPermMvcTest;

/**
 * P2-14：{@link AiChatSessionController} 权限校验——7 个端点、6 个权限边界
 * （list 与 stats 共用 list 权限），逐端点验证未登录 401、权限码不符 403、
 * 持对应权限码放行 200。
 */
public class AiChatSessionControllerPermTest extends AbstractPermMvcTest
{
    @Mock
    private IAiChatSessionService aiChatSessionService;

    private final List<Endpoint> endpoints = new ArrayList<>();

    @BeforeEach
    void setUp()
    {
        MockitoAnnotations.openMocks(this);
        when(aiChatSessionService.selectAiChatSessionList(any())).thenReturn(new ArrayList<>());
        startMvc(AiChatSessionController.class);
        buildEndpoints();
    }

    @Override
    protected void registerMocks()
    {
        registerMock("aiChatSessionService", aiChatSessionService);
    }

    private void buildEndpoints()
    {
        String base = "/lawyers/chatSession";
        endpoints.add(new Endpoint("lawyers:chatSession:list", get(base + "/list")));
        endpoints.add(new Endpoint("lawyers:chatSession:export", post(base + "/export")));
        endpoints.add(new Endpoint("lawyers:chatSession:query", get(base + "/1")));
        endpoints.add(new Endpoint("lawyers:chatSession:add",
                post(base).contentType(MediaType.APPLICATION_JSON).content("{}")));
        endpoints.add(new Endpoint("lawyers:chatSession:edit",
                put(base).contentType(MediaType.APPLICATION_JSON).content("{}")));
        endpoints.add(new Endpoint("lawyers:chatSession:remove", delete(base + "/1")));
        endpoints.add(new Endpoint("lawyers:chatSession:list", get(base + "/stats")));
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
