package ai.lawyers.web.controller.lawyers.collab;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import ai.lawyers.common.core.domain.AjaxResult;
import ai.lawyers.common.core.domain.entity.SysUser;
import ai.lawyers.common.core.domain.model.LoginUser;
import ai.lawyers.common.core.page.TableDataInfo;
import ai.lawyers.common.utils.SecurityUtils;
import ai.lawyers.common.utils.sign.PiiCryptoUtils;
import ai.lawyers.system.domain.lawyers.AiCallerProfile;
import ai.lawyers.system.domain.lawyers.AiChannelIdentity;
import ai.lawyers.system.domain.lawyers.AiUnifiedSession;
import ai.lawyers.system.mapper.lawyers.AiCallerProfileMapper;
import ai.lawyers.system.service.lawyers.IAiChannelIdentityService;
import ai.lawyers.system.service.lawyers.IAiUnifiedSessionService;

/**
 * 渠道身份绑定接口单元测试
 *
 * @author ai-lawyers
 */
@ExtendWith(MockitoExtension.class)
class AiChannelIdentityControllerTest
{
    @Mock
    private IAiChannelIdentityService channelIdentityService;

    @Mock
    private IAiUnifiedSessionService unifiedSessionService;

    @Mock
    private AiCallerProfileMapper callerProfileMapper;

    @InjectMocks
    private AiChannelIdentityController controller;

    @AfterEach
    void tearDown()
    {
        RequestContextHolder.resetRequestAttributes();
    }

    private LoginUser loginUser(String username)
    {
        SysUser user = new SysUser();
        user.setUserName(username);
        return new LoginUser(1L, 2L, user, null);
    }

    @Test
    void 身份列表返回分页数据()
    {
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(new MockHttpServletRequest()));
        AiChannelIdentity query = new AiChannelIdentity();
        List<AiChannelIdentity> list = new ArrayList<AiChannelIdentity>();
        list.add(new AiChannelIdentity());
        when(channelIdentityService.selectIdentityList(query)).thenReturn(list);

        TableDataInfo table = controller.list(query);

        assertEquals(200, table.getCode());
        assertEquals(1, table.getTotal());
    }

    @Test
    void 发起绑定使用当前操作人()
    {
        AiChannelIdentity body = new AiChannelIdentity();
        body.setProfileId(9L);
        body.setChannelType("wechat");
        body.setChannelUid("wx-001");
        body.setChannelNickname("张三");
        when(channelIdentityService.requestBind(9L, "wechat", "wx-001", "张三", "agent")).thenReturn(31L);

        try (MockedStatic<SecurityUtils> mocked = mockStatic(SecurityUtils.class))
        {
            mocked.when(SecurityUtils::getLoginUser).thenReturn(loginUser("agent"));
            AjaxResult result = controller.requestBind(body);

            assertEquals(31L, result.get(AjaxResult.DATA_TAG));
        }
        verify(channelIdentityService).requestBind(9L, "wechat", "wx-001", "张三", "agent");
    }

    @Test
    void 确认绑定允许空请求体()
    {
        AiChannelIdentity body = new AiChannelIdentity();
        body.setChannelNickname("新昵称");
        when(channelIdentityService.confirmBind(31L, "新昵称")).thenReturn(1);
        when(channelIdentityService.confirmBind(32L, null)).thenReturn(0);

        assertEquals(200, controller.confirm(31L, body).get(AjaxResult.CODE_TAG));
        assertEquals(500, controller.confirm(32L, null).get(AjaxResult.CODE_TAG));
    }

    @Test
    void 解绑使用当前操作人()
    {
        when(channelIdentityService.unbind(31L, "agent")).thenReturn(1);

        try (MockedStatic<SecurityUtils> mocked = mockStatic(SecurityUtils.class))
        {
            mocked.when(SecurityUtils::getLoginUser).thenReturn(loginUser("agent"));
            assertEquals(200, controller.unbind(31L).get(AjaxResult.CODE_TAG));
        }
        verify(channelIdentityService).unbind(31L, "agent");
    }

    @Test
    void 时间线参数缺失返回错误()
    {
        AjaxResult result = controller.timeline(null, "");

        assertEquals(500, result.get(AjaxResult.CODE_TAG));
        assertEquals("profileId 与 callerNumber 至少传一个", result.get(AjaxResult.MSG_TAG));
    }

    @Test
    void 仅传手机号时反查档案再聚合时间线()
    {
        AiCallerProfile profile = new AiCallerProfile();
        profile.setProfileId(9L);
        List<AiUnifiedSession> sessions = new ArrayList<AiUnifiedSession>();
        try (MockedStatic<PiiCryptoUtils> mocked = mockStatic(PiiCryptoUtils.class))
        {
            mocked.when(() -> PiiCryptoUtils.blindIndex("13800000000")).thenReturn("idx-1");
            when(callerProfileMapper.selectAiCallerProfileByCallerNumberIndex("idx-1")).thenReturn(profile);
            when(unifiedSessionService.timeline(eq(9L), eq("13800000000"))).thenReturn(sessions);

            AjaxResult result = controller.timeline(null, "13800000000");

            assertSame(sessions, result.get(AjaxResult.DATA_TAG));
        }
        verify(unifiedSessionService).timeline(9L, "13800000000");
    }

    @Test
    void 手机号未匹配档案时按空档案聚合()
    {
        List<AiUnifiedSession> sessions = new ArrayList<AiUnifiedSession>();
        try (MockedStatic<PiiCryptoUtils> mocked = mockStatic(PiiCryptoUtils.class))
        {
            mocked.when(() -> PiiCryptoUtils.blindIndex("13900000000")).thenReturn("idx-2");
            when(callerProfileMapper.selectAiCallerProfileByCallerNumberIndex("idx-2")).thenReturn(null);
            when(unifiedSessionService.timeline(eq(null), eq("13900000000"))).thenReturn(sessions);

            assertSame(sessions, controller.timeline(null, "13900000000").get(AjaxResult.DATA_TAG));
        }
    }

    @Test
    void 档案绑定与直接档案查询()
    {
        List<AiChannelIdentity> bound = new ArrayList<AiChannelIdentity>();
        List<AiUnifiedSession> sessions = new ArrayList<AiUnifiedSession>();
        when(channelIdentityService.selectBoundByProfileId(9L)).thenReturn(bound);
        when(unifiedSessionService.timeline(eq(9L), eq(null))).thenReturn(sessions);

        assertSame(bound, controller.bound(9L).get(AjaxResult.DATA_TAG));
        assertSame(sessions, controller.timeline(9L, null).get(AjaxResult.DATA_TAG));
    }
}
