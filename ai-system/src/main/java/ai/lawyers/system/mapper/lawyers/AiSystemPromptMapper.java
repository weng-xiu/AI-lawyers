package ai.lawyers.system.mapper.lawyers;

import java.util.List;
import ai.lawyers.system.domain.lawyers.AiSystemPrompt;

/**
 * 系统提示词配置Mapper接口
 * 
 * @author ai-lawyers
 * @date 2025-07-15
 */
public interface AiSystemPromptMapper 
{
    /**
     * 查询系统提示词配置
     * 
     * @param promptId 系统提示词配置主键
     * @return 系统提示词配置
     */
    public AiSystemPrompt selectAiSystemPromptByPromptId(Long promptId);

    /**
     * 查询系统提示词配置列表
     * 
     * @param aiSystemPrompt 系统提示词配置
     * @return 系统提示词配置集合
     */
    public List<AiSystemPrompt> selectAiSystemPromptList(AiSystemPrompt aiSystemPrompt);

    /**
     * 新增系统提示词配置
     * 
     * @param aiSystemPrompt 系统提示词配置
     * @return 结果
     */
    public int insertAiSystemPrompt(AiSystemPrompt aiSystemPrompt);

    /**
     * 修改系统提示词配置
     * 
     * @param aiSystemPrompt 系统提示词配置
     * @return 结果
     */
    public int updateAiSystemPrompt(AiSystemPrompt aiSystemPrompt);

    /**
     * 删除系统提示词配置
     * 
     * @param promptId 系统提示词配置主键
     * @return 结果
     */
    public int deleteAiSystemPromptByPromptId(Long promptId);

    /**
     * 批量删除系统提示词配置
     * 
     * @param promptIds 需要删除的数据主键集合
     * @return 结果
     */
    public int deleteAiSystemPromptByPromptIds(Long[] promptIds);

    /**
     * 查询默认的系统提示词配置
     * 
     * @param promptType 提示词类型
     * @param sceneType 场景类型
     * @return 系统提示词配置
     */
    public AiSystemPrompt selectDefaultAiSystemPrompt(String promptType, String sceneType);

    /**
     * 更新默认提示词状态
     * 
     * @param promptId 提示词ID
     * @return 结果
     */
    public int updateDefaultPrompt(Long promptId);
}