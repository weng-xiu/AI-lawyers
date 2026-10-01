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
import ai.lawyers.system.service.lawyers.IAiCallLedgerService;
import ai.lawyers.web.support.AbstractPermMvcTest;

/**
 * P2-14：{@link AiCallLedgerController} 权限校验——10 个端点、6 个权限边界。
 * 模板/台账转工单等辅助端点分别挂 list/add/edit 权限，验证权限码精确区分。
 */
public class AiCallLedgerControllerPermTest extends AbstractPermMvcTest
{
    @Mock
    private IAiCallLedgerService ledgerService;

    private final List<Endpoint> endpoints = new ArrayList<>();

    @BeforeEach
    void setUp()
    {
        MockitoAnnotations.openMocks(this);
        when(ledgerService.selectAiCallLedgerList(any())).thenReturn(new ArrayList<>());
        startMvc(AiCallLedgerController.class);
        buildEndpoints();
    }

    @Override
    protected void registerMocks()
    {
        registerMock("aiCallLedgerService", ledgerService);
    }

    private void buildEndpoints()
    {
        String base = "/lawyers/call/ledger";
        endpoints.add(new Endpoint("lawyers:call:ledger:list", get(base + "/list")));
        endpoints.add(new Endpoint("lawyers:call:ledger:export", post(base + "/export")));
        endpoints.add(new Endpoint("lawyers:call:ledger:query", get(base + "/1")));
        endpoints.add(new Endpoint("lawyers:call:ledger:add",
                post(base).contentType(MediaType.APPLICATION_JSON).content("{}")));
        endpoints.add(new Endpoint("lawyers:call:ledger:edit",
                put(base).contentType(MediaType.APPLICATION_JSON).content("{}")));
        endpoints.add(new Endpoint("lawyers:call:ledger:remove", delete(base + "/1")));
        endpoints.add(new Endpoint("lawyers:call:ledger:add", get(base + "/generateNo")));
        endpoints.add(new Endpoint("lawyers:call:ledger:list", get(base + "/templates")));
        endpoints.add(new Endpoint("lawyers:call:ledger:add", get(base + "/autoFill/1")));
        // mock transferToTicket 返回 null → 走业务 error 分支，HTTP 仍 200
        endpoints.add(new Endpoint("lawyers:call:ledger:edit",
                post(base + "/transferTicket/1")));
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
