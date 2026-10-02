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
import ai.lawyers.system.service.lawyers.IAiRiskWarningService;
import ai.lawyers.web.support.AbstractPermMvcTest;

/**
 * P2-14：{@link AiRiskWarningController} 权限校验——8 个端点、7 个权限边界
 * （转办目标机构查询用 query，一键转办复用工单转办权限 ticketTransfer:add），
 * 逐端点验证未登录 401、权限码不符 403、持对应权限码放行 200。
 */
public class AiRiskWarningControllerPermTest extends AbstractPermMvcTest
{
    @Mock
    private IAiRiskWarningService aiRiskWarningService;

    private final List<Endpoint> endpoints = new ArrayList<>();

    @BeforeEach
    void setUp()
    {
        MockitoAnnotations.openMocks(this);
        when(aiRiskWarningService.selectAiRiskWarningList(any())).thenReturn(new ArrayList<>());
        startMvc(AiRiskWarningController.class);
        buildEndpoints();
    }

    @Override
    protected void registerMocks()
    {
        registerMock("aiRiskWarningService", aiRiskWarningService);
    }

    private void buildEndpoints()
    {
        String base = "/lawyers/riskWarning";
        endpoints.add(new Endpoint("lawyers:riskWarning:list", get(base + "/list")));
        endpoints.add(new Endpoint("lawyers:riskWarning:export", post(base + "/export")));
        endpoints.add(new Endpoint("lawyers:riskWarning:query", get(base + "/1")));
        endpoints.add(new Endpoint("lawyers:riskWarning:add",
                post(base).contentType(MediaType.APPLICATION_JSON).content("{}")));
        endpoints.add(new Endpoint("lawyers:riskWarning:edit",
                put(base).contentType(MediaType.APPLICATION_JSON).content("{}")));
        endpoints.add(new Endpoint("lawyers:riskWarning:remove", delete(base + "/1")));
        endpoints.add(new Endpoint("lawyers:riskWarning:query",
                get(base + "/transfer/orgs").param("externalType", "1")));
        endpoints.add(new Endpoint("lawyers:ticketTransfer:add",
                post(base + "/transfer").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"warningId\":1,\"orgId\":2,\"remark\":\"测试\"}")));
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
