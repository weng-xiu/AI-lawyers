package ai.lawyers.web.controller.lawyers.ivr;

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
import ai.lawyers.system.service.lawyers.ivr.engine.IIvrEngineService;
import ai.lawyers.system.service.lawyers.ivr.engine.IntentionRecognitionService;
import ai.lawyers.web.support.AbstractPermMvcTest;

/**
 * {@link IvrEngineController} 权限校验——在线调试/运行时端点：验证未登录 401、
 * 登录后（Service 全部 mock）放行 200。
 */
public class IvrEngineControllerPermTest extends AbstractPermMvcTest
{
    @Mock
    private IIvrEngineService ivrEngineService;

    @Mock
    private IntentionRecognitionService intentionRecognitionService;

    private final List<Endpoint> endpoints = new ArrayList<>();

    @BeforeEach
    void setUp()
    {
        MockitoAnnotations.openMocks(this);
        startMvc(IvrEngineController.class);
        buildEndpoints();
    }

    @Override
    protected void registerMocks()
    {
        registerMock("ivrEngineService", ivrEngineService);
        registerMock("intentionRecognitionService", intentionRecognitionService);
    }

    private void buildEndpoints()
    {
        endpoints.add(new Endpoint("lawyers:ivr:flow:list",
                post("/lawyers/ivr/engine/execute")
                        .contentType(MediaType.APPLICATION_JSON).content("{}")));
        endpoints.add(new Endpoint("lawyers:ivr:intention:list",
                post("/lawyers/ivr/engine/intention")
                        .contentType(MediaType.APPLICATION_JSON).content("{}")));
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
    void allEndpoints_loggedIn_200() throws Exception
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
