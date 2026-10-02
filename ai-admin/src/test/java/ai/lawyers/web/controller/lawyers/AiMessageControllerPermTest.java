package ai.lawyers.web.controller.lawyers;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import ai.lawyers.system.service.lawyers.IAiMessageService;
import ai.lawyers.web.support.AbstractPermMvcTest;

/**
 * P2-14：{@link AiMessageController} 权限校验——5 个受权限保护端点 + 1 个无
 * {@code @PreAuthorize} 的登录即用端点（GET /unreadCount，站内信未读数属个人数据）。
 * 受保护端点逐个验证 401/403/200；unreadCount 另测任意登录用户 200。
 */
public class AiMessageControllerPermTest extends AbstractPermMvcTest
{
    @Mock
    private IAiMessageService aiMessageService;

    private final List<Endpoint> endpoints = new ArrayList<>();

    @BeforeEach
    void setUp()
    {
        MockitoAnnotations.openMocks(this);
        when(aiMessageService.selectAiMessageList(any())).thenReturn(new ArrayList<>());
        startMvc(AiMessageController.class);
        buildEndpoints();
    }

    @Override
    protected void registerMocks()
    {
        registerMock("aiMessageService", aiMessageService);
    }

    private void buildEndpoints()
    {
        String base = "/lawyers/message";
        endpoints.add(new Endpoint("lawyers:message:list", get(base + "/list")));
        endpoints.add(new Endpoint("lawyers:message:query", get(base + "/1")));
        endpoints.add(new Endpoint("lawyers:message:read", put(base + "/read/1")));
        endpoints.add(new Endpoint("lawyers:message:read", put(base + "/readAll")));
        endpoints.add(new Endpoint("lawyers:message:remove", delete(base + "/1")));
    }

    @Test
    void allEndpoints_unauthenticated_401() throws Exception
    {
        for (Endpoint endpoint : endpoints)
        {
            mockMvc.perform(endpoint.builder).andExpect(status().isUnauthorized());
        }
        // unreadCount 无方法级注解，但 URL 层 anyRequest authenticated 仍拦截未登录
        mockMvc.perform(get("/lawyers/message/unreadCount")).andExpect(status().isUnauthorized());
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

    /**
     * unreadCount 无 @PreAuthorize：任意登录用户（含无权限用户）可达业务，仅 401 兜底。
     * 注：迷你链路不模拟生产 PermitAllUrlProperties 匿名放行清单，语义为登录即可用。
     */
    @Test
    void unreadCount_anyLoggedInUser_200() throws Exception
    {
        mockMvc.perform(get("/lawyers/message/unreadCount").with(loginAs("lawyers:nope")))
                .andExpect(status().isOk());
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
