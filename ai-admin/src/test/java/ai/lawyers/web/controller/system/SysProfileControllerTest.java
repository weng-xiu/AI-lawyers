package ai.lawyers.web.controller.system;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import ai.lawyers.common.core.domain.AjaxResult;
import ai.lawyers.common.core.domain.entity.SysUser;
import ai.lawyers.common.core.domain.model.LoginUser;
import ai.lawyers.common.utils.SecurityUtils;
import ai.lawyers.common.utils.file.FileUploadUtils;
import ai.lawyers.framework.web.service.TokenService;
import ai.lawyers.system.service.ISysUserService;

/**
 * 个人中心接口单元测试
 *
 * @author ai-lawyers
 */
@ExtendWith(MockitoExtension.class)
class SysProfileControllerTest
{
    @Mock
    private ISysUserService userService;

    @Mock
    private TokenService tokenService;

    @InjectMocks
    private SysProfileController controller;

    private LoginUser loginUser(String username)
    {
        SysUser user = new SysUser();
        user.setUserId(1L);
        user.setUserName(username);
        user.setPassword("encoded");
        return new LoginUser(1L, 2L, user, null);
    }

    private SysUser submittedUser()
    {
        SysUser user = new SysUser();
        user.setNickName("新昵称");
        user.setEmail("new@example.com");
        user.setPhonenumber("13800000000");
        user.setSex("1");
        return user;
    }

    @Test
    void 获取个人信息附带角色与岗位()
    {
        LoginUser loginUser = loginUser("admin");
        when(userService.selectUserRoleGroup("admin")).thenReturn("管理员");
        when(userService.selectUserPostGroup("admin")).thenReturn("研发岗");

        try (MockedStatic<SecurityUtils> mocked = mockStatic(SecurityUtils.class))
        {
            mocked.when(SecurityUtils::getLoginUser).thenReturn(loginUser);
            AjaxResult result = controller.profile();

            assertSame(loginUser.getUser(), result.get(AjaxResult.DATA_TAG));
            assertEquals("管理员", result.get("roleGroup"));
            assertEquals("研发岗", result.get("postGroup"));
        }
    }

    @Test
    void 修改个人信息手机号重复()
    {
        LoginUser loginUser = loginUser("admin");
        SysUser submitted = submittedUser();
        when(userService.checkPhoneUnique(loginUser.getUser())).thenReturn(false);

        try (MockedStatic<SecurityUtils> mocked = mockStatic(SecurityUtils.class))
        {
            mocked.when(SecurityUtils::getLoginUser).thenReturn(loginUser);
            AjaxResult result = controller.updateProfile(submitted);

            assertEquals(500, result.get(AjaxResult.CODE_TAG));
            assertEquals("修改用户'admin'失败，手机号码已存在", result.get(AjaxResult.MSG_TAG));
        }
        verify(userService, never()).updateUserProfile(any(SysUser.class));
    }

    @Test
    void 修改个人信息邮箱重复()
    {
        LoginUser loginUser = loginUser("admin");
        SysUser submitted = submittedUser();
        submitted.setPhonenumber("");
        when(userService.checkEmailUnique(loginUser.getUser())).thenReturn(false);

        try (MockedStatic<SecurityUtils> mocked = mockStatic(SecurityUtils.class))
        {
            mocked.when(SecurityUtils::getLoginUser).thenReturn(loginUser);
            AjaxResult result = controller.updateProfile(submitted);

            assertEquals("修改用户'admin'失败，邮箱账号已存在", result.get(AjaxResult.MSG_TAG));
        }
        verify(userService, never()).updateUserProfile(any(SysUser.class));
    }

    @Test
    void 修改个人信息成功刷新缓存()
    {
        LoginUser loginUser = loginUser("admin");
        SysUser submitted = submittedUser();
        when(userService.checkPhoneUnique(loginUser.getUser())).thenReturn(true);
        when(userService.checkEmailUnique(loginUser.getUser())).thenReturn(true);
        when(userService.updateUserProfile(loginUser.getUser())).thenReturn(1);

        try (MockedStatic<SecurityUtils> mocked = mockStatic(SecurityUtils.class))
        {
            mocked.when(SecurityUtils::getLoginUser).thenReturn(loginUser);
            assertEquals(200, controller.updateProfile(submitted).get(AjaxResult.CODE_TAG));
        }
        assertEquals("新昵称", loginUser.getUser().getNickName());
        verify(tokenService).setLoginUser(loginUser);
    }

    @Test
    void 修改个人信息更新失败提示异常()
    {
        LoginUser loginUser = loginUser("admin");
        SysUser submitted = submittedUser();
        when(userService.checkPhoneUnique(loginUser.getUser())).thenReturn(true);
        when(userService.checkEmailUnique(loginUser.getUser())).thenReturn(true);
        when(userService.updateUserProfile(loginUser.getUser())).thenReturn(0);

        try (MockedStatic<SecurityUtils> mocked = mockStatic(SecurityUtils.class))
        {
            mocked.when(SecurityUtils::getLoginUser).thenReturn(loginUser);
            assertEquals("修改个人信息异常，请联系管理员",
                    controller.updateProfile(submitted).get(AjaxResult.MSG_TAG));
        }
        verify(tokenService, never()).setLoginUser(any(LoginUser.class));
    }

    @Test
    void 修改密码旧密码错误()
    {
        LoginUser loginUser = loginUser("admin");
        Map<String, String> params = new HashMap<String, String>();
        params.put("oldPassword", "old");
        params.put("newPassword", "new");

        try (MockedStatic<SecurityUtils> mocked = mockStatic(SecurityUtils.class))
        {
            mocked.when(SecurityUtils::getLoginUser).thenReturn(loginUser);
            mocked.when(() -> SecurityUtils.matchesPassword("old", "encoded")).thenReturn(false);

            assertEquals("修改密码失败，旧密码错误", controller.updatePwd(params).get(AjaxResult.MSG_TAG));
        }
        verify(userService, never()).resetUserPwd(any(), anyString());
    }

