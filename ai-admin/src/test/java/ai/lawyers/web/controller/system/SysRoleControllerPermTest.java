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
import ai.lawyers.framework.web.service.SysPermissionService;
import ai.lawyers.framework.web.service.TokenService;
import ai.lawyers.system.service.ISysDeptService;
import ai.lawyers.system.service.ISysRoleService;
import ai.lawyers.system.service.ISysUserService;
import ai.lawyers.web.support.AbstractPermMvcTest;

/**
 * {@link SysRoleController} 权限校验——逐端点验证未登录 401、权限码不符 403、
 * 持对应权限码放行 200。
 */
public class SysRoleControllerPermTest extends AbstractPermMvcTest
{
    @Mock
    private ISysRoleService roleService;

    @Mock
    private TokenService tokenService;

    @Mock
    private SysPermissionService permissionService;

    @Mock
    private ISysUserService userService;

    @Mock
    private ISysDeptService deptService;

    private final List<Endpoint> endpoints = new ArrayList<>();

    @BeforeEach
    void setUp()
    {
        MockitoAnnotations.openMocks(this);
        when(roleService.selectRoleList(any())).thenReturn(new ArrayList<>());
        when(userService.selectAllocatedList(any())).thenReturn(new ArrayList<>());
        when(userService.selectUnallocatedList(any())).thenReturn(new ArrayList<>());
        when(deptService.selectDeptTreeList(any())).thenReturn(new ArrayList<>());
        startMvc(SysRoleController.class);
        buildEndpoints();
    }

    @Override
    protected void registerMocks()
    {
        registerMock("roleService", roleService);
        registerMock("tokenService", tokenService);
        registerMock("permissionService", permissionService);
        registerMock("userService", userService);
        registerMock("deptService", deptService);
    }

    private void buildEndpoints()
    {
        String base = "/system/role";
        endpoints.add(new Endpoint("system:role:list", get(base + "/list")));
        endpoints.add(new Endpoint("system:role:export", post(base + "/export")));
        endpoints.add(new Endpoint("system:role:query", get(base + "/1")));
        endpoints.add(new Endpoint("system:role:add",
                post(base).contentType(MediaType.APPLICATION_JSON).content("{}")));
        endpoints.add(new Endpoint("system:role:edit",
                put(base).contentType(MediaType.APPLICATION_JSON).content("{}")));
        endpoints.add(new Endpoint("system:role:edit",
                put(base + "/dataScope").contentType(MediaType.APPLICATION_JSON).content("{}")));
        endpoints.add(new Endpoint("system:role:edit",
                put(base + "/changeStatus").contentType(MediaType.APPLICATION_JSON).content("{}")));
        endpoints.add(new Endpoint("system:role:remove", delete(base + "/1")));
        endpoints.add(new Endpoint("system:role:query", get(base + "/optionselect")));
        endpoints.add(new Endpoint("system:role:list", get(base + "/authUser/allocatedList")));
        endpoints.add(new Endpoint("system:role:list", get(base + "/authUser/unallocatedList")));
        endpoints.add(new Endpoint("system:role:edit",
                put(base + "/authUser/cancel").contentType(MediaType.APPLICATION_JSON).content("{}")));
        endpoints.add(new Endpoint("system:role:edit",
                put(base + "/authUser/cancelAll").param("roleId", "1").param("userIds", "2")));
        endpoints.add(new Endpoint("system:role:edit",
                put(base + "/authUser/selectAll").param("roleId", "1").param("userIds", "2")));
        endpoints.add(new Endpoint("system:role:query", get(base + "/deptTree/1")));
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
            mockMvc.perform(endpoint.builder.with(loginAs("system:nope")))
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
