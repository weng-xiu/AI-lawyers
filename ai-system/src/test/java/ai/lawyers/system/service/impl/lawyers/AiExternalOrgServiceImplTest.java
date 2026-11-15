package ai.lawyers.system.service.impl.lawyers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.test.util.ReflectionTestUtils;
import ai.lawyers.common.exception.ServiceException;
import ai.lawyers.system.domain.lawyers.AiExternalOrg;
import ai.lawyers.system.mapper.lawyers.AiExternalOrgMapper;

/**
 * 协同外部机构台账 Service 单测。
 *
 * <p>覆盖公众端目录的启用态强制与凭证/接口地址脱敏、保存校验（名称/条线/API 地址）
 * 与新增默认值、其余透传方法。</p>
 *
 * @author ai-lawyers
 */
class AiExternalOrgServiceImplTest
{
    private AiExternalOrgServiceImpl service;

    @Mock private AiExternalOrgMapper orgMapper;

    @BeforeEach
    void setUp()
    {
        MockitoAnnotations.openMocks(this);
        service = new AiExternalOrgServiceImpl();
        ReflectionTestUtils.setField(service, "aiExternalOrgMapper", orgMapper);
    }

    @Test
    void selectQueries_delegate()
    {
        AiExternalOrg org = new AiExternalOrg();
        List<AiExternalOrg> list = Collections.singletonList(org);
        when(orgMapper.selectAiExternalOrgByOrgId(1L)).thenReturn(org);
        when(orgMapper.selectAiExternalOrgList(any())).thenReturn(list);

        assertSame(org, service.selectAiExternalOrgByOrgId(1L));
        assertEquals(list, service.selectAiExternalOrgList(new AiExternalOrg()));
    }

    @Test
    void portal_nullQuery_forcesEnabledStatus()
    {
        AiExternalOrg returned = new AiExternalOrg();
        returned.setAccessMode("MANUAL");
        when(orgMapper.selectAiExternalOrgList(any()))
                .thenReturn(Collections.singletonList(returned));

        List<AiExternalOrg> list = service.selectEnabledOrgsForPortal(null);

        ArgumentCaptor<AiExternalOrg> captor = ArgumentCaptor.forClass(AiExternalOrg.class);
        verify(orgMapper).selectAiExternalOrgList(captor.capture());
        assertEquals("0", captor.getValue().getStatus());
        assertSame(returned, list.get(0));
    }

    @Test
    void portal_masksCredentialsAndNonManualApiUrl()
    {
        AiExternalOrg api = new AiExternalOrg();
        api.setAccessMode("API");
        api.setApiUrl("https://partner.example.com/api");
        api.setAppId("app-1");
        api.setAppSecret("secret-1");
        AiExternalOrg manual = new AiExternalOrg();
        manual.setAccessMode("MANUAL");
        manual.setApiUrl("https://manual.example.com");
        manual.setAppId("app-2");
        manual.setAppSecret("secret-2");
        when(orgMapper.selectAiExternalOrgList(any())).thenReturn(Arrays.asList(api, manual));

        service.selectEnabledOrgsForPortal(new AiExternalOrg());

        assertNull(api.getAppSecret());
        assertNull(api.getAppId());
        assertNull(api.getApiUrl());
        assertNull(manual.getAppSecret());
        assertNull(manual.getAppId());
        assertEquals("https://manual.example.com", manual.getApiUrl());
    }

    @Test
    void insert_fillsDefaultsAndDelegates()
    {
        AiExternalOrg org = validOrg();
        when(orgMapper.insertAiExternalOrg(org)).thenReturn(1);

        assertEquals(1, service.insertAiExternalOrg(org));

        assertEquals("0", org.getStatus());
        assertEquals("MANUAL", org.getAccessMode());
    }

    @Test
    void insert_keepsProvidedStatusAndAccessMode()
    {
        AiExternalOrg org = validOrg();
        org.setStatus("1");
        org.setAccessMode("API");
        org.setApiUrl("https://partner.example.com/api");
        when(orgMapper.insertAiExternalOrg(org)).thenReturn(1);

        service.insertAiExternalOrg(org);

        assertEquals("1", org.getStatus());
        assertEquals("API", org.getAccessMode());
    }

    @Test
    void insert_nullOrg_throws()
    {
        assertThrows(ServiceException.class, () -> service.insertAiExternalOrg(null));
        verify(orgMapper, never()).insertAiExternalOrg(any());
    }

    @Test
    void insert_missingOrgNameOrType_throws()
    {
        AiExternalOrg noName = validOrg();
        noName.setOrgName(" ");
        assertThrows(ServiceException.class, () -> service.insertAiExternalOrg(noName));

        AiExternalOrg noType = validOrg();
        noType.setExternalType(null);
        assertThrows(ServiceException.class, () -> service.insertAiExternalOrg(noType));

        verify(orgMapper, never()).insertAiExternalOrg(any());
    }

    @Test
    void insert_apiModeWithoutUrl_throws()
    {
        AiExternalOrg org = validOrg();
        org.setAccessMode("API");

        assertThrows(ServiceException.class, () -> service.insertAiExternalOrg(org));
        verify(orgMapper, never()).insertAiExternalOrg(any());
    }

    @Test
    void update_missingId_throws()
    {
        assertThrows(ServiceException.class, () -> service.updateAiExternalOrg(validOrg()));
        verify(orgMapper, never()).updateAiExternalOrg(any());
    }

    @Test
    void update_valid_delegates()
    {
        AiExternalOrg org = validOrg();
        org.setOrgId(7L);
        when(orgMapper.updateAiExternalOrg(org)).thenReturn(1);

        assertEquals(1, service.updateAiExternalOrg(org));
        assertNotNull(org.getOrgName());
    }

    @Test
    void delete_delegates()
    {
        Long[] ids = {1L, 2L};
        when(orgMapper.deleteAiExternalOrgByOrgIds(ids)).thenReturn(2);

        assertEquals(2, service.deleteAiExternalOrgByOrgIds(ids));
    }

    private AiExternalOrg validOrg()
    {
        AiExternalOrg org = new AiExternalOrg();
        org.setOrgName("市法律援助中心");
        org.setExternalType("LEGAL_AID");
        return org;
    }
}
