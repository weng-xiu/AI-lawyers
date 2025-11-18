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
        AiModelConfig config = selectAiModelConfigByConfigId(configId);
        if (config == null) {
            return false;
        }
        
        try {
            // 根据模型类型调用不同的API
            if ("OpenAI".equals(config.getModelType())) {
                return testOpenAIConnection(config);
            } else if ("Claude".equals(config.getModelType())) {
                return testClaudeConnection(config);
            } else if ("ChatGLM".equals(config.getModelType())) {
                return testChatGLMConnection(config);
            } else {
                // 默认使用通用测试方法
                return testGenericConnection(config);
            }
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }
    
    /**
     * 调用AI模型API生成回答
     * 
     * @param question 用户问题
     * @param context 上下文信息
     * @return AI生成的回答
     */
    @Override
    public String callAiModel(String question, String context)
    {
        // 获取默认配置
        AiModelConfig config = getDefaultAiModelConfig();
        if (config == null) {
            return "未找到可用的AI模型配置，请联系管理员配置模型参数。";
        }
        
        try {
            // 根据模型类型调用不同的API
            if ("OpenAI".equals(config.getModelType()) || "gpt".equals(config.getModelType())) {
                return callOpenAI(config, question, context);
            } else if ("Claude".equals(config.getModelType())) {
                return callClaude(config, question, context);
            } else if ("ChatGLM".equals(config.getModelType())) {
                return callChatGLM(config, question, context);
            } else if ("local".equals(config.getModelType())) {
                return callLocalModel(config, question, context);
            } else {
                // 默认使用通用调用方法
                return callGenericModel(config, question, context);
            }
        } catch (Exception e) {
            e.printStackTrace();
            return "调用AI模型失败：" + e.getMessage();
        }
    }
    
    /**
     * 测试OpenAI模型连接
     */
    private boolean testOpenAIConnection(AiModelConfig config) {
        try {
            // 构建测试请求
            String testPrompt = "你好，请回复'连接成功'";
            String response = callOpenAI(config, testPrompt, null);
            return response != null && !response.isEmpty();
        } catch (Exception e) {
            return false;
        }
    }
    
    /**
     * 测试Claude模型连接
     */
    private boolean testClaudeConnection(AiModelConfig config) {
        try {
            // 构建测试请求
            String testPrompt = "你好，请回复'连接成功'";
            String response = callClaude(config, testPrompt, null);
            return response != null && !response.isEmpty();
        } catch (Exception e) {
            return false;
        }
    }
    
    /**
     * 测试ChatGLM模型连接
     */
    private boolean testChatGLMConnection(AiModelConfig config) {
        try {
            // 构建测试请求
            String testPrompt = "你好，请回复'连接成功'";
            String response = callChatGLM(config, testPrompt, null);
            return response != null && !response.isEmpty();
        } catch (Exception e) {
            return false;
        }
    }
    
    /**
     * 测试通用模型连接
     */
    private boolean testGenericConnection(AiModelConfig config) {
        try {
            // 构建测试请求
            String testPrompt = "你好，请回复'连接成功'";
            String response = callGenericModel(config, testPrompt, null);
            return response != null && !response.isEmpty();
        } catch (Exception e) {
            return false;
        }
    }
    
    /**
     * 调用OpenAI API
     */
    private String callOpenAI(AiModelConfig config, String question, String context) {
        try {
            // 构建请求体
            StringBuilder promptBuilder = new StringBuilder();
            if (context != null && !context.isEmpty()) {
                promptBuilder.append("背景信息：").append(context).append("\n\n");
            }
            promptBuilder.append("用户问题：").append(question);
            
            // 这里应该使用HTTP客户端调用OpenAI API
            // 为了简化示例，这里返回模拟结果
            // 实际实现需要使用RestTemplate或HttpClient调用真实API
            
            // 模拟API调用
            String simulatedResponse = generateLegalResponse(question, context);
            
            return simulatedResponse;
        } catch (Exception e) {
            throw new RuntimeException("调用OpenAI API失败：" + e.getMessage());
        }
    }
    
    /**
     * 调用Claude API
     */
    private String callClaude(AiModelConfig config, String question, String context) {
        try {
            // 构建请求体
            StringBuilder promptBuilder = new StringBuilder();
            if (context != null && !context.isEmpty()) {
                promptBuilder.append("背景信息：").append(context).append("\n\n");
            }
            promptBuilder.append("用户问题：").append(question);
            
            // 这里应该使用HTTP客户端调用Claude API
            // 为了简化示例，这里返回模拟结果
            
            // 模拟API调用
            String simulatedResponse = generateLegalResponse(question, context);
            
            return simulatedResponse;
        } catch (Exception e) {
            throw new RuntimeException("调用Claude API失败：" + e.getMessage());
        }
    }
    
    /**
     * 调用ChatGLM API
     */
    private String callChatGLM(AiModelConfig config, String question, String context) {
        try {
            // 构建请求体
            StringBuilder promptBuilder = new StringBuilder();
            if (context != null && !context.isEmpty()) {
                promptBuilder.append("背景信息：").append(context).append("\n\n");
            }
            promptBuilder.append("用户问题：").append(question);
            
            // 这里应该使用HTTP客户端调用ChatGLM API
            // 为了简化示例，这里返回模拟结果
            
            // 模拟API调用
            String simulatedResponse = generateLegalResponse(question, context);
            
            return simulatedResponse;
        } catch (Exception e) {
            throw new RuntimeException("调用ChatGLM API失败：" + e.getMessage());
        }
    }
    
    /**
     * 调用本地模型API
     */
    private String callLocalModel(AiModelConfig config, String question, String context) {
        try {
            // 构建请求体
            StringBuilder promptBuilder = new StringBuilder();
            if (context != null && !context.isEmpty()) {
                promptBuilder.append("背景信息：").append(context).append("\n\n");
            }
            promptBuilder.append("用户问题：").append(question);
            
            // 这里应该使用HTTP客户端调用本地模型API
            // 为了简化示例，这里返回模拟结果
            
            // 模拟API调用
            String simulatedResponse = generateLegalResponse(question, context);
            
            return simulatedResponse;
        } catch (Exception e) {
            throw new RuntimeException("调用本地模型API失败：" + e.getMessage());
        }
    }
    
    /**
     * 调用通用模型API
     */
    private String callGenericModel(AiModelConfig config, String question, String context) {
        try {
            // 构建请求体
            StringBuilder promptBuilder = new StringBuilder();
            if (context != null && !context.isEmpty()) {
                promptBuilder.append("背景信息：").append(context).append("\n\n");
            }
            promptBuilder.append("用户问题：").append(question);
            
            // 这里应该使用HTTP客户端调用通用API
            // 为了简化示例，这里返回模拟结果
            
            // 模拟API调用
            String simulatedResponse = generateLegalResponse(question, context);
            
            return simulatedResponse;
        } catch (Exception e) {
            throw new RuntimeException("调用通用模型API失败：" + e.getMessage());
        }
    }
    
    /**
     * 生成法律咨询响应
     * 
     * @param question 用户问题
     * @param context 上下文信息
     * @return 生成的法律响应
     */
    private String generateLegalResponse(String question, String context) {
        // 根据问题关键词生成更有针对性的回复
        String lowerQuestion = question.toLowerCase();
        
        StringBuilder response = new StringBuilder();
        response.append("根据您的问题，我提供以下法律建议：\n\n");
        
        // 根据问题类型提供不同的建议
        if (lowerQuestion.contains("离婚") || lowerQuestion.contains("婚姻")) {
            response.append("关于婚姻家庭问题：\n");
            response.append("1. 根据《民法典》规定，夫妻感情确已破裂是离婚的法定条件\n");
            response.append("2. 离婚涉及财产分割、子女抚养等问题，建议协商解决\n");
            response.append("3. 如无法协商，可向人民法院提起离婚诉讼\n");
        } 
        else if (lowerQuestion.contains("合同") || lowerQuestion.contains("违约")) {
            response.append("关于合同纠纷问题：\n");
            response.append("1. 根据《民法典》合同编规定，合同当事人应当全面履行合同义务\n");
            response.append("2. 违约方应当承担继续履行、采取补救措施或者赔偿损失等违约责任\n");
            response.append("3. 建议保留合同原件、履行凭证等相关证据\n");
        }
        else if (lowerQuestion.contains("劳动") || lowerQuestion.contains("工资") || lowerQuestion.contains("加班")) {
            response.append("关于劳动纠纷问题：\n");
            response.append("1. 根据《劳动法》和《劳动合同法》，用人单位应当按时足额支付劳动报酬\n");
            response.append("2. 加班工资应按照不低于工资的150%（工作日）、200%（休息日）、300%（法定节假日）支付\n");
            response.append("3. 建议保留劳动合同、工资条、考勤记录等证据\n");
        }
        else if (lowerQuestion.contains("交通") || lowerQuestion.contains("事故")) {
            response.append("关于交通事故问题：\n");
            response.append("1. 根据《道路交通安全法》，发生交通事故应当立即停车、保护现场、抢救伤员\n");
            response.append("2. 交通事故赔偿包括医疗费、误工费、护理费、交通费等\n");
            response.append("3. 建议及时报警、保留现场照片、维修发票等证据\n");
        }
        else {
            response.append("1. 首先需要了解具体情况和相关事实\n");
            response.append("2. 根据相关法律条款和规定进行分析\n");
            response.append("3. 建议您采取以下措施：收集证据、咨询专业律师、通过合法途径维权\n");
        }
        
        response.append("\n重要提示：\n");
        response.append("- 以上建议仅供参考，不构成正式的法律意见\n");
        response.append("- 法律问题具有复杂性，建议您咨询专业律师获取针对性建议\n");
        response.append("- 请注意保留相关证据材料，以备后续维权使用\n");
        
        return response.toString();
    }
}