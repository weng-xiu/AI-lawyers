package ai.lawyers.framework.web.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;
import java.util.Collections;
import java.util.Set;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.context.MessageSource;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.test.util.ReflectionTestUtils;

import ai.lawyers.common.core.domain.entity.SysUser;
import ai.lawyers.common.core.domain.model.LoginUser;
import ai.lawyers.common.enums.UserStatus;
import ai.lawyers.common.exception.ServiceException;
import ai.lawyers.common.utils.spring.SpringUtils;
import ai.lawyers.system.service.ISysUserService;

/**
 * 登录用户加载测试：用户不存在/已删除/已停用异常分支，以及正常登录信息封装。
 *
 * @author ai-lawyers
 */
class UserDetailsServiceImplTest
{
    private UserDetailsServiceImpl userDetailsService;

    private ISysUserService userService;

    private SysPasswordService passwordService;

    private SysPermissionService permissionService;

    @BeforeEach
    void setUp() throws Exception
    {
        userDetailsService = new UserDetailsServiceImpl();
        userService = mock(ISysUserService.class);
        passwordService = mock(SysPasswordService.class);
        permissionService = mock(SysPermissionService.class);
        ReflectionTestUtils.setField(userDetailsService, "userService", userService);
        ReflectionTestUtils.setField(userDetailsService, "passwordService", passwordService);
        ReflectionTestUtils.setField(userDetailsService, "permissionService", permissionService);

        // MessageUtils 依赖 SpringUtils 中的 BeanFactory 获取 MessageSource
        MessageSource messageSource = mock(MessageSource.class);
        when(messageSource.getMessage(anyString(), any(), any())).thenAnswer(invocation -> "msg:" + invocation.getArgument(0));
        ConfigurableListableBeanFactory beanFactory = mock(ConfigurableListableBeanFactory.class);
        when(beanFactory.getBean(MessageSource.class)).thenReturn(messageSource);
        Field field = SpringUtils.class.getDeclaredField("beanFactory");
        field.setAccessible(true);
        field.set(null, beanFactory);
    }

    @AfterEach
    void tearDown() throws Exception
    {
        Field field = SpringUtils.class.getDeclaredField("beanFactory");
        field.setAccessible(true);
        field.set(null, null);
    }

    @Test
    void loadUserByUsername_用户不存在抛业务异常()
    {
        when(userService.selectUserByUserName("ghost")).thenReturn(null);

        ServiceException error = assertThrows(ServiceException.class,
                () -> userDetailsService.loadUserByUsername("ghost"));

        assertEquals("msg:user.not.exists", error.getMessage());
    }

    @Test
    void loadUserByUsername_用户已删除抛业务异常()
    {
        SysUser user = user();
        user.setDelFlag(UserStatus.DELETED.getCode());
        when(userService.selectUserByUserName("lawyer")).thenReturn(user);

        ServiceException error = assertThrows(ServiceException.class,
                () -> userDetailsService.loadUserByUsername("lawyer"));

        assertEquals("msg:user.password.delete", error.getMessage());
    }

    @Test
    void loadUserByUsername_用户已停用抛业务异常且不校验密码()
    {
        SysUser user = user();
        user.setStatus(UserStatus.DISABLE.getCode());
        when(userService.selectUserByUserName("lawyer")).thenReturn(user);

        ServiceException error = assertThrows(ServiceException.class,
                () -> userDetailsService.loadUserByUsername("lawyer"));

        assertEquals("msg:user.blocked", error.getMessage());
        verifyNoInteractions(passwordService);
    }

    @Test
    void loadUserByUsername_正常用户校验密码并返回登录信息()
    {
        SysUser user = user();
        Set<String> permissions = Collections.singleton("law:case:list");
        when(userService.selectUserByUserName("lawyer")).thenReturn(user);
        when(permissionService.getMenuPermission(user)).thenReturn(permissions);

        UserDetails userDetails = userDetailsService.loadUserByUsername("lawyer");

        assertTrue(userDetails instanceof LoginUser);
        LoginUser loginUser = (LoginUser) userDetails;
        assertEquals(Long.valueOf(2L), loginUser.getUserId());
        assertEquals(Long.valueOf(3L), loginUser.getDeptId());
        assertEquals("lawyer", loginUser.getUsername());
        assertEquals(permissions, loginUser.getPermissions());
        verify(passwordService).validate(user);
    }

    @Test
    void createLoginUser_封装用户与菜单权限()
    {
        SysUser user = user();
        Set<String> permissions = Collections.singleton("system:user:list");
        when(permissionService.getMenuPermission(user)).thenReturn(permissions);

        LoginUser loginUser = (LoginUser) userDetailsService.createLoginUser(user);

        assertEquals(Long.valueOf(2L), loginUser.getUserId());
        assertEquals(Long.valueOf(3L), loginUser.getDeptId());
        assertEquals(permissions, loginUser.getPermissions());
        assertEquals(user, loginUser.getUser());
    }

    /** 构造状态正常的示例用户 */
    private SysUser user()
    {
        SysUser user = new SysUser();
        user.setUserId(2L);
        user.setDeptId(3L);
        user.setUserName("lawyer");
        user.setStatus(UserStatus.OK.getCode());
        user.setDelFlag("0");
        return user;
    }
}
