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
import ai.lawyers.system.service.lawyers.IAiLegalConsultationService;
import ai.lawyers.web.support.AbstractPermMvcTest;

/**
 * P2-14：{@link AiLegalConsultationController} 权限校验——11 个端点、5 个权限边界
 * （list 与 4 个统计端点共用 list 权限，/{id} 与 /user/{userId} 共用 query 权限），
 * 逐端点验证未登录 401、权限码不符 403、持对应权限码放行 200。
 */
public class AiLegalConsultationControllerPermTest extends AbstractPermMvcTest
{
    @Mock
    private IAiLegalConsultationService aiLegalConsultationService;

    private final List<Endpoint> endpoints = new ArrayList<>();

    @BeforeEach
    void setUp()
    {
        MockitoAnnotations.openMocks(this);
        when(aiLegalConsultationService.selectAiLegalConsultationList(any()))
                .thenReturn(new ArrayList<>());
        when(aiLegalConsultationService.selectAiLegalConsultationByUserId(any()))
                .thenReturn(new ArrayList<>());
        startMvc(AiLegalConsultationController.class);
        buildEndpoints();
    }

    @Override
    protected void registerMocks()
    {
        registerMock("aiLegalConsultationService", aiLegalConsultationService);
    }

    private void buildEndpoints()
    {
        String base = "/lawyers/consultation";
        endpoints.add(new Endpoint("lawyers:consultation:list", get(base + "/list")));
        endpoints.add(new Endpoint("lawyers:consultation:export", post(base + "/export")));
        endpoints.add(new Endpoint("lawyers:consultation:query", get(base + "/1")));
        endpoints.add(new Endpoint("lawyers:consultation:query", get(base + "/user/1")));
        endpoints.add(new Endpoint("lawyers:consultation:add",
                post(base).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"question\":\"劳务纠纷如何仲裁\",\"categoryId\":1}")));
        endpoints.add(new Endpoint("lawyers:consultation:edit",
                put(base).contentType(MediaType.APPLICATION_JSON).content("{}")));
        endpoints.add(new Endpoint("lawyers:consultation:remove", delete(base + "/1")));
        endpoints.add(new Endpoint("lawyers:consultation:list", get(base + "/statistics")));
        endpoints.add(new Endpoint("lawyers:consultation:list", get(base + "/statistics/category")));
        endpoints.add(new Endpoint("lawyers:consultation:list",
                get(base + "/statistics/date").param("days", "7")));
        endpoints.add(new Endpoint("lawyers:consultation:list", get(base + "/stats")));
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
