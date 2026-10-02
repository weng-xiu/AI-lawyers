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
import ai.lawyers.system.service.lawyers.IAiVideoConsultLogService;
import ai.lawyers.system.service.lawyers.IAiVideoConsultService;
import ai.lawyers.web.support.AbstractPermMvcTest;

/**
 * P2-14：{@link AiVideoConsultController} 权限校验——7 个受权限保护端点、6 个权限
 * 边界 + 1 个无 {@code @PreAuthorize} 的登录即用端点（POST /log，浏览器端事件上报）。
 * 受保护端点逐个验证 401/403/200；/log 另测任意登录用户 200。
 */
public class AiVideoConsultControllerPermTest extends AbstractPermMvcTest
{
    @Mock
    private IAiVideoConsultService aiVideoConsultService;

    @Mock
    private IAiVideoConsultLogService aiVideoConsultLogService;

    private final List<Endpoint> endpoints = new ArrayList<>();

    @BeforeEach
    void setUp()
    {
        MockitoAnnotations.openMocks(this);
        when(aiVideoConsultService.selectAiVideoConsultList(any())).thenReturn(new ArrayList<>());
        when(aiVideoConsultLogService.selectAiVideoConsultLogList(any()))
                .thenReturn(new ArrayList<>());
        startMvc(AiVideoConsultController.class);
        buildEndpoints();
    }

    @Override
    protected void registerMocks()
    {
        registerMock("aiVideoConsultService", aiVideoConsultService);
        registerMock("aiVideoConsultLogService", aiVideoConsultLogService);
    }

    private void buildEndpoints()
    {
        String base = "/lawyers/videoConsult";
        endpoints.add(new Endpoint("lawyers:videoConsult:list", get(base + "/list")));
        endpoints.add(new Endpoint("lawyers:videoConsult:export", post(base + "/export")));
        endpoints.add(new Endpoint("lawyers:videoConsult:query", get(base + "/1")));
        endpoints.add(new Endpoint("lawyers:videoConsult:add",
                post(base).contentType(MediaType.APPLICATION_JSON).content("{}")));
        endpoints.add(new Endpoint("lawyers:videoConsult:edit",
                put(base).contentType(MediaType.APPLICATION_JSON).content("{}")));
        endpoints.add(new Endpoint("lawyers:videoConsult:remove", delete(base + "/1")));
        endpoints.add(new Endpoint("lawyers:videoConsult:query", get(base + "/log/1")));
    }

    @Test
    void allEndpoints_unauthenticated_401() throws Exception
    {
        for (Endpoint endpoint : endpoints)
        {
            mockMvc.perform(endpoint.builder).andExpect(status().isUnauthorized());
        }
        // POST /log 无方法级注解，URL 层 anyRequest authenticated 仍拦截未登录
        mockMvc.perform(post("/lawyers/videoConsult/log")
                .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
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
     * POST /log 无 @PreAuthorize：任意登录用户（含无权限用户）可达业务，仅 401 兜底。
     * 注：迷你链路不模拟生产 PermitAllUrlProperties 匿名放行清单，语义为登录即可用。
     */
    @Test
    void addLog_anyLoggedInUser_200() throws Exception
    {
        mockMvc.perform(post("/lawyers/videoConsult/log")
                .contentType(MediaType.APPLICATION_JSON).content("{}")
                .with(loginAs("lawyers:nope")))
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
