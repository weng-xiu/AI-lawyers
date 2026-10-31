package ai.lawyers.web.controller.lawyers.schedule;

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
import ai.lawyers.system.service.lawyers.IAiCallAgentStatusService;
import ai.lawyers.system.service.lawyers.schedule.IAiAgentScheduleService;
import ai.lawyers.system.service.lawyers.schedule.IAiShiftChangeService;
import ai.lawyers.web.controller.lawyers.AiShiftChangeController;
import ai.lawyers.web.support.AbstractPermMvcTest;

/**
 * F9 {@link AiShiftChangeController} 权限校验——9 个端点、3 个权限边界：
 * 列表/我的申请/我的排班/互换可选对方排班需 lawyers:schedule:list，详情需 lawyers:schedule:query，
 * 申请与撤销需 lawyers:schedule:add，审批通过/驳回需 lawyers:schedule:edit。
 * 逐端点验证未登录 401、权限码不符 403、持对应权限码放行 200。
 */
public class AiShiftChangeControllerPermTest extends AbstractPermMvcTest
{
    @Mock
    private IAiShiftChangeService shiftChangeService;

    @Mock
    private IAiCallAgentStatusService aiCallAgentStatusService;

    @Mock
    private IAiAgentScheduleService aiAgentScheduleService;

    private final List<Endpoint> endpoints = new ArrayList<>();

    @BeforeEach
    void setUp()
    {
        MockitoAnnotations.openMocks(this);
        // 未绑定坐席的账号：mine / my-schedules 直接返回空列表，避免依赖 DB
        when(aiCallAgentStatusService.selectAiCallAgentStatusByUserId(any())).thenReturn(null);
        when(shiftChangeService.selectShiftChangeList(any())).thenReturn(new ArrayList<>());
        startMvc(AiShiftChangeController.class);
        buildEndpoints();
    }

    @Override
    protected void registerMocks()
    {
        registerMock("shiftChangeService", shiftChangeService);
        registerMock("aiCallAgentStatusService", aiCallAgentStatusService);
        registerMock("aiAgentScheduleService", aiAgentScheduleService);
    }

    private void buildEndpoints()
    {
        String base = "/lawyers/shift-change";
        endpoints.add(new Endpoint("lawyers:schedule:list", get(base + "/list")));
        endpoints.add(new Endpoint("lawyers:schedule:list", get(base + "/mine")));
        endpoints.add(new Endpoint("lawyers:schedule:list", get(base + "/my-schedules")));
        endpoints.add(new Endpoint("lawyers:schedule:list",
                get(base + "/swap-targets").param("applicantScheduleId", "11")));
        endpoints.add(new Endpoint("lawyers:schedule:query", get(base + "/1")));
        endpoints.add(new Endpoint("lawyers:schedule:add",
                post(base).contentType(MediaType.APPLICATION_JSON).content("{}")));
        endpoints.add(new Endpoint("lawyers:schedule:add", put(base + "/cancel/1")));
        endpoints.add(new Endpoint("lawyers:schedule:edit",
                put(base + "/approve/1").contentType(MediaType.APPLICATION_JSON).content("{}")));
        endpoints.add(new Endpoint("lawyers:schedule:edit",
                put(base + "/reject/1").contentType(MediaType.APPLICATION_JSON).content("{}")));
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
