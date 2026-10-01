package ai.lawyers.web.controller.lawyers;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
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
import ai.lawyers.system.service.lawyers.IAiCallTransferService;
import ai.lawyers.web.support.AbstractPermMvcTest;

/**
 * P2-14：{@link AiCallTransferController} 权限校验——6 个端点、5 个权限边界。
 * doTransfer 为话务转接核心写操作，需完整转接参数。
 */
public class AiCallTransferControllerPermTest extends AbstractPermMvcTest
{
    @Mock
    private IAiCallTransferService transferService;

    private final List<Endpoint> endpoints = new ArrayList<>();

    @BeforeEach
    void setUp()
    {
        MockitoAnnotations.openMocks(this);
        when(transferService.selectAiCallTransferList(any())).thenReturn(new ArrayList<>());
        startMvc(AiCallTransferController.class);
        buildEndpoints();
    }

    @Override
    protected void registerMocks()
    {
        registerMock("aiCallTransferService", transferService);
    }

    private void buildEndpoints()
    {
        String base = "/lawyers/call/transfer";
        endpoints.add(new Endpoint("lawyers:call:transfer:list", get(base + "/list")));
        endpoints.add(new Endpoint("lawyers:call:transfer:query", get(base + "/1")));
        endpoints.add(new Endpoint("lawyers:call:transfer:add",
                post(base).contentType(MediaType.APPLICATION_JSON).content("{}")));
        endpoints.add(new Endpoint("lawyers:call:transfer:remove", delete(base + "/1")));
        endpoints.add(new Endpoint("lawyers:call:transfer:transfer",
                post(base + "/doTransfer").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"recordId\":1,\"fromAgentId\":10,\"fromAgentName\":\"甲\","
                                + "\"toAgentId\":20,\"toAgentName\":\"乙\",\"reason\":\"专业匹配\"}")));
        endpoints.add(new Endpoint("lawyers:call:transfer:list", get(base + "/record/1")));
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
