package ai.lawyers.web.controller.lawyers.ivr;

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
import ai.lawyers.system.service.lawyers.ivr.IAiIvrIntentionLogService;
import ai.lawyers.web.support.AbstractPermMvcTest;

/**
 * {@link AiIvrIntentionLogController} 权限校验——逐端点验证未登录 401、权限码不符 403、
 * 持对应权限码放行 200。
 */
public class AiIvrIntentionLogControllerPermTest extends AbstractPermMvcTest
{
    @Mock
    private IAiIvrIntentionLogService aiIvrIntentionLogService;

    private final List<Endpoint> endpoints = new ArrayList<>();

    @BeforeEach
    void setUp()
    {
        MockitoAnnotations.openMocks(this);
        when(aiIvrIntentionLogService.selectAiIvrIntentionLogList(any())).thenReturn(new ArrayList<>());
        startMvc(AiIvrIntentionLogController.class);
        buildEndpoints();
    }

    @Override
    protected void registerMocks()
    {
        registerMock("aiIvrIntentionLogService", aiIvrIntentionLogService);
    }

    private void buildEndpoints()
    {
        String base = "/lawyers/ivr/intentionLog";
        endpoints.add(new Endpoint("lawyers:ivr:intentionLog:list", get(base + "/list")));
        endpoints.add(new Endpoint("lawyers:ivr:intentionLog:list", get(base + "/record/1")));
        endpoints.add(new Endpoint("lawyers:ivr:intentionLog:export", post(base + "/export")));
        endpoints.add(new Endpoint("lawyers:ivr:intentionLog:query", get(base + "/1")));
        endpoints.add(new Endpoint("lawyers:ivr:intentionLog:add",
                post(base).contentType(MediaType.APPLICATION_JSON).content("{}")));
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
            if (!endpoint.expect200)
            {
                continue;
            }
            mockMvc.perform(endpoint.builder.with(loginAs(endpoint.permission)))
                    .andExpect(status().isOk());
        }
    }

    private static class Endpoint
    {
        private final String permission;

        private final MockHttpServletRequestBuilder builder;

        private final boolean expect200;

        Endpoint(String permission, MockHttpServletRequestBuilder builder)
        {
            this(permission, builder, true);
        }

        Endpoint(String permission, MockHttpServletRequestBuilder builder, boolean expect200)
        {
            this.permission = permission;
            this.builder = builder;
            this.expect200 = expect200;
        }
    }
}
