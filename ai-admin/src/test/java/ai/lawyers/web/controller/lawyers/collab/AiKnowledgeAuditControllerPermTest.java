package ai.lawyers.web.controller.lawyers.collab;

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
import ai.lawyers.system.service.lawyers.IAiKnowledgeAuditService;
import ai.lawyers.web.support.AbstractPermMvcTest;

/**
 * P2-14：{@link AiKnowledgeAuditController} 权限校验——6 个端点、4 个权限边界，
 * 逐端点验证未登录 401、权限码不符 403、持对应权限码放行 200。
 * approve/reject/publish 共用 lawyers:knowledge:audit:approve。
 */
public class AiKnowledgeAuditControllerPermTest extends AbstractPermMvcTest
{
    @Mock
    private IAiKnowledgeAuditService knowledgeAuditService;

    private final List<Endpoint> endpoints = new ArrayList<>();

    @BeforeEach
    void setUp()
    {
        MockitoAnnotations.openMocks(this);
        when(knowledgeAuditService.selectAuditList(any())).thenReturn(new ArrayList<>());
        startMvc(AiKnowledgeAuditController.class);
        buildEndpoints();
    }

    @Override
    protected void registerMocks()
    {
        registerMock("knowledgeAuditService", knowledgeAuditService);
    }

    private void buildEndpoints()
    {
        String base = "/lawyers/knowledge/audit";
        endpoints.add(new Endpoint("lawyers:knowledge:audit:list", get(base + "/list")));
        endpoints.add(new Endpoint("lawyers:knowledge:audit:submit",
                post(base + "/submit/1").contentType(MediaType.APPLICATION_JSON).content("{}")));
        endpoints.add(new Endpoint("lawyers:knowledge:audit:approve",
                post(base + "/approve/1").contentType(MediaType.APPLICATION_JSON).content("{}")));
        endpoints.add(new Endpoint("lawyers:knowledge:audit:approve",
                post(base + "/reject/1").contentType(MediaType.APPLICATION_JSON).content("{}")));
        endpoints.add(new Endpoint("lawyers:knowledge:audit:approve",
                post(base + "/publish/1").contentType(MediaType.APPLICATION_JSON).content("{}")));
        endpoints.add(new Endpoint("lawyers:knowledge:audit:offline",
                post(base + "/offline/1").contentType(MediaType.APPLICATION_JSON).content("{}")));
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
