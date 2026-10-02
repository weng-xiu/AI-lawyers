package ai.lawyers.web.controller.lawyers;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
import ai.lawyers.system.service.lawyers.ICallPopupService;
import ai.lawyers.web.support.AbstractPermMvcTest;

/**
 * P2-14：{@link AiCallPopupController} 权限校验——5 个端点、2 个权限边界
 * （四个查询视图共用 query，资料保存用 edit），逐端点验证未登录 401、
 * 权限码不符 403、持对应权限码放行 200。
 */
public class AiCallPopupControllerPermTest extends AbstractPermMvcTest
{
    @Mock
    private ICallPopupService callPopupService;

    private final List<Endpoint> endpoints = new ArrayList<>();

    @BeforeEach
    void setUp()
    {
        MockitoAnnotations.openMocks(this);
        startMvc(AiCallPopupController.class);
        buildEndpoints();
    }

    @Override
    protected void registerMocks()
    {
        registerMock("callPopupService", callPopupService);
    }

    private void buildEndpoints()
    {
        String base = "/lawyers/call/popup";
        endpoints.add(new Endpoint("lawyers:call:popup:query", get(base + "/profile/13800000000")));
        endpoints.add(new Endpoint("lawyers:call:popup:query", get(base + "/history/13800000000")));
        endpoints.add(new Endpoint("lawyers:call:popup:query", get(base + "/tickets/13800000000")));
        endpoints.add(new Endpoint("lawyers:call:popup:query", get(base + "/track/13800000000")));
        endpoints.add(new Endpoint("lawyers:call:popup:edit",
                put(base + "/profile").contentType(MediaType.APPLICATION_JSON).content("{}")));
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
