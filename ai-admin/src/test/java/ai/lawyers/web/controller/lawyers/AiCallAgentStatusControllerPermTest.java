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
import ai.lawyers.system.service.lawyers.IAiCallAgentStatusService;
import ai.lawyers.web.support.AbstractPermMvcTest;

/**
 * P2-14：{@link AiCallAgentStatusController} 权限校验——24 个端点、9 个权限边界。
 * 话务操作类端点（status/callMode/makeCall/hold/resume/transfer/consult/threeWay/
 * afterWork/hangup/robotTakeover/ivrTransfer）共用 lawyers:call:agent:status。
 *
 * <p>autoLogout 特例：@Anonymous 无 @PreAuthorize，浏览器卸载时调用——
 * 本迷你链不模拟 RuoYi PermitAllUrlProperties 的匿名放行，单独验证
 * 登录态（任意权限）直达业务。</p>
 */
public class AiCallAgentStatusControllerPermTest extends AbstractPermMvcTest
{
    @Mock
    private IAiCallAgentStatusService agentStatusService;

    private final List<Endpoint> endpoints = new ArrayList<>();

    @BeforeEach
    void setUp()
    {
        MockitoAnnotations.openMocks(this);
        when(agentStatusService.selectAiCallAgentStatusList(any())).thenReturn(new ArrayList<>());
        startMvc(AiCallAgentStatusController.class);
        buildEndpoints();
    }

    @Override
    protected void registerMocks()
    {
        registerMock("aiCallAgentStatusService", agentStatusService);
    }

    private void buildEndpoints()
    {
        String base = "/lawyers/call/agent";
        endpoints.add(new Endpoint("lawyers:call:agent:list", get(base + "/list")));
        endpoints.add(new Endpoint("lawyers:call:agent:export", post(base + "/export")));
        endpoints.add(new Endpoint("lawyers:call:agent:query", get(base + "/1")));
        endpoints.add(new Endpoint("lawyers:call:agent:add",
                post(base).contentType(MediaType.APPLICATION_JSON).content("{}")));
        endpoints.add(new Endpoint("lawyers:call:agent:edit",
                put(base).contentType(MediaType.APPLICATION_JSON).content("{}")));
        endpoints.add(new Endpoint("lawyers:call:agent:remove", delete(base + "/1")));
        endpoints.add(new Endpoint("lawyers:call:agent:login",
                post(base + "/login").contentType(MediaType.APPLICATION_JSON).content("{}")));
        endpoints.add(new Endpoint("lawyers:call:agent:login", get(base + "/my")));
        endpoints.add(new Endpoint("lawyers:call:agent:logout",
                post(base + "/logout").contentType(MediaType.APPLICATION_JSON).content("{}")));
        endpoints.add(new Endpoint("lawyers:call:agent:status",
                post(base + "/status").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"agentId\":1,\"status\":\"1\"}")));
        endpoints.add(new Endpoint("lawyers:call:agent:list", get(base + "/online")));
        endpoints.add(new Endpoint("lawyers:call:agent:status", get(base + "/current/1")));
        endpoints.add(new Endpoint("lawyers:call:agent:status",
                post(base + "/callMode").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"agentId\":1,\"callMode\":\"1\"}")));
        endpoints.add(new Endpoint("lawyers:call:agent:status",
                post(base + "/makeCall").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"agentId\":1,\"phone\":\"13800000000\"}")));
        endpoints.add(new Endpoint("lawyers:call:agent:status",
                post(base + "/hold").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"agentId\":1}")));
        endpoints.add(new Endpoint("lawyers:call:agent:status",
                post(base + "/resume").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"agentId\":1}")));
        endpoints.add(new Endpoint("lawyers:call:agent:status",
                post(base + "/transfer").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"agentId\":1,\"toAgentId\":2}")));
        endpoints.add(new Endpoint("lawyers:call:agent:status",
                post(base + "/consult").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"agentId\":1,\"toAgentId\":2}")));
        endpoints.add(new Endpoint("lawyers:call:agent:status",
                post(base + "/threeWay").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"agentId\":1,\"toAgentId\":2}")));
        endpoints.add(new Endpoint("lawyers:call:agent:status",
                post(base + "/afterWork").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"agentId\":1}")));
        endpoints.add(new Endpoint("lawyers:call:agent:status",
                post(base + "/hangup").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"agentId\":1}")));
        endpoints.add(new Endpoint("lawyers:call:agent:status",
                post(base + "/robotTakeover").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"agentId\":1}")));
        endpoints.add(new Endpoint("lawyers:call:agent:status",
                post(base + "/ivrTransfer").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"agentId\":1}")));
        endpoints.add(new Endpoint("lawyers:call:agent:list", get(base + "/todayRecords/1")));
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

    @Test
    void autoLogout_loggedIn_anyPermission_reachesController() throws Exception
    {
        // @Anonymous + 无 @PreAuthorize：登录态直接放行（生产另对匿名提供签名校验路径）
        mockMvc.perform(post("/lawyers/call/agent/autoLogout")
                        .param("agentId", "1").param("userId", "9")
                        .with(loginAs("lawyers:unrelated:perm")))
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
