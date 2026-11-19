package ai.lawyers.web.controller.lawyers.agent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
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
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import ai.lawyers.common.core.domain.AjaxResult;
import ai.lawyers.common.core.domain.entity.SysUser;
import ai.lawyers.common.core.domain.model.LoginUser;
import ai.lawyers.common.core.page.TableDataInfo;
import ai.lawyers.common.utils.SecurityUtils;
import ai.lawyers.common.utils.sign.SecretCryptoUtils;
import ai.lawyers.system.domain.lawyers.agent.AiAgentConfig;
import ai.lawyers.system.service.lawyers.agent.IAiAgentConfigService;

/**
 * AI 智能体配置接口单元测试
 *
 * @author ai-lawyers
 */
@ExtendWith(MockitoExtension.class)
class AiAgentConfigControllerTest
{
    @Mock
    private IAiAgentConfigService aiAgentConfigService;

    @InjectMocks
    private AiAgentConfigController controller;

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
    void 列表对APIKey脱敏回显()
    {
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(new MockHttpServletRequest()));
        AiAgentConfig config = new AiAgentConfig();
        config.setApiKey("sk-raw");
        List<AiAgentConfig> list = new ArrayList<AiAgentConfig>();
        list.add(config);
        when(aiAgentConfigService.selectAiAgentConfigList(any(AiAgentConfig.class))).thenReturn(list);
        try (MockedStatic<SecretCryptoUtils> mocked = mockStatic(SecretCryptoUtils.class))
        {
            mocked.when(() -> SecretCryptoUtils.mask("sk-raw")).thenReturn("******");

            TableDataInfo table = controller.list(new AiAgentConfig());

            assertEquals(1, table.getTotal());
            assertEquals("******", config.getApiKey());
        }
    }

    @Test
    void 导出前对APIKey脱敏()
    {
        AiAgentConfig config = new AiAgentConfig();
        config.setApiKey("sk-export");
        when(aiAgentConfigService.selectAiAgentConfigList(any(AiAgentConfig.class)))
                .thenReturn(List.of(config));
        MockHttpServletResponse response = new MockHttpServletResponse();
        try (MockedStatic<SecretCryptoUtils> mocked = mockStatic(SecretCryptoUtils.class))
        {
            mocked.when(() -> SecretCryptoUtils.mask("sk-export")).thenReturn("******");

            controller.export(response, new AiAgentConfig());
        }
        assertEquals("******", config.getApiKey());
        assertTrue(response.getContentType().contains("spreadsheetml"));
    }

    @Test
    void 详情脱敏且为空时安全返回()
    {
        AiAgentConfig config = new AiAgentConfig();
        config.setApiKey("sk-detail");
        when(aiAgentConfigService.selectAiAgentConfigByAgentId(3L)).thenReturn(config);
        when(aiAgentConfigService.selectAiAgentConfigByAgentId(4L)).thenReturn(null);
        try (MockedStatic<SecretCryptoUtils> mocked = mockStatic(SecretCryptoUtils.class))
        {
            mocked.when(() -> SecretCryptoUtils.mask("sk-detail")).thenReturn("******");

            assertSame(config, controller.getInfo(3L).get(AjaxResult.DATA_TAG));
            assertEquals("******", config.getApiKey());
            assertNull(controller.getInfo(4L).get(AjaxResult.DATA_TAG));
        }
    }

    @Test
    void 新增与修改写入操作人()
    {
        AiAgentConfig config = new AiAgentConfig();
        when(aiAgentConfigService.insertAiAgentConfig(config)).thenReturn(1);
        when(aiAgentConfigService.updateAiAgentConfig(config)).thenReturn(0);

        try (MockedStatic<SecurityUtils> mocked = mockStatic(SecurityUtils.class))
        {
            mocked.when(SecurityUtils::getLoginUser).thenReturn(loginUser("admin"));
            assertEquals(200, controller.add(config).get(AjaxResult.CODE_TAG));
            assertEquals("admin", config.getCreateBy());
            assertEquals(500, controller.edit(config).get(AjaxResult.CODE_TAG));
        }
        assertEquals("admin", config.getUpdateBy());
    }

    @Test
    void 批量删除返回影响行数()
    {
        Long[] agentIds = new Long[] { 1L, 2L };
        when(aiAgentConfigService.deleteAiAgentConfigByAgentIds(agentIds)).thenReturn(2);

        assertEquals(200, controller.remove(agentIds).get(AjaxResult.CODE_TAG));
        verify(aiAgentConfigService).deleteAiAgentConfigByAgentIds(agentIds);
    }

    @Test
    void 启用智能体列表脱敏()
    {
        AiAgentConfig config = new AiAgentConfig();
        config.setApiKey("sk-active");
        List<AiAgentConfig> list = new ArrayList<AiAgentConfig>();
        list.add(config);
        when(aiAgentConfigService.selectActiveAgentConfigs()).thenReturn(list);
        try (MockedStatic<SecretCryptoUtils> mocked = mockStatic(SecretCryptoUtils.class))
        {
            mocked.when(() -> SecretCryptoUtils.mask("sk-active")).thenReturn("******");

            AjaxResult result = controller.active();

            assertSame(list, result.get(AjaxResult.DATA_TAG));
            assertEquals("******", config.getApiKey());
        }
    }

    @Test
    void 连接测试按结果返回提示()
    {
        when(aiAgentConfigService.testConnection(3L)).thenReturn(true);
        when(aiAgentConfigService.testConnection(4L)).thenReturn(false);

        assertEquals("连接正常", controller.test(3L).get(AjaxResult.MSG_TAG));
        assertEquals(500, controller.test(4L).get(AjaxResult.CODE_TAG));
        assertEquals("连接失败，请检查模型配置或接口地址", controller.test(4L).get(AjaxResult.MSG_TAG));
    }
}
