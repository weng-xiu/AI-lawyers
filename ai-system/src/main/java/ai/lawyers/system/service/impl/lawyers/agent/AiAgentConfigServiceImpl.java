package ai.lawyers.system.service.impl.lawyers.agent;

import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import ai.lawyers.common.utils.sign.SecretCryptoUtils;
import ai.lawyers.system.domain.lawyers.agent.AiAgentConfig;
import ai.lawyers.system.mapper.lawyers.agent.AiAgentConfigMapper;
import ai.lawyers.system.service.lawyers.IAiModelConfigService;
import ai.lawyers.system.service.lawyers.agent.IAiAgentConfigService;

/**
 * AI智能体配置Service实现
 *
 * @author ai-lawyers
 */
@Service
public class AiAgentConfigServiceImpl implements IAiAgentConfigService
{
    @Autowired
    private AiAgentConfigMapper aiAgentConfigMapper;

    @Autowired
    private IAiModelConfigService modelConfigService;

    @Override
    public AiAgentConfig selectAiAgentConfigByAgentId(Long agentId)
    {
        return aiAgentConfigMapper.selectAiAgentConfigByAgentId(agentId);
    }

    @Override
    public List<AiAgentConfig> selectAiAgentConfigList(AiAgentConfig aiAgentConfig)
    {
        return aiAgentConfigMapper.selectAiAgentConfigList(aiAgentConfig);
    }

    @Override
    public List<AiAgentConfig> selectActiveAgentConfigs()
    {
        return aiAgentConfigMapper.selectActiveAgentConfigs();
    }

    @Override
    public int insertAiAgentConfig(AiAgentConfig aiAgentConfig)
    {
        // S6：apiKey 加密落库
        aiAgentConfig.setApiKey(SecretCryptoUtils.encrypt(aiAgentConfig.getApiKey()));
        return aiAgentConfigMapper.insertAiAgentConfig(aiAgentConfig);
    }

    @Override
    public int updateAiAgentConfig(AiAgentConfig aiAgentConfig)
    {
        // S6：回显占位符 ****** 表示未修改，置空由动态 SQL 跳过；否则加密新值
        if (SecretCryptoUtils.isMaskPlaceholder(aiAgentConfig.getApiKey()))
        {
            aiAgentConfig.setApiKey(null);
        }
        else
        {
            aiAgentConfig.setApiKey(SecretCryptoUtils.encrypt(aiAgentConfig.getApiKey()));
        }
        return aiAgentConfigMapper.updateAiAgentConfig(aiAgentConfig);
    }

    @Override
    public int deleteAiAgentConfigByAgentIds(Long[] agentIds)
    {
        return aiAgentConfigMapper.deleteAiAgentConfigByAgentIds(agentIds);
    }

    @Override
    public int deleteAiAgentConfigByAgentId(Long agentId)
    {
        return aiAgentConfigMapper.deleteAiAgentConfigByAgentId(agentId);
    }

    @Override
    public boolean testConnection(Long agentId)
    {
        AiAgentConfig config = aiAgentConfigMapper.selectAiAgentConfigByAgentId(agentId);
        if (config == null)
        {
            return false;
        }
        // 外部平台连通性测试当前以是否配置了接口地址为准；实际 HTTP 探活在 B1 后续 provider 落地时补充
        if (!"local".equalsIgnoreCase(config.getProvider()))
        {
            return config.getApiUrl() != null && !config.getApiUrl().trim().isEmpty();
        }
        // local 模式：测试默认/指定大模型连通性
        try
        {
            if (config.getModelId() != null)
            {
                return modelConfigService.testAiModelConnection(config.getModelId());
            }
            return modelConfigService.getDefaultAiModelConfig() != null
                    && modelConfigService.testAiModelConnection(
                            modelConfigService.getDefaultAiModelConfig().getConfigId());
        }
        catch (Exception e)
        {
            return false;
        }
    }
}
