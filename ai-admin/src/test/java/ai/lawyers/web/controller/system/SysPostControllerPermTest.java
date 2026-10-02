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
import ai.lawyers.system.service.ISysPostService;
import ai.lawyers.web.support.AbstractPermMvcTest;

/**
 * {@link SysPostController} 权限校验——optionselect 无方法级权限码，登录即可访问；
 * 其余端点逐端点验证 401/403/200。
 */
public class SysPostControllerPermTest extends AbstractPermMvcTest
{
    @Mock
    private ISysPostService postService;

    private final List<Endpoint> endpoints = new ArrayList<>();

    @BeforeEach
    void setUp()
    {
        MockitoAnnotations.openMocks(this);
        when(postService.selectPostList(any())).thenReturn(new ArrayList<>());
        when(postService.selectPostAll()).thenReturn(new ArrayList<>());
        startMvc(SysPostController.class);
        buildEndpoints();
    }

    @Override
    protected void registerMocks()
    {
        registerMock("postService", postService);
    }

    private void buildEndpoints()
    {
        String base = "/system/post";
        endpoints.add(new Endpoint("system:post:list", get(base + "/list")));
        endpoints.add(new Endpoint("system:post:export", post(base + "/export")));
        endpoints.add(new Endpoint("system:post:query", get(base + "/1")));
        endpoints.add(new Endpoint("system:post:add",
                post(base).contentType(MediaType.APPLICATION_JSON).content("{}")));
        endpoints.add(new Endpoint("system:post:edit",
                put(base).contentType(MediaType.APPLICATION_JSON).content("{}")));
        endpoints.add(new Endpoint("system:post:remove", delete(base + "/1")));
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
