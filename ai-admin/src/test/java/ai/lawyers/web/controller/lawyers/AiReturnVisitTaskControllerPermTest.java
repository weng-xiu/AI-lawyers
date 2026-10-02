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
import ai.lawyers.system.service.lawyers.IAiReturnVisitTaskService;
import ai.lawyers.system.service.lawyers.trunk.ICallDispatchService;
import ai.lawyers.web.support.AbstractPermMvcTest;

/**
 * P2-14：{@link AiReturnVisitTaskController} 权限校验——8 个端点、6 个权限边界
 * （stats 共用 list，一键外呼共用 edit），逐端点验证未登录 401、权限码不符 403、
 * 持对应权限码放行 200。call 对不存在的任务走 error 分支（HTTP 200），不触达外呼调度。
 */
public class AiReturnVisitTaskControllerPermTest extends AbstractPermMvcTest
{
    @Mock
    private IAiReturnVisitTaskService aiReturnVisitTaskService;

    @Mock
    private ICallDispatchService callDispatchService;

    private final List<Endpoint> endpoints = new ArrayList<>();

    @BeforeEach
    void setUp()
    {
        MockitoAnnotations.openMocks(this);
        when(aiReturnVisitTaskService.selectAiReturnVisitTaskList(any()))
                .thenReturn(new ArrayList<>());
        startMvc(AiReturnVisitTaskController.class);
        buildEndpoints();
    }

    @Override
    protected void registerMocks()
    {
        registerMock("aiReturnVisitTaskService", aiReturnVisitTaskService);
        registerMock("callDispatchService", callDispatchService);
    }

    private void buildEndpoints()
    {
        String base = "/lawyers/returnVisitTask";
        endpoints.add(new Endpoint("lawyers:returnVisitTask:list", get(base + "/list")));
        endpoints.add(new Endpoint("lawyers:returnVisitTask:export", post(base + "/export")));
        endpoints.add(new Endpoint("lawyers:returnVisitTask:query", get(base + "/1")));
        endpoints.add(new Endpoint("lawyers:returnVisitTask:add",
                post(base).contentType(MediaType.APPLICATION_JSON).content("{}")));
        endpoints.add(new Endpoint("lawyers:returnVisitTask:edit",
                put(base).contentType(MediaType.APPLICATION_JSON).content("{}")));
        endpoints.add(new Endpoint("lawyers:returnVisitTask:remove", delete(base + "/1")));
        endpoints.add(new Endpoint("lawyers:returnVisitTask:list", get(base + "/stats")));
        endpoints.add(new Endpoint("lawyers:returnVisitTask:edit", put(base + "/call/1")));
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
