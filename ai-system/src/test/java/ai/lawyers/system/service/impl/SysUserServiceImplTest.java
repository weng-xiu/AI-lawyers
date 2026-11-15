package ai.lawyers.system.service.impl;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.MockitoAnnotations;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;
import ai.lawyers.common.constant.UserConstants;
import ai.lawyers.common.core.domain.entity.SysRole;
import ai.lawyers.common.core.domain.entity.SysUser;
import ai.lawyers.common.exception.ServiceException;
import ai.lawyers.common.utils.SecurityUtils;
import ai.lawyers.common.utils.spring.SpringUtils;
import ai.lawyers.system.domain.SysPost;
import ai.lawyers.system.domain.SysUserPost;
import ai.lawyers.system.domain.SysUserRole;
import ai.lawyers.system.mapper.SysPostMapper;
import ai.lawyers.system.mapper.SysRoleMapper;
import ai.lawyers.system.mapper.SysUserMapper;
import ai.lawyers.system.mapper.SysUserPostMapper;
import ai.lawyers.system.mapper.SysUserRoleMapper;
import ai.lawyers.system.service.ISysConfigService;
import ai.lawyers.system.service.ISysDeptService;
import ai.lawyers.system.service.lawyers.agent.event.UserAgentReleaseEvent;
import ai.lawyers.system.service.lawyers.agent.event.UserAgentSyncEvent;

/**
 * 用户服务单测。
 *
 * <p>覆盖用户/角色组/岗位组查询、账号手机邮箱唯一性校验、超级管理员与数据权限校验、
 * 新增/修改/授权/删除用户及其角色岗位关联、坐席同步/释放事件发布、
 * 以及批量导入用户的成功/已存在/校验失败分支。</p>
 *
 * @author ai-lawyers
 */
class SysUserServiceImplTest
{
    private SysUserServiceImpl service;

    @Mock private SysUserMapper userMapper;
    @Mock private SysRoleMapper roleMapper;
    @Mock private SysPostMapper postMapper;
    @Mock private SysUserRoleMapper userRoleMapper;
    @Mock private SysUserPostMapper userPostMapper;
    @Mock private ISysConfigService configService;
    @Mock private ISysDeptService deptService;
    @Mock private ApplicationEventPublisher eventPublisher;

    @BeforeEach
    void setUp()
    {
        MockitoAnnotations.openMocks(this);
        service = new SysUserServiceImpl();
        ReflectionTestUtils.setField(service, "userMapper", userMapper);
        ReflectionTestUtils.setField(service, "roleMapper", roleMapper);
        ReflectionTestUtils.setField(service, "postMapper", postMapper);
        ReflectionTestUtils.setField(service, "userRoleMapper", userRoleMapper);
        ReflectionTestUtils.setField(service, "userPostMapper", userPostMapper);
        ReflectionTestUtils.setField(service, "configService", configService);
        ReflectionTestUtils.setField(service, "deptService", deptService);
        ReflectionTestUtils.setField(service, "eventPublisher", eventPublisher);
        // 使用真实校验器，避免 Mockito 可变参数匹配问题
        Validator validator = Validation.buildDefaultValidatorFactory().getValidator();
        ReflectionTestUtils.setField(service, "validator", validator);
    }

    private static SysUser user(Long userId, String userName)
    {
        SysUser user = new SysUser(userId);
        user.setUserName(userName);
        return user;
    }

    // ---------------- 查询 ----------------

    @Test
    void selectUserList_delegates()
    {
        SysUser query = new SysUser();
        List<SysUser> list = Collections.singletonList(user(2L, "u"));
        when(userMapper.selectUserList(query)).thenReturn(list);

        assertSame(list, service.selectUserList(query));
    }

    @Test
    void selectAllocatedList_delegates()
    {
        SysUser query = new SysUser();
        List<SysUser> list = Collections.singletonList(user(2L, "u"));
        when(userMapper.selectAllocatedList(query)).thenReturn(list);

        assertSame(list, service.selectAllocatedList(query));
    }

    @Test
    void selectUnallocatedList_delegates()
    {
        SysUser query = new SysUser();
        List<SysUser> list = Collections.singletonList(user(2L, "u"));
        when(userMapper.selectUnallocatedList(query)).thenReturn(list);

        assertSame(list, service.selectUnallocatedList(query));
    }

