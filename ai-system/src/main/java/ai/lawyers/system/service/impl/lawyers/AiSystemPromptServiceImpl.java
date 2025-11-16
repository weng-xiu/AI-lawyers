package ai.lawyers.system.service.impl.lawyers;

import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import ai.lawyers.system.mapper.lawyers.AiSystemPromptMapper;
import ai.lawyers.system.domain.lawyers.AiSystemPrompt;
import ai.lawyers.system.service.lawyers.IAiSystemPromptService;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * 系统提示词配置Service业务层处理
 * 
 * @author ai-lawyers
 * @date 2025-07-15
 */
@Service
public class AiSystemPromptServiceImpl implements IAiSystemPromptService 
{
    @Autowired
    private AiSystemPromptMapper aiSystemPromptMapper;

    /**
     * 查询系统提示词配置
     * 
     * @param promptId 系统提示词配置主键
     * @return 系统提示词配置
     */
    @Override
    public AiSystemPrompt selectAiSystemPromptByPromptId(Long promptId)
    {
        return aiSystemPromptMapper.selectAiSystemPromptByPromptId(promptId);
    }

    /**
     * 查询系统提示词配置列表
     * 
     * @param aiSystemPrompt 系统提示词配置
     * @return 系统提示词配置
     */
    @Override
    public List<AiSystemPrompt> selectAiSystemPromptList(AiSystemPrompt aiSystemPrompt)
    {
        return aiSystemPromptMapper.selectAiSystemPromptList(aiSystemPrompt);
    }

    /**
     * 新增系统提示词配置
     * 
     * @param aiSystemPrompt 系统提示词配置
     * @return 结果
     */
    @Override
    public int insertAiSystemPrompt(AiSystemPrompt aiSystemPrompt)
    {
        // 如果设置为默认提示词，先将其他同类型提示词设为非默认
        if ("1".equals(aiSystemPrompt.getIsDefault())) {
            aiSystemPromptMapper.updateDefaultPrompt(aiSystemPrompt.getPromptId());
        }
        return aiSystemPromptMapper.insertAiSystemPrompt(aiSystemPrompt);
    }

    /**
     * 修改系统提示词配置
     * 
     * @param aiSystemPrompt 系统提示词配置
     * @return 结果
     */
    @Override
    public int updateAiSystemPrompt(AiSystemPrompt aiSystemPrompt)
    {
        // 如果设置为默认提示词，先将其他同类型提示词设为非默认
        if ("1".equals(aiSystemPrompt.getIsDefault())) {
            aiSystemPromptMapper.updateDefaultPrompt(aiSystemPrompt.getPromptId());
        }
        return aiSystemPromptMapper.updateAiSystemPrompt(aiSystemPrompt);
    }

    /**
     * 批量删除系统提示词配置
     * 
     * @param promptIds 需要删除的系统提示词配置主键
     * @return 结果
     */
    @Override
    public int deleteAiSystemPromptByPromptIds(Long[] promptIds)
    {
        return aiSystemPromptMapper.deleteAiSystemPromptByPromptIds(promptIds);
    }

    /**
     * 删除系统提示词配置信息
     * 
     * @param promptId 系统提示词配置主键
     * @return 结果
     */
    @Override
    public int deleteAiSystemPromptByPromptId(Long promptId)
    {
        return aiSystemPromptMapper.deleteAiSystemPromptByPromptId(promptId);
    }

    /**
     * 获取默认的系统提示词配置
     * 
     * @param promptType 提示词类型
     * @param sceneType 场景类型
     * @return 系统提示词配置
     */
    @Override
    public AiSystemPrompt getDefaultAiSystemPrompt(String promptType, String sceneType)
    {
        return aiSystemPromptMapper.selectDefaultAiSystemPrompt(promptType, sceneType);
    }

    /**
     * 设置默认提示词
     * 
     * @param promptId 提示词ID
     * @return 结果
     */
    @Override
    public int setDefaultPrompt(Long promptId)
    {
        return aiSystemPromptMapper.updateDefaultPrompt(promptId);
    }

    /**
     * 预览提示词效果
     * 
     * @param promptId 提示词ID
     * @param variables 变量值(JSON格式)
     * @return 预览结果
     */
    @Override
    public String previewPrompt(Long promptId, String variables)
    {
        AiSystemPrompt prompt = aiSystemPromptMapper.selectAiSystemPromptByPromptId(promptId);
        if (prompt == null) {
            return "提示词不存在";
        }
        
        String content = prompt.getContent();
        if (variables == null || variables.isEmpty()) {
            return content;
        }
        
        try {
            // 解析变量JSON
            ObjectMapper mapper = new ObjectMapper();
            @SuppressWarnings("unchecked")
            Map<String, Object> variableMap = mapper.readValue(variables, Map.class);
            
            // 替换内容中的变量
            for (Map.Entry<String, Object> entry : variableMap.entrySet()) {
                String key = entry.getKey();
                Object value = entry.getValue();
                if (value != null) {
                    content = content.replaceAll("\\{" + key + "\\}", value.toString());
                }
            }
            
            return content;
        } catch (Exception e) {
            return "变量解析失败：" + e.getMessage();
        }
    }
}