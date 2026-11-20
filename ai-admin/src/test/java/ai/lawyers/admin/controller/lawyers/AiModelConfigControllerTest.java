package ai.lawyers.admin.controller.lawyers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import ai.lawyers.common.core.domain.AjaxResult;
import ai.lawyers.common.core.page.TableDataInfo;
import ai.lawyers.system.domain.lawyers.AiModelConfig;
import ai.lawyers.system.service.lawyers.IAiModelConfigService;

/**
 * AI模型参数配置接口单元测试
 *
 * @author ai-lawyers
 */
@ExtendWith(MockitoExtension.class)
class AiModelConfigControllerTest
{
    @Mock
    private IAiModelConfigService aiModelConfigService;

    @InjectMocks
    private AiModelConfigController controller;

    @AfterEach
    void tearDown()
    {
        RequestContextHolder.resetRequestAttributes();
    }

    private AiModelConfig config(Long configId)
    {
        AiModelConfig config = new AiModelConfig();
        config.setConfigId(configId);
        config.setApiKey("sk-plain-key");
        return config;
    }

    @Test
    void 列表回显脱敏密钥()
    {
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(new MockHttpServletRequest()));
        AiModelConfig query = new AiModelConfig();
        AiModelConfig config = config(1L);
        List<AiModelConfig> list = new ArrayList<AiModelConfig>();
        list.add(config);
        when(aiModelConfigService.selectAiModelConfigList(query)).thenReturn(list);

        TableDataInfo table = controller.list(query);

        assertEquals(200, table.getCode());
        assertSame(list, table.getRows());
        assertEquals("******", config.getApiKey());
    }

    @Test
    void 导出同样脱敏密钥()
    {
        AiModelConfig config = config(1L);
        when(aiModelConfigService.selectAiModelConfigList(any(AiModelConfig.class)))
                .thenReturn(new ArrayList<AiModelConfig>(List.of(config)));
        MockHttpServletResponse response = new MockHttpServletResponse();

        controller.export(response, new AiModelConfig());

        assertEquals("******", config.getApiKey());
        assertTrue(response.getContentType().contains("spreadsheetml"));
        assertTrue(response.getContentAsByteArray().length > 0);
    }

    @Test
    void 详情回显脱敏且兼容空记录()
    {
        AiModelConfig config = config(1L);
        when(aiModelConfigService.selectAiModelConfigByConfigId(1L)).thenReturn(config);
        when(aiModelConfigService.selectAiModelConfigByConfigId(2L)).thenReturn(null);

        assertSame(config, controller.getInfo(1L).get(AjaxResult.DATA_TAG));
        assertEquals("******", config.getApiKey());
        assertNull(controller.getInfo(2L).get(AjaxResult.DATA_TAG));
    }

    @Test
    void 新增修改与删除配置()
    {
        AiModelConfig config = config(1L);
        Long[] ids = new Long[] { 1L };
        when(aiModelConfigService.insertAiModelConfig(config)).thenReturn(1);
        when(aiModelConfigService.updateAiModelConfig(config)).thenReturn(0);
        when(aiModelConfigService.deleteAiModelConfigByConfigIds(ids)).thenReturn(1);

        assertEquals(200, controller.add(config).get(AjaxResult.CODE_TAG));
        assertEquals(500, controller.edit(config).get(AjaxResult.CODE_TAG));
        assertEquals(200, controller.remove(ids).get(AjaxResult.CODE_TAG));
    }

    @Test
    void 默认配置查询与设置()
    {
        AiModelConfig config = config(1L);
        when(aiModelConfigService.getDefaultAiModelConfig()).thenReturn(config);
        when(aiModelConfigService.setDefaultConfig(1L)).thenReturn(1);

        assertSame(config, controller.getDefault().get(AjaxResult.DATA_TAG));
        assertEquals(200, controller.setDefault(1L).get(AjaxResult.CODE_TAG));
    }

    @Test
    void 连接测试成功与失败()
    {
        when(aiModelConfigService.testAiModelConnection(1L)).thenReturn(true);
        when(aiModelConfigService.testAiModelConnection(2L)).thenReturn(false);

        assertEquals("连接测试成功", controller.test(1L).get(AjaxResult.MSG_TAG));
        assertEquals(500, controller.test(2L).get(AjaxResult.CODE_TAG));
        assertEquals("连接测试失败", controller.test(2L).get(AjaxResult.MSG_TAG));
    }
}
