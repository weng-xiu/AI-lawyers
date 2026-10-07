package ai.lawyers.web.controller.lawyers.trunk;

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
import ai.lawyers.system.domain.lawyers.trunk.DialResult;
import ai.lawyers.system.mapper.lawyers.trunk.AiCallDialLogMapper;
import ai.lawyers.system.service.lawyers.trunk.ICallDispatchService;
import ai.lawyers.web.support.AbstractPermMvcTest;

/**
 * {@link CallDispatchController} 权限校验——6 个受保护端点逐端点验证未登录 401、
 * 权限码不符 403、持对应权限码放行 200。{@code /event} 网关事件回调为 {@code @Anonymous}
 * 匿名端点（迷你容器不处理 @Anonymous，匿名访问仍被 URL 层拦截 401），单独验证
 * 未登录 401 与任意登录用户 200。
 */
public class CallDispatchControllerPermTest extends AbstractPermMvcTest
{
    @Mock
    private ICallDispatchService callDispatchService;

    @Mock
    private AiCallDialLogMapper dialLogMapper;

    private final List<Endpoint> endpoints = new ArrayList<>();

    @BeforeEach
    void setUp()
    {
        MockitoAnnotations.openMocks(this);
        DialResult failResult = DialResult.fail("TEST_ERR", "外呼失败");
        when(callDispatchService.dialWithQueue(any())).thenReturn(failResult);
        when(callDispatchService.dial(any())).thenReturn(failResult);
        when(dialLogMapper.selectAiCallDialLogList(any())).thenReturn(new ArrayList<>());
        startMvc(CallDispatchController.class);
        buildEndpoints();
    }

    @Override
    protected void registerMocks()
    {
        registerMock("callDispatchService", callDispatchService);
        registerMock("dialLogMapper", dialLogMapper);
    }

    private void buildEndpoints()
    {
        String base = "/lawyers/call";
        endpoints.add(new Endpoint("lawyers:call:dial",
                post(base + "/dial").contentType(MediaType.APPLICATION_JSON).content("{}")));
        endpoints.add(new Endpoint("lawyers:call:dial",
                post(base + "/dialDirect").contentType(MediaType.APPLICATION_JSON).content("{}")));
        endpoints.add(new Endpoint("lawyers:call:dial",
                post(base + "/hangup").contentType(MediaType.APPLICATION_JSON).content("{}")));
        endpoints.add(new Endpoint("lawyers:call:dial", get(base + "/dispatchStatus")));
        endpoints.add(new Endpoint("lawyers:diallog:list", get(base + "/dialLog/list")));
        endpoints.add(new Endpoint("lawyers:diallog:list",
                post(base + "/dialLog/export").contentType(MediaType.APPLICATION_JSON).content("{}")));
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
    void eventCallback_unauthenticated_401() throws Exception
    {
        // 迷你容器不处理 @Anonymous，匿名访问仍被 URL 层拦截
        mockMvc.perform(post("/lawyers/call/event")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"callUuid\":\"uuid-1\",\"event\":\"ANSWERED\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void eventCallback_loggedIn_200() throws Exception
    {
        mockMvc.perform(post("/lawyers/call/event")
                        .with(loginAs("lawyers:nope"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"callUuid\":\"uuid-1\",\"event\":\"ANSWERED\"}"))
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
