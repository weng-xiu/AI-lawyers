package ai.lawyers.framework.web.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mockStatic;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import ai.lawyers.common.constant.Constants;
import ai.lawyers.common.core.domain.entity.SysRole;
import ai.lawyers.common.core.domain.entity.SysUser;
import ai.lawyers.common.core.domain.model.LoginUser;
import ai.lawyers.common.utils.SecurityUtils;
import ai.lawyers.framework.security.context.PermissionContextHolder;

/**
 * 模板表达式权限校验测试：hasPermi/hasAnyPermi（通配与缺省）、
 * hasRole/hasAnyRoles（超管、去空格、多角色任一）、取反方法、权限上下文写入。
 *
 * @author ai-lawyers
 */
class PermissionServiceTest
{
    private MockedStatic<SecurityUtils> securityUtils;

    private PermissionService permissionService;

    @BeforeEach
    void setUp()
    {
        permissionService = new PermissionService();
        securityUtils = mockStatic(SecurityUtils.class);
        // PermissionContextHolder 依赖请求上下文
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(new MockHttpServletRequest()));
    }

    @AfterEach
    void tearDown()
    {
        securityUtils.close();
        RequestContextHolder.resetRequestAttributes();
    }

    private static LoginUser loginUser(Set<String> permissions, SysRole... roles)
    {
        SysUser user = new SysUser();
        user.setUserId(5L);
        user.setRoles(Arrays.asList(roles));
        return new LoginUser(5L, 1L, user, permissions);
    }

    private static SysRole role(String roleKey)
    {
        SysRole role = new SysRole();
        role.setRoleKey(roleKey);
        return role;
    }

    private void loginAs(LoginUser loginUser)
    {
        securityUtils.when(SecurityUtils::getLoginUser).thenReturn(loginUser);
    }

    @Test
    void hasPermi_空权限或未登录返回false()
    {
        assertThat(permissionService.hasPermi(null)).isFalse();
        assertThat(permissionService.hasPermi("")).isFalse();

        loginAs(null);
        assertThat(permissionService.hasPermi("system:user:list")).isFalse();
    }

    @Test
    void hasPermi_权限集为空返回false()
    {
        loginAs(loginUser(Collections.emptySet()));

        assertThat(permissionService.hasPermi("system:user:list")).isFalse();
    }

    @Test
    void hasPermi_命中具体权限并写入上下文()
    {
        loginAs(loginUser(new HashSet<>(Collections.singletonList("system:user:list"))));

        assertThat(permissionService.hasPermi("system:user:list")).isTrue();
        assertThat(PermissionContextHolder.getContext()).isEqualTo("system:user:list");
    }

    @Test
    void hasPermi_通配权限放行全部()
    {
        loginAs(loginUser(new HashSet<>(Collections.singletonList(Constants.ALL_PERMISSION))));

        assertThat(permissionService.hasPermi("lawyer:ticket:delete")).isTrue();
    }

    @Test
    void hasPermi_未命中返回false()
    {
        loginAs(loginUser(new HashSet<>(Collections.singletonList("system:user:list"))));

        assertThat(permissionService.hasPermi("system:role:list")).isFalse();
    }

    @Test
    void lacksPermi_与hasPermi相反()
    {
        loginAs(loginUser(new HashSet<>(Collections.singletonList("system:user:list"))));

        assertThat(permissionService.lacksPermi("system:user:list")).isFalse();
        assertThat(permissionService.lacksPermi("system:role:list")).isTrue();
    }

    @Test
    void hasAnyPermi_任一命中即通过()
    {
        loginAs(loginUser(new HashSet<>(Collections.singletonList("system:user:list"))));

        assertThat(permissionService.hasAnyPermi("system:role:list,system:user:list")).isTrue();
        assertThat(permissionService.hasAnyPermi("system:role:list,system:dict:list")).isFalse();
        assertThat(permissionService.hasAnyPermi("")).isFalse();
    }

    @Test
    void hasRole_空角色或角色集为空返回false()
    {
        assertThat(permissionService.hasRole("")).isFalse();

        loginAs(loginUser(Collections.emptySet()));
        assertThat(permissionService.hasRole("admin")).isFalse();
    }

    @Test
    void hasRole_超管放行任意角色()
    {
        loginAs(loginUser(Collections.emptySet(), role(Constants.SUPER_ADMIN)));

        assertThat(permissionService.hasRole("lawyer")).isTrue();
    }

    @Test
    void hasRole_按roleKey匹配并忽略首尾空格()
    {
        loginAs(loginUser(Collections.emptySet(), role("lawyer"), role("teamleader")));

        assertThat(permissionService.hasRole("lawyer")).isTrue();
        assertThat(permissionService.hasRole(" teamleader ")).isTrue();
        assertThat(permissionService.hasRole("admin")).isFalse();
    }

    @Test
    void lacksRole_与hasRole相反()
    {
        loginAs(loginUser(Collections.emptySet(), role("lawyer")));

        assertThat(permissionService.lacksRole("lawyer")).isFalse();
        assertThat(permissionService.lacksRole("admin")).isTrue();
    }

    @Test
    void hasAnyRoles_逗号分隔任一命中()
    {
        loginAs(loginUser(Collections.emptySet(), role("lawyer")));

        assertThat(permissionService.hasAnyRoles("agent,lawyer")).isTrue();
        assertThat(permissionService.hasAnyRoles("agent,admin")).isFalse();
        assertThat(permissionService.hasAnyRoles("")).isFalse();
    }
}
