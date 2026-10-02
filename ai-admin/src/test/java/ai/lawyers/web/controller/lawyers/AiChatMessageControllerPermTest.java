package ai.lawyers.web.controller.lawyers;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
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
import ai.lawyers.system.service.lawyers.IAiChatMessageService;
import ai.lawyers.system.service.lawyers.IAiChatSessionService;
import ai.lawyers.web.support.AbstractPermMvcTest;

/**
 * P2-14：{@link AiChatMessageController} 权限校验——7 个端点、5 个权限边界
 * （list/read/{id}/read 共用 query 权限），逐端点验证未登录 401、权限码不符 403、
 * 持对应权限码放行 200。send/close/transfer 内部对会话的更新走 mock，WS 推送
 * 失败被 try-catch 兜底，不影响状态码。
 */
public class AiChatMessageControllerPermTest extends AbstractPermMvcTest
{
    @Mock
    private IAiChatMessageService aiChatMessageService;

    @Mock
    private IAiChatSessionService aiChatSessionService;

    private final List<Endpoint> endpoints = new ArrayList<>();

    @BeforeEach
    void setUp()
    {
        MockitoAnnotations.openMocks(this);
        when(aiChatMessageService.selectAiChatMessageList(any())).thenReturn(new ArrayList<>());
        startMvc(AiChatMessageController.class);
        buildEndpoints();
    }

    @Override
    protected void registerMocks()
    {
        registerMock("aiChatMessageService", aiChatMessageService);
        registerMock("aiChatSessionService", aiChatSessionService);
    }

    private void buildEndpoints()
    {
        String base = "/lawyers/chatMessage";
        endpoints.add(new Endpoint("lawyers:chat:query", get(base + "/list/1")));
        endpoints.add(new Endpoint("lawyers:chat:send",
                post(base + "/send").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sessionId\":1,\"content\":\"你好\"}")));
        endpoints.add(new Endpoint("lawyers:chat:close", put(base + "/close/1")));
        endpoints.add(new Endpoint("lawyers:chat:transfer",
                put(base + "/transfer").contentType(MediaType.APPLICATION_JSON).content("{}")));
        endpoints.add(new Endpoint("lawyers:chat:query", put(base + "/read/1")));
        endpoints.add(new Endpoint("lawyers:chat:query", get(base + "/1")));
        endpoints.add(new Endpoint("lawyers:chat:export",
                post(base + "/export").param("sessionId", "1")));
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