    @Test
    void 修改密码新旧密码相同()
    {
        LoginUser loginUser = loginUser("admin");
        Map<String, String> params = new HashMap<String, String>();
        params.put("oldPassword", "old");
        params.put("newPassword", "new");

        try (MockedStatic<SecurityUtils> mocked = mockStatic(SecurityUtils.class))
        {
            mocked.when(SecurityUtils::getLoginUser).thenReturn(loginUser);
            mocked.when(() -> SecurityUtils.matchesPassword(anyString(), anyString())).thenReturn(true);

            assertEquals("新密码不能与旧密码相同", controller.updatePwd(params).get(AjaxResult.MSG_TAG));
        }
        verify(userService, never()).resetUserPwd(any(), anyString());
    }

    @Test
    void 修改密码重置失败提示异常()
    {
        LoginUser loginUser = loginUser("admin");
        Map<String, String> params = new HashMap<String, String>();
        params.put("oldPassword", "old");
        params.put("newPassword", "new");
        when(userService.resetUserPwd(1L, "newEncoded")).thenReturn(0);

        try (MockedStatic<SecurityUtils> mocked = mockStatic(SecurityUtils.class))
        {
            mocked.when(SecurityUtils::getLoginUser).thenReturn(loginUser);
            mocked.when(() -> SecurityUtils.matchesPassword("old", "encoded")).thenReturn(true);
            mocked.when(() -> SecurityUtils.matchesPassword("new", "encoded")).thenReturn(false);
            mocked.when(() -> SecurityUtils.encryptPassword("new")).thenReturn("newEncoded");

            assertEquals("修改密码异常，请联系管理员", controller.updatePwd(params).get(AjaxResult.MSG_TAG));
        }
    }

    @Test
    void 修改密码成功刷新缓存()
    {
        LoginUser loginUser = loginUser("admin");
        Map<String, String> params = new HashMap<String, String>();
        params.put("oldPassword", "old");
        params.put("newPassword", "new");
        when(userService.resetUserPwd(1L, "newEncoded")).thenReturn(1);

        try (MockedStatic<SecurityUtils> mocked = mockStatic(SecurityUtils.class))
        {
            mocked.when(SecurityUtils::getLoginUser).thenReturn(loginUser);
            mocked.when(() -> SecurityUtils.matchesPassword("old", "encoded")).thenReturn(true);
            mocked.when(() -> SecurityUtils.matchesPassword("new", "encoded")).thenReturn(false);
            mocked.when(() -> SecurityUtils.encryptPassword("new")).thenReturn("newEncoded");

            assertEquals(200, controller.updatePwd(params).get(AjaxResult.CODE_TAG));
        }
        assertEquals("newEncoded", loginUser.getUser().getPassword());
        assertNotNull(loginUser.getUser().getPwdUpdateDate());
        verify(tokenService).setLoginUser(loginUser);
    }

    @Test
    void 头像上传空文件返回异常() throws Exception
    {
        MockMultipartFile file = new MockMultipartFile("avatarfile", "a.png", "image/png", new byte[0]);

        AjaxResult result = controller.avatar(file);

        assertEquals(500, result.get(AjaxResult.CODE_TAG));
        assertEquals("上传图片异常，请联系管理员", result.get(AjaxResult.MSG_TAG));
    }

    @Test
    void 头像上传成功刷新缓存() throws Exception
    {
        LoginUser loginUser = loginUser("admin");
        MockMultipartFile file = new MockMultipartFile("avatarfile", "a.png", "image/png", new byte[] { 1 });
        when(userService.updateUserAvatar(1L, "/avatar/x.png")).thenReturn(true);

        try (MockedStatic<SecurityUtils> mockedSecurity = mockStatic(SecurityUtils.class);
                MockedStatic<FileUploadUtils> mockedUpload = mockStatic(FileUploadUtils.class))
        {
            mockedSecurity.when(SecurityUtils::getLoginUser).thenReturn(loginUser);
            mockedUpload.when(() -> FileUploadUtils.upload(anyString(), any(), any(), anyBoolean()))
                    .thenReturn("/avatar/x.png");

            AjaxResult result = controller.avatar(file);

            assertEquals(200, result.get(AjaxResult.CODE_TAG));
            assertEquals("/avatar/x.png", result.get("imgUrl"));
        }
        assertEquals("/avatar/x.png", loginUser.getUser().getAvatar());
        verify(tokenService).setLoginUser(loginUser);
    }

    @Test
    void 头像上传更新失败返回异常() throws Exception
    {
        LoginUser loginUser = loginUser("admin");
        MockMultipartFile file = new MockMultipartFile("avatarfile", "a.png", "image/png", new byte[] { 1 });
        when(userService.updateUserAvatar(1L, "/avatar/x.png")).thenReturn(false);

        try (MockedStatic<SecurityUtils> mockedSecurity = mockStatic(SecurityUtils.class);
                MockedStatic<FileUploadUtils> mockedUpload = mockStatic(FileUploadUtils.class))
        {
            mockedSecurity.when(SecurityUtils::getLoginUser).thenReturn(loginUser);
            mockedUpload.when(() -> FileUploadUtils.upload(anyString(), any(), any(), anyBoolean()))
                    .thenReturn("/avatar/x.png");

            assertEquals("上传图片异常，请联系管理员", controller.avatar(file).get(AjaxResult.MSG_TAG));
        }
        verify(tokenService, never()).setLoginUser(any(LoginUser.class));
    }
}
