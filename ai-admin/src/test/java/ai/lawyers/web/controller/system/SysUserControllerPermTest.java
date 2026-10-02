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
import ai.lawyers.common.core.domain.entity.SysUser;
import ai.lawyers.system.service.ISysDeptService;
import ai.lawyers.system.service.ISysPostService;
import ai.lawyers.system.service.ISysRoleService;
import ai.lawyers.system.service.ISysUserService;
import ai.lawyers.web.support.AbstractPermMvcTest;

/**
 * {@link SysUserController} 权限校验——逐端点验证未登录 401、权限码不符 403、
 * 持对应权限码放行 200。resetPwd 依赖密码加密器，仅校验安全边界（401/403）。
 */
public class SysUserControllerPermTest extends AbstractPermMvcTest
{
    @Mock
    private ISysUserService userService;

    @Mock
    private ISysRoleService roleService;

    @Mock
    private ISysDeptService deptService;

    @Mock
    private ISysPostService postService;

    private final List<Endpoint> endpoints = new ArrayList<>();

    @BeforeEach
    void setUp()
    {
        MockitoAnnotations.openMocks(this);
        when(userService.selectUserList(any())).thenReturn(new ArrayList<>());
        SysUser user = new SysUser();
        user.setRoles(new ArrayList<>());
        when(userService.selectUserById(1L)).thenReturn(user);
        when(roleService.selectRoleAll()).thenReturn(new ArrayList<>());
        when(roleService.selectRolesByUserId(1L)).thenReturn(new ArrayList<>());
        when(deptService.selectDeptTreeList(any())).thenReturn(new ArrayList<>());
        startMvc(SysUserController.class);
        buildEndpoints();
    }

    @Override
    protected void registerMocks()
    {
        registerMock("userService", userService);
        registerMock("roleService", roleService);
        registerMock("deptService", deptService);
        registerMock("postService", postService);
    }

    private void buildEndpoints()
    {
        String base = "/system/user";
        endpoints.add(new Endpoint("system:user:list", get(base + "/list")));
        endpoints.add(new Endpoint("system:user:export", post(base + "/export")));
        endpoints.add(new Endpoint("system:user:query", get(base + "/1")));
        endpoints.add(new Endpoint("system:user:add",
                post(base).contentType(MediaType.APPLICATION_JSON).content("{}")));
        endpoints.add(new Endpoint("system:user:edit",
                put(base).contentType(MediaType.APPLICATION_JSON).content("{}")));
        endpoints.add(new Endpoint("system:user:remove", delete(base + "/1")));
        // resetPwd 方法体内使用密码加密器，迷你容器无法构造，仅校验 401/403
        endpoints.add(new Endpoint("system:user:resetPwd",
                put(base + "/resetPwd").contentType(MediaType.APPLICATION_JSON).content("{}"), false));
        endpoints.add(new Endpoint("system:user:edit",
                put(base + "/changeStatus").contentType(MediaType.APPLICATION_JSON).content("{}")));
        endpoints.add(new Endpoint("system:user:query", get(base + "/authRole/1")));
        endpoints.add(new Endpoint("system:user:edit",
                put(base + "/authRole").param("userId", "1").param("roleIds", "2")));
        endpoints.add(new Endpoint("system:user:list", get(base + "/deptTree")));
        // importTemplate 无 @PreAuthorize：登录后即可访问
        endpoints.add(new Endpoint(null, post(base + "/importTemplate")));
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
            if (!endpoint.expect200)
            {
                continue;
            }
            mockMvc.perform(endpoint.builder.with(loginAs(
                            endpoint.permission == null ? new String[0] : new String[] { endpoint.permission })))
                    .andExpect(status().isOk());
        }
    }

    private static class Endpoint
    {
        private final String permission;

        private final MockHttpServletRequestBuilder builder;

        /** false 表示方法体依赖迷你容器外组件，仅校验 401/403 安全边界。 */
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
