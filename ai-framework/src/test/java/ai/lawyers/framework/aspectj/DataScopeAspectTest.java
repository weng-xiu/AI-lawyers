package ai.lawyers.framework.aspectj;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;

import org.aspectj.lang.JoinPoint;
import org.junit.jupiter.api.Test;

import ai.lawyers.common.constant.UserConstants;
import ai.lawyers.common.core.domain.entity.SysRole;
import ai.lawyers.common.core.domain.entity.SysUser;

/**
 * 数据权限过滤测试：全部/自定义（单个与多个角色）/部门/部门及以下/仅本人（有无用户别名）、
 * 停用角色跳过、权限不匹配兜底 dept_id = 0、非 BaseEntity 参数安全跳过。
 *
 * @author ai-lawyers
 */
class DataScopeAspectTest
{
    private static final String PERMISSION = "system:user:list";

    private static SysRole role(Long roleId, String dataScope, String status, String... permissions)
    {
        SysRole role = new SysRole();
        role.setRoleId(roleId);
        role.setDataScope(dataScope);
        role.setStatus(status);
        role.setPermissions(new HashSet<>(Arrays.asList(permissions)));
        return role;
    }

    private static SysRole normalRole(Long roleId, String dataScope, String... permissions)
    {
        return role(roleId, dataScope, UserConstants.ROLE_NORMAL, permissions);
    }

    private static SysUser user(SysRole... roles)
    {
        SysUser user = new SysUser();
        user.setUserId(7L);
        user.setDeptId(100L);
        user.setRoles(new ArrayList<>(Arrays.asList(roles)));
        return user;
    }

    private static JoinPoint pointOf(Object arg)
    {
        JoinPoint point = mock(JoinPoint.class);
        when(point.getArgs()).thenReturn(new Object[]{arg});
        return point;
    }

    /** 以 SysUser 作为查询参数执行过滤，返回写入的 params.dataScope（未写入返回 null） */
    private static String scope(SysUser currentUser, String userAlias, String permission)
    {
        SysUser param = new SysUser();
        DataScopeAspect.dataScopeFilter(pointOf(param), currentUser, "sys_user", userAlias, permission);
        Object value = param.getParams().get(DataScopeAspect.DATA_SCOPE);
        return value == null ? null : String.valueOf(value);
    }

    @Test
    void 全部数据权限不拼接条件()
    {
        assertThat(scope(user(normalRole(1L, DataScopeAspect.DATA_SCOPE_ALL, PERMISSION)), null, PERMISSION)).isNull();
    }

    @Test
    void 全部数据权限优先于后续角色()
    {
        SysUser user = user(normalRole(1L, DataScopeAspect.DATA_SCOPE_ALL, PERMISSION),
                normalRole(2L, DataScopeAspect.DATA_SCOPE_DEPT, PERMISSION));

        assertThat(scope(user, null, PERMISSION)).isNull();
    }

    @Test
    void 自定义数据权限单角色按role_id关联()
    {
        String sql = scope(user(normalRole(5L, DataScopeAspect.DATA_SCOPE_CUSTOM, PERMISSION)), null, PERMISSION);

        assertThat(sql).isEqualTo(" AND (sys_user.dept_id IN ( SELECT dept_id FROM sys_role_dept WHERE role_id = 5 ) )");
    }

    @Test
    void 自定义数据权限多角色合并为in查询()
    {
        SysUser user = user(normalRole(5L, DataScopeAspect.DATA_SCOPE_CUSTOM, PERMISSION),
                normalRole(6L, DataScopeAspect.DATA_SCOPE_CUSTOM, PERMISSION));

        String sql = scope(user, null, PERMISSION);

        assertThat(sql).contains("role_id in (5,6)");
    }

    @Test
    void 部门数据权限按当前部门过滤()
    {
        String sql = scope(user(normalRole(3L, DataScopeAspect.DATA_SCOPE_DEPT, PERMISSION)), null, PERMISSION);

        assertThat(sql).isEqualTo(" AND (sys_user.dept_id = 100 )");
    }

    @Test
    void 部门及以下数据权限使用find_in_set()
    {
        String sql = scope(user(normalRole(4L, DataScopeAspect.DATA_SCOPE_DEPT_AND_CHILD, PERMISSION)), null, PERMISSION);

        assertThat(sql).contains("sys_dept WHERE dept_id = 100 or find_in_set( 100 , ancestors )");
    }

    @Test
    void 仅本人数据权限使用用户别名()
    {
        String sql = scope(user(normalRole(6L, DataScopeAspect.DATA_SCOPE_SELF, PERMISSION)), "u", PERMISSION);

        assertThat(sql).isEqualTo(" AND (u.user_id = 7 )");
    }

    @Test
    void 仅本人且无别名时限制为空集()
    {
        String sql = scope(user(normalRole(6L, DataScopeAspect.DATA_SCOPE_SELF, PERMISSION)), null, PERMISSION);

        assertThat(sql).isEqualTo(" AND (sys_user.dept_id = 0 )");
    }

    @Test
    void 停用角色被跳过并兜底空集()
    {
        SysUser user = user(role(7L, UserConstants.ROLE_DISABLE, UserConstants.ROLE_DISABLE, PERMISSION));

        String sql = scope(user, null, PERMISSION);

        assertThat(sql).isEqualTo(" AND (sys_user.dept_id = 0 )");
    }

    @Test
    void 权限不匹配兜底空集()
    {
        SysUser user = user(normalRole(8L, DataScopeAspect.DATA_SCOPE_DEPT, "system:role:list"));

        assertThat(scope(user, null, PERMISSION)).isEqualTo(" AND (sys_user.dept_id = 0 )");
    }

    @Test
    void 未传权限字符兜底空集()
    {
        SysUser user = user(normalRole(9L, DataScopeAspect.DATA_SCOPE_DEPT, PERMISSION));

        assertThat(scope(user, null, null)).isEqualTo(" AND (sys_user.dept_id = 0 )");
    }

    @Test
    void 参数非BaseEntity时安全跳过()
    {
        Object param = new Object();
        JoinPoint point = pointOf(param);
        SysUser user = user(normalRole(10L, DataScopeAspect.DATA_SCOPE_DEPT, PERMISSION));

        assertThatCode(() -> DataScopeAspect.dataScopeFilter(point, user, "sys_user", null, PERMISSION))
                .doesNotThrowAnyException();
    }

    @Test
    void 多角色同数据权限只拼接一次()
    {
        SysUser user = user(normalRole(11L, DataScopeAspect.DATA_SCOPE_DEPT, PERMISSION),
                normalRole(12L, DataScopeAspect.DATA_SCOPE_DEPT, PERMISSION));

        String sql = scope(user, null, PERMISSION);

        assertThat(sql).isEqualTo(" AND (sys_user.dept_id = 100 )");
    }
}
