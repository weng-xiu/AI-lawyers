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
import ai.lawyers.system.service.lawyers.IAiMissedCallService;
import ai.lawyers.web.support.AbstractPermMvcTest;

/**
 * P2-14：{@link AiMissedCallController} 权限校验——9 个端点、6 个权限边界。
 * 特例：voice/play 授权通过但 mock 无记录 → 404（证明 @PreAuthorize 已放行）；
 * 语音转工单对不存在记录走业务 error 分支（HTTP 200）。
 */
public class AiMissedCallControllerPermTest extends AbstractPermMvcTest
{
    @Mock
    private IAiMissedCallService missedCallService;

    @Mock
    private IAiCallTicketService ticketService;

    private final List<Endpoint> endpoints = new ArrayList<>();

    @BeforeEach
    void setUp()
    {
        MockitoAnnotations.openMocks(this);
        when(missedCallService.selectAiMissedCallList(any())).thenReturn(new ArrayList<>());
        startMvc(AiMissedCallController.class);
        buildEndpoints();
    }

    @Override
    protected void registerMocks()
    {
        registerMock("aiMissedCallService", missedCallService);
        registerMock("aiCallTicketService", ticketService);
    }

    private void buildEndpoints()
    {
        String base = "/lawyers/call/missed";
        endpoints.add(new Endpoint("lawyers:call:missed:list", get(base + "/list"), 200));
        endpoints.add(new Endpoint("lawyers:call:missed:export", post(base + "/export"), 200));
        endpoints.add(new Endpoint("lawyers:call:missed:query", get(base + "/1"), 200));
        endpoints.add(new Endpoint("lawyers:call:missed:add",
                post(base).contentType(MediaType.APPLICATION_JSON).content("{}"), 200));
        endpoints.add(new Endpoint("lawyers:call:missed:edit",
                put(base).contentType(MediaType.APPLICATION_JSON).content("{}"), 200));
        endpoints.add(new Endpoint("lawyers:call:missed:remove", delete(base + "/1"), 200));
        endpoints.add(new Endpoint("lawyers:call:missed:list", get(base + "/stats"), 200));
        endpoints.add(new Endpoint("lawyers:call:missed:callback",
                put(base + "/callback/1"), 200));
        // 授权通过后 mock 查无留言文件 → 404，404 即证明放行
        endpoints.add(new Endpoint("lawyers:call:missed:query",
                get(base + "/1/voice/play"), 404));
        // 记录不存在 → error 分支，HTTP 200
        endpoints.add(new Endpoint("lawyers:call:missed:callback",
                post(base + "/1/transfer"), 200));
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
    void allEndpoints_withCorrectPermission_reachesController() throws Exception
    {
        for (Endpoint endpoint : endpoints)
        {
            mockMvc.perform(endpoint.builder.with(loginAs(endpoint.permission)))
                    .andExpect(status().is(endpoint.expectedStatus));
        }
    }

    private static class Endpoint
    {
        private final String permission;

        private final MockHttpServletRequestBuilder builder;

        private final int expectedStatus;

        Endpoint(String permission, MockHttpServletRequestBuilder builder, int expectedStatus)
        {
            this.permission = permission;
            this.builder = builder;
            this.expectedStatus = expectedStatus;
        }
    }
}
