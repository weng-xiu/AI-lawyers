package ai.lawyers.admin.controller.lawyers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
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
import ai.lawyers.common.exception.ServiceException;
import ai.lawyers.common.utils.SecurityUtils;
import ai.lawyers.common.utils.sign.PiiCryptoUtils;
import ai.lawyers.system.domain.lawyers.AiCallTicket;
import ai.lawyers.system.domain.lawyers.AiCallerProfile;
import ai.lawyers.system.domain.lawyers.AiChannelIdentity;
import ai.lawyers.system.domain.lawyers.AiExternalOrg;
import ai.lawyers.system.domain.lawyers.AiLegalKnowledge;
import ai.lawyers.system.domain.lawyers.AiMessage;
import ai.lawyers.system.mapper.lawyers.AiCallerProfileMapper;
import ai.lawyers.system.mapper.lawyers.AiLegalKnowledgeMapper;
import ai.lawyers.system.service.lawyers.IAiCallTicketService;
import ai.lawyers.system.service.lawyers.IAiChannelIdentityService;
import ai.lawyers.system.service.lawyers.IAiExternalOrgService;
import ai.lawyers.system.service.lawyers.IAiMessageService;
import ai.lawyers.system.service.lawyers.IPiiSearchTokenService;

/**
 * 公众端门户接口单元测试
 *
 * @author ai-lawyers
 */
@ExtendWith(MockitoExtension.class)
class AiUserPortalControllerTest
{
    @Mock
    private IAiCallTicketService callTicketService;

    @Mock
    private IAiExternalOrgService externalOrgService;

    @Mock
    private AiCallerProfileMapper callerProfileMapper;

    @Mock
    private IAiChannelIdentityService channelIdentityService;

    @Mock
    private IPiiSearchTokenService piiSearchTokenService;

    @Mock
    private AiLegalKnowledgeMapper legalKnowledgeMapper;

    @Mock
    private IAiMessageService messageService;

    @InjectMocks
    private AiUserPortalController controller;

    @AfterEach
    void tearDown()
    {
        RequestContextHolder.resetRequestAttributes();
    }

    private LoginUser loginUser(String phone, String username)
    {
        SysUser user = new SysUser();
        user.setUserId(1L);
        user.setUserName(username);
        user.setPhonenumber(phone);
        return new LoginUser(1L, 2L, user, null);
    }

    private AiCallerProfile profile(Long profileId)
    {
        AiCallerProfile profile = new AiCallerProfile();
        profile.setProfileId(profileId);
        profile.setLanguagePreference("zh-CN");
        return profile;
    }

    private AiChannelIdentity identity(Long id, Long profileId)
    {
        AiChannelIdentity identity = new AiChannelIdentity();
        identity.setId(id);
        identity.setProfileId(profileId);
        return identity;
    }

    @Test
    void 我的工单仅按当前手机号与状态过滤()
    {
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(new MockHttpServletRequest()));
        AiCallTicket query = new AiCallTicket();
        query.setCallerNumber("19900000000");
        query.setStatus("1");
        List<AiCallTicket> list = new ArrayList<AiCallTicket>();
        when(callTicketService.selectAiCallTicketList(any(AiCallTicket.class))).thenReturn(list);