    @Test
    void selectUserByUserName_delegates()
    {
        SysUser u = user(3L, "admin2");
        when(userMapper.selectUserByUserName("admin2")).thenReturn(u);

        assertSame(u, service.selectUserByUserName("admin2"));
    }

    @Test
    void selectUserById_delegates()
    {
        SysUser u = user(4L, "u4");
        when(userMapper.selectUserById(4L)).thenReturn(u);

        assertSame(u, service.selectUserById(4L));
    }

    @Test
    void selectUserRoleGroup_joinsRoleNames()
    {
        SysRole r1 = new SysRole();
        r1.setRoleName("管理员");
        SysRole r2 = new SysRole();
        r2.setRoleName("客服");
        when(roleMapper.selectRolesByUserName("u1")).thenReturn(Arrays.asList(r1, r2));

        assertEquals("管理员,客服", service.selectUserRoleGroup("u1"));
    }

    @Test
    void selectUserRoleGroup_empty_returnsEmptyString()
    {
        when(roleMapper.selectRolesByUserName("u1")).thenReturn(Collections.emptyList());

        assertEquals("", service.selectUserRoleGroup("u1"));
    }

    @Test
    void selectUserPostGroup_joinsPostNames()
    {
        SysPost p1 = new SysPost();
        p1.setPostName("组长");
        SysPost p2 = new SysPost();
        p2.setPostName("坐席");
        when(postMapper.selectPostsByUserName("u1")).thenReturn(Arrays.asList(p1, p2));

        assertEquals("组长,坐席", service.selectUserPostGroup("u1"));
    }

    @Test
    void selectUserPostGroup_empty_returnsEmptyString()
    {
        when(postMapper.selectPostsByUserName("u1")).thenReturn(null);

        assertEquals("", service.selectUserPostGroup("u1"));
    }

    // ---------------- 唯一性校验 ----------------

    @Test
    void checkUserNameUnique_whenNotExists_returnsUnique()
    {
        when(userMapper.checkUserNameUnique("new")).thenReturn(null);

        assertTrue(service.checkUserNameUnique(user(null, "new")));
    }

    @Test
    void checkUserNameUnique_whenSameUserId_returnsUnique()
    {
        when(userMapper.checkUserNameUnique("u1")).thenReturn(user(5L, "u1"));

        assertTrue(service.checkUserNameUnique(user(5L, "u1")));
    }

    @Test
    void checkUserNameUnique_whenOtherUserId_returnsNotUnique()
    {
        when(userMapper.checkUserNameUnique("u1")).thenReturn(user(6L, "u1"));

        assertFalse(service.checkUserNameUnique(user(5L, "u1")));
    }

    @Test
    void checkPhoneUnique_whenOtherUserId_returnsNotUnique()
    {
        SysUser dbUser = user(6L, "u1");
        dbUser.setPhonenumber("13800000000");
        SysUser input = user(5L, "u1");
        input.setPhonenumber("13800000000");
        when(userMapper.checkPhoneUnique("13800000000")).thenReturn(dbUser);

        assertFalse(service.checkPhoneUnique(input));
    }

    @Test
    void checkPhoneUnique_whenSameUserId_returnsUnique()
    {
        SysUser dbUser = user(5L, "u1");
        dbUser.setPhonenumber("13800000000");
        SysUser input = user(5L, "u1");
        input.setPhonenumber("13800000000");
        when(userMapper.checkPhoneUnique("13800000000")).thenReturn(dbUser);

        assertTrue(service.checkPhoneUnique(input));
    }

    @Test
    void checkEmailUnique_whenOtherUserId_returnsNotUnique()
    {
        SysUser dbUser = user(6L, "u1");
        dbUser.setEmail("a@b.com");
        SysUser input = user(5L, "u1");
        input.setEmail("a@b.com");
        when(userMapper.checkEmailUnique("a@b.com")).thenReturn(dbUser);

        assertFalse(service.checkEmailUnique(input));
    }

    @Test
    void checkEmailUnique_whenNotExists_returnsUnique()
    {
        when(userMapper.checkEmailUnique("a@b.com")).thenReturn(null);

        assertTrue(service.checkEmailUnique(user(null, "u1")));
    }

    // ---------------- 权限校验 ----------------

