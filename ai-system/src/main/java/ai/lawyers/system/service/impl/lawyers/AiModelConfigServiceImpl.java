package ai.lawyers.system.service.impl.lawyers;

import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import ai.lawyers.system.mapper.lawyers.AiModelConfigMapper;
import ai.lawyers.system.domain.lawyers.AiModelConfig;
import ai.lawyers.system.service.lawyers.IAiModelConfigService;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * AI模型参数配置Service业务层处理
 * 
 * @author ai-lawyers
 * @date 2025-07-15
 */
@Service
public class AiModelConfigServiceImpl implements IAiModelConfigService 
{
    @Autowired
    private AiModelConfigMapper aiModelConfigMapper;

    /**
     * 查询AI模型参数配置
     * 
     * @param configId AI模型参数配置主键
     * @return AI模型参数配置
     */
    @Override
    public AiModelConfig selectAiModelConfigByConfigId(Long configId)
    {
        return aiModelConfigMapper.selectAiModelConfigByConfigId(configId);
    }

    /**
     * 查询AI模型参数配置列表
     * 
     * @param aiModelConfig AI模型参数配置
     * @return AI模型参数配置
     */
    @Override
    public List<AiModelConfig> selectAiModelConfigList(AiModelConfig aiModelConfig)
    {
        return aiModelConfigMapper.selectAiModelConfigList(aiModelConfig);
    }

    /**
     * 新增AI模型参数配置
     * 
     * @param aiModelConfig AI模型参数配置
     * @return 结果
     */
    @Override
    public int insertAiModelConfig(AiModelConfig aiModelConfig)
    {
        // 如果设置为默认配置，先将其他配置设为非默认
        if ("1".equals(aiModelConfig.getIsDefault())) {
            aiModelConfigMapper.updateDefaultConfig(aiModelConfig.getConfigId());
        }
        return aiModelConfigMapper.insertAiModelConfig(aiModelConfig);
    }

    /**
     * 修改AI模型参数配置
     * 
     * @param aiModelConfig AI模型参数配置
     * @return 结果
     */
    @Override
    public int updateAiModelConfig(AiModelConfig aiModelConfig)
    {
        // 如果设置为默认配置，先将其他配置设为非默认
        if ("1".equals(aiModelConfig.getIsDefault())) {
            aiModelConfigMapper.updateDefaultConfig(aiModelConfig.getConfigId());
        }
        return aiModelConfigMapper.updateAiModelConfig(aiModelConfig);
    }

    /**
     * 批量删除AI模型参数配置
     * 
     * @param configIds 需要删除的AI模型参数配置主键
     * @return 结果
     */
    @Override
    public int deleteAiModelConfigByConfigIds(Long[] configIds)
    {
        return aiModelConfigMapper.deleteAiModelConfigByConfigIds(configIds);
    }

    /**
     * 删除AI模型参数配置信息
     * 
     * @param configId AI模型参数配置主键
     * @return 结果
     */
    @Override
    public int deleteAiModelConfigByConfigId(Long configId)
    {
        return aiModelConfigMapper.deleteAiModelConfigByConfigId(configId);
    }

    /**
     * 获取默认的AI模型配置
     * 
     * @return AI模型参数配置
     */
    @Override
    public AiModelConfig getDefaultAiModelConfig()
    {
        return aiModelConfigMapper.selectDefaultAiModelConfig();
    }

    /**
     * 设置默认配置
     * 
     * @param configId 配置ID
     * @return 结果
     */
    @Override
    public int setDefaultConfig(Long configId)
    {
        return aiModelConfigMapper.updateDefaultConfig(configId);
    }

    /**
     * 测试AI模型连接
     * 
     * @param configId 配置ID
     * @return 测试结果
     */
    @Override
    public boolean testAiModelConnection(Long configId)
    {
        AiModelConfig config = aiModelConfigMapper.selectAiModelConfigByConfigId(configId);
        if (config == null) {
            return false;
        }
        
        // 这里可以实现实际的连接测试逻辑
        // 例如发送一个简单的测试请求到AI模型API
        // 目前简单返回true，表示连接成功
        return true;
    }
}