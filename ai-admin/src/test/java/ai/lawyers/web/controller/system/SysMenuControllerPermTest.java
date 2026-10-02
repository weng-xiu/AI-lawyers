package ai.lawyers.web.controller.system;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
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
import ai.lawyers.system.service.ISysMenuService;
import ai.lawyers.web.support.AbstractPermMvcTest;

/**
 * {@link SysMenuController} 权限校验——treeselect / roleMenuTreeselect 无方法级权限码，
 * 登录即可访问；其余端点逐端点验证 401/403/200。
 */
public class SysMenuControllerPermTest extends AbstractPermMvcTest
{
    @Mock
    private ISysMenuService menuService;

    private final List<Endpoint> endpoints = new ArrayList<>();

    @BeforeEach
    void setUp()
    {
        MockitoAnnotations.openMocks(this);
        when(menuService.selectMenuList(any(), anyLong())).thenReturn(new ArrayList<>());
        when(menuService.selectMenuList(anyLong())).thenReturn(new ArrayList<>());
        when(menuService.buildMenuTreeSelect(any())).thenReturn(new ArrayList<>());
        when(menuService.selectMenuListByRoleId(anyLong())).thenReturn(new ArrayList<>());
        startMvc(SysMenuController.class);
        buildEndpoints();
    }

    @Override
    protected void registerMocks()
    {
        registerMock("menuService", menuService);
    }

    private void buildEndpoints()
    {
        String base = "/system/menu";
        endpoints.add(new Endpoint("system:menu:list", get(base + "/list")));
        endpoints.add(new Endpoint("system:menu:query", get(base + "/1")));
        // treeselect / roleMenuTreeselect 无 @PreAuthorize
        endpoints.add(new Endpoint(null, get(base + "/treeselect")));
        endpoints.add(new Endpoint(null, get(base + "/roleMenuTreeselect/1")));
        endpoints.add(new Endpoint("system:menu:add",
                post(base).contentType(MediaType.APPLICATION_JSON).content("{}")));
        endpoints.add(new Endpoint("system:menu:edit",
                put(base).contentType(MediaType.APPLICATION_JSON).content("{}")));
        endpoints.add(new Endpoint("system:menu:remove", delete(base + "/1")));
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
