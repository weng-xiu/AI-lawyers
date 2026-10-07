package ai.lawyers.web.controller.lawyers.trunk;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.ArrayList;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import ai.lawyers.system.mapper.lawyers.AiCallRecordArchiveMapper;
import ai.lawyers.web.support.AbstractPermMvcTest;

/**
 * {@link CallArchiveController} 权限校验——3 个只读端点逐端点验证未登录 401、
 * 权限码不符 403、持对应权限码放行 200。
 */
public class CallArchiveControllerPermTest extends AbstractPermMvcTest
{
    @Mock
    private AiCallRecordArchiveMapper archiveMapper;

    private final java.util.List<Endpoint> endpoints = new java.util.ArrayList<>();

    @BeforeEach
    void setUp()
    {
        MockitoAnnotations.openMocks(this);
        when(archiveMapper.selectArchiveList(any())).thenReturn(new ArrayList<>());
        startMvc(CallArchiveController.class);
        buildEndpoints();
    }

    @Override
    protected void registerMocks()
    {
        registerMock("archiveMapper", archiveMapper);
    }

    private void buildEndpoints()
    {
        String base = "/lawyers/trunk/callArchive";
        endpoints.add(new Endpoint("lawyers:trunk:callArchive:list", get(base + "/list")));
        endpoints.add(new Endpoint("lawyers:trunk:callArchive:query", get(base + "/1")));
        endpoints.add(new Endpoint("lawyers:trunk:callArchive:list", get(base + "/stat")));
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
