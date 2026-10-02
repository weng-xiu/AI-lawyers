package ai.lawyers.web.controller.monitor;

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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import ai.lawyers.framework.web.service.SysPasswordService;
import ai.lawyers.system.service.ISysLogininforService;
import ai.lawyers.web.support.AbstractPermMvcTest;

/**
 * {@link SysLogininforController} 权限校验——逐端点验证未登录 401、权限码不符 403、
 * 持对应权限码放行 200。
 */
public class SysLogininforControllerPermTest extends AbstractPermMvcTest
{
    @Mock
    private ISysLogininforService logininforService;

    @Mock
    private SysPasswordService passwordService;

    private final List<Endpoint> endpoints = new ArrayList<>();

    @BeforeEach
    void setUp()
    {
        MockitoAnnotations.openMocks(this);
        when(logininforService.selectLogininforList(any())).thenReturn(new ArrayList<>());
        startMvc(SysLogininforController.class);
        buildEndpoints();
    }

    @Override
    protected void registerMocks()
    {
        registerMock("logininforService", logininforService);
        registerMock("passwordService", passwordService);
    }

    private void buildEndpoints()
    {
        String base = "/monitor/logininfor";
        endpoints.add(new Endpoint("monitor:logininfor:list", get(base + "/list")));
        endpoints.add(new Endpoint("monitor:logininfor:export", post(base + "/export")));
        endpoints.add(new Endpoint("monitor:logininfor:remove", delete(base + "/1")));
        endpoints.add(new Endpoint("monitor:logininfor:remove", delete(base + "/clean")));
        endpoints.add(new Endpoint("monitor:logininfor:unlock", get(base + "/unlock/admin")));
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
            mockMvc.perform(endpoint.builder.with(loginAs("monitor:nope")))
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
