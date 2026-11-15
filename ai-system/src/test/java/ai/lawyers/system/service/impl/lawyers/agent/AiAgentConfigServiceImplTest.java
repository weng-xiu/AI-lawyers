package ai.lawyers.system.service.impl.lawyers.agent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.test.util.ReflectionTestUtils;
import ai.lawyers.system.domain.lawyers.agent.AiAgentConfig;
import ai.lawyers.system.domain.lawyers.AiModelConfig;
import ai.lawyers.system.mapper.lawyers.agent.AiAgentConfigMapper;
import ai.lawyers.system.service.lawyers.IAiModelConfigService;

/**
 * AI 智能体配置服务单测。
 *
 * <p>覆盖配置查询与删除委托、apiKey 新增加密与修改时占位符跳过，
 * 以及连通性测试在本地模型、外部平台与异常场景下的判定。</p>
 *
 * @author ai-lawyers
 */
class AiAgentConfigServiceImplTest
{
    private AiAgentConfigServiceImpl service;

    @Mock private AiAgentConfigMapper aiAgentConfigMapper;
    @Mock private IAiModelConfigService modelConfigService;

    @BeforeEach
    void setUp()
    {
        MockitoAnnotations.openMocks(this);
        service = new AiAgentConfigServiceImpl();
        ReflectionTestUtils.setField(service, "aiAgentConfigMapper", aiAgentConfigMapper);
        ReflectionTestUtils.setField(service, "modelConfigService", modelConfigService);
    }

    private static AiAgentConfig config(String provider, String apiUrl, Long modelId)
    {
        AiAgentConfig config = new AiAgentConfig();
        config.setProvider(provider);
        config.setApiUrl(apiUrl);
        config.setModelId(modelId);
        return config;
    }

    @Test
    void selectAiAgentConfigByAgentId_delegates()
    {
        AiAgentConfig expected = new AiAgentConfig();
        when(aiAgentConfigMapper.selectAiAgentConfigByAgentId(1L)).thenReturn(expected);

        assertSame(expected, service.selectAiAgentConfigByAgentId(1L));
    }

    @Test
    void selectAiAgentConfigList_delegates()
    {
        AiAgentConfig query = new AiAgentConfig();
        List<AiAgentConfig> list = Collections.singletonList(new AiAgentConfig());
        when(aiAgentConfigMapper.selectAiAgentConfigList(query)).thenReturn(list);

        assertSame(list, service.selectAiAgentConfigList(query));
    }

    @Test
    void selectActiveAgentConfigs_delegates()
    {
        List<AiAgentConfig> list = Collections.singletonList(new AiAgentConfig());
        when(aiAgentConfigMapper.selectActiveAgentConfigs()).thenReturn(list);

        assertSame(list, service.selectActiveAgentConfigs());
    }

    @Test
    void insertAiAgentConfig_encryptsApiKey()
    {
        AiAgentConfig config = new AiAgentConfig();
        config.setApiKey("plain-key");
        when(aiAgentConfigMapper.insertAiAgentConfig(config)).thenReturn(1);

        assertEquals(1, service.insertAiAgentConfig(config));
        assertTrue(config.getApiKey().startsWith("enc3:"));
    }

    @Test
    void insertAiAgentConfig_nullApiKey_keepsNull()
    {
        AiAgentConfig config = new AiAgentConfig();
        when(aiAgentConfigMapper.insertAiAgentConfig(config)).thenReturn(1);

        assertEquals(1, service.insertAiAgentConfig(config));
        assertNull(config.getApiKey());
    }

    @Test
    void updateAiAgentConfig_maskPlaceholder_clearsApiKey()
    {
        AiAgentConfig config = new AiAgentConfig();
        config.setApiKey("******");
        when(aiAgentConfigMapper.updateAiAgentConfig(config)).thenReturn(1);

        assertEquals(1, service.updateAiAgentConfig(config));
        assertNull(config.getApiKey());
    }

    @Test
    void updateAiAgentConfig_newKey_encrypts()
    {
        AiAgentConfig config = new AiAgentConfig();
        config.setApiKey("plain-key");
        when(aiAgentConfigMapper.updateAiAgentConfig(config)).thenReturn(1);

        assertEquals(1, service.updateAiAgentConfig(config));
        assertTrue(config.getApiKey().startsWith("enc3:"));
    }

    @Test
    void deleteAiAgentConfigByAgentIds_delegates()
    {
        Long[] ids = new Long[] { 1L, 2L };
        when(aiAgentConfigMapper.deleteAiAgentConfigByAgentIds(ids)).thenReturn(2);

        assertEquals(2, service.deleteAiAgentConfigByAgentIds(ids));
    }

    @Test
    void deleteAiAgentConfigByAgentId_delegates()
    {
        when(aiAgentConfigMapper.deleteAiAgentConfigByAgentId(1L)).thenReturn(1);

        assertEquals(1, service.deleteAiAgentConfigByAgentId(1L));
    }

    @Test
    void testConnection_missingConfig_returnsFalse()
    {
        when(aiAgentConfigMapper.selectAiAgentConfigByAgentId(1L)).thenReturn(null);

        assertFalse(service.testConnection(1L));
    }

    @Test
    void testConnection_externalProvider_dependsOnApiUrl()
    {
        when(aiAgentConfigMapper.selectAiAgentConfigByAgentId(1L))
            .thenReturn(config("DIFY", " https://api ", null));
        when(aiAgentConfigMapper.selectAiAgentConfigByAgentId(2L))
            .thenReturn(config("DIFY", "   ", null));

        assertTrue(service.testConnection(1L));
        assertFalse(service.testConnection(2L));
    }

    @Test
    void testConnection_localWithModel_delegatesToModelService()
    {
        when(aiAgentConfigMapper.selectAiAgentConfigByAgentId(1L))
            .thenReturn(config("local", null, 5L));
        when(modelConfigService.testAiModelConnection(5L)).thenReturn(true);

        assertTrue(service.testConnection(1L));
        verify(modelConfigService).testAiModelConnection(5L);
    }

    @Test
    void testConnection_localWithoutModel_noDefault_returnsFalse()
    {
        when(aiAgentConfigMapper.selectAiAgentConfigByAgentId(1L))
            .thenReturn(config("LOCAL", null, null));
        when(modelConfigService.getDefaultAiModelConfig()).thenReturn(null);

        assertFalse(service.testConnection(1L));
    }

    @Test
    void testConnection_localWithoutModel_usesDefault()
    {
        AiModelConfig defaultConfig = new AiModelConfig();
        defaultConfig.setConfigId(8L);
        when(aiAgentConfigMapper.selectAiAgentConfigByAgentId(1L))
            .thenReturn(config("local", null, null));
        when(modelConfigService.getDefaultAiModelConfig()).thenReturn(defaultConfig);
        when(modelConfigService.testAiModelConnection(8L)).thenReturn(true);

        assertTrue(service.testConnection(1L));
    }

    @Test
    void testConnection_localThrows_returnsFalse()
    {
        when(aiAgentConfigMapper.selectAiAgentConfigByAgentId(1L))
            .thenReturn(config("local", null, 5L));
        when(modelConfigService.testAiModelConnection(5L)).thenThrow(new IllegalStateException("down"));

        assertFalse(service.testConnection(1L));
    }
}
