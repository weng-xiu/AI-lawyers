package ai.lawyers.system.mapper.lawyers;

import java.util.List;
import ai.lawyers.system.domain.lawyers.AiModelConfig;

/**
 * AI模型参数配置Mapper接口
 * 
 * @author ai-lawyers
 * @date 2025-07-15
 */
public interface AiModelConfigMapper 
{
    /**
     * 查询AI模型参数配置
     * 
     * @param configId AI模型参数配置主键
     * @return AI模型参数配置
     */
    public AiModelConfig selectAiModelConfigByConfigId(Long configId);

    /**
     * 查询AI模型参数配置列表
     * 
     * @param aiModelConfig AI模型参数配置
     * @return AI模型参数配置集合
     */
    public List<AiModelConfig> selectAiModelConfigList(AiModelConfig aiModelConfig);

    /**
     * 新增AI模型参数配置
     * 
     * @param aiModelConfig AI模型参数配置
     * @return 结果
     */
    public int insertAiModelConfig(AiModelConfig aiModelConfig);

    /**
     * 修改AI模型参数配置
     * 
     * @param aiModelConfig AI模型参数配置
     * @return 结果
     */
    public int updateAiModelConfig(AiModelConfig aiModelConfig);

    /**
     * 删除AI模型参数配置
     * 
     * @param configId AI模型参数配置主键
     * @return 结果
     */
    public int deleteAiModelConfigByConfigId(Long configId);

    /**
     * 批量删除AI模型参数配置
     * 
     * @param configIds 需要删除的数据主键集合
     * @return 结果
     */
    public int deleteAiModelConfigByConfigIds(Long[] configIds);

    /**
     * 查询默认的AI模型配置
     * 
     * @return AI模型参数配置
     */
    public AiModelConfig selectDefaultAiModelConfig();

    /**
     * 更新默认配置状态
     * 
     * @param configId 配置ID
     * @return 结果
     */
    public int updateDefaultConfig(Long configId);
}