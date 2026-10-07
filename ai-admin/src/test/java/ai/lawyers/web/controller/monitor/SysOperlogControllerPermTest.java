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
import ai.lawyers.system.service.ISysOperLogService;
import ai.lawyers.web.support.AbstractPermMvcTest;

/**
 * {@link SysOperlogController} 权限校验——4 个端点、3 个权限边界
 * （list/export/remove，clean 复用 remove），逐端点验证未登录 401、
 * 权限码不符 403、持对应权限码放行 200。
 */
public class SysOperlogControllerPermTest extends AbstractPermMvcTest
{
    @Mock
    private ISysOperLogService operLogService;

    private final List<Endpoint> endpoints = new ArrayList<>();

    @BeforeEach
    void setUp()
    {
        MockitoAnnotations.openMocks(this);
        when(operLogService.selectOperLogList(any())).thenReturn(new ArrayList<>());
        startMvc(SysOperlogController.class);
        buildEndpoints();
    }

    @Override
    protected void registerMocks()
    {
        registerMock("operLogService", operLogService);
    }

    private void buildEndpoints()
    {
        String base = "/monitor/operlog";
        endpoints.add(new Endpoint("monitor:operlog:list", get(base + "/list")));
        endpoints.add(new Endpoint("monitor:operlog:export", post(base + "/export")));
        endpoints.add(new Endpoint("monitor:operlog:remove", delete(base + "/1")));
        endpoints.add(new Endpoint("monitor:operlog:remove", delete(base + "/clean")));
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
