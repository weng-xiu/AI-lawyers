package ai.lawyers.system.service.impl.lawyers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.test.util.ReflectionTestUtils;
import ai.lawyers.common.exception.ServiceException;
import ai.lawyers.system.domain.lawyers.AiChannelIdentity;
import ai.lawyers.system.mapper.lawyers.AiChannelIdentityMapper;

/**
 * 多渠道身份绑定服务单测。
 *
 * <p>覆盖身份查询、发起绑定的入参校验与重复/转移/同档案分支、确认绑定以及解绑的记录校验。</p>
 *
 * @author ai-lawyers
 */
class AiChannelIdentityServiceImplTest
{
    private AiChannelIdentityServiceImpl service;

    @Mock private AiChannelIdentityMapper channelIdentityMapper;

    @BeforeEach
    void setUp()
    {
        MockitoAnnotations.openMocks(this);
        service = new AiChannelIdentityServiceImpl();
        ReflectionTestUtils.setField(service, "channelIdentityMapper", channelIdentityMapper);
    }

    private static AiChannelIdentity identity(Long id, Long profileId, String bindStatus, String nickname)
    {
        AiChannelIdentity identity = new AiChannelIdentity();
        identity.setId(id);
        identity.setProfileId(profileId);
        identity.setBindStatus(bindStatus);
        identity.setChannelNickname(nickname);
        return identity;
    }

    @Test
    void selectIdentityById_delegates()
    {
        AiChannelIdentity expected = new AiChannelIdentity();
        when(channelIdentityMapper.selectAiChannelIdentityById(1L)).thenReturn(expected);

        assertSame(expected, service.selectIdentityById(1L));
    }

    @Test
    void selectIdentityList_delegates()
    {
        AiChannelIdentity query = new AiChannelIdentity();
        List<AiChannelIdentity> list = Collections.singletonList(new AiChannelIdentity());
        when(channelIdentityMapper.selectAiChannelIdentityList(query)).thenReturn(list);

        assertSame(list, service.selectIdentityList(query));
    }

    @Test
    void selectBoundByProfileId_delegates()
    {
        List<AiChannelIdentity> list = Collections.singletonList(new AiChannelIdentity());
        when(channelIdentityMapper.selectBoundByProfileId(1L)).thenReturn(list);

        assertSame(list, service.selectBoundByProfileId(1L));
    }

    @Test
    void requestBind_missingProfileId_throws()
    {
        assertEquals("来电档案ID不能为空",
            assertThrows(ServiceException.class,
                () -> service.requestBind(null, "WECHAT", "uid", "昵称", "admin")).getMessage());
    }

    @Test
    void requestBind_missingChannelTypeOrUid_throws()
    {
        assertEquals("渠道类型与渠道标识不能为空",
            assertThrows(ServiceException.class,
                () -> service.requestBind(1L, "", "uid", "昵称", "admin")).getMessage());
        assertEquals("渠道类型与渠道标识不能为空",
            assertThrows(ServiceException.class,
                () -> service.requestBind(1L, "WECHAT", "", "昵称", "admin")).getMessage());
    }

    @Test
    void requestBind_boundByOtherProfile_throws()
    {
        when(channelIdentityMapper.selectByChannelUid("WECHAT", "uid"))
            .thenReturn(identity(9L, 2L, "0", "旧昵称"));

        assertEquals("该渠道身份已绑定其他档案，请先解绑后再绑定",
            assertThrows(ServiceException.class,
                () -> service.requestBind(1L, "WECHAT", "uid", "昵称", "admin")).getMessage());
        verify(channelIdentityMapper, never()).insertAiChannelIdentity(any());
    }

    @Test
    void requestBind_sameProfile_refreshesNicknameAndReturnsId()
    {
        when(channelIdentityMapper.selectByChannelUid("WECHAT", "uid"))
            .thenReturn(identity(9L, 1L, "0", "旧昵称"));

        assertEquals(9L, service.requestBind(1L, "WECHAT", "uid", "新昵称", "admin"));
        verify(channelIdentityMapper).refreshNickname(9L, "新昵称");
    }

    @Test
    void requestBind_sameProfile_sameNickname_skipsRefresh()
    {
        when(channelIdentityMapper.selectByChannelUid("WECHAT", "uid"))
            .thenReturn(identity(9L, 1L, "0", "昵称"));

        assertEquals(9L, service.requestBind(1L, "WECHAT", "uid", "昵称", "admin"));
        verify(channelIdentityMapper, never()).refreshNickname(any(), anyString());
    }

    @Test
    void requestBind_unboundOtherProfile_transfersOwnership()
    {
        when(channelIdentityMapper.selectByChannelUid("WECHAT", "uid"))
            .thenReturn(identity(9L, 2L, "1", "旧昵称"));

        assertEquals(9L, service.requestBind(1L, "WECHAT", "uid", "昵称", "admin"));
        verify(channelIdentityMapper).transferOwnership(9L, 1L, "昵称", "admin");
        verify(channelIdentityMapper, never()).insertAiChannelIdentity(any());
    }

    @Test
    void requestBind_noExisting_insertsPendingRecord()
    {
        when(channelIdentityMapper.selectByChannelUid("WECHAT", "uid")).thenReturn(null);
        when(channelIdentityMapper.insertAiChannelIdentity(any(AiChannelIdentity.class))).thenAnswer(invocation -> {
            AiChannelIdentity saved = invocation.getArgument(0);
            saved.setId(11L);
            return 1;
        });

        assertEquals(11L, service.requestBind(1L, "WECHAT", "uid", "昵称", "admin"));

        ArgumentCaptor<AiChannelIdentity> captor = ArgumentCaptor.forClass(AiChannelIdentity.class);
        verify(channelIdentityMapper).insertAiChannelIdentity(captor.capture());
        AiChannelIdentity saved = captor.getValue();
        assertEquals(1L, saved.getProfileId());
        assertEquals("WECHAT", saved.getChannelType());
        assertEquals("uid", saved.getChannelUid());
        assertEquals("昵称", saved.getChannelNickname());
        assertEquals("1", saved.getBindStatus());
        assertEquals("admin", saved.getCreateBy());
    }

    @Test
    void confirmBind_missingRecord_throws()
    {
        when(channelIdentityMapper.selectAiChannelIdentityById(1L)).thenReturn(null);

        assertEquals("绑定记录不存在",
            assertThrows(ServiceException.class, () -> service.confirmBind(1L, "昵称")).getMessage());
    }

    @Test
    void confirmBind_exists_delegates()
    {
        when(channelIdentityMapper.selectAiChannelIdentityById(1L)).thenReturn(identity(1L, 1L, "1", "昵称"));
        when(channelIdentityMapper.confirmBind(eq(1L), eq("新昵称"), any())).thenReturn(1);

        assertEquals(1, service.confirmBind(1L, "新昵称"));
    }

    @Test
    void unbind_missingRecord_throws()
    {
        when(channelIdentityMapper.selectAiChannelIdentityById(1L)).thenReturn(null);

        assertEquals("绑定记录不存在",
            assertThrows(ServiceException.class, () -> service.unbind(1L, "admin")).getMessage());
    }

    @Test
    void unbind_exists_delegates()
    {
        when(channelIdentityMapper.selectAiChannelIdentityById(1L)).thenReturn(identity(1L, 1L, "0", "昵称"));
        when(channelIdentityMapper.unbind(eq(1L), any(), eq("admin"))).thenReturn(1);

        assertEquals(1, service.unbind(1L, "admin"));
    }
}
