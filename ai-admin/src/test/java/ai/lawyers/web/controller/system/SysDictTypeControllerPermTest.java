package ai.lawyers.web.controller.system;

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
import ai.lawyers.system.service.ISysDictTypeService;
import ai.lawyers.web.support.AbstractPermMvcTest;

/**
 * {@link SysDictTypeController} 权限校验——optionselect 无方法级权限码，登录即可访问；
 * 其余端点逐端点验证 401/403/200。
 */
public class SysDictTypeControllerPermTest extends AbstractPermMvcTest
{
    @Mock
    private ISysDictTypeService dictTypeService;

    private final List<Endpoint> endpoints = new ArrayList<>();

    @BeforeEach
    void setUp()
    {
        MockitoAnnotations.openMocks(this);
        when(dictTypeService.selectDictTypeList(any())).thenReturn(new ArrayList<>());
        when(dictTypeService.selectDictTypeAll()).thenReturn(new ArrayList<>());
        startMvc(SysDictTypeController.class);
        buildEndpoints();
    }

    @Override
    protected void registerMocks()
    {
        registerMock("dictTypeService", dictTypeService);
    }

    private void buildEndpoints()
    {
        String base = "/system/dict/type";
        endpoints.add(new Endpoint("system:dict:list", get(base + "/list")));
        endpoints.add(new Endpoint("system:dict:export", post(base + "/export")));
        endpoints.add(new Endpoint("system:dict:query", get(base + "/1")));
        endpoints.add(new Endpoint("system:dict:add",
                post(base).contentType(MediaType.APPLICATION_JSON).content("{}")));
        endpoints.add(new Endpoint("system:dict:edit",
                put(base).contentType(MediaType.APPLICATION_JSON).content("{}")));
        endpoints.add(new Endpoint("system:dict:remove", delete(base + "/1")));
        endpoints.add(new Endpoint("system:dict:remove", delete(base + "/refreshCache")));
        // optionselect 无 @PreAuthorize
        endpoints.add(new Endpoint(null, get(base + "/optionselect")));
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
            if (endpoint.permission == null)
            {
                continue;
            }
            mockMvc.perform(endpoint.builder.with(loginAs("system:nope")))
                    .andExpect(status().isForbidden());
        }
    }

    @Test
    void allEndpoints_withCorrectPermission_200() throws Exception
    {
        for (Endpoint endpoint : endpoints)
        {
            mockMvc.perform(endpoint.builder.with(loginAs(
                            endpoint.permission == null ? new String[0] : new String[] { endpoint.permission })))
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