    @Test
    void checkUserAllowed_admin_throws()
    {
        assertThrows(ServiceException.class, () -> service.checkUserAllowed(user(1L, "admin")));
    }

    @Test
    void checkUserAllowed_normalUser_passes()
    {
        assertDoesNotThrow(() -> service.checkUserAllowed(user(2L, "u")));
    }

    @Test
    void checkUserDataScope_currentAdmin_skipsQuery()
    {
        try (MockedStatic<SecurityUtils> security = mockStatic(SecurityUtils.class))
        {
            security.when(SecurityUtils::getUserId).thenReturn(1L);

            assertDoesNotThrow(() -> service.checkUserDataScope(2L));
        }
        verify(userMapper, never()).selectUserList(any());
    }

    @Test
    void checkUserDataScope_visible_passes()
    {
        try (MockedStatic<SecurityUtils> security = mockStatic(SecurityUtils.class);
             MockedStatic<SpringUtils> spring = mockStatic(SpringUtils.class))
        {
            security.when(SecurityUtils::getUserId).thenReturn(2L);
            spring.when(() -> SpringUtils.getAopProxy(service)).thenReturn(service);
            when(userMapper.selectUserList(any())).thenReturn(Collections.singletonList(user(3L, "u3")));

            assertDoesNotThrow(() -> service.checkUserDataScope(3L));
        }
    }

    @Test
    void checkUserDataScope_notVisible_throws()
    {
        try (MockedStatic<SecurityUtils> security = mockStatic(SecurityUtils.class);
             MockedStatic<SpringUtils> spring = mockStatic(SpringUtils.class))
        {
            security.when(SecurityUtils::getUserId).thenReturn(2L);
            spring.when(() -> SpringUtils.getAopProxy(service)).thenReturn(service);
            when(userMapper.selectUserList(any())).thenReturn(Collections.emptyList());

            ServiceException ex = assertThrows(ServiceException.class, () -> service.checkUserDataScope(9L));
            assertEquals("没有权限访问用户数据！", ex.getMessage());
        }
    }

    // ---------------- 新增/修改 ----------------

    @Test
    void insertUser_withAgent_publishesSyncEvent()
    {
        SysUser u = user(8L, "u8");
        u.setNickName("张三");
        u.setAgentId(7L);
        u.setSipExtension("8001");
        u.setCallMode("auto");
        u.setCreateBy("admin");
        u.setRoleIds(new Long[] { 10L, 11L });
        u.setPostIds(new Long[] { 20L });
        when(userMapper.insertUser(u)).thenReturn(1);

        assertEquals(1, service.insertUser(u));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<SysUserRole>> roleCaptor = ArgumentCaptor.forClass(List.class);
        verify(userRoleMapper).batchUserRole(roleCaptor.capture());
        assertEquals(2, roleCaptor.getValue().size());

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<SysUserPost>> postCaptor = ArgumentCaptor.forClass(List.class);
        verify(userPostMapper).batchUserPost(postCaptor.capture());
        assertEquals(1, postCaptor.getValue().size());

        ArgumentCaptor<UserAgentSyncEvent> eventCaptor = ArgumentCaptor.forClass(UserAgentSyncEvent.class);
        verify(eventPublisher).publishEvent(eventCaptor.capture());
        UserAgentSyncEvent event = eventCaptor.getValue();
        assertEquals(8L, event.getUserId());
        assertEquals(7L, event.getAgentId());
        assertEquals("张三", event.getAgentName());
        assertEquals("8001", event.getSipExtension());
        assertEquals("auto", event.getCallMode());
        assertEquals("admin", event.getOperator());
    }