        try (MockedStatic<SecurityUtils> mocked = mockStatic(SecurityUtils.class))
        {
            mocked.when(SecurityUtils::getLoginUser).thenReturn(loginUser("13800000000", "portal"));
            TableDataInfo table = controller.myTickets(query);
            assertEquals(200, table.getCode());
            assertSame(list, table.getRows());

            controller.myTickets(new AiCallTicket());
        }
        ArgumentCaptor<AiCallTicket> captor = ArgumentCaptor.forClass(AiCallTicket.class);
        verify(callTicketService, org.mockito.Mockito.times(2)).selectAiCallTicketList(captor.capture());
        assertEquals("13800000000", captor.getAllValues().get(0).getCallerNumber());
        assertEquals("1", captor.getAllValues().get(0).getStatus());
        assertEquals("13800000000", captor.getAllValues().get(1).getCallerNumber());
        assertNull(captor.getAllValues().get(1).getStatus());
    }

    @Test
    void 工单详情按来电号码做对象级权限校验()
    {
        AiCallTicket other = new AiCallTicket();
        other.setCallerNumber("13900000000");
        AiCallTicket own = new AiCallTicket();
        own.setCallerNumber("13800000000");
        when(callTicketService.selectAiCallTicketByTicketId(1L)).thenReturn(null);
        when(callTicketService.selectAiCallTicketByTicketId(2L)).thenReturn(other);
        when(callTicketService.selectAiCallTicketByTicketId(3L)).thenReturn(own);

        try (MockedStatic<SecurityUtils> mocked = mockStatic(SecurityUtils.class))
        {
            mocked.when(SecurityUtils::getLoginUser).thenReturn(loginUser("13800000000", "portal"));
            assertEquals("工单ID不能为空", controller.myTicketDetail(null).get(AjaxResult.MSG_TAG));
            assertEquals("工单不存在或无权查看", controller.myTicketDetail(1L).get(AjaxResult.MSG_TAG));
            assertEquals("工单不存在或无权查看", controller.myTicketDetail(2L).get(AjaxResult.MSG_TAG));
            assertSame(own, controller.myTicketDetail(3L).get(AjaxResult.DATA_TAG));
        }
    }

    @Test
    void 密文来电号码解密后比对()
    {
        AiCallTicket encrypted = new AiCallTicket();
        encrypted.setCallerNumber("cipher-1");
        AiCallTicket broken = new AiCallTicket();
        broken.setCallerNumber("cipher-2");
        when(callTicketService.selectAiCallTicketByTicketId(4L)).thenReturn(encrypted);
        when(callTicketService.selectAiCallTicketByTicketId(5L)).thenReturn(broken);

        try (MockedStatic<SecurityUtils> mocked = mockStatic(SecurityUtils.class);
             MockedStatic<PiiCryptoUtils> pii = mockStatic(PiiCryptoUtils.class))
        {
            mocked.when(SecurityUtils::getLoginUser).thenReturn(loginUser("13800000000", "portal"));
            pii.when(() -> PiiCryptoUtils.isEncrypted("cipher-1")).thenReturn(true);
            pii.when(() -> PiiCryptoUtils.isEncrypted("cipher-2")).thenReturn(true);
            pii.when(() -> PiiCryptoUtils.decrypt("cipher-1")).thenReturn(" 13800000000 ");
            pii.when(() -> PiiCryptoUtils.decrypt("cipher-2")).thenThrow(new RuntimeException("密钥不可用"));

            assertSame(encrypted, controller.myTicketDetail(4L).get(AjaxResult.DATA_TAG));
            assertEquals("工单不存在或无权查看", controller.myTicketDetail(5L).get(AjaxResult.MSG_TAG));
        }
    }

    @Test
    void 服务导航匿名透传()
    {
        List<AiExternalOrg> orgs = new ArrayList<AiExternalOrg>();
        AiExternalOrg query = new AiExternalOrg();
        when(externalOrgService.selectEnabledOrgsForPortal(query)).thenReturn(orgs);

        assertSame(orgs, controller.serviceOrgs(query).get(AjaxResult.DATA_TAG));
    }

    @Test
    void 常见问题裁剪字段并压缩摘要()
    {
        AiLegalKnowledge knowledge = new AiLegalKnowledge();
        knowledge.setKnowledgeId(1L);
        knowledge.setTitle("劳动仲裁流程");
        knowledge.setCategoryId(2L);
        knowledge.setViewCount(9L);
        StringBuilder content = new StringBuilder("  第一段\n\n第二段 ");
        for (int i = 0; i < 200; i++)
        {
            content.append('说');
        }
        knowledge.setContent(content.toString());
        when(legalKnowledgeMapper.selectFaqKnowledge(2L, 20)).thenReturn(List.of(knowledge));
        when(legalKnowledgeMapper.selectFaqKnowledge(null, 8)).thenReturn(new ArrayList<AiLegalKnowledge>());
        when(legalKnowledgeMapper.selectFaqKnowledge(3L, 1)).thenReturn(null);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> rows = (List<Map<String, Object>>) controller.faq(2L, 100)
                .get(AjaxResult.DATA_TAG);
        assertEquals(1, rows.size());
        Map<String, Object> item = rows.get(0);
        assertEquals(Set.of("knowledgeId", "title", "summary", "categoryId", "viewCount"), item.keySet());
        assertEquals(1L, item.get("knowledgeId"));
        assertEquals("劳动仲裁流程", item.get("title"));
        String summary = String.valueOf(item.get("summary"));
        assertEquals(161, summary.length());
        assertTrue(summary.endsWith("…"));
        assertFalse(summary.contains("\n"));

        assertTrue(((List<?>) controller.faq(null, null).get(AjaxResult.DATA_TAG)).isEmpty());
        assertTrue(((List<?>) controller.faq(3L, 0).get(AjaxResult.DATA_TAG)).isEmpty());
    }

    @Test
    void 消息中心按登录用户过滤与已读操作()
    {
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(new MockHttpServletRequest()));
        AiMessage query = new AiMessage();
        query.setReceiverUserId(99L);
        query.setMsgType("2");
        query.setIsRead("1");
        List<AiMessage> list = new ArrayList<AiMessage>();
        when(messageService.selectAiMessageList(any(AiMessage.class))).thenReturn(list);
        when(messageService.countUnread(1L)).thenReturn(3);
        when(messageService.markRead(9L, 1L)).thenReturn(1);
        when(messageService.markAllRead(1L)).thenReturn(0);

        try (MockedStatic<SecurityUtils> mocked = mockStatic(SecurityUtils.class))
        {
            mocked.when(SecurityUtils::getUserId).thenReturn(1L);
            assertSame(list, controller.myMessages(query).getRows());
            assertEquals(3, controller.unreadCount().get(AjaxResult.DATA_TAG));
            assertEquals(200, controller.readMessage(9L).get(AjaxResult.CODE_TAG));
            assertEquals(500, controller.readAllMessages().get(AjaxResult.CODE_TAG));
        }
        ArgumentCaptor<AiMessage> captor = ArgumentCaptor.forClass(AiMessage.class);
        verify(messageService).selectAiMessageList(captor.capture());
        assertEquals(1L, captor.getValue().getReceiverUserId());
        assertEquals("2", captor.getValue().getMsgType());
        assertEquals("1", captor.getValue().getIsRead());
    }

    @Test
    void 适老偏好为空时拒绝保存()
    {
        try (MockedStatic<SecurityUtils> mocked = mockStatic(SecurityUtils.class))
        {
            mocked.when(SecurityUtils::getLoginUser).thenReturn(loginUser("13800000000", "portal"));
            ServiceException exception = assertThrows(ServiceException.class,
                    () -> controller.savePreference(new LinkedHashMap<String, Object>()));
            assertEquals("没有需要保存的偏好项", exception.getMessage());
        }
    }

    @Test
    void 首次保存偏好新建档案并回填检索token()
    {
        Map<String, Object> body = new LinkedHashMap<String, Object>();
        body.put("languagePreference", "yue-CN");
        body.put("careMode", 1);
        when(callerProfileMapper.selectAiCallerProfileByCallerNumberIndex("idx-1")).thenReturn(null);

        try (MockedStatic<SecurityUtils> mocked = mockStatic(SecurityUtils.class);
             MockedStatic<PiiCryptoUtils> pii = mockStatic(PiiCryptoUtils.class))
        {
            mocked.when(SecurityUtils::getLoginUser).thenReturn(loginUser("13800000000", "portal"));
            mocked.when(SecurityUtils::getUsername).thenReturn("portal");
            pii.when(() -> PiiCryptoUtils.blindIndex("13800000000")).thenReturn("idx-1");
            pii.when(() -> PiiCryptoUtils.encrypt("13800000000")).thenReturn("cipher-1");

            assertEquals("偏好已保存", controller.savePreference(body).get(AjaxResult.MSG_TAG));
        }
        ArgumentCaptor<AiCallerProfile> captor = ArgumentCaptor.forClass(AiCallerProfile.class);
        verify(callerProfileMapper).insertAiCallerProfile(captor.capture());
        AiCallerProfile saved = captor.getValue();
        assertEquals("cipher-1", saved.getCallerNumber());
        assertEquals("idx-1", saved.getCallerNumberIndex());
        assertEquals("yue-CN", saved.getLanguagePreference());
        assertEquals(1, saved.getCareMode());
        assertEquals("portal", saved.getCreateBy());
        verify(piiSearchTokenService).rebuild(IPiiSearchTokenService.OWNER_CALLER_PROFILE, null, "13800000000");
    }

    @Test
    void 已有档案时仅更新偏好项()
    {
        AiCallerProfile first = profile(5L);
        AiCallerProfile second = profile(6L);
        when(callerProfileMapper.selectAiCallerProfileByCallerNumberIndex("idx-1")).thenReturn(first, second);
        Map<String, Object> careModeBody = new LinkedHashMap<String, Object>();
        careModeBody.put("careMode", 1);
        Map<String, Object> languageBody = new LinkedHashMap<String, Object>();
        languageBody.put("languagePreference", "en-US");

        try (MockedStatic<SecurityUtils> mocked = mockStatic(SecurityUtils.class);
             MockedStatic<PiiCryptoUtils> pii = mockStatic(PiiCryptoUtils.class))
        {
            mocked.when(SecurityUtils::getLoginUser).thenReturn(loginUser("13800000000", "portal"));
            mocked.when(SecurityUtils::getUsername).thenReturn("portal");
            pii.when(() -> PiiCryptoUtils.blindIndex("13800000000")).thenReturn("idx-1");

            assertEquals("偏好已保存", controller.savePreference(careModeBody).get(AjaxResult.MSG_TAG));
            assertEquals("偏好已保存", controller.savePreference(languageBody).get(AjaxResult.MSG_TAG));
        }
        verify(callerProfileMapper).updatePreference(5L, "zh-CN", 1);
        verify(callerProfileMapper).updatePreference(6L, "en-US", 0);
    }

    @Test
    void 渠道列表与绑定申请()
    {
        List<AiChannelIdentity> bound = new ArrayList<AiChannelIdentity>();
        when(callerProfileMapper.selectAiCallerProfileByCallerNumberIndex("idx-1")).thenReturn(profile(5L));
        when(channelIdentityService.selectBoundByProfileId(5L)).thenReturn(bound);
        AiChannelIdentity body = new AiChannelIdentity();
        body.setChannelType("WECHAT_MINI");
        body.setChannelUid("openid-1");
        body.setChannelNickname("昵称");
        when(channelIdentityService.requestBind(5L, "WECHAT_MINI", "openid-1", "昵称", "portal")).thenReturn(7L);

        try (MockedStatic<SecurityUtils> mocked = mockStatic(SecurityUtils.class);
             MockedStatic<PiiCryptoUtils> pii = mockStatic(PiiCryptoUtils.class))
        {
            mocked.when(SecurityUtils::getLoginUser).thenReturn(loginUser("13800000000", "portal"));
            mocked.when(SecurityUtils::getUsername).thenReturn("portal");
            pii.when(() -> PiiCryptoUtils.blindIndex("13800000000")).thenReturn("idx-1");

            assertSame(bound, controller.myChannels().get(AjaxResult.DATA_TAG));
            AjaxResult result = controller.requestBind(body);
            assertEquals("绑定申请已提交，待二次确认", result.get(AjaxResult.MSG_TAG));
            assertEquals(7L, result.get(AjaxResult.DATA_TAG));
        }
    }

    @Test
    void 确认绑定与解绑需校验归属()
    {
        when(callerProfileMapper.selectAiCallerProfileByCallerNumberIndex("idx-1")).thenReturn(profile(5L));
        when(channelIdentityService.selectIdentityById(8L)).thenReturn(identity(8L, 9L));
        when(channelIdentityService.selectIdentityById(9L)).thenReturn(identity(9L, 5L));
        when(channelIdentityService.confirmBind(9L, "昵称")).thenReturn(1);
        when(channelIdentityService.unbind(9L, "portal")).thenReturn(1);
        AiChannelIdentity noId = new AiChannelIdentity();
        AiChannelIdentity other = identity(8L, 9L);
        AiChannelIdentity own = identity(9L, 5L);
        own.setChannelNickname("昵称");

        try (MockedStatic<SecurityUtils> mocked = mockStatic(SecurityUtils.class);
             MockedStatic<PiiCryptoUtils> pii = mockStatic(PiiCryptoUtils.class))
        {
            mocked.when(SecurityUtils::getLoginUser).thenReturn(loginUser("13800000000", "portal"));
            mocked.when(SecurityUtils::getUsername).thenReturn("portal");
            pii.when(() -> PiiCryptoUtils.blindIndex("13800000000")).thenReturn("idx-1");

            assertEquals("绑定记录ID不能为空",
                    assertThrows(ServiceException.class, () -> controller.confirmBind(noId)).getMessage());
            assertEquals("绑定记录不存在或无权操作",
                    assertThrows(ServiceException.class, () -> controller.confirmBind(other)).getMessage());
            assertEquals(200, controller.confirmBind(own).get(AjaxResult.CODE_TAG));
            assertEquals(200, controller.unbind(own).get(AjaxResult.CODE_TAG));
        }
    }

    @Test
    void 未绑定手机号无法关联服务记录()
    {
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(new MockHttpServletRequest()));
        LoginUser loginUser = loginUser(null, "portal");

        try (MockedStatic<SecurityUtils> mocked = mockStatic(SecurityUtils.class))
        {
            mocked.when(SecurityUtils::getLoginUser).thenReturn(loginUser);
            ServiceException exception = assertThrows(ServiceException.class,
                    () -> controller.myTickets(new AiCallTicket()));
            assertEquals("当前账号未绑定手机号，无法关联服务记录", exception.getMessage());
        }
    }
}
