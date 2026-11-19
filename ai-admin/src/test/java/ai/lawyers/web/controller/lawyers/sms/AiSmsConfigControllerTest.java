package ai.lawyers.web.controller.lawyers.sms;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;
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
import ai.lawyers.common.utils.SecurityUtils;
import ai.lawyers.common.utils.sign.SecretCryptoUtils;
import ai.lawyers.system.domain.lawyers.sms.AiSmsConfig;
import ai.lawyers.system.mapper.lawyers.sms.AiSmsConfigMapper;

/**
 * 短信通道配置接口单元测试
 *
 * @author ai-lawyers
 */
@ExtendWith(MockitoExtension.class)
class AiSmsConfigControllerTest
{
    @Mock
    private AiSmsConfigMapper aiSmsConfigMapper;

    @InjectMocks
    private AiSmsConfigController controller;

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
    void 列表对密钥脱敏回显()
    {
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(new MockHttpServletRequest()));
        AiSmsConfig config = new AiSmsConfig();
        config.setAccessKeySecret("plain-secret");
        List<AiSmsConfig> list = new ArrayList<AiSmsConfig>();
        list.add(config);
        when(aiSmsConfigMapper.selectAiSmsConfigList(any(AiSmsConfig.class))).thenReturn(list);
        try (MockedStatic<SecretCryptoUtils> mocked = mockStatic(SecretCryptoUtils.class))
        {
            mocked.when(() -> SecretCryptoUtils.mask("plain-secret")).thenReturn("******");

            TableDataInfo table = controller.list(new AiSmsConfig());

            assertEquals(1, table.getTotal());
            assertEquals("******", config.getAccessKeySecret());
        }
    }

    @Test
    void 详情为空时不抛异常()
    {
        when(aiSmsConfigMapper.selectAiSmsConfigByConfigId(9L)).thenReturn(null);

        AjaxResult result = controller.getInfo(9L);

        assertNull(result.get(AjaxResult.DATA_TAG));
    }

    @Test
    void 新增加密密钥并写入操作人()
    {
        AiSmsConfig config = new AiSmsConfig();
        config.setAccessKeySecret("raw-secret");
        when(aiSmsConfigMapper.insertAiSmsConfig(any(AiSmsConfig.class))).thenReturn(1);
        try (MockedStatic<SecurityUtils> security = mockStatic(SecurityUtils.class);
                MockedStatic<SecretCryptoUtils> crypto = mockStatic(SecretCryptoUtils.class))
        {
            security.when(SecurityUtils::getLoginUser).thenReturn(loginUser("admin"));
            crypto.when(() -> SecretCryptoUtils.encrypt("raw-secret")).thenReturn("enc-secret");

            AjaxResult result = controller.add(config);

            assertEquals(200, result.get(AjaxResult.CODE_TAG));
        }
        assertEquals("admin", config.getCreateBy());
        assertEquals("enc-secret", config.getAccessKeySecret());
    }

    @Test
    void 修改时占位符不覆盖密钥()
    {
        AiSmsConfig config = new AiSmsConfig();
        config.setAccessKeySecret("******");
        when(aiSmsConfigMapper.updateAiSmsConfig(any(AiSmsConfig.class))).thenReturn(1);
        try (MockedStatic<SecurityUtils> security = mockStatic(SecurityUtils.class);
                MockedStatic<SecretCryptoUtils> crypto = mockStatic(SecretCryptoUtils.class))
        {
            security.when(SecurityUtils::getLoginUser).thenReturn(loginUser("admin"));
            crypto.when(() -> SecretCryptoUtils.isMaskPlaceholder("******")).thenReturn(true);

            controller.edit(config);
        }
        assertNull(config.getAccessKeySecret());
        assertEquals("admin", config.getUpdateBy());
    }

    @Test
    void 修改时新密钥加密落库()
    {
        AiSmsConfig config = new AiSmsConfig();
        config.setAccessKeySecret("new-secret");
        when(aiSmsConfigMapper.updateAiSmsConfig(any(AiSmsConfig.class))).thenReturn(0);
        try (MockedStatic<SecurityUtils> security = mockStatic(SecurityUtils.class);
                MockedStatic<SecretCryptoUtils> crypto = mockStatic(SecretCryptoUtils.class))
        {
            security.when(SecurityUtils::getLoginUser).thenReturn(loginUser("admin"));
            crypto.when(() -> SecretCryptoUtils.isMaskPlaceholder("new-secret")).thenReturn(false);
            crypto.when(() -> SecretCryptoUtils.encrypt("new-secret")).thenReturn("enc-new");

            AjaxResult result = controller.edit(config);

            assertEquals(500, result.get(AjaxResult.CODE_TAG));
        }
        assertEquals("enc-new", config.getAccessKeySecret());
    }

    @Test
    void 启用通道只查状态为启用的配置()
    {
        List<AiSmsConfig> list = new ArrayList<AiSmsConfig>();
        AiSmsConfig config = new AiSmsConfig();
        config.setAccessKeySecret("secret");
        list.add(config);
        when(aiSmsConfigMapper.selectAiSmsConfigList(any(AiSmsConfig.class))).thenReturn(list);
        try (MockedStatic<SecretCryptoUtils> mocked = mockStatic(SecretCryptoUtils.class))
        {
            mocked.when(() -> SecretCryptoUtils.mask("secret")).thenReturn("******");

            AjaxResult result = controller.enabled();

            assertSame(list, result.get(AjaxResult.DATA_TAG));
        }
        ArgumentCaptor<AiSmsConfig> captor = ArgumentCaptor.forClass(AiSmsConfig.class);
        verify(aiSmsConfigMapper).selectAiSmsConfigList(captor.capture());
        assertEquals("1", captor.getValue().getStatus());
    }

    @Test
    void 批量删除返回影响行数()
    {
        Long[] configIds = new Long[] { 3L, 4L };
        when(aiSmsConfigMapper.deleteAiSmsConfigByConfigIds(configIds)).thenReturn(2);

        AjaxResult result = controller.remove(configIds);

        assertEquals(200, result.get(AjaxResult.CODE_TAG));
        verify(aiSmsConfigMapper).deleteAiSmsConfigByConfigIds(configIds);
    }
}