    @Test
    void insertUser_withoutAgent_noEventAndNoAssociations()
    {
        SysUser u = user(8L, "u8");
        when(userMapper.insertUser(u)).thenReturn(1);

        assertEquals(1, service.insertUser(u));

        verify(userRoleMapper, never()).batchUserRole(any());
        verify(userPostMapper, never()).batchUserPost(any());
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void registerUser_returnsWhetherInserted()
    {
        SysUser u = user(null, "new");
        when(userMapper.insertUser(u)).thenReturn(1, 0);

        assertTrue(service.registerUser(u));
        assertFalse(service.registerUser(u));
    }

    @Test
    void updateUser_withUpdateBy_publishesEventWithOperator()
    {
        SysUser u = user(5L, "u5");
        u.setUpdateBy("editor");
        u.setAgentId(9L);
        u.setRoleIds(new Long[] { 10L });
        u.setPostIds(new Long[] { 20L });
        when(userMapper.updateUser(u)).thenReturn(1);

        assertEquals(1, service.updateUser(u));

        verify(userRoleMapper).deleteUserRoleByUserId(5L);
        verify(userPostMapper).deleteUserPostByUserId(5L);
        verify(userMapper).updateUserAgentConfig(u);

        ArgumentCaptor<UserAgentSyncEvent> eventCaptor = ArgumentCaptor.forClass(UserAgentSyncEvent.class);
        verify(eventPublisher).publishEvent(eventCaptor.capture());
        assertEquals(5L, eventCaptor.getValue().getUserId());
        assertEquals(9L, eventCaptor.getValue().getAgentId());
        assertEquals("editor", eventCaptor.getValue().getOperator());
    }

    @Test
    void updateUser_blankUpdateBy_usesCurrentUsername()
    {
        SysUser u = user(5L, "u5");
        when(userMapper.updateUser(u)).thenReturn(1);

        try (MockedStatic<SecurityUtils> security = mockStatic(SecurityUtils.class))
        {
            security.when(SecurityUtils::getUsername).thenReturn("sys");

            service.updateUser(u);

            ArgumentCaptor<UserAgentSyncEvent> eventCaptor = ArgumentCaptor.forClass(UserAgentSyncEvent.class);
            verify(eventPublisher).publishEvent(eventCaptor.capture());
            assertEquals("sys", eventCaptor.getValue().getOperator());
        }
    }

    @Test
    void insertUserAuth_deletesThenBatchInserts()
    {
        Long[] roleIds = new Long[] { 10L, 11L };

        service.insertUserAuth(5L, roleIds);

        verify(userRoleMapper).deleteUserRoleByUserId(5L);
        verify(userRoleMapper).batchUserRole(any());
    }

    @Test
    void insertUserAuth_emptyRoleIds_onlyDeletes()
    {
        service.insertUserAuth(5L, new Long[0]);

        verify(userRoleMapper).deleteUserRoleByUserId(5L);
        verify(userRoleMapper, never()).batchUserRole(any());
    }

    @Test
    void insertUserRole_nullRoleIds_noop()
    {
        service.insertUserRole(5L, null);

        verify(userRoleMapper, never()).batchUserRole(any());
    }

    @Test
    void insertUserPost_nullPostIds_noop()
    {
        service.insertUserPost(user(5L, "u5"));

        verify(userPostMapper, never()).batchUserPost(any());
    }

    // ---------------- 状态/资料/密码 ----------------

    @Test
    void updateUserStatus_delegates()
    {
        SysUser u = user(5L, "u5");
        when(userMapper.updateUser(u)).thenReturn(1);

        assertEquals(1, service.updateUserStatus(u));
    }

    @Test
    void updateUserProfile_delegates()
    {
        SysUser u = user(5L, "u5");
        when(userMapper.updateUser(u)).thenReturn(1);

        assertEquals(1, service.updateUserProfile(u));
    }

    @Test
    void updateUserAvatar_returnsWhetherUpdated()
    {
        when(userMapper.updateUserAvatar(5L, "/a.png")).thenReturn(1, 0);

        assertTrue(service.updateUserAvatar(5L, "/a.png"));
        assertFalse(service.updateUserAvatar(5L, "/a.png"));
    }

    @Test
    void resetPwd_delegates()
    {
        SysUser u = user(5L, "u5");
        when(userMapper.updateUser(u)).thenReturn(2);

        assertEquals(2, service.resetPwd(u));
    }

    @Test
    void resetUserPwd_delegates()
    {
        when(userMapper.resetUserPwd(5L, "pwd")).thenReturn(1);

        assertEquals(1, service.resetUserPwd(5L, "pwd"));
    }

    // ---------------- 删除 ----------------

    @Test
    void deleteUserById_deletesAssociationsAndReleasesAgent()
    {
        when(userMapper.deleteUserById(5L)).thenReturn(1);

        assertEquals(1, service.deleteUserById(5L));

        verify(userRoleMapper).deleteUserRoleByUserId(5L);
        verify(userPostMapper).deleteUserPostByUserId(5L);
        ArgumentCaptor<UserAgentReleaseEvent> captor = ArgumentCaptor.forClass(UserAgentReleaseEvent.class);
        verify(eventPublisher).publishEvent(captor.capture());
        assertEquals(5L, captor.getValue().getUserId());
    }

    @Test
    void deleteUserByIds_checksEachAndReleasesAgent()
    {
        Long[] ids = new Long[] { 5L, 6L };
        when(userMapper.deleteUserByIds(ids)).thenReturn(2);

        int rows;

        try (MockedStatic<SecurityUtils> security = mockStatic(SecurityUtils.class))
        {
            security.when(SecurityUtils::getUserId).thenReturn(1L);
            rows = service.deleteUserByIds(ids);
        }

        assertEquals(2, rows);
        verify(userRoleMapper).deleteUserRole(ids);
        verify(userPostMapper).deleteUserPost(ids);
        verify(eventPublisher, times(2)).publishEvent(any(UserAgentReleaseEvent.class));
    }

    // ---------------- 导入 ----------------

    @Test
    void importUser_emptyList_throws()
    {
        ServiceException ex = assertThrows(ServiceException.class,
            () -> service.importUser(new ArrayList<>(), false, "admin"));
        assertEquals("导入用户数据不能为空！", ex.getMessage());
    }

    @Test
    void importUser_newUser_insertsWithInitPassword()
    {
        SysUser u = user(null, "new");
        u.setDeptId(100L);
        when(userMapper.selectUserByUserName("new")).thenReturn(null);
        when(configService.selectConfigByKey("sys.user.initPassword")).thenReturn("123456");
        when(userMapper.insertUser(u)).thenReturn(1);

        String result;

        try (MockedStatic<SecurityUtils> security = mockStatic(SecurityUtils.class))
        {
            security.when(() -> SecurityUtils.encryptPassword("123456")).thenReturn("ENC");
            result = service.importUser(Collections.singletonList(u), false, "admin");
        }

        assertTrue(result.contains("全部导入成功"));
        assertEquals("ENC", u.getPassword());
        assertEquals("admin", u.getCreateBy());
        verify(deptService).checkDeptDataScope(100L);
    }

    @Test
    void importUser_existingWithoutUpdate_countsFailure()
    {
        SysUser u = user(null, "exists");
        when(userMapper.selectUserByUserName("exists")).thenReturn(user(3L, "exists"));

        ServiceException ex = assertThrows(ServiceException.class,
            () -> service.importUser(Collections.singletonList(u), false, "admin"));

        assertTrue(ex.getMessage().contains("已存在"));
        verify(userMapper, never()).insertUser(any());
    }

    @Test
    void importUser_existingWithUpdate_updatesAndSyncsAgent()
    {
        SysUser u = user(null, "exists");
        u.setAgentId(9L);
        u.setNickName("李四");
        when(userMapper.selectUserByUserName("exists")).thenReturn(user(7L, "exists"));
        when(userMapper.updateUser(u)).thenReturn(1);

        String result;

        try (MockedStatic<SecurityUtils> security = mockStatic(SecurityUtils.class))
        {
            security.when(SecurityUtils::getUserId).thenReturn(1L);
            result = service.importUser(Collections.singletonList(u), true, "op");
        }

        assertTrue(result.contains("更新成功"));
        assertEquals(7L, u.getUserId());
        assertEquals("op", u.getUpdateBy());
        verify(userMapper).updateUserAgentConfig(u);

        ArgumentCaptor<UserAgentSyncEvent> captor = ArgumentCaptor.forClass(UserAgentSyncEvent.class);
        verify(eventPublisher).publishEvent(captor.capture());
        assertEquals(7L, captor.getValue().getUserId());
        assertEquals(9L, captor.getValue().getAgentId());
        assertEquals("op", captor.getValue().getOperator());
    }

    @Test
    void importUser_validationFailure_aggregatesMessage()
    {
        SysUser invalid = user(null, "");
        when(userMapper.selectUserByUserName("")).thenReturn(null);

        ServiceException ex = assertThrows(ServiceException.class,
            () -> service.importUser(Collections.singletonList(invalid), false, "admin"));

        assertTrue(ex.getMessage().contains("导入失败"));
        verify(userMapper, never()).insertUser(any());
    }
}
